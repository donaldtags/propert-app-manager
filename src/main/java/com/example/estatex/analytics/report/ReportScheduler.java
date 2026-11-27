package com.example.estatex.analytics.report;

import com.example.estatex.common.BusinessTime;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends scheduled reports. Off in tests, which call {@link ReportService#deliverDue} directly. */
@Component
@ConditionalOnProperty(name = "app.analytics.scheduling-enabled", havingValue = "true", matchIfMissing = true)
class ReportScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final ReportService reports;
    private final BusinessTime time;

    ReportScheduler(ReportService reports, BusinessTime time) {
        this.reports = reports;
        this.time = time;
    }

    @Scheduled(fixedDelay = 10, initialDelay = 3, timeUnit = TimeUnit.MINUTES)
    void deliver() {
        try {
            int n = reports.deliverDue(time.clock().instant());
            if (n > 0) log.info("Delivered {} scheduled report(s)", n);
        } catch (RuntimeException e) {
            log.error("Scheduled report delivery failed", e);
        }
    }
}
