package com.example.estatex.analytics.insight;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The significance tests behind market observations (docs/admin-analytics-plan.md, Phase G).
 * Pure functions, no state. Every test returns a two-sided p-value; callers decide on thresholds,
 * minimum sample sizes and the false-discovery correction ({@link #benjaminiHochberg}).
 */
public final class Significance {
    /** Below this many events in total an exact binomial is used instead of the normal approximation. */
    static final int EXACT_LIMIT = 2000;

    private Significance() {
    }

    public record Result(double statistic, double pValue) {
    }

    /**
     * Did a rate change? {@code x1} events over exposure {@code t1} against {@code x0} over {@code t0}
     * (exposure is the period length, so a 31-day month is compared fairly with a 30-day one).
     * Conditional test: given n = x0 + x1 events, under "no change" x1 ~ Binomial(n, t1 / (t0 + t1)).
     * The statistic is the signed z-score; the p-value is exact for n up to {@value #EXACT_LIMIT}.
     */
    public static Result poissonRate(long x1, double t1, long x0, double t0) {
        if (t1 <= 0 || t0 <= 0) throw new IllegalArgumentException("Exposure must be positive");
        long n = x0 + x1;
        if (n == 0) return new Result(0, 1);
        double p = t1 / (t0 + t1);
        double mean = n * p;
        double sd = Math.sqrt(n * p * (1 - p));
        double z = (x1 - mean) / sd;
        double pValue = n <= EXACT_LIMIT ? binomialTwoSided(x1, n, p) : 2 * upperTail(Math.max(0, Math.abs(x1 - mean) - 0.5) / sd);
        return new Result(z, clamp(pValue));
    }

    /**
     * Did a median move? Uses the large-sample standard error of a median,
     * 1.2533 × σ / √n, with σ estimated robustly as IQR / 1.349. An approximation: listings that
     * appear in both periods make the samples overlap, which this ignores (it is conservative
     * only when they don't), so callers also require a minimum size and a practical change.
     */
    public static Result medianShift(double m1, double iqr1, int n1, double m0, double iqr0, int n0) {
        if (n1 < 2 || n0 < 2) return new Result(0, 1);
        double se1 = 1.2533 * (Math.max(iqr1, 0) / 1.349) / Math.sqrt(n1);
        double se0 = 1.2533 * (Math.max(iqr0, 0) / 1.349) / Math.sqrt(n0);
        double se = Math.sqrt(se1 * se1 + se0 * se0);
        if (se == 0) return new Result(0, m1 == m0 ? 1 : 0);
        double z = (m1 - m0) / se;
        return new Result(z, clamp(2 * upperTail(Math.abs(z))));
    }

    /** Two independent proportions, x1/n1 against x2/n2 (pooled z-test). */
    public static Result twoProportions(long x1, long n1, long x2, long n2) {
        if (n1 <= 0 || n2 <= 0) return new Result(0, 1);
        double p1 = (double) x1 / n1;
        double p2 = (double) x2 / n2;
        double pooled = (double) (x1 + x2) / (n1 + n2);
        double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2));
        if (se == 0) return new Result(0, 1);
        double z = (p1 - p2) / se;
        return new Result(z, clamp(2 * upperTail(Math.abs(z))));
    }

    /**
     * Benjamini–Hochberg adjusted p-values (q-values), in the input order. Many suburbs and metrics
     * are tested every run, so a raw p &lt; 0.05 would produce a steady stream of false findings;
     * keeping q &lt; 0.05 keeps the expected share of false findings among those reported at 5%.
     */
    public static double[] benjaminiHochberg(List<Double> pValues) {
        int m = pValues.size();
        double[] q = new double[m];
        if (m == 0) return q;
        Integer[] order = new Integer[m];
        for (int i = 0; i < m; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(pValues::get));
        double running = 1;
        for (int rank = m; rank >= 1; rank--) {
            int i = order[rank - 1];
            running = Math.min(running, pValues.get(i) * m / rank);
            q[i] = clamp(running);
        }
        return q;
    }

    /** P(Z &gt; z) for a standard normal. */
    static double upperTail(double z) {
        return 0.5 * erfc(z / Math.sqrt(2));
    }

    /** Complementary error function (Numerical Recipes erfcc; relative error below 1.2e-7). */
    static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1 / (1 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? r : 2 - r;
    }

    /** Exact two-sided binomial p-value: the total probability of outcomes no more likely than k. */
    static double binomialTwoSided(long k, long n, double p) {
        double observed = logPmf(k, n, p);
        double total = 0;
        for (long i = 0; i <= n; i++) {
            double lp = logPmf(i, n, p);
            if (lp <= observed + 1e-7) total += Math.exp(lp);
        }
        return total;
    }

    private static double logPmf(long k, long n, double p) {
        if (p <= 0) return k == 0 ? 0 : Double.NEGATIVE_INFINITY;
        if (p >= 1) return k == n ? 0 : Double.NEGATIVE_INFINITY;
        return logChoose(n, k) + k * Math.log(p) + (n - k) * Math.log1p(-p);
    }

    private static double logChoose(long n, long k) {
        return logGamma(n + 1) - logGamma(k + 1) - logGamma(n - k + 1);
    }

    /** Lanczos approximation (g = 7, 9 terms). */
    static double logGamma(double x) {
        double[] c = {0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};
        if (x < 0.5) return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - logGamma(1 - x);
        x -= 1;
        double a = c[0];
        double t = x + 7.5;
        for (int i = 1; i < 9; i++) a += c[i] / (x + i);
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }

    private static double clamp(double p) {
        return Math.max(0, Math.min(1, p));
    }
}
