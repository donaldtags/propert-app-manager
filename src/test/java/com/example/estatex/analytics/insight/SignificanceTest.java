package com.example.estatex.analytics.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class SignificanceTest {

    @Test
    void poissonRateIsAnExactBinomialForSmallCounts() {
        // 10 vs 30 over equal periods: two-sided exact Binomial(40, 0.5) p = 2 × P(X ≤ 10) = 0.002221.
        Significance.Result r = Significance.poissonRate(30, 7, 10, 7);
        assertThat(r.pValue()).isCloseTo(0.002221, within(2e-5));
        assertThat(r.statistic()).isCloseTo(10 / Math.sqrt(10), within(1e-9));   // (30 − 20) / √(40 × ¼)
        assertThat(Significance.poissonRate(20, 7, 20, 7).pValue()).isCloseTo(1.0, within(1e-9));
        assertThat(Significance.poissonRate(0, 7, 0, 7).pValue()).isEqualTo(1.0);
    }

    @Test
    void poissonRateAdjustsForPeriodLength() {
        // 31 events in a 31-day month is the same rate as 30 in a 30-day month.
        Significance.Result r = Significance.poissonRate(31, 31, 30, 30);
        assertThat(r.statistic()).isCloseTo(0, within(1e-9));
        assertThat(r.pValue()).isGreaterThan(0.9);
    }

    @Test
    void poissonRateUsesTheNormalApproximationForLargeCounts() {
        Significance.Result r = Significance.poissonRate(1_100, 7, 1_000, 7);
        // z = 50 / √525 = 2.182; with continuity correction 49.5 / 22.91 = 2.160, two-sided p ≈ 0.0307.
        assertThat(r.statistic()).isCloseTo(2.1822, within(1e-3));
        assertThat(r.pValue()).isCloseTo(0.0307, within(3e-4));
    }

    @Test
    void medianShiftUsesARobustStandardError() {
        assertThat(Significance.medianShift(500, 100, 30, 500, 100, 30).pValue()).isCloseTo(1.0, within(1e-9));
        // σ = 100 / 1.349 = 74.13; SE each = 1.2533 × 74.13 / √30 = 16.96; z = 60 / (16.96 × √2) = 2.501.
        Significance.Result r = Significance.medianShift(560, 100, 30, 500, 100, 30);
        assertThat(r.statistic()).isCloseTo(2.501, within(2e-3));
        assertThat(r.pValue()).isCloseTo(0.0124, within(3e-4));
        assertThat(Significance.medianShift(560, 100, 1, 500, 100, 30).pValue()).isEqualTo(1.0);
    }

    @Test
    void twoProportions() {
        // 30/100 vs 10/100: pooled 0.2, SE = √(0.2 × 0.8 × 0.02) = 0.05657, z = 3.536, p = 0.000407.
        Significance.Result r = Significance.twoProportions(30, 100, 10, 100);
        assertThat(r.statistic()).isCloseTo(3.5355, within(1e-3));
        assertThat(r.pValue()).isCloseTo(0.000407, within(1e-5));
    }

    @Test
    void benjaminiHochbergKeepsInputOrderAndIsMonotone() {
        double[] q = Significance.benjaminiHochberg(List.of(0.01, 0.04, 0.03, 0.2));
        assertThat(q[0]).isCloseTo(0.04, within(1e-12));
        assertThat(q[1]).isCloseTo(0.0533333, within(1e-6));
        assertThat(q[2]).isCloseTo(0.0533333, within(1e-6));
        assertThat(q[3]).isCloseTo(0.2, within(1e-12));
        assertThat(Significance.benjaminiHochberg(List.of())).isEmpty();
    }

    @Test
    void numericHelpers() {
        assertThat(Significance.upperTail(1.959964)).isCloseTo(0.025, within(1e-6));
        assertThat(Significance.upperTail(0)).isCloseTo(0.5, within(1e-7));
        assertThat(Significance.logGamma(5)).isCloseTo(Math.log(24), within(1e-10));
        assertThat(Significance.binomialTwoSided(5, 10, 0.5)).isCloseTo(1.0, within(1e-9));
    }
}
