package com.example.estatex.analytics.insight;

import com.example.estatex.analytics.metrics.Dims;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.MetricDefinition;
import com.example.estatex.common.BusinessTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns published figures into deterministic observations (Phase G). For each of week and month
 * it compares the latest completed period with the one before:
 *
 * <ul>
 *   <li><b>Counts</b> (enquiries, views, new listings, failed payments, cancellations…): a Poisson
 *       rate test, adjusted for period length. Needs at least {@value #MIN_EVENTS} events across
 *       both periods.</li>
 *   <li><b>Medians</b> (asking price, agreed rent): an approximate z-test on the median. Needs at
 *       least {@value #MIN_MEDIAN_N} prices in each period, per listing type.</li>
 *   <li><b>Category demand vs supply</b>: a category's share of enquiries against its share of
 *       listings (two-proportion z-test) within the latest period.</li>
 *   <li><b>Supply</b> (active listings) and <b>gross yield</b>: stock figures, so no significance
 *       test; reported as THRESHOLD observations with LOW or MEDIUM confidence.</li>
 * </ul>
 *
 * Every tested comparison in a run goes through one Benjamini–Hochberg correction; only those with
 * q ≤ {@value #MAX_Q} <em>and</em> a practical change are kept. Comparisons whose history started
 * part-way through the earlier period are not tested at all (a half-recorded period would look
 * like growth).
 */
@Service
public class ObservationService {
    public static final int MIN_EVENTS = 20;
    public static final int MIN_MEDIAN_N = 10;
    public static final int MIN_STOCK = 20;
    public static final int MIN_SHARE_EVENTS = 30;
    public static final int MIN_CATEGORY_LISTINGS = 5;
    public static final double MAX_Q = 0.05;
    static final double MIN_COUNT_CHANGE_PCT = 10;
    static final double MIN_PRICE_CHANGE_PCT = 5;
    static final double MIN_SHARE_RATIO = 1.5;
    static final double MIN_YIELD_CHANGE_PP = 0.5;
    static final List<Grain> GRAINS = List.of(Grain.WEEK, Grain.MONTH);

    /** Count metrics tested by slice ("local"), or only for the whole platform. */
    private static final Map<MetricDefinition, Boolean> RATE_METRICS = new LinkedHashMap<>();
    static {
        RATE_METRICS.put(MetricDefinition.ENQUIRIES, true);
        RATE_METRICS.put(MetricDefinition.VIEWING_REQUESTS, true);
        RATE_METRICS.put(MetricDefinition.LISTING_VIEWS, true);
        RATE_METRICS.put(MetricDefinition.SAVES, true);
        RATE_METRICS.put(MetricDefinition.NEW_LISTINGS, true);
        RATE_METRICS.put(MetricDefinition.RENTAL_APPLICATIONS, true);
        RATE_METRICS.put(MetricDefinition.OFFERS, true);
        RATE_METRICS.put(MetricDefinition.PAYMENTS_FAILED, false);
        RATE_METRICS.put(MetricDefinition.SUBSCRIPTION_CANCELLATIONS, false);
    }
    private static final List<MetricDefinition> MEDIAN_METRICS = List.of(MetricDefinition.ASKING_PRICE, MetricDefinition.CONTRACTED_RENT);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MarketObservationRepository observations;
    private final ObservationCoverageRepository coverage;
    private final BusinessTime time;

    public ObservationService(JdbcTemplate jdbc, TransactionTemplate tx, MarketObservationRepository observations,
                              ObservationCoverageRepository coverage, BusinessTime time) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.observations = observations;
        this.coverage = coverage;
        this.time = time;
    }

    /** The observations now current, and how much evidence there was. */
    public record Result(List<MarketObservation> observations, List<ObservationCoverage> coverage) {
        public int tested() {
            return coverage.stream().mapToInt(ObservationCoverage::getTested).sum();
        }
    }

    record Row(String metric, String dimsKey, String listingType, String country, String city, String suburb, String category,
               String bedrooms, BigDecimal value, int n, BigDecimal median, BigDecimal p25, BigDecimal p75, boolean historyComplete,
               String currency) {
    }

    record PeriodRef(Instant start, Instant end) {
        double days() {
            return Duration.between(start, end).toMinutes() / 1440.0;
        }
    }

    /** One comparison that will become an observation if it survives the correction. */
    private static final class Candidate {
        final MarketObservation o;
        final Double p;
        final boolean practical;

        Candidate(MarketObservation o, Double p, boolean practical) {
            this.o = o;
            this.p = p;
            this.practical = practical;
        }
    }

    private static final class Counter {
        int tested;
        int belowMinimum;
        int historyIncomplete;
    }

    public Result generate(Instant now, Long runId) {
        Map<String, Instant> captureStarts = new HashMap<>();
        jdbc.query("select source, started_at from analytics_capture_starts",
                rs -> { captureStarts.put(rs.getString(1), rs.getTimestamp(2).toInstant()); });
        List<Candidate> all = new ArrayList<>();
        Map<Grain, PeriodRef[]> periods = new LinkedHashMap<>();
        Map<Grain, Counter> counters = new LinkedHashMap<>();
        for (Grain g : GRAINS) {
            List<PeriodRef> latest = jdbc.query("select distinct period_start, period_end from metric_values where grain = ? "
                            + "and variant = 'FULL' and period_end <= ? order by period_start desc",
                    (rs, i) -> new PeriodRef(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()),
                    g.name(), Timestamp.from(now));
            Counter c = new Counter();
            counters.put(g, c);
            if (latest.isEmpty()) continue;
            PeriodRef cur = latest.get(0);
            PeriodRef prev = latest.size() > 1 && latest.get(1).end().equals(cur.start()) ? latest.get(1) : null;
            periods.put(g, new PeriodRef[]{cur, prev});
            Map<String, Row> curRows = rows(g, cur.start());
            Map<String, Row> prevRows = prev == null ? Map.of() : rows(g, prev.start());
            if (prev != null) {
                rates(g, cur, prev, curRows, prevRows, captureStarts, c, all);
                medians(g, cur, prev, curRows, prevRows, c, all);
                supply(g, cur, prev, curRows, prevRows, captureStarts, c, all);
                yields(g, cur, prev, curRows, prevRows, c, all);
            }
            shares(g, cur, curRows, c, all);
        }

        // One false-discovery correction across every tested comparison in the run.
        List<Candidate> tested = all.stream().filter(x -> x.p != null).toList();
        double[] q = Significance.benjaminiHochberg(tested.stream().map(x -> x.p).toList());
        for (int i = 0; i < tested.size(); i++) tested.get(i).o.setQValue(round(q[i]));
        List<MarketObservation> kept = new ArrayList<>();
        for (Candidate x : all) {
            boolean significant = x.p == null || x.o.getQValue() <= MAX_Q;
            if (!significant || !x.practical) continue;
            x.o.setConfidence(confidence(x.o));
            kept.add(x.o);
        }

        return tx.execute(status -> {
            List<MarketObservation> saved = new ArrayList<>();
            List<ObservationCoverage> covs = new ArrayList<>();
            for (Grain g : GRAINS) {
                Counter c = counters.get(g);
                PeriodRef[] pr = periods.get(g);
                ObservationCoverage cov = coverage.findById(g.name()).orElseGet(ObservationCoverage::new);
                cov.setGrain(g.name());
                cov.setPeriodStart(pr == null ? null : pr[0].start());
                cov.setPeriodEnd(pr == null ? null : pr[0].end());
                cov.setComparisonStart(pr == null || pr[1] == null ? null : pr[1].start());
                cov.setComparisonEnd(pr == null || pr[1] == null ? null : pr[1].end());
                cov.setTested(c.tested);
                cov.setBelowMinimum(c.belowMinimum);
                cov.setHistoryIncomplete(c.historyIncomplete);
                cov.setComputedAt(now);
                List<MarketObservation> mine = kept.stream().filter(o -> o.getGrain().equals(g.name())).toList();
                cov.setReported(mine.size());
                covs.add(coverage.save(cov));
                if (pr == null) continue;
                // Replace this period's set: keep ids of findings that persist, drop those that no longer hold.
                Set<String> fingerprints = new HashSet<>();
                for (MarketObservation o : mine) {
                    fingerprints.add(o.getFingerprint());
                    MarketObservation row = observations.findByFingerprint(o.getFingerprint()).orElse(null);
                    if (row == null) {
                        o.setFirstSeenAt(now);
                        row = o;
                    } else {
                        copy(o, row);
                    }
                    row.setUpdatedAt(now);
                    row.setJobRunId(runId);
                    saved.add(observations.save(row));
                }
                for (MarketObservation old : observations.findByGrainAndPeriodStart(g.name(), pr[0].start())) {
                    if (!fingerprints.contains(old.getFingerprint())) observations.delete(old);
                }
            }
            return new Result(saved, covs);
        });
    }

    // ---- Count metrics: Poisson rate test -------------------------------------------------------

    private void rates(Grain g, PeriodRef cur, PeriodRef prev, Map<String, Row> curRows, Map<String, Row> prevRows,
                       Map<String, Instant> captureStarts, Counter c, List<Candidate> out) {
        for (Map.Entry<MetricDefinition, Boolean> e : RATE_METRICS.entrySet()) {
            MetricDefinition m = e.getKey();
            boolean local = e.getValue();
            if (!historyCovers(m, prev, captureStarts)) {
                c.historyIncomplete++;
                continue;
            }
            for (String dk : keys(m, curRows, prevRows)) {
                Row a = curRows.get(m.name() + "#" + dk);
                Row b = prevRows.get(m.name() + "#" + dk);
                Row d = a != null ? a : b;
                if (!(local ? eligible(d) : dk.equals(Dims.ALL.key()))) continue;
                if ((a != null && !a.historyComplete()) || (b != null && !b.historyComplete())) {
                    c.historyIncomplete++;
                    continue;
                }
                long x1 = a == null ? 0 : a.value().longValue();
                long x0 = b == null ? 0 : b.value().longValue();
                if (x0 + x1 < MIN_EVENTS) {
                    c.belowMinimum++;
                    continue;
                }
                c.tested++;
                Significance.Result t = Significance.poissonRate(x1, cur.days(), x0, prev.days());
                double r1 = x1 / cur.days();
                double r0 = x0 / prev.days();
                Double change = x0 == 0 ? null : (r1 - r0) / r0 * 100;
                boolean practical = change == null || Math.abs(change) >= MIN_COUNT_CHANGE_PCT;
                boolean up = r1 > r0;
                MarketObservation o = base(g, cur, prev, d, m, m.name() + (up ? "_UP" : "_DOWN"), "POISSON_RATE");
                o.setCurrentValue(BigDecimal.valueOf(x1));
                o.setPreviousValue(BigDecimal.valueOf(x0));
                o.setChangePct(change == null ? null : bd(change, 2));
                o.setSampleCurrent(x1);
                o.setSamplePrevious(x0);
                o.setStatistic(round(t.statistic()));
                o.setPValue(round(t.pValue()));
                boolean adjusted = Math.abs(cur.days() - prev.days()) > 0.01;
                o.setStatement(capitalise(countLabel(m, d)) + " " + (up ? "rose" : "fell") + " from " + x0 + " to " + x1
                        + (change == null ? "" : " (" + signed(change) + "%" + (adjusted ? ", adjusted for period length" : "") + ")")
                        + " in " + periodLabel(g, cur) + " compared with " + periodLabel(g, prev) + ".");
                out.add(new Candidate(o, t.pValue(), practical));
            }
        }
    }

    // ---- Medians: approximate z-test -------------------------------------------------------------

    private void medians(Grain g, PeriodRef cur, PeriodRef prev, Map<String, Row> curRows, Map<String, Row> prevRows,
                         Counter c, List<Candidate> out) {
        for (MetricDefinition m : MEDIAN_METRICS) {
            for (String dk : keys(m, curRows, prevRows)) {
                Row a = curRows.get(m.name() + "#" + dk);
                Row b = prevRows.get(m.name() + "#" + dk);
                Row d = a != null ? a : b;
                if (d.listingType() == null || !eligible(d)) continue;   // never mix rents with sale prices
                if (a == null || b == null || a.median() == null || b.median() == null
                        || a.n() < MIN_MEDIAN_N || b.n() < MIN_MEDIAN_N) {
                    c.belowMinimum++;
                    continue;
                }
                c.tested++;
                double m1 = a.median().doubleValue();
                double m0 = b.median().doubleValue();
                Significance.Result t = Significance.medianShift(m1, iqr(a), a.n(), m0, iqr(b), b.n());
                double change = m0 == 0 ? 0 : (m1 - m0) / m0 * 100;
                boolean up = m1 > m0;
                MarketObservation o = base(g, cur, prev, d, m, m.name() + (up ? "_UP" : "_DOWN"), "MEDIAN_SHIFT");
                o.setCurrentValue(a.median());
                o.setPreviousValue(b.median());
                o.setChangePct(bd(change, 2));
                o.setSampleCurrent(a.n());
                o.setSamplePrevious(b.n());
                o.setStatistic(round(t.statistic()));
                o.setPValue(round(t.pValue()));
                o.setCurrency(a.currency());
                o.setStatement(capitalise(priceLabel(m, d)) + " " + (up ? "rose" : "fell") + " from " + money(m0) + " to " + money(m1)
                        + " (" + signed(change) + "%) in " + periodLabel(g, cur) + " compared with " + periodLabel(g, prev)
                        + ", from " + a.n() + " and " + b.n() + " prices.");
                out.add(new Candidate(o, t.pValue(), Math.abs(change) >= MIN_PRICE_CHANGE_PCT));
            }
        }
    }

    // ---- Supply: a stock, so a threshold rather than a test --------------------------------------

    private void supply(Grain g, PeriodRef cur, PeriodRef prev, Map<String, Row> curRows, Map<String, Row> prevRows,
                        Map<String, Instant> captureStarts, Counter c, List<Candidate> out) {
        MetricDefinition m = MetricDefinition.ACTIVE_LISTINGS;
        if (!historyCovers(m, prev, captureStarts)) {
            c.historyIncomplete++;
            return;
        }
        for (String dk : keys(m, curRows, prevRows)) {
            Row a = curRows.get(m.name() + "#" + dk);
            Row b = prevRows.get(m.name() + "#" + dk);
            Row d = a != null ? a : b;
            if (!eligible(d)) continue;
            long x1 = a == null ? 0 : a.value().longValue();
            long x0 = b == null ? 0 : b.value().longValue();
            if (x0 < MIN_STOCK) {
                c.belowMinimum++;
                continue;
            }
            double change = (double) (x1 - x0) / x0 * 100;
            if (Math.abs(change) < MIN_COUNT_CHANGE_PCT) continue;
            boolean up = x1 > x0;
            MarketObservation o = base(g, cur, prev, d, m, m.name() + (up ? "_UP" : "_DOWN"), "THRESHOLD");
            o.setCurrentValue(BigDecimal.valueOf(x1));
            o.setPreviousValue(BigDecimal.valueOf(x0));
            o.setChangePct(bd(change, 2));
            o.setSampleCurrent(x1);
            o.setSamplePrevious(x0);
            o.setStatement(capitalise(countLabel(m, d)) + " " + (up ? "rose" : "fell") + " from " + x0 + " to " + x1 + " ("
                    + signed(change) + "%) in " + periodLabel(g, cur) + " compared with " + periodLabel(g, prev)
                    + ". A change in the stock of listings, not a significance test.");
            out.add(new Candidate(o, null, true));
        }
    }

    // ---- Gross yield from asking medians: a threshold --------------------------------------------

    private void yields(Grain g, PeriodRef cur, PeriodRef prev, Map<String, Row> curRows, Map<String, Row> prevRows,
                        Counter c, List<Candidate> out) {
        String m = MetricDefinition.ASKING_PRICE.name();
        for (Row rent : curRows.values()) {
            if (!m.equals(rent.metric()) || !"RENT".equals(rent.listingType()) || rent.city() == null
                    || rent.category() != null || rent.bedrooms() != null) continue;
            String saleKey = m + "#" + new Dims("SALE", rent.country(), rent.city(), rent.suburb(), null, null).key();
            Row sale = curRows.get(saleKey);
            Row rentPrev = prevRows.get(m + "#" + rent.dimsKey());
            Row salePrev = prevRows.get(saleKey);
            if (sale == null || rentPrev == null || salePrev == null) continue;
            if (Math.min(Math.min(rent.n(), sale.n()), Math.min(rentPrev.n(), salePrev.n())) < MIN_MEDIAN_N
                    || rent.median() == null || sale.median() == null || rentPrev.median() == null || salePrev.median() == null
                    || sale.median().signum() <= 0 || salePrev.median().signum() <= 0) {
                c.belowMinimum++;
                continue;
            }
            double y1 = 1200 * rent.median().doubleValue() / sale.median().doubleValue();
            double y0 = 1200 * rentPrev.median().doubleValue() / salePrev.median().doubleValue();
            double pp = y1 - y0;
            if (Math.abs(pp) < MIN_YIELD_CHANGE_PP) continue;
            boolean up = pp > 0;
            Row d = new Row(m, new Dims(null, rent.country(), rent.city(), rent.suburb(), null, null).key(), null, rent.country(),
                    rent.city(), rent.suburb(), null, null, null, 0, null, null, null, true, null);
            MarketObservation o = base(g, cur, prev, d, MetricDefinition.ASKING_PRICE, up ? "YIELD_UP" : "YIELD_DOWN", "THRESHOLD");
            o.setCurrentValue(bd(y1, 2));
            o.setPreviousValue(bd(y0, 2));
            o.setChangePct(bd(pp, 2));
            o.setSampleCurrent(Math.min(rent.n(), sale.n()));
            o.setSamplePrevious(Math.min(rentPrev.n(), salePrev.n()));
            o.setStatement("Indicative gross yield in " + place(d) + " (median asking rent × 12 ÷ median asking sale price) "
                    + (up ? "rose" : "fell") + " from " + fmt(y0, 1) + "% to " + fmt(y1, 1) + "% (" + signed(pp) + " percentage points) in "
                    + periodLabel(g, cur) + ". Built from asking prices, not agreed ones; not a significance test.");
            out.add(new Candidate(o, null, true));
        }
    }

    // ---- Category demand share vs supply share, within the latest period --------------------------

    private void shares(Grain g, PeriodRef cur, Map<String, Row> rows, Counter c, List<Candidate> out) {
        for (String type : List.of("RENT", "SALE", "SHORT_STAY")) {
            Row enquiries = rows.get(MetricDefinition.ENQUIRIES.name() + "#" + new Dims(type, null, null, null, null, null).key());
            Row listings = rows.get(MetricDefinition.ACTIVE_LISTINGS.name() + "#" + new Dims(type, null, null, null, null, null).key());
            long s = listings == null ? 0 : listings.value().longValue();
            if (s >= MIN_STOCK) {
                // Which category has the most supply: a plain count, no test needed.
                Row top = null;
                for (Row r : rows.values()) {
                    if (r.metric().equals(MetricDefinition.ACTIVE_LISTINGS.name()) && type.equals(r.listingType()) && r.category() != null
                            && r.city() == null && r.bedrooms() == null && (top == null || r.value().compareTo(top.value()) > 0)) top = r;
                }
                if (top != null) {
                    long n = top.value().longValue();
                    MarketObservation o = base(g, cur, null, top, MetricDefinition.ACTIVE_LISTINGS, "TOP_SUPPLY_CATEGORY", "DESCRIPTIVE");
                    o.setCurrentValue(BigDecimal.valueOf(n));
                    o.setPreviousValue(BigDecimal.valueOf(s));
                    o.setChangePct(bd(100.0 * n / s, 2));
                    o.setSampleCurrent(s);
                    o.setStatement(capitalise(categoryLabel(top.category())) + " is the largest category of " + typeNoun(type) + " listings: "
                            + n + " of " + s + " active (" + fmt(100.0 * n / s, 0) + "%) in " + periodLabel(g, cur) + ".");
                    out.add(new Candidate(o, null, true));
                }
            }
            long e = enquiries == null ? 0 : enquiries.value().longValue();
            if (e < MIN_SHARE_EVENTS || s < MIN_STOCK) {
                if (e > 0 || s > 0) c.belowMinimum++;
                continue;
            }
            Set<String> categories = new LinkedHashSet<>();
            for (Row r : rows.values()) {
                if (type.equals(r.listingType()) && r.category() != null && r.city() == null && r.bedrooms() == null
                        && (r.metric().equals(MetricDefinition.ENQUIRIES.name()) || r.metric().equals(MetricDefinition.ACTIVE_LISTINGS.name()))) {
                    categories.add(r.category());
                }
            }
            for (String cat : categories) {
                String dk = new Dims(type, null, null, null, cat, null).key();
                Row ec = rows.get(MetricDefinition.ENQUIRIES.name() + "#" + dk);
                Row sc = rows.get(MetricDefinition.ACTIVE_LISTINGS.name() + "#" + dk);
                long xe = ec == null ? 0 : ec.value().longValue();
                long xs = sc == null ? 0 : sc.value().longValue();
                if (xs < MIN_CATEGORY_LISTINGS && xe < MIN_CATEGORY_LISTINGS) {
                    c.belowMinimum++;
                    continue;
                }
                c.tested++;
                double demand = (double) xe / e;
                double supply = (double) xs / s;
                Significance.Result t = Significance.twoProportions(xe, e, xs, s);
                boolean under = demand > supply;
                double ratio = supply == 0 ? Double.POSITIVE_INFINITY : demand / supply;
                boolean practical = under ? ratio >= MIN_SHARE_RATIO : ratio <= 1 / MIN_SHARE_RATIO;
                Row d = ec != null ? ec : sc;
                MarketObservation o = base(g, cur, null, d, MetricDefinition.ENQUIRIES,
                        under ? "CATEGORY_UNDERSUPPLIED" : "CATEGORY_OVERSUPPLIED", "SHARE_GAP");
                o.setCurrentValue(bd(demand * 100, 2));
                o.setPreviousValue(bd(supply * 100, 2));
                o.setChangePct(Double.isInfinite(ratio) ? null : bd((ratio - 1) * 100, 2));
                o.setSampleCurrent(e);
                o.setSamplePrevious(s);
                o.setStatistic(round(t.statistic()));
                o.setPValue(round(t.pValue()));
                o.setStatement(capitalise(categoryLabel(cat)) + " listings drew " + fmt(demand * 100, 0) + "% of " + typeNoun(type)
                        + " enquiries (" + xe + " of " + e + ") but are " + fmt(supply * 100, 0) + "% of " + typeNoun(type)
                        + " listings (" + xs + " of " + s + ") in " + periodLabel(g, cur) + ".");
                out.add(new Candidate(o, t.pValue(), practical));
            }
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private Map<String, Row> rows(Grain g, Instant start) {
        Map<String, Row> out = new HashMap<>();
        jdbc.query("select metric_key, dims_key, dim_listing_type, dim_country, dim_city, dim_suburb, dim_category, dim_bedrooms, "
                        + "metric_value, sample_size, median, p25, p75, history_complete, currency from metric_values "
                        + "where grain = ? and period_start = ? and variant = 'FULL'",
                rs -> {
                    Row r = new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7), rs.getString(8), rs.getBigDecimal(9), rs.getInt(10), rs.getBigDecimal(11),
                            rs.getBigDecimal(12), rs.getBigDecimal(13), rs.getBoolean(14), rs.getString(15));
                    if (r.value() != null) out.put(r.metric() + "#" + r.dimsKey(), r);
                }, g.name(), Timestamp.from(start));
        return out;
    }

    private static Set<String> keys(MetricDefinition m, Map<String, Row> a, Map<String, Row> b) {
        Set<String> out = new LinkedHashSet<>();
        String prefix = m.name() + "#";
        for (String k : a.keySet()) if (k.startsWith(prefix)) out.add(k.substring(prefix.length()));
        for (String k : b.keySet()) if (k.startsWith(prefix)) out.add(k.substring(prefix.length()));
        return out;
    }

    /** Slices worth testing: overall, by type, city, suburb or category. Bedroom and city×category cuts are too thin. */
    private static boolean eligible(Row r) {
        return r.bedrooms() == null && !(r.city() != null && r.category() != null);
    }

    private static boolean historyCovers(MetricDefinition m, PeriodRef prev, Map<String, Instant> captureStarts) {
        if (m.captureSource() == null) return true;
        Instant started = captureStarts.get(m.captureSource());
        return started != null && !prev.start().isBefore(started);
    }

    private MarketObservation base(Grain g, PeriodRef cur, PeriodRef prev, Row d, MetricDefinition m, String kind, String method) {
        MarketObservation o = new MarketObservation();
        o.setKind(kind);
        o.setMetricKey(m.name());
        o.setDomain(m.domain().name());
        o.setMethod(method);
        o.setGrain(g.name());
        o.setPeriodStart(cur.start());
        o.setPeriodEnd(cur.end());
        o.setComparisonStart(prev == null ? null : prev.start());
        o.setComparisonEnd(prev == null ? null : prev.end());
        o.setDimsKey(d.dimsKey());
        o.setListingType(d.listingType());
        o.setCity(d.city());
        o.setSuburb(d.suburb());
        o.setCategory(d.category());
        o.setFingerprint(g.name() + "|" + cur.start().getEpochSecond() + "|" + kind + "|" + d.dimsKey());
        return o;
    }

    static String confidence(MarketObservation o) {
        long n = Math.min(o.getSampleCurrent(), o.getSamplePrevious() == 0 ? o.getSampleCurrent() : o.getSamplePrevious());
        return switch (o.getMethod()) {
            case "DESCRIPTIVE" -> o.getSampleCurrent() >= 100 ? "HIGH" : "MEDIUM";
            case "THRESHOLD" -> n >= 50 ? "MEDIUM" : "LOW";
            default -> o.getQValue() != null && o.getQValue() <= 0.01 && n >= 30 ? "HIGH" : "MEDIUM";
        };
    }

    private static void copy(MarketObservation from, MarketObservation to) {
        to.setKind(from.getKind());
        to.setMetricKey(from.getMetricKey());
        to.setDomain(from.getDomain());
        to.setMethod(from.getMethod());
        to.setPeriodEnd(from.getPeriodEnd());
        to.setComparisonStart(from.getComparisonStart());
        to.setComparisonEnd(from.getComparisonEnd());
        to.setCurrentValue(from.getCurrentValue());
        to.setPreviousValue(from.getPreviousValue());
        to.setChangePct(from.getChangePct());
        to.setSampleCurrent(from.getSampleCurrent());
        to.setSamplePrevious(from.getSamplePrevious());
        to.setStatistic(from.getStatistic());
        to.setPValue(from.getPValue());
        to.setQValue(from.getQValue());
        to.setConfidence(from.getConfidence());
        to.setStatement(from.getStatement());
        to.setCurrency(from.getCurrency());
    }

    private static double iqr(Row r) {
        return r.p25() == null || r.p75() == null ? 0 : r.p75().doubleValue() - r.p25().doubleValue();
    }

    private String periodLabel(Grain g, PeriodRef p) {
        ZoneId zone = time.zone();
        return switch (g) {
            case WEEK -> "the week of " + DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).format(p.start().atZone(zone));
            case MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK).format(p.start().atZone(zone));
            default -> DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).format(p.start().atZone(zone));
        };
    }

    static String place(Row d) {
        if (d.suburb() != null) return d.suburb() + ", " + d.city();
        if (d.city() != null) return d.city();
        return "all areas";
    }

    private static String scope(Row d) {
        StringBuilder s = new StringBuilder();
        if (d.category() != null) s.append(" for ").append(categoryLabel(d.category()));
        if (d.listingType() != null) s.append(d.category() != null ? " " : " for ").append(typeLabel(d.listingType()));
        if (d.city() != null) s.append(" in ").append(place(d));
        return s.toString();
    }

    private static String countLabel(MetricDefinition m, Row d) {
        String what = switch (m) {
            case ENQUIRIES -> "enquiries";
            case VIEWING_REQUESTS -> "viewing requests";
            case LISTING_VIEWS -> "listing views";
            case SAVES -> "saved listings";
            case NEW_LISTINGS -> "new listings";
            case RENTAL_APPLICATIONS -> "rental applications";
            case OFFERS -> "offers";
            case PAYMENTS_FAILED -> "failed payments";
            case SUBSCRIPTION_CANCELLATIONS -> "subscription cancellations";
            case ACTIVE_LISTINGS -> "active listings";
            default -> m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
        return what + scope(d);
    }

    private static String priceLabel(MetricDefinition m, Row d) {
        String what = m == MetricDefinition.CONTRACTED_RENT ? "the median agreed rent"
                : "RENT".equals(d.listingType()) ? "the median asking rent" : "the median asking price";
        StringBuilder s = new StringBuilder(what);
        if (d.category() != null) s.append(" for ").append(categoryLabel(d.category()));
        if (!"RENT".equals(d.listingType()) && d.listingType() != null) s.append(d.category() != null ? " " : " for ").append(typeLabel(d.listingType()));
        if (d.city() != null) s.append(" in ").append(place(d));
        return s.toString();
    }

    static String typeLabel(String type) {
        return switch (type) {
            case "RENT" -> "rentals";
            case "SALE" -> "sales";
            case "SHORT_STAY" -> "short stays";
            default -> type.toLowerCase(Locale.ROOT);
        };
    }

    /** As an adjective: "rental enquiries", "sale listings". */
    static String typeNoun(String type) {
        return switch (type) {
            case "RENT" -> "rental";
            case "SALE" -> "sale";
            case "SHORT_STAY" -> "short-stay";
            default -> type.toLowerCase(Locale.ROOT);
        };
    }

    static String categoryLabel(String category) {
        return category.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String money(double v) {
        NumberFormat f = NumberFormat.getIntegerInstance(Locale.US);
        return "$" + f.format(Math.round(v));
    }

    private static String signed(double v) {
        return (v >= 0 ? "+" : "−") + fmt(Math.abs(v), Math.abs(v) >= 10 ? 0 : 1);
    }

    private static String fmt(double v, int dp) {
        return BigDecimal.valueOf(v).setScale(dp, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal bd(double v, int dp) {
        return BigDecimal.valueOf(v).setScale(dp, RoundingMode.HALF_UP);
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
