package com.example.primenestprop.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily sweep that auto-renews paid subscriptions and downgrades accounts that have been
 * unable to renew for longer than the grace period. Runs once a day - subscriptions are billed
 * in 30-day periods, so there is no need for tighter polling. */
@Component
class SubscriptionBillingScheduler {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingScheduler.class);
    private static final long ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L;

    private final SubscriptionService service;

    SubscriptionBillingScheduler(SubscriptionService service) {
        this.service = service;
    }

    @Scheduled(initialDelay = 60_000, fixedRate = ONE_DAY_MILLIS)
    public void runBillingSweep() {
        try {
            service.renewDuePaidSubscriptions();
        } catch (Exception e) {
            log.warn("Subscription renewal sweep failed: {}", e.getMessage());
        }
        try {
            service.downgradeStalePastDue();
        } catch (Exception e) {
            log.warn("Subscription downgrade sweep failed: {}", e.getMessage());
        }
    }
}