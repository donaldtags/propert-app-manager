package com.example.estatex.property;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.PlatformAccountService;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.payment.PaymentSucceededEvent;
import com.example.estatex.user.AppUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * There's no free listing tier: every private listing costs a flat monthly fee. The fee is a real
 * bill (a {@link PaymentPurpose#LISTING_FEE} payment the owner pays through checkout or their
 * wallet); nothing is marked paid without money arriving.
 * <ul>
 *   <li>A new listing waits unpublished ({@code AWAITING_PAYMENT}) until its first month is paid.</li>
 *   <li>When a paid month runs out, the daily sweep raises the next bill and the listing turns
 *       {@code PAST_DUE}; it stays live for the grace period and is then deactivated (INACTIVE).</li>
 *   <li>Paying any open bill makes the billing ACTIVE for 30 days and puts the listing back on the
 *       market if it was waiting for this payment or had been deactivated for non-payment.</li>
 * </ul>
 * Until 2026-11-27 these fees were self-confirmed without collecting money; analytics reports
 * those older ones as "charges recorded without payment".
 */
@Service
public class PropertyBillingService {
    public static final BigDecimal MONTHLY_FEE = new BigDecimal("7.00");
    public static final String CURRENCY = "USD";
    private static final int BILLING_PERIOD_DAYS = 30;
    private static final int GRACE_PERIOD_DAYS = 3;

    private final PropertyBillingRepository billings;
    private final PropertyRepository properties;
    private final PaymentService payments;
    private final PlatformAccountService platformAccountService;

    public PropertyBillingService(
            PropertyBillingRepository billings,
            PropertyRepository properties,
            @Lazy PaymentService payments,
            PlatformAccountService platformAccountService
    ) {
        this.billings = billings;
        this.properties = properties;
        this.payments = payments;
        this.platformAccountService = platformAccountService;
    }

    /** Raises the first month's bill for a new listing, which stays unpublished until it's paid. */
    @Transactional
    public PropertyBilling chargeForNewListing(Property property, AppUser landlord) {
        PropertyBilling billing = new PropertyBilling();
        billing.setProperty(property);
        billing.setStatus(PropertyBillingStatus.AWAITING_PAYMENT);
        billing.setPendingPaymentId(raiseBill(property, landlord, "Listing fee: " + property.getTitle()).getId());
        return billings.save(billing);
    }

    private Payment raiseBill(Property property, AppUser owner, String description) {
        AppUser platformAccount = platformAccountService.billingAccount();
        return payments.create(new PaymentDtos.CreatePaymentRequest(
                platformAccount.getId(),
                property.getId(),
                null,
                MONTHLY_FEE,
                CURRENCY,
                "platform",
                description
        ), owner, PaymentPurpose.LISTING_FEE, "propertyId=" + property.getId(), null);
    }

    @Transactional(readOnly = true)
    public PropertyBillingDtos.PropertyBillingResponse describe(Property property) {
        PropertyBilling billing = billings.findByProperty(property)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No billing record for this listing"));
        return new PropertyBillingDtos.PropertyBillingResponse(
                billing.getStatus(), billing.getCurrentPeriodEnd(), MONTHLY_FEE, CURRENCY, billing.getPendingPaymentId()
        );
    }

    /**
     * Whether a private listing may be (or stay) on the market as far as fees go: paid up, or
     * inside a renewal's grace period. Listings without a billing record (agency listings, and
     * private listings created before billing existed) aren't held back here.
     */
    @Transactional(readOnly = true)
    public boolean feesAllowLive(Property property) {
        return billings.findByProperty(property)
                .map(b -> b.getStatus() == PropertyBillingStatus.ACTIVE
                        || (b.getStatus() == PropertyBillingStatus.PAST_DUE && !Boolean.TRUE.equals(b.getDeactivatedForNonPayment())))
                .orElse(true);
    }

    /** Raises the next bill for everyone whose paid month has run out; called by the daily sweep. */
    @Transactional
    public void renewDueBillings() {
        for (PropertyBilling billing : billings.findByStatusAndCurrentPeriodEndBefore(PropertyBillingStatus.ACTIVE, Instant.now())) {
            Property property = billing.getProperty();
            billing.setStatus(PropertyBillingStatus.PAST_DUE);
            billing.setPastDueSince(Instant.now());
            billing.setPendingPaymentId(raiseBill(property, property.getLandlord(), "Listing fee renewal: " + property.getTitle()).getId());
            billing.setUpdatedAt(Instant.now());
        }
    }

    /** A paid listing fee: 30 more days, and the listing goes (back) on the market if it was held for this. */
    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Payment payment = event.payment();
        if (payment.getPurposeType() != PaymentPurpose.LISTING_FEE || payment.getMetadata() == null
                || !payment.getMetadata().startsWith("propertyId=")) {
            return;
        }
        Long propertyId = Long.valueOf(payment.getMetadata().substring("propertyId=".length()));
        Property property = properties.findById(propertyId).orElse(null);
        if (property == null) return;
        PropertyBilling billing = billings.findByProperty(property).orElse(null);
        if (billing == null) return;
        boolean firstPayment = billing.getStatus() == PropertyBillingStatus.AWAITING_PAYMENT;
        boolean wasDeactivated = Boolean.TRUE.equals(billing.getDeactivatedForNonPayment());
        Instant from = billing.getCurrentPeriodEnd() != null && billing.getCurrentPeriodEnd().isAfter(Instant.now())
                ? billing.getCurrentPeriodEnd() : Instant.now();
        billing.setStatus(PropertyBillingStatus.ACTIVE);
        billing.setCurrentPeriodEnd(from.plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS));
        billing.setPastDueSince(null);
        billing.setDeactivatedForNonPayment(null);
        if (payment.getId().equals(billing.getPendingPaymentId())) billing.setPendingPaymentId(null);
        billing.setUpdatedAt(Instant.now());
        boolean held = (firstPayment && property.getStatus() == PropertyStatus.DRAFT)
                || (wasDeactivated && property.getStatus() == PropertyStatus.INACTIVE);
        if (held && !com.example.estatex.agent.AgentLicenceService.mustWaitForLicence(property.getLandlord())) {
            property.setStatus(PropertyStatus.AVAILABLE);
            properties.save(property);
        }
    }

    /** Deactivates listings that have been unable to renew for longer than the grace period. */
    @Transactional
    public void deactivateStalePastDue() {
        Instant cutoff = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        for (PropertyBilling billing : billings.findByStatusAndPastDueSinceBefore(PropertyBillingStatus.PAST_DUE, cutoff)) {
            Property property = billing.getProperty();
            // Only a live listing is taken down (and later restored); one the owner withdrew, sold
            // or let stays as the owner left it.
            if (property.getStatus() == PropertyStatus.AVAILABLE) {
                property.setStatus(PropertyStatus.INACTIVE);
                properties.save(property);
                billing.setDeactivatedForNonPayment(true);
            }
            // Clearing pastDueSince (status stays PAST_DUE, the bill stays open) keeps this out of
            // future sweeps. Paying the open bill puts a deactivated listing back on the market.
            billing.setPastDueSince(null);
            billing.setUpdatedAt(Instant.now());
        }
    }
}