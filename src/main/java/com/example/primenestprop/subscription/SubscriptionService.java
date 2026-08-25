package com.example.primenestprop.subscription;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.PlatformAccountService;
import com.example.primenestprop.payment.Payment;
import com.example.primenestprop.payment.PaymentDtos;
import com.example.primenestprop.payment.PaymentService;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    public SubscriptionService(
            LandlordSubscriptionRepository subscriptions,
            SubscriptionPlanSettingsRepository planSettings,
            PropertyService properties,
            PaymentService payments,
            PlatformAccountService platformAccountService
    ) {
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
                Arrays.stream(SubscriptionFeature.values()).filter(settings::supports).toList()
        );
    }

    @Transactional
    public SubscriptionDtos.SubscriptionResponse subscribe(AppUser user, SubscriptionPlan targetPlan) {
        LandlordSubscription subscription = subscriptionFor(user);
        SubscriptionPlanSettings settings = planSettings(targetPlan);

        if (targetPlan == SubscriptionPlan.STARTER) {
            subscription.setPlan(SubscriptionPlan.STARTER);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodEnd(null);
            subscription.setPastDueSince(null);
        } else {
            AppUser platformAccount = platformAccountService.billingAccount();
            Payment payment = payments.create(new PaymentDtos.CreatePaymentRequest(
                    platformAccount.getId(),
                    null,
                    null,
                    settings.getMonthlyPrice(),
                    settings.getCurrency(),
                    "platform",
                    "Subscription: " + targetPlan + " plan"
            ), user);
            payments.markSuccessful(payment.getId(), platformAccount);

            subscription.setPlan(targetPlan);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodEnd(Instant.now().plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS));
            subscription.setPastDueSince(null);
        }
        subscription.setUpdatedAt(Instant.now());
        return describe(user);
    }

    /** Renews everyone whose paid period has lapsed; called by the daily billing sweep. */
    @Transactional
    public void renewDuePaidSubscriptions() {
        for (LandlordSubscription subscription : subscriptions.findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, Instant.now())) {
            if (subscription.getPlan() == SubscriptionPlan.STARTER) {
                continue;
            }
            try {
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
                ), subscription.getUser());
                payments.markSuccessful(payment.getId(), platformAccount);
                subscription.setCurrentPeriodEnd(Instant.now().plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS));
                subscription.setPastDueSince(null);
            } catch (Exception e) {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                subscription.setPastDueSince(Instant.now());
            }
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