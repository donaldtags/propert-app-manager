package com.example.estatex.analytics.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.Period;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ToDatePeriodTest {
    private static final ZoneId HARARE = ZoneId.of("Africa/Harare");

    @Test
    void monthToDateComparesTheSameNumberOfDays() {
        Instant now = Instant.parse("2026-11-26T08:00:00Z"); // 26 Nov 10:00 Harare
        Period nov = Period.containing(Grain.MONTH, now, HARARE);
        Period cut = AnalyticsEngine.toDate(nov.previous(HARARE), nov, now);
        assertThat(cut.start()).isEqualTo(Instant.parse("2026-09-30T22:00:00Z")); // 1 Oct 00:00 Harare
        assertThat(cut.end()).isEqualTo(Instant.parse("2026-10-26T08:00:00Z"));   // 26 Oct 10:00 Harare
    }

    @Test
    void aShorterPreviousPeriodIsNeverOverrun() {
        Instant now = Instant.parse("2027-03-31T20:00:00Z"); // 31 Mar 22:00 Harare
        Period march = Period.containing(Grain.MONTH, now, HARARE);
        Period feb = march.previous(HARARE);
        assertThat(AnalyticsEngine.toDate(feb, march, now).end()).isEqualTo(feb.end());
    }
}
