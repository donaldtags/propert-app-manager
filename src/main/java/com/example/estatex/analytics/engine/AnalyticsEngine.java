package com.example.estatex.analytics.engine;

import com.example.estatex.analytics.alert.AlertService;
import com.example.estatex.analytics.insight.MarketInsightService;
import com.example.estatex.analytics.insight.ObservationService;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.MarketMetricsCalculator;
import com.example.estatex.analytics.metrics.MetricValue;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.common.BusinessTime;
import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The hourly analytics engine (docs/admin-analytics-plan.md §3).
 *
 * <p>Each run covers a <em>window</em> of time since the last successful run. It reads what
 * changed, checks data quality, recalculates every period the window touches (from source data,
 * so a re-run gives the same answer), and publishes all figures in one transaction. If any step
 * fails after its retries, nothing is published: the previous figures stay, and the dashboard
 * shows them as stale.
 */
@Service
public class AnalyticsEngine {
    public static final String HOURLY = "hourly";
    public static final String NIGHTLY = "nightly";
    public static final String REBUILD = "rebuild";
    static final Duration MAX_CATCH_UP = Duration.ofHours(72);
    private static final Logger log = LoggerFactory.getLogger(AnalyticsEngine.class);
    private static final String METRIC_INSERT = "insert into metric_values (metric_key, definition_version, grain, period_start, "
            + "period_end, as_of, complete, variant, history_complete, dims_key, dim_listing_type, dim_country, dim_city, dim_suburb, dim_category, "
            + "dim_bedrooms, metric_value, sample_size, median, p25, p75, mean, min_value, max_value, sum_value, excluded_count, "
            + "coverage, low_sample, currency, computed_at, job_run_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final JobLock lock;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MarketMetricsCalculator calculator;
    private final SnapshotWriter snapshots;
    private final DataQualityChecker quality;
    private final ObservationService observations;
    private final MarketInsightService insights;
    private final AlertService alerts;
    private final BusinessTime time;
    private final ObjectMapper json;
    private final Duration staleAfter;
    private final long[] backoffSeconds;
    private final String worker = ManagementFactory.getRuntimeMXBean().getName();

    /** Test hook: called before each step; throw to simulate a failure. */
    volatile Consumer<String> beforeStep = step -> { };

    public AnalyticsEngine(JobLock lock, JdbcTemplate jdbc, TransactionTemplate tx, MarketMetricsCalculator calculator,
                           SnapshotWriter snapshots, DataQualityChecker quality, ObservationService observations,
                           MarketInsightService insights, AlertService alerts, BusinessTime time, ObjectMapper json,
                           @Value("${app.analytics.lock-stale-minutes:45}") long staleMinutes,
                           @Value("${app.analytics.retry-backoff-seconds:30,120,300}") String backoff) {
        this.lock = lock;
        this.jdbc = jdbc;
        this.tx = tx;
        this.calculator = calculator;
        this.snapshots = snapshots;
        this.quality = quality;
        this.observations = observations;
        this.insights = insights;
        this.alerts = alerts;
        this.time = time;
        this.json = json;
        this.staleAfter = Duration.ofMinutes(staleMinutes);
        this.backoffSeconds = Arrays.stream(backoff.split(",")).map(String::trim).mapToLong(Long::parseLong).toArray();
    }

    /**
     * On a normal shutdown (a deploy or restart sends SIGTERM), give up this server's unfinished
     * runs at once, so the next start doesn't wait out the 45-minute stale-lock timeout. A crash
     * skips this; the timeout covers that case.
     */
    @jakarta.annotation.PreDestroy
    public void releaseOnShutdown() {
        try {
            int released = jdbc.update("update analytics_job_runs set status = 'ABANDONED', finished_at = ?, "
                            + "error = 'Server shut down during the run; the next run picks up the same window.' "
                            + "where status = 'RUNNING' and worker = ?", Timestamp.from(now()), worker);
            if (released > 0) log.info("Released {} unfinished analytics run(s) on shutdown", released);
        } catch (RuntimeException e) {
            log.warn("Could not release analytics runs on shutdown: {}", e.getMessage());
        }
    }

    String worker() {
        return worker;
    }

    public record Outcome(String status, Long runId, String detail) {
        static Outcome skipped(String why) {
            return new Outcome("SKIPPED", null, why);
        }
    }

    /**
     * Processes everything since the last successful hourly run, up to the top of the current
     * hour (or up to now, for a manual run).
     */
    public Outcome runHourly(String triggeredBy) {
        Instant now = now();
        Instant end = "MANUAL".equals(triggeredBy) ? now : now.truncatedTo(ChronoUnit.HOURS);
        Instant start = jdbc.query("select max(window_end) from analytics_job_runs where job_name = ? and status = 'SUCCEEDED'",
                rs -> rs.next() ? ts(rs.getTimestamp(1)) : null, HOURLY);
        if (start == null) {
            start = jdbc.query("select min(window_start) from analytics_job_runs where job_name = ?",
                    rs -> rs.next() ? ts(rs.getTimestamp(1)) : null, HOURLY);
        }
        if (start == null) start = end.minus(Duration.ofHours(1));
        List<String> warnings = new ArrayList<>();
        if (Duration.between(start, end).compareTo(MAX_CATCH_UP) > 0) {
            warnings.add("Analytics were not updated between " + start + " and " + end.minus(MAX_CATCH_UP)
                    + ". Only the last 72 hours were caught up; use Rebuild to recalculate earlier periods.");
            start = end.minus(MAX_CATCH_UP);
        }
        if (!start.isBefore(end)) return Outcome.skipped("Already up to date");
        return process(HOURLY, start, end, triggeredBy, false, warnings);
    }

    /** 02:00: finalise and reconcile yesterday, and refresh quarter and year figures. */
    public Outcome runNightly() {
        Instant now = now();
        LocalDate yesterday = time.today().minusDays(1);
        Instant start = yesterday.atStartOfDay(zone()).toInstant();
        Instant end = start.plus(Duration.ofDays(1));
        return process(NIGHTLY, start, end, "NIGHTLY", false, new ArrayList<>());
    }

    /** Recalculates every period overlapping [from, to) from scratch (admin "Rebuild"). */
    public Outcome rebuild(Instant from, Instant to) {
        return process(REBUILD, from, to, "MANUAL", true, new ArrayList<>());
    }

    Outcome process(String job, Instant windowStart, Instant windowEnd, String triggeredBy, boolean allowRerun,
                    List<String> carriedWarnings) {
        Instant now = now();
        Optional<Long> acquired = lock.acquire(job, windowStart, windowEnd, now, staleAfter, triggeredBy, worker, allowRerun);
        if (acquired.isEmpty()) return Outcome.skipped("Another run holds this window, or it is already done");
        long runId = acquired.get();
        // Stamp the real start: acquiring can wait (for a connection or a lock), and the run's history
        // must show when it actually began.
        Instant began = now();
        jdbc.update("update analytics_job_runs set started_at = ?, heartbeat_at = ? where id = ?",
                Timestamp.from(began), Timestamp.from(began), runId);
        RunLog run = new RunLog(runId);
        run.warnings.addAll(carriedWarnings.stream().map(w -> Map.<String, Object>of("code", "CATCH_UP_GAP", "message", w)).toList());
        ZoneId zone = zone();
        int[] newDuplicatePairs = {0};
        try {
            // 1. What changed in the window.
            Set<Long> changed = step(run, "collect_changes", () -> {
                Set<Long> ids = new LinkedHashSet<>(jdbc.queryForList(
                        "select distinct property_id from property_events where occurred_at >= ? and occurred_at < ?",
                        Long.class, Timestamp.from(windowStart), Timestamp.from(now)));
                run.recordsRead = countInWindow(windowStart, now);
                run.detail("collect_changes", ids.size() + " listing(s) changed; " + run.recordsRead + " record(s) in window");
                return ids;
            });

            // 2–4. Validation, duplicates, verification backlog.
            step(run, "validate_and_deduplicate", () -> {
                DataQualityChecker.Result q = quality.check(now);
                run.recordsRejected = q.rejected();
                newDuplicatePairs[0] = q.newDuplicatePairs();
                q.warnings().forEach(w -> run.warnings.add(Map.of("code", w.code(), "message", w.message(),
                        "count", w.count(), "sampleIds", w.sampleIds())));
                run.detail("validate_and_deduplicate", q.liveListings() + " live listing(s) checked; " + q.rejected()
                        + " left out of price figures; " + q.newDuplicatePairs() + " new possible duplicate pair(s)");
                return null;
            });
            step(run, "check_verification", () -> {
                Integer pendingListings = jdbc.queryForObject(
                        "select count(*) from properties where verification_status = 'PENDING'", Integer.class);
                Integer pendingSales = jdbc.queryForObject(
                        "select count(*) from sale_transactions where verification = 'REPORTED'", Integer.class);
                if (pendingSales != null && pendingSales > 0) {
                    run.warnings.add(Map.of("code", "SALES_AWAITING_VERIFICATION", "message", pendingSales
                            + " reported sale(s) are awaiting verification and are not in verified-sales figures.", "count", pendingSales));
                }
                run.detail("check_verification", pendingListings + " listing(s) and " + pendingSales + " sale(s) awaiting verification");
                return null;
            });

            // 5–7. Figures for every affected period (read-only until publish).
            List<Work> work = work(job, windowStart, windowEnd, now, zone);
            List<MetricValue> values = step(run, "calculate_metrics", () -> {
                MarketMetricsCalculator.RunContext ctx = calculator.context();
                List<MetricValue> out = new ArrayList<>();
                for (Work w : work) {
                    for (MetricValue v : calculator.compute(w.period(), now, zone, ctx, runId)) {
                        v.setVariant(w.variant());
                        out.add(v);
                    }
                    lock.heartbeat(runId, now());
                }
                long suburbRows = out.stream().filter(v -> v.getDimSuburb() != null).count();
                run.detail("calculate_metrics", out.size() + " figure(s) across " + work.size() + " period(s), "
                        + suburbRows + " at suburb level");
                return out;
            });
            // 8. Publish snapshots and figures together, or not at all.
            List<LocalDate> days = days(job, windowStart, windowEnd, now, zone);
            step(run, "publish", () -> {
                tx.executeWithoutResult(status -> {
                    int snap = 0;
                    for (LocalDate d : days) {
                        if (NIGHTLY.equals(job) || REBUILD.equals(job)) {
                            SnapshotWriter.Reconciliation r = snapshots.reconcile(d, zone, now, runId);
                            snap += r.expected();
                            if (r.mismatches() > 0 && NIGHTLY.equals(job)) {
                                run.warnings.add(Map.of("code", "RECONCILIATION_MISMATCH", "message", "Snapshot for " + d
                                        + " differed from a full rebuild (" + r.missing() + " missing, " + r.extra() + " extra, "
                                        + r.different() + " different). Replaced with the rebuilt figures.", "count", r.mismatches()));
                            }
                        } else {
                            snap += snapshots.update(d, zone, now, changed, runId);
                        }
                    }
                    for (Work w : work) {
                        jdbc.update("delete from metric_values where grain = ? and period_start = ? and variant = ?",
                                w.period().grain().name(), Timestamp.from(w.period().start()), w.variant());
                    }
                    insert(values);
                    run.metricsWritten = values.size();
                    run.detail("publish", values.size() + " figure(s) and " + snap + " snapshot row(s) published");
                });
                return null;
            });

            // 9–11. Observations, AI interpretation and alerts, from the figures just published. These
            // never undo a publish: a failure here is recorded as a warning and retried next run.
            ObservationService.Result found = soft(run, "significance_tests", () -> {
                ObservationService.Result r = observations.generate(now(), runId);
                run.detail("significance_tests", r.tested() + " comparison(s) tested; " + r.observations().size()
                        + " observation(s) current");
                return r;
            });
            if (found != null) {
                soft(run, "market_intelligence", () -> {
                    MarketInsightService.Outcome o = insights.refresh(found.observations(), now(), runId);
                    run.detail("market_intelligence", o.status() + ": " + o.detail());
                    if ("FAILED".equals(o.status()) || "REFUSED".equals(o.status())) {
                        run.warnings.add(Map.of("code", "AI_INTERPRETATION_" + o.status(), "message", o.detail()));
                    }
                    return null;
                });
            } else {
                run.skip("market_intelligence", "No observations: significance tests did not complete");
            }
            soft(run, "alerts", () -> {
                AlertService.Raised r = alerts.evaluate(found == null ? List.of() : found.observations(), newDuplicatePairs[0], runId, now());
                run.detail("alerts", r.raised() + " alert(s) raised; " + r.suppressed() + " held back by dedupe, cooldown or the per-run cap");
                return null;
            });
            finish(run, "SUCCEEDED", null);
            return new Outcome("SUCCEEDED", runId, run.metricsWritten + " figure(s) published");
        } catch (Exception e) {
            log.error("Analytics run {} ({}) failed", runId, job, e);
            finish(run, "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());
            try {
                alerts.jobFailed(job, runId, e.getMessage(), now());
            } catch (RuntimeException alertError) {
                log.warn("Could not raise the job-failed alert for run {}", runId, alertError);
            }
            return new Outcome("FAILED", runId, e.getMessage());
        }
    }

    /** One period to calculate, and whether it is the whole period or the like-for-like cut of an earlier one. */
    record Work(Period period, String variant) {
    }

    private static final List<Grain> CALENDAR = List.of(Grain.DAY, Grain.WEEK, Grain.MONTH, Grain.QUARTER, Grain.YEAR);

    private List<Work> work(String job, Instant ws, Instant we, Instant now, ZoneId zone) {
        List<Period> periods = new ArrayList<>();
        Instant through = now.plusMillis(1);
        if (REBUILD.equals(job)) {
            for (Grain g : Grain.values()) periods.addAll(Period.overlapping(g, ws, we, zone));
        } else {
            if (HOURLY.equals(job)) periods.addAll(Period.overlapping(Grain.HOUR, ws, we, zone));
            for (Grain g : CALENDAR) periods.addAll(Period.overlapping(g, ws, through, zone));
        }
        List<Work> out = new ArrayList<>();
        for (Period p : periods.stream().distinct().filter(p -> p.start().isBefore(now)).toList()) {
            out.add(new Work(p, "FULL"));
            if (p.grain() == Grain.HOUR) continue;
            Period previous = p.previous(zone);
            if (!p.isComplete(now)) {
                // Like-for-like: the previous period cut to the same length as this one so far.
                out.add(new Work(toDate(previous, p, now), "TO_DATE"));
            }
            if (!exists(previous)) out.add(new Work(previous, "FULL"));
        }
        return out.stream().distinct().toList();
    }

    /** {@code previous} cut to the time {@code current} has run so far (capped at its own end). */
    static Period toDate(Period previous, Period current, Instant now) {
        Instant cut = previous.start().plus(Duration.between(current.start(), current.asOf(now)));
        return new Period(previous.grain(), previous.start(), cut.isAfter(previous.end()) ? previous.end() : cut);
    }

    private boolean exists(Period p) {
        Integer n = jdbc.queryForObject("select count(*) from metric_values where metric_key = 'TOTAL_USERS' and grain = ? "
                + "and period_start = ? and variant = 'FULL'", Integer.class, p.grain().name(), Timestamp.from(p.start()));
        return n != null && n > 0;
    }

    private List<LocalDate> days(String job, Instant ws, Instant we, Instant now, ZoneId zone) {
        Instant last = HOURLY.equals(job) ? now : (we.isAfter(now) ? now : we.minusMillis(1));
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = ws.atZone(zone).toLocalDate(); !d.isAfter(last.atZone(zone).toLocalDate()); d = d.plusDays(1)) {
            out.add(d);
        }
        return out;
    }

    private long countInWindow(Instant from, Instant to) {
        long total = 0;
        Timestamp f = Timestamp.from(from);
        Timestamp t = Timestamp.from(to);
        for (String sql : List.of(
                "select count(*) from property_events where occurred_at >= ? and occurred_at < ?",
                "select count(*) from marketplace_events where occurred_at >= ? and occurred_at < ?",
                "select count(*) from enquiries where created_at >= ? and created_at < ?",
                "select count(*) from property_inquiries where created_at >= ? and created_at < ?",
                "select count(*) from viewing_requests where created_at >= ? and created_at < ?",
                "select count(*) from offers where created_at >= ? and created_at < ?",
                "select count(*) from leases where signed_at >= ? and signed_at < ?",
                "select count(*) from sale_transactions where reported_at >= ? and reported_at < ?",
                "select count(*) from payments where created_at >= ? and created_at < ?")) {
            Long n = jdbc.queryForObject(sql, Long.class, f, t);
            total += n == null ? 0 : n;
        }
        return total;
    }

    private void insert(List<MetricValue> values) {
        List<Object[]> rows = new ArrayList<>(values.size());
        for (MetricValue v : values) {
            rows.add(new Object[]{v.getMetricKey(), v.getDefinitionVersion(), v.getGrain(), Timestamp.from(v.getPeriodStart()),
                    Timestamp.from(v.getPeriodEnd()), Timestamp.from(v.getAsOf()), v.isComplete(), v.getVariant(),
                    v.isHistoryComplete(), v.getDimsKey(),
                    v.getDimListingType(), v.getDimCountry(), v.getDimCity(), v.getDimSuburb(), v.getDimCategory(),
                    v.getDimBedrooms(), v.getValue(), v.getSampleSize(), v.getMedian(), v.getP25(), v.getP75(), v.getMean(),
                    v.getMinValue(), v.getMaxValue(), v.getSumValue(), v.getExcludedCount(), v.getCoverage(), v.isLowSample(),
                    v.getCurrency(), Timestamp.from(v.getComputedAt()), v.getJobRunId()});
        }
        for (int i = 0; i < rows.size(); i += 500) {
            jdbc.batchUpdate(METRIC_INSERT, rows.subList(i, Math.min(rows.size(), i + 500)));
        }
    }

    /** Runs one step, retrying with backoff; records its outcome. */
    private <T> T step(RunLog run, String name, Callable<T> body) throws Exception {
        long started = System.currentTimeMillis();
        Exception last = null;
        for (int attempt = 0; attempt <= backoffSeconds.length; attempt++) {
            if (attempt > 0) {
                run.retries++;
                long wait = backoffSeconds[attempt - 1];
                log.warn("Analytics step {} failed (attempt {}), retrying in {}s: {}", name, attempt, wait, last.getMessage());
                if (wait > 0) Thread.sleep(wait * 1000);
            }
            try {
                beforeStep.accept(name);
                T result = body.call();
                run.step(name, "OK", System.currentTimeMillis() - started);
                lock.heartbeat(run.id, now());
                return result;
            } catch (Exception e) {
                last = e;
            }
        }
        run.step(name, "FAILED", System.currentTimeMillis() - started);
        run.detail(name, last.getMessage());
        throw last;
    }

    /** A post-publish step: no retries, and a failure becomes a warning instead of failing the run. */
    private <T> T soft(RunLog run, String name, Callable<T> body) {
        long started = System.currentTimeMillis();
        try {
            beforeStep.accept(name);
            T result = body.call();
            run.step(name, "OK", System.currentTimeMillis() - started);
            return result;
        } catch (Exception e) {
            log.warn("Analytics step {} failed in run {}: {}", name, run.id, e.getMessage(), e);
            run.step(name, "FAILED", System.currentTimeMillis() - started);
            run.detail(name, e.getMessage());
            run.warnings.add(Map.of("code", "STEP_FAILED", "message", "Step " + name + " failed: " + e.getMessage()
                    + ". Figures were still published; it is retried next run."));
            return null;
        }
    }

    private void finish(RunLog run, String status, String error) {
        try {
            jdbc.update("update analytics_job_runs set status = ?, finished_at = ?, heartbeat_at = ?, records_read = ?, "
                            + "records_rejected = ?, metrics_written = ?, step_retries = ?, steps_json = ?, warnings_json = ?, error = ? "
                            + "where id = ?",
                    status, Timestamp.from(now()), Timestamp.from(now()), run.recordsRead, run.recordsRejected,
                    run.metricsWritten, run.retries, truncate(json.writeValueAsString(run.steps.values()), 8000),
                    truncate(json.writeValueAsString(run.warnings), 8000), truncate(error, 2000), run.id);
        } catch (RuntimeException e) {
            log.error("Could not record the outcome of analytics run {}", run.id, e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private Instant now() {
        return time.clock().instant();
    }

    private ZoneId zone() {
        return time.zone();
    }

    /** What happened in one run, written to its row at the end. */
    static final class RunLog {
        final long id;
        final Map<String, Map<String, Object>> steps = new LinkedHashMap<>();
        final List<Map<String, Object>> warnings = java.util.Collections.synchronizedList(new ArrayList<>());
        long recordsRead;
        long recordsRejected;
        long metricsWritten;
        int retries;

        RunLog(long id) {
            this.id = id;
        }

        void step(String name, String status, long ms) {
            steps.computeIfAbsent(name, k -> new LinkedHashMap<>(Map.of("name", k))).put("status", status);
            steps.get(name).put("ms", ms);
        }

        void detail(String name, String detail) {
            steps.computeIfAbsent(name, k -> new LinkedHashMap<>(Map.of("name", k))).put("detail", detail == null ? "" : detail);
        }

        void skip(String name, String why) {
            step(name, "SKIPPED", 0);
            detail(name, why);
        }
    }
}
