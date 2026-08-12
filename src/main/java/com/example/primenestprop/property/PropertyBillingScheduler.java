package com.example.primenestprop.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily sweep that auto-renews the per-listing fee and deactivates listings that have been
 * unable to renew for longer than the grace period. */
@Component
class PropertyBillingScheduler {
    private static final Logger log = LoggerFactory.getLogger(PropertyBillingScheduler.class);
    private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L;

    private final PropertyBillingService service;

    PropertyBillingScheduler(PropertyBillingService service) {
        this.service = service;
    }

    @Scheduled(initialDelay = 90_000, fixedRate = ONE_DAY_MILLIS)
    public void runBillingSweep() {
        try {
            service.renewDueBillings();
        } catch (Exception e) {
            log.warn("Property billing renewal sweep failed: {}", e.getMessage());
        }
        try {
            service.deactivateStalePastDue();
        } catch (Exception e) {
            log.warn("Property billing deactivation sweep failed: {}", e.getMessage());
        }
    }
}