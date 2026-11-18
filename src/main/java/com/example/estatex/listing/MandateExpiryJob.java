package com.example.estatex.listing;

import com.example.estatex.common.BusinessTime;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Just after midnight Harare time, expires mandates whose last day was yesterday and unpublishes
 * the listings that depended on them. Also runs once at startup so a server that was down at
 * midnight catches up immediately rather than a day late.
 */
@Component
class MandateExpiryJob {
    private final MandateService mandates;
    private final BusinessTime time;

    MandateExpiryJob(MandateService mandates, BusinessTime time) {
        this.mandates = mandates;
        this.time = time;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Harare")
    void nightly() {
        mandates.expireMandates(time.today());
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        mandates.expireMandates(time.today());
    }
}
