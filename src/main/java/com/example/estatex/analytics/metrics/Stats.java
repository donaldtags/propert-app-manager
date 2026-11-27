package com.example.estatex.analytics.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Descriptive statistics for market figures. Outliers are never removed: a legitimate $2m sale
 * is part of the market. Instead the median and the 25th–75th percentile range are reported
 * next to the mean, so one extreme value can't mislead.
 *
 * <p>Percentiles use linear interpolation between closest ranks (the "type 7" method used by
 * spreadsheets and R by default), so the median of [1, 2, 3, 4] is 2.5.
 */
public final class Stats {
    private Stats() {
    }

    public record Summary(int n, BigDecimal mean, BigDecimal median, BigDecimal p25, BigDecimal p75,
                          BigDecimal min, BigDecimal max, BigDecimal sum) {
        public static final Summary EMPTY = new Summary(0, null, null, null, null, null, null, BigDecimal.ZERO);
    }

    public static Summary summarise(Collection<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<>();
        for (BigDecimal v : values) {
            if (v != null) sorted.add(v);
        }
        if (sorted.isEmpty()) return Summary.EMPTY;
        Collections.sort(sorted);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : sorted) sum = sum.add(v);
        int n = sorted.size();
        return new Summary(n, sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                percentile(sorted, 0.5), percentile(sorted, 0.25), percentile(sorted, 0.75),
                scale(sorted.get(0)), scale(sorted.get(n - 1)), scale(sum));
    }

    /** {@code sorted} must be in ascending order and non-empty. */
    static BigDecimal percentile(List<BigDecimal> sorted, double p) {
        double h = (sorted.size() - 1) * p;
        int lo = (int) Math.floor(h);
        int hi = (int) Math.ceil(h);
        BigDecimal low = sorted.get(lo);
        if (lo == hi) return scale(low);
        BigDecimal fraction = BigDecimal.valueOf(h - lo);
        return scale(low.add(sorted.get(hi).subtract(low).multiply(fraction)));
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
