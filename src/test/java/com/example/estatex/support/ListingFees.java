package com.example.estatex.support;

import com.example.estatex.common.PlatformAccountService;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.property.PropertyBilling;
import com.example.estatex.property.PropertyBillingRepository;
import com.example.estatex.property.PropertyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pays a new private listing's first listing fee in tests, through the same success path a
 * Paynow/card confirmation takes (PaymentSucceededEvent), so the listing goes live exactly as it
 * would for a real owner. Since 2026-11-27 a private listing stays unpublished until then.
 */
@Component
public class ListingFees {
    private final PropertyRepository properties;
    private final PropertyBillingRepository billings;
    private final PaymentService payments;
    private final PlatformAccountService platform;
    private final TransactionTemplate tx;

    public ListingFees(PropertyRepository properties, PropertyBillingRepository billings, PaymentService payments,
                       PlatformAccountService platform, org.springframework.transaction.PlatformTransactionManager tm) {
        this.properties = properties;
        this.billings = billings;
        this.payments = payments;
        this.platform = platform;
        this.tx = new TransactionTemplate(tm);
    }

    /** Pays the open listing fee (if any) and returns the property id, for chaining. */
    public long paid(long propertyId) {
        tx.executeWithoutResult(s -> {
            PropertyBilling billing = billings.findByProperty(properties.findById(propertyId).orElseThrow()).orElse(null);
            if (billing != null && billing.getPendingPaymentId() != null) {
                payments.markSuccessful(billing.getPendingPaymentId(), platform.billingAccount());
            }
        });
        return propertyId;
    }
}
