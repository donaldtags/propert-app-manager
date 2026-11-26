package com.example.estatex.analytics.engine;

import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the engine on the server's own schedule: no browser or signed-in admin is needed.
 * At 5 past every hour (Harare), at 02:00 for the nightly reconciliation, and once shortly after
 * startup to catch up on anything missed while the server was down.
 *
 * <p>Switched off in tests ({@code app.analytics.scheduling-enabled=false}), which call the
 * engine directly.
 */
@Component
@ConditionalOnProperty(name = "app.analytics.scheduling-enabled", havingValue = "true", matchIfMissing = true)
class AnalyticsScheduler {
    static final String HOURLY_CRON = "0 5 * * * *";
    static final String NIGHTLY_CRON = "0 0 2 * * *";
    private static final Logger log = LoggerFactory.getLogger(AnalyticsScheduler.class);

    private final AnalyticsEngine engine;

    AnalyticsScheduler(AnalyticsEngine engine) {
        this.engine = engine;
    }

    @Scheduled(cron = HOURLY_CRON, zone = "Africa/Harare")
    void hourly() {
        log.info("Analytics hourly run: {}", engine.runHourly("SCHEDULE"));
    }

    @Scheduled(cron = NIGHTLY_CRON, zone = "Africa/Harare")
    void nightly() {
        log.info("Analytics nightly run: {}", engine.runNightly());
    }

    /** Off the startup thread, so a long catch-up never delays the app accepting requests. */
    @EventListener(ApplicationReadyEvent.class)
    void catchUpAfterStartup() {
        Executors.newVirtualThreadPerTaskExecutor().execute(() -> {
            try {
                log.info("Analytics catch-up after startup: {}", engine.runHourly("STARTUP"));
            } catch (RuntimeException e) {
                log.error("Analytics catch-up after startup failed", e);
            }
        });
    }
}
