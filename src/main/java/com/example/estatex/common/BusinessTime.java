package com.example.estatex.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * The single source of "what day is it" for business rules (mandate expiry, rent due dates,
 * campaign day N, scorecard weeks). Timestamps are still stored as UTC {@link java.time.Instant};
 * only date-based decisions go through here, always in Africa/Harare. The {@link Clock} is a bean
 * so tests can pin it.
 */
@Component
public class BusinessTime {
    private final Clock clock;
    private final ZoneId zone;

    public BusinessTime(Clock clock, @Value("${app.timezone:Africa/Harare}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(zone));
    }

    public Clock clock() {
        return clock;
    }

    @Configuration
    static class ClockConfig {
        @Bean
        @ConditionalOnMissingBean
        Clock systemClock() {
            return Clock.systemUTC();
        }
    }
}
