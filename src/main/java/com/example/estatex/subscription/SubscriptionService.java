package com.example.estatex.subscription;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.PlatformAccountService;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.payment.PaymentSucceededEvent;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyService;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Landlord/agent subscription tiers (Starter/Growth/Professional). Plan pricing and per-plan
 * feature flags are admin-editable ({@link SubscriptionPlanSettings}); this service resolves a
 * user's current plan, handles paid upgrades (charged instantly through the existing Payment
 * flow, same pattern as featured listings), and gives the rest of the app two checks to call:
 * {@link #requirePropertyCapacity} before creating a listing, and {@link #requireFeature} before
 * a Growth+-only action.
 */
@Service
public class SubscriptionService {
    private static final int BILLING_PERIOD_DAYS = 30;
    /** How long a failed renewal stays PAST_DUE before we downgrade the account to Starter. */
    private static final int GRACE_PERIOD_DAYS = 3;

    private final LandlordSubscriptionRepository subscriptions;
    private final SubscriptionPlanSettingsRepository planSettings;
    private final PropertyService properties;
    private final PaymentService payments;
    private final PlatformAccountService platformAccountService;

    private final com.example.estatex.referral.ReferralService referralService;

    public SubscriptionService(
            LandlordSubscriptionRepository subscriptions,
            SubscriptionPlanSettingsRepository planSettings,
            PropertyService properties,
            PaymentService payments,
            PlatformAccountService platformAccountService,
            com.example.estatex.referral.ReferralService referralService
    ) {
        this.referralService = referralService;
        this.subscriptions = subscriptions;
        this.planSettings = planSettings;
        this.properties = properties;
        this.payments = payments;
        this.platformAccountService = platformAccountService;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanSettings> allPlanSettings() {
        return Arrays.stream(SubscriptionPlan.values()).map(this::planSettings).toList();
    }

    @Transactional
    public SubscriptionPlanSettings planSettings(SubscriptionPlan plan) {
        return planSettings.findById(plan).orElseGet(() -> planSettings.save(new SubscriptionPlanSettings(plan)));
    }

    @Transactional
    public SubscriptionPlanSettings updatePlanSettings(SubscriptionPlan plan, SubscriptionDtos.UpdatePlanSettingsRequest request, AppUser currentUser) {
        if (!currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can change subscription plan pricing");
        }
        SubscriptionPlanSettings row = planSettings(plan);
        row.setMonthlyPrice(request.monthlyPrice());
        row.setCurrency(request.currency().toUpperCase());
        row.setMaxProperties(request.maxProperties());
        row.setEscrowEnabled(request.escrowEnabled());
        row.setDigitalLeasesEnabled(request.digitalLeasesEnabled());
        row.setMaintenanceCoordinationEnabled(request.maintenanceCoordinationEnabled());
        row.setRentRemindersEnabled(request.rentRemindersEnabled());
        row.setAiPricingEnabled(request.aiPricingEnabled());
        row.setTenantPassportEnabled(request.tenantPassportEnabled());
        row.setReportsEnabled(request.reportsEnabled());
        row.setUpdatedAt(Instant.now());
        return planSettings.save(row);
    }

    @Transactional
    public LandlordSubscription subscriptionFor(AppUser user) {
        return subscriptions.findByUser(user).orElseGet(() -> {
            LandlordSubscription created = new LandlordSubscription();
            created.setUser(user);
            created.setPlan(SubscriptionPlan.STARTER);
            created.setStatus(SubscriptionStatus.ACTIVE);
            return subscriptions.save(created);
        });
    }

    @Transactional
    public SubscriptionDtos.SubscriptionResponse describe(AppUser user) {
        LandlordSubscription subscription = subscriptionFor(user);
        SubscriptionPlanSettings settings = planSettings(subscription.getPlan());
        return new SubscriptionDtos.SubscriptionResponse(
                subscription.getPlan(),
                subscription.getStatus(),
                subscription.getCurrentPeriodEnd(),
                settings.getMaxProperties(),
                activePropertyCount(user),
                Arrays.stream(SubscriptionFeature.values()).filter(settings::supports).toList(),
                subscription.getPendingPaymentId()
        );
    }

    /** Only handles the free Starter tier now - a paid upgrade must go through a real checkout
     * (see {@link #initiateUpgradeCheckout}) instead of self-confirming its own payment. */
    @Transactional
    public SubscriptionDtos.SubscriptionResponse subscribe(AppUser user, SubscriptionPlan targetPlan) {
        if (targetPlan != SubscriptionPlan.STARTER) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Upgrading to a paid plan requires checkout - use POST /subscriptions/checkout");
        }
        LandlordSubscription subscription = subscriptionFor(user);
        subscription.setPlan(SubscriptionPlan.STARTER);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(null);
        subscription.setPastDueSince(null);
        subscription.setUpdatedAt(Instant.now());
        return describe(user);
    }

    /** Starts a real Paynow/Stripe checkout for a paid plan upgrade; the upgrade itself is
     * applied by {@link #onPaymentSucceeded} once the gateway confirms payment. */
    @Transactional
    public PaymentDtos.CheckoutResponse initiateUpgradeCheckout(AppUser user, SubscriptionPlan targetPlan, String provider) {
        if (targetPlan == SubscriptionPlan.STARTER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Starter is free - use POST /subscriptions/subscribe instead");
        }
        SubscriptionPlanSettings settings = planSettings(targetPlan);
        AppUser platformAccount = platformAccountService.billingAccount();
        // A friend's referral: a discount on the very first subscription payment only.
        java.util.Optional<com.example.estatex.referral.ReferralService.Discount> discount =
                referralService.discountFor(user, settings.getMonthlyPrice());
        java.math.BigDecimal amount = discount.map(com.example.estatex.referral.ReferralService.Discount::payable)
                .orElse(settings.getMonthlyPrice());
        String metadata = "plan=" + targetPlan + discount.map(d -> ",referralId=" + d.referralId() + ",discountPercent=" + d.percent()
                + ",discountCents=" + d.amount().movePointRight(2).longValueExact()).orElse("");
        Payment payment = payments.create(
                new PaymentDtos.CreatePaymentRequest(
                        platformAccount.getId(),
                        null,
                        null,
                        amount,
                        settings.getCurrency(),
                        provider,
                        "Subscription: " + targetPlan + " plan" + discount.map(d -> " (" + d.percent() + "% referral discount)").orElse("")
                ),
                user,
                PaymentPurpose.SUBSCRIPTION,
                metadata,
                null
        );
        return payments.initiateCheckout(payment.getId(), provider, user);
    }

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Payment payment = event.payment();
        if (payment.getPurposeType() != PaymentPurpose.SUBSCRIPTION) {
            return;
        }
        SubscriptionPlan targetPlan = planFromMetadata(payment.getMetadata());
        if (targetPlan == null) {
            return;
        }
        LandlordSubscription subscription = subscriptionFor(payment.getPayer());
        subscription.setPlan(targetPlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(Instant.now().plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS));
        subscription.setPastDueSince(null);
        subscription.setPendingPaymentId(null);
        subscription.setUpdatedAt(Instant.now());
    }

    private SubscriptionPlan planFromMetadata(String metadata) {
        if (metadata == null) {
            return null;
        }
        for (String part : metadata.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals("plan")) {
                try {
                    return SubscriptionPlan.valueOf(kv[1]);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Renews everyone whose paid period has lapsed; called by the daily billing sweep.
     * Still self-confirmed rather than gateway-charged: a true unattended renewal needs a
     * stored payment method (Stripe Customer + card-on-file, or equivalent), which is a
     * separate feature from the checkout-redirect flow this integration adds. */
    @Transactional
    public void renewDuePaidSubscriptions() {
        // Raises the next month's bill. Until it's paid the plan is PAST_DUE (still usable for the
        // grace period), then it drops to Starter. Paying it (checkout or wallet) reactivates the
        // plan through onPaymentSucceeded. Until 2026-11-27 renewals were self-confirmed without
        // collecting money; analytics reports those as "charges recorded without payment".
        for (LandlordSubscription subscription : subscriptions.findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, Instant.now())) {
            if (subscription.getPlan() == SubscriptionPlan.STARTER) {
                continue;
            }
            SubscriptionPlanSettings settings = planSettings(subscription.getPlan());
            AppUser platformAccount = platformAccountService.billingAccount();
            Payment payment = payments.create(new PaymentDtos.CreatePaymentRequest(
                    platformAccount.getId(),
                    null,
                    null,
                    settings.getMonthlyPrice(),
                    settings.getCurrency(),
                    "platform",
                    "Subscription renewal: " + subscription.getPlan() + " plan"
            ), subscription.getUser(), PaymentPurpose.SUBSCRIPTION, "plan=" + subscription.getPlan() + ",renewal=true", null);
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            subscription.setPastDueSince(Instant.now());
            subscription.setPendingPaymentId(payment.getId());
            subscription.setUpdatedAt(Instant.now());
        }
    }

    /** Downgrades anyone who's been unable to renew for longer than the grace period. */
    @Transactional
    public void downgradeStalePastDue() {
        Instant cutoff = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        for (LandlordSubscription subscription : subscriptions.findByStatusAndPastDueSinceBefore(SubscriptionStatus.PAST_DUE, cutoff)) {
            subscription.setPlan(SubscriptionPlan.STARTER);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodEnd(null);
            subscription.setPastDueSince(null);
            subscription.setUpdatedAt(Instant.now());
        }
    }

    @Transactional
    public void requirePropertyCapacity(AppUser landlord) {
        LandlordSubscription subscription = subscriptionFor(landlord);
        SubscriptionPlanSettings settings = planSettings(subscription.getPlan());
        Integer max = settings.getMaxProperties();
        if (max == null) {
            return;
        }
        if (activePropertyCount(landlord) >= max) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Your " + subscription.getPlan() + " plan allows up to " + max
                            + " properties. Upgrade your subscription to list more.");
        }
    }

    @Transactional
    public void requireFeature(AppUser owner, SubscriptionFeature feature) {
        if (!hasFeature(owner, feature)) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "This feature requires upgrading to the Growth plan or higher.");
        }
    }

    @Transactional
    public boolean hasFeature(AppUser owner, SubscriptionFeature feature) {
        SubscriptionPlanSettings settings = planSettings(subscriptionFor(owner).getPlan());
        return settings.supports(feature);
    }

    private int activePropertyCount(AppUser user) {
        Set<Long> ids = new LinkedHashSet<>();
        properties.forLandlord(user.getId()).forEach(p -> ids.add(p.getId()));
        properties.forAgent(user.getId()).forEach(p -> ids.add(p.getId()));
        return ids.size();
    }
}