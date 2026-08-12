package com.example.primenestprop.property;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.PlatformAccountService;
import com.example.primenestprop.payment.Payment;
import com.example.primenestprop.payment.PaymentDtos;
import com.example.primenestprop.payment.PaymentService;
import com.example.primenestprop.user.AppUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** There's no free listing tier: every property costs a flat monthly fee, charged instantly on
 * creation (same simulated-payment pattern as subscription upgrades) and renewed by a daily
 * sweep. A listing that can't renew past the grace period gets marked INACTIVE rather than
 * deleted, so the landlord can pick up billing again without re-creating it. */
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

    /** Charges the landlord instantly for a newly-created listing. */
    @Transactional
    public PropertyBilling chargeForNewListing(Property property, AppUser landlord) {
        AppUser platformAccount = platformAccountService.billingAccount();
        Payment payment = payments.create(new PaymentDtos.CreatePaymentRequest(
                platformAccount.getId(),
                property.getId(),
                null,
                MONTHLY_FEE,
                CURRENCY,
                "platform",
                "Listing fee: " + property.getTitle()
        ), landlord);
        payments.markSuccessful(payment.getId(), platformAccount);

        PropertyBilling billing = new PropertyBilling();
        billing.setProperty(property);
        billing.setStatus(PropertyBillingStatus.ACTIVE);
        billing.setCurrentPeriodEnd(Instant.now().plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS));
        return billings.save(billing);
    }

    @Transactional(readOnly = true)
    public PropertyBillingDtos.PropertyBillingResponse describe(Property property) {
        PropertyBilling billing = billings.findByProperty(property)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No billing record for this listing"));
        return new PropertyBillingDtos.PropertyBillingResponse(
                billing.getStatus(), billing.getCurrentPeriodEnd(), MONTHLY_FEE, CURRENCY
        );
    }

    /** Renews everyone whose paid period has lapsed; called by the daily billing sweep. */
    @Transactional
    public void renewDueBillings() {
        for (PropertyBilling billing : billings.findByStatusAndCurrentPeriodEndBefore(PropertyBillingStatus.ACTIVE, Instant.now())) {
            Property property = billing.getProperty();
            try {
                AppUser platformAccount = platformAccountService.billingAccount();
                Payment payment = payments.create(new PaymentDtos.CreatePaymentRequest(
                        platformAccount.getId(),
                        property.getId(),
                        null,
                        MONTHLY_FEE,
                        CURRENCY,
                        "platform",
                        "Listing fee renewal: " + property.getTitle()
                ), property.getLandlord());
                payments.markSuccessful(payment.getId(), platformAccount);
                billing.setCurrentPeriodEnd(Instant.now().plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS));
                billing.setPastDueSince(null);
            } catch (Exception e) {
                billing.setStatus(PropertyBillingStatus.PAST_DUE);
                billing.setPastDueSince(Instant.now());
            }
            billing.setUpdatedAt(Instant.now());
        }
    }

    /** Deactivates listings that have been unable to renew for longer than the grace period. */
    @Transactional
    public void deactivateStalePastDue() {
        Instant cutoff = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        for (PropertyBilling billing : billings.findByStatusAndPastDueSinceBefore(PropertyBillingStatus.PAST_DUE, cutoff)) {
            Property property = billing.getProperty();
            if (property.getStatus() != PropertyStatus.SOLD) {
                property.setStatus(PropertyStatus.INACTIVE);
                properties.save(property);
            }
            // Deactivated: stop retrying the charge daily. Clearing pastDueSince (while leaving
            // status PAST_DUE to reflect it's unpaid) keeps this out of future sweeps until the
            // landlord takes some action to reactivate the listing.
            billing.setPastDueSince(null);
            billing.setUpdatedAt(Instant.now());
        }
    }
}