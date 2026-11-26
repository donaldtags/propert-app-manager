package com.example.estatex.analytics.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Known datasets checked against values worked out by hand (and matching spreadsheet PERCENTILE.INC). */
class StatsTest {
    private static List<BigDecimal> of(long... v) {
        return Arrays.stream(v).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void oddCount() {
        Stats.Summary s = Stats.summarise(of(500, 600, 700, 800, 5000));
        assertThat(s.n()).isEqualTo(5);
        assertThat(s.median()).isEqualByComparingTo("700");
        assertThat(s.mean()).isEqualByComparingTo("1520");
        assertThat(s.p25()).isEqualByComparingTo("600");
        assertThat(s.p75()).isEqualByComparingTo("800");
        assertThat(s.min()).isEqualByComparingTo("500");
        assertThat(s.max()).isEqualByComparingTo("5000");
        assertThat(s.sum()).isEqualByComparingTo("7600");
    }

    @Test
    void evenCountInterpolates() {
        Stats.Summary s = Stats.summarise(of(1, 2, 3, 4));
        assertThat(s.median()).isEqualByComparingTo("2.5");
        assertThat(s.p25()).isEqualByComparingTo("1.75");
        assertThat(s.p75()).isEqualByComparingTo("3.25");
        assertThat(s.mean()).isEqualByComparingTo("2.5");
    }

    @Test
    void unsortedInputAndOneValue() {
        assertThat(Stats.summarise(of(9, 1, 5)).median()).isEqualByComparingTo("5");
        Stats.Summary one = Stats.summarise(of(42));
        assertThat(one.median()).isEqualByComparingTo("42");
        assertThat(one.p25()).isEqualByComparingTo("42");
        assertThat(one.p75()).isEqualByComparingTo("42");
    }

    @Test
    void emptyAndNullsGiveNoFigures() {
        List<BigDecimal> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(Stats.summarise(withNull).n()).isZero();
        assertThat(Stats.summarise(List.of()).median()).isNull();
    }

    @Test
    void aLegitimateOutlierIsKeptButBarelyMovesTheMedian() {
        Stats.Summary s = Stats.summarise(of(100_000, 110_000, 120_000, 130_000, 2_500_000));
        assertThat(s.n()).isEqualTo(5);
        assertThat(s.max()).isEqualByComparingTo("2500000");
        assertThat(s.median()).isEqualByComparingTo("120000");
        assertThat(s.mean()).isEqualByComparingTo("592000");
    }
}
