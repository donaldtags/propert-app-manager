package com.example.estatex.stay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 10 minutes: release expired holds and unanswered requests, complete finished stays. */
@Component
class StayHousekeepingJob {
    private static final Logger log = LoggerFactory.getLogger(StayHousekeepingJob.class);
    private final StayService service;

    StayHousekeepingJob(StayService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.stays.housekeeping-ms:600000}", initialDelayString = "${app.stays.housekeeping-initial-ms:60000}")
    void run() {
        try {
            int changed = service.housekeeping();
            if (changed > 0) log.info("stay_housekeeping changed={}", changed);
        } catch (RuntimeException e) {
            log.warn("stay_housekeeping failed", e);
        }
    }
}
