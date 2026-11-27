package com.example.estatex.analytics.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodTest {
    private static final ZoneId HARARE = ZoneId.of("Africa/Harare"); // UTC+2, no daylight saving

    @Test
    void daysFollowHarareMidnightNotUtc() {
        // 23:30 UTC on 25 Nov is 01:30 on 26 Nov in Harare.
        Period day = Period.containing(Grain.DAY, Instant.parse("2026-11-25T23:30:00Z"), HARARE);
        assertThat(day.start()).isEqualTo(Instant.parse("2026-11-25T22:00:00Z"));
        assertThat(day.end()).isEqualTo(Instant.parse("2026-11-26T22:00:00Z"));
    }

    @Test
    void weeksStartOnMondayAndQuartersOnTheirFirstMonth() {
        Instant thursday = Instant.parse("2026-11-26T10:00:00Z");
        assertThat(Period.containing(Grain.WEEK, thursday, HARARE).start()).isEqualTo(Instant.parse("2026-11-22T22:00:00Z"));
        Period q = Period.containing(Grain.QUARTER, thursday, HARARE);
        assertThat(q.start()).isEqualTo(Instant.parse("2026-09-30T22:00:00Z"));
        assertThat(q.end()).isEqualTo(Instant.parse("2026-12-31T22:00:00Z"));
        Period y = Period.containing(Grain.YEAR, thursday, HARARE);
        assertThat(y.start()).isEqualTo(Instant.parse("2025-12-31T22:00:00Z"));
    }

    @Test
    void incompletePeriodsRunToNow() {
        Instant now = Instant.parse("2026-11-26T10:00:00Z");
        Period month = Period.containing(Grain.MONTH, now, HARARE);
        assertThat(month.isComplete(now)).isFalse();
        assertThat(month.asOf(now)).isEqualTo(now);
        Period previous = month.previous(HARARE);
        assertThat(previous.isComplete(now)).isTrue();
        assertThat(previous.asOf(now)).isEqualTo(previous.end());
        assertThat(previous.end()).isEqualTo(month.start());
    }

    @Test
    void overlappingListsEveryPeriodTouched() {
        List<Period> hours = Period.overlapping(Grain.HOUR, Instant.parse("2026-11-26T08:00:00Z"),
                Instant.parse("2026-11-26T11:00:00Z"), HARARE);
        assertThat(hours).hasSize(3);
        List<Period> days = Period.overlapping(Grain.DAY, Instant.parse("2026-11-25T21:00:00Z"),
                Instant.parse("2026-11-26T23:00:00Z"), HARARE);
        assertThat(days).hasSize(3);
    }
}
