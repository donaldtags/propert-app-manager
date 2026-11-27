package com.example.estatex.analytics.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.analytics.metrics.MetricValue;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase B of docs/admin-analytics-plan.md: the hourly engine. Each test uses its own city (or
 * its own job name) so tests sharing the database can't affect each other's figures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalyticsEngineTests {
    @Autowired private com.example.estatex.support.ListingFees fees;
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AnalyticsEngine engine;
    @Autowired private JobLock lock;
    @Autowired private SnapshotWriter snapshots;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BusinessTime time;

    private TestAccounts accounts;
    private Account admin;
    private Account owner;
    private String city;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        city = "Testcity" + System.nanoTime();
    }

    @AfterEach
    void resetHook() {
        engine.beforeStep = step -> { };
    }

    @Test
    @Order(1)
    void knownDatasetGivesExactFigures() throws Exception {
        List<Long> rents = new ArrayList<>();
        long[] prices = {500, 600, 700, 800, 5000};
        Integer[] areas = {50, 60, null, 80, 100};
        for (int i = 0; i < prices.length; i++) rents.add(createListing("RENT", prices[i], areas[i], "FLAT", 2));
        createListing("SALE", 120_000, 200, "HOUSE", 3);
        call(patch("/api/v1/properties/" + rents.get(1)), admin, "{\"price\": 540}").andExpect(status().isOk());
        call(patch("/api/v1/properties/" + rents.get(4)), admin, "{\"status\": \"WITHDRAWN\"}").andExpect(status().isOk());

        AnalyticsEngine.Outcome outcome = rebuildToday();
        assertThat(outcome.status()).isEqualTo("SUCCEEDED");

        String rentCity = "rent|zimbabwe|" + city.toLowerCase(Locale.ROOT) + "|*|*|*";
        assertThat(day("ACTIVE_LISTINGS", rentCity).getValue()).isEqualByComparingTo("4");
        assertThat(day("NEW_LISTINGS", rentCity).getValue()).isEqualByComparingTo("5");
        assertThat(day("WITHDRAWN_LISTINGS", rentCity).getValue()).isEqualByComparingTo("1");

        // Live at some point today: all five (the withdrawn one was live this morning), latest prices.
        MetricValue asking = day("ASKING_PRICE", rentCity);
        assertThat(asking.getSampleSize()).isEqualTo(5);
        assertThat(asking.getMedian()).isEqualByComparingTo("700");
        assertThat(asking.getMean()).isEqualByComparingTo("1508");
        assertThat(asking.getP25()).isEqualByComparingTo("540");
        assertThat(asking.getP75()).isEqualByComparingTo("800");
        assertThat(asking.isLowSample()).isFalse();
        assertThat(asking.getCurrency()).isEqualTo("USD");

        MetricValue perM2 = day("ASKING_PRICE_PER_M2", rentCity);
        assertThat(perM2.getSampleSize()).isEqualTo(4);
        assertThat(perM2.getCoverage()).isEqualByComparingTo("0.8");
        assertThat(perM2.getMedian()).isEqualByComparingTo("10"); // 500/50, 540/60, 800/80, 5000/100 → 9, 10, 10, 50

        MetricValue cuts = day("PRICE_REDUCTIONS", rentCity);
        assertThat(cuts.getValue()).isEqualByComparingTo("1");
        assertThat(cuts.getMedian()).isEqualByComparingTo("10.00");

        MetricValue sale = day("ASKING_PRICE", "sale|zimbabwe|" + city.toLowerCase(Locale.ROOT) + "|*|*|*");
        assertThat(sale.getSampleSize()).isEqualTo(1);
        assertThat(sale.isLowSample()).isTrue();

        // Snapshot: one row per listing, with the latest state.
        Integer snaps = jdbc.queryForObject("select count(*) from listing_daily_snapshots where snapshot_date = ? and city = ?",
                Integer.class, Date.valueOf(time.today()), city);
        assertThat(snaps).isEqualTo(6);
        assertThat(jdbc.queryForObject("select price from listing_daily_snapshots where snapshot_date = ? and property_id = ?",
                BigDecimal.class, Date.valueOf(time.today()), rents.get(1))).isEqualByComparingTo("540");
    }

    @Test
    @Order(2)
    void reRunningTheSameWindowGivesTheSameFigures() throws Exception {
        createListing("RENT", 900, 90, "HOUSE", 3);
        createListing("RENT", 1100, null, "HOUSE", 3);
        rebuildToday();
        String key = "rent|zimbabwe|" + city.toLowerCase(Locale.ROOT) + "|*|*|*";
        MetricValue first = day("ASKING_PRICE", key);
        Integer rowsBefore = jdbc.queryForObject("select count(*) from metric_values", Integer.class);
        rebuildToday();
        MetricValue second = day("ASKING_PRICE", key);
        assertThat(second.getMedian()).isEqualByComparingTo(first.getMedian()).isEqualByComparingTo("1000");
        assertThat(second.getSampleSize()).isEqualTo(first.getSampleSize());
        assertThat(jdbc.queryForObject("select count(*) from metric_values", Integer.class)).isEqualTo(rowsBefore);
    }

    /** Repeated, because a race shows up only some of the time (it once let two workers through). */
    @org.junit.jupiter.api.RepeatedTest(5)
    @Order(3)
    void onlyOneWorkerProcessesAWindow() throws Exception {
        String job = "locktest" + System.nanoTime();
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofHours(1));
        engine.beforeStep = step -> sleep(400);
        CompletableFuture<AnalyticsEngine.Outcome> a = CompletableFuture.supplyAsync(
                () -> engine.process(job, start, end, "SCHEDULE", false, List.of()));
        CompletableFuture<AnalyticsEngine.Outcome> b = CompletableFuture.supplyAsync(
                () -> engine.process(job, start, end, "SCHEDULE", false, List.of()));
        List<String> outcomes = List.of(a.get().status(), b.get().status());
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCEEDED", "SKIPPED");
        assertThat(jdbc.queryForObject("select count(*) from analytics_job_runs where job_name = ?", Integer.class, job)).isEqualTo(1);

        // A finished window is never processed again.
        assertThat(engine.process(job, start, end, "SCHEDULE", false, List.of()).status()).isEqualTo("SKIPPED");
    }

    @Test
    @Order(4)
    void aFailingStepIsRetried() {
        AtomicInteger failures = new AtomicInteger();
        engine.beforeStep = step -> {
            if (step.equals("calculate_metrics") && failures.getAndIncrement() < 2) throw new IllegalStateException("db blip");
        };
        String job = "retrytest" + System.nanoTime();
        AnalyticsEngine.Outcome out = engine.process(job, Instant.now().minusSeconds(3600), Instant.now(), "SCHEDULE", false, List.of());
        assertThat(out.status()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select step_retries from analytics_job_runs where id = ?", Integer.class, out.runId()))
                .isEqualTo(2);
    }

    @Test
    @Order(5)
    void aFailedRunKeepsTheLastGoodFiguresAndIsReportedAsFailed() throws Exception {
        createListing("RENT", 750, 75, "FLAT", 2);
        rebuildToday();
        String key = "rent|zimbabwe|" + city.toLowerCase(Locale.ROOT) + "|*|*|*";
        MetricValue good = day("ACTIVE_LISTINGS", key);

        createListing("RENT", 850, 85, "FLAT", 2);
        engine.beforeStep = step -> {
            if (step.equals("publish")) throw new IllegalStateException("disk full");
        };
        AnalyticsEngine.Outcome failed = rebuildToday();
        assertThat(failed.status()).isEqualTo("FAILED");

        MetricValue still = day("ACTIVE_LISTINGS", key);
        assertThat(still.getValue()).isEqualByComparingTo(good.getValue()).isEqualByComparingTo("1");
        assertThat(still.getComputedAt()).isEqualTo(good.getComputedAt());
        call(get("/api/v1/admin/analytics/engine/status"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.lastRun.error").value("IllegalStateException: disk full"))
                .andExpect(jsonPath("$.lastSuccess.status").value("SUCCEEDED"));
    }

    /**
     * Linux clocks give nanoseconds; the database keeps microseconds. A window starting at a
     * nanosecond instant must still be found again after it's inserted (CI on Linux failed here
     * while macOS, whose clock only gives microseconds, passed).
     */
    @Test
    @Order(4)
    void windowsWithNanosecondTimestampsStillRun() {
        Instant end = Instant.parse("2026-11-27T08:15:30.123456789Z");
        AnalyticsEngine.Outcome outcome = engine.process("nanos" + System.nanoTime(), end.minus(Duration.ofHours(1)), end, "MANUAL", false, List.of());
        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
    }

    /**
     * QA finding F-4: six runs of one job (different windows) released at the same instant. No two
     * that ran may overlap. Before the per-job lock row, a run's id was allocated before its row
     * was visible, so simultaneous starts could each miss the other and both run.
     */
    @org.junit.jupiter.api.RepeatedTest(30)
    @Order(4)
    void simultaneousStartsOfAJobNeverOverlap() throws Exception {
        String job = "stampede" + System.nanoTime();
        Instant end = Instant.now();
        engine.beforeStep = step -> sleep(5);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(12);
        try {
            List<java.util.concurrent.Future<AnalyticsEngine.Outcome>> runs = new java.util.ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                Instant start = end.minus(Duration.ofHours(i));
                runs.add(pool.submit(() -> { go.await(); return engine.process(job, start, end, "MANUAL", false, List.of()); }));
            }
            go.countDown();
            for (var r : runs) r.get();
        } finally {
            pool.shutdownNow();
        }
        List<java.util.Map<String, Object>> done = jdbc.queryForList(
                "select started_at, finished_at from analytics_job_runs where job_name = ? and status = 'SUCCEEDED' order by started_at", job);
        assertThat(done).isNotEmpty();
        for (int i = 1; i < done.size(); i++) {
            assertThat(JobLock.instant(done.get(i).get("started_at")))
                    .as("run %d started only after run %d finished", i + 1, i)
                    .isAfterOrEqualTo(JobLock.instant(done.get(i - 1).get("finished_at")));
        }
    }

    /**
     * Different windows of the same job (a manual run and the scheduled one) must never run at the
     * same time. Started together, either one is skipped or they ran one after the other.
     */
    @org.junit.jupiter.api.RepeatedTest(5)
    @Order(4)
    void runsOfAJobNeverOverlap() throws Exception {
        String job = "overlaptest" + System.nanoTime();
        Instant end = Instant.now();
        engine.beforeStep = step -> sleep(300);
        CompletableFuture<AnalyticsEngine.Outcome> a = CompletableFuture.supplyAsync(
                () -> engine.process(job, end.minus(Duration.ofHours(2)), end, "SCHEDULE", false, List.of()));
        CompletableFuture<AnalyticsEngine.Outcome> b = CompletableFuture.supplyAsync(
                () -> engine.process(job, end.minus(Duration.ofHours(1)), end, "MANUAL", false, List.of()));
        a.get();
        b.get();
        List<java.util.Map<String, Object>> runs = jdbc.queryForList(
                "select started_at, finished_at from analytics_job_runs where job_name = ? and status = 'SUCCEEDED' order by started_at", job);
        assertThat(runs).isNotEmpty();
        if (runs.size() == 2) {
            assertThat(JobLock.instant(runs.get(1).get("started_at")))
                    .as("the second run started only after the first finished")
                    .isAfterOrEqualTo(JobLock.instant(runs.get(0).get("finished_at")));
        }
    }

    /** Deterministic version: a run that starts while another of the same job is in progress is skipped. */
    @Test
    @Order(5)
    void aRunStartedDuringAnotherIsSkipped() throws Exception {
        String job = "duringtest" + System.nanoTime();
        Instant end = Instant.now();
        java.util.concurrent.CountDownLatch inProgress = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        engine.beforeStep = step -> {
            if (step.equals("collect_changes") && inProgress.getCount() > 0) {
                inProgress.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        CompletableFuture<AnalyticsEngine.Outcome> first = CompletableFuture.supplyAsync(
                () -> engine.process(job, end.minus(Duration.ofHours(2)), end, "SCHEDULE", false, List.of()));
        assertThat(inProgress.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        AnalyticsEngine.Outcome second = engine.process(job, end.minus(Duration.ofHours(1)), end, "MANUAL", false, List.of());
        release.countDown();
        assertThat(second.status()).isEqualTo("SKIPPED");
        assertThat(first.get().status()).isEqualTo("SUCCEEDED");
    }

    @Test
    @Order(6)
    void aCrashedRunsLockIsTakenOver() {
        String job = "staletest" + System.nanoTime();
        Instant now = Instant.now();
        jdbc.update("insert into analytics_job_runs (job_name, window_start, window_end, attempt, status, started_at, heartbeat_at, "
                        + "records_read, records_rejected, metrics_written, step_retries) values (?, ?, ?, 1, 'RUNNING', ?, ?, 0, 0, 0, 0)",
                job, Timestamp.from(now.minus(Duration.ofHours(3))), Timestamp.from(now.minus(Duration.ofHours(2))),
                Timestamp.from(now.minus(Duration.ofHours(2))), Timestamp.from(now.minus(Duration.ofHours(2))));
        // A fresh lock holds…
        String fresh = "freshtest" + System.nanoTime();
        assertThat(lock.acquire(fresh, now, now.plusSeconds(1), now, Duration.ofMinutes(45), "T", "w", false)).isPresent();
        assertThat(lock.acquire(fresh, now.plusSeconds(5), now.plusSeconds(6), now, Duration.ofMinutes(45), "T", "w", false)).isEmpty();
        // …a stale one is taken over.
        assertThat(lock.acquire(job, now.minus(Duration.ofHours(1)), now, now, Duration.ofMinutes(45), "T", "w", false)).isPresent();
        assertThat(jdbc.queryForObject("select status from analytics_job_runs where job_name = ? and attempt = 1 and window_start < ?",
                String.class, job, Timestamp.from(now.minus(Duration.ofMinutes(150))))).isEqualTo("ABANDONED");
    }

    @Test
    @Order(6)
    void aNormalShutdownReleasesItsRunsAtOnce() {
        String job = "shutdowntest" + System.nanoTime();
        Instant now = Instant.now();
        jdbc.update("insert into analytics_job_runs (job_name, window_start, window_end, attempt, status, worker, started_at, "
                        + "heartbeat_at, records_read, records_rejected, metrics_written, step_retries) values (?, ?, ?, 1, 'RUNNING', ?, ?, ?, 0, 0, 0, 0)",
                job, Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now), engine.worker(), Timestamp.from(now), Timestamp.from(now));
        engine.releaseOnShutdown();
        assertThat(jdbc.queryForObject("select status from analytics_job_runs where job_name = ?", String.class, job)).isEqualTo("ABANDONED");
        // The next start can take the window straight away instead of waiting 45 minutes.
        assertThat(lock.acquire(job, now.minusSeconds(3600), now, now, Duration.ofMinutes(45), "T", "w", false)).isPresent();
    }

    @Test
    @Order(7)
    void catchUpAfterLongDowntimeIsCappedAndSaysSo() {
        Instant now = Instant.now();
        jdbc.update("insert into analytics_job_runs (job_name, window_start, window_end, attempt, status, started_at, heartbeat_at, "
                        + "finished_at, records_read, records_rejected, metrics_written, step_retries) "
                        + "values ('hourly', ?, ?, 1, 'SUCCEEDED', ?, ?, ?, 0, 0, 0, 0)",
                Timestamp.from(now.minus(Duration.ofHours(101))), Timestamp.from(now.minus(Duration.ofHours(100))),
                Timestamp.from(now.minus(Duration.ofHours(100))), Timestamp.from(now.minus(Duration.ofHours(100))),
                Timestamp.from(now.minus(Duration.ofHours(100))));
        AnalyticsEngine.Outcome out = engine.runHourly("STARTUP");
        assertThat(out.status()).isEqualTo("SUCCEEDED");
        var row = jdbc.queryForMap("select window_start, window_end, warnings_json from analytics_job_runs where id = ?", out.runId());
        Instant ws = JobLock.instant(row.get("window_start"));
        Instant we = JobLock.instant(row.get("window_end"));
        assertThat(Duration.between(ws, we)).isEqualTo(Duration.ofHours(72));
        assertThat(we).isEqualTo(now.truncatedTo(ChronoUnit.HOURS));
        assertThat((String) row.get("warnings_json")).contains("CATCH_UP_GAP");
        // Next run: nothing new until the next hour.
        assertThat(engine.runHourly("SCHEDULE").status()).isEqualTo("SKIPPED");
    }

    @Test
    @Order(8)
    void reconciliationFindsAndRepairsADriftedSnapshot() throws Exception {
        long id = createListing("RENT", 610, 61, "FLAT", 1);
        rebuildToday();
        LocalDate today = time.today();
        jdbc.update("update listing_daily_snapshots set price = 1 where snapshot_date = ? and property_id = ?", Date.valueOf(today), id);
        jdbc.update("delete from listing_daily_snapshots where snapshot_date = ? and property_id <> ? and city = ?",
                Date.valueOf(today), id, city);
        SnapshotWriter.Reconciliation r = snapshots.reconcile(today, time.zone(), Instant.now(), 0L);
        assertThat(r.different()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select price from listing_daily_snapshots where snapshot_date = ? and property_id = ?",
                BigDecimal.class, Date.valueOf(today), id)).isEqualByComparingTo("610");
    }

    @Test
    @Order(9)
    void onlyAuthorisedStaffSeeOrRunTheEngine() throws Exception {
        Account tenant = accounts.register("Tenant " + System.nanoTime(), UserRole.TENANT);
        call(get("/api/v1/admin/analytics/engine/status"), tenant, null).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/engine/runs"), tenant, null).andExpect(status().isForbidden());
        call(post("/api/v1/admin/analytics/engine/run"), owner, null).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/engine/runs?limit=5"), admin, null).andExpect(status().isOk());
        call(get("/api/v1/admin/analytics/definitions"), admin, null)
                .andExpect(jsonPath("$[?(@.key == 'CONTRACTED_RENT')].description").exists());
        call(post("/api/v1/admin/analytics/engine/rebuild?from=2026-01-01T00:00:00Z&to=2025-01-01T00:00:00Z"), admin, null)
                .andExpect(status().isBadRequest());
    }

    /** 50,000 listings: one run must stay well inside the hourly budget. */
    @Test
    @Order(99)
    void largeDatasetStaysWithinBudget() {
        Instant base = Instant.now().minus(Duration.ofDays(2));
        String loadCity = "Loadcity" + System.nanoTime();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            rows.add(new Object[]{9_000_000L + i, i % 2 == 0 ? "RENT" : "SALE", "AVAILABLE", "Zimbabwe", loadCity,
                    "Suburb " + (i % 250), i % 3 == 0 ? "HOUSE" : "FLAT", 1 + i % 5,
                    BigDecimal.valueOf(i % 2 == 0 ? 300 + i % 2000 : 50_000 + i), Timestamp.from(base.plusSeconds(i))});
        }
        String sql = "insert into property_events (property_id, event_type, listing_type, status, country, city, suburb, category, "
                + "bedrooms, price, currency, occurred_at, recorded_at, source) values (?, 'CREATED', ?, ?, ?, ?, ?, ?, ?, ?, 'USD', ?, ?, 'APP')";
        List<Object[]> withRecorded = rows.stream().map(r -> {
            Object[] x = java.util.Arrays.copyOf(r, r.length + 1);
            x[r.length] = r[r.length - 1];
            return x;
        }).toList();
        for (int i = 0; i < withRecorded.size(); i += 1000) jdbc.batchUpdate(sql, withRecorded.subList(i, i + 1000));

        long started = System.currentTimeMillis();
        Instant now = Instant.now();
        AnalyticsEngine.Outcome out = engine.process("loadtest" + System.nanoTime(), now.minus(Duration.ofHours(1)), now,
                "SCHEDULE", false, List.of());
        long ms = System.currentTimeMillis() - started;
        System.out.println("Analytics load test: 50,000 listings processed in " + ms + " ms");
        assertThat(out.status()).isEqualTo("SUCCEEDED");
        assertThat(ms).isLessThan(120_000);
        Integer suburbs = jdbc.queryForObject("select count(distinct dim_suburb) from metric_values where metric_key = 'ACTIVE_LISTINGS' "
                + "and grain = 'DAY' and dim_city = ? and job_run_id = ?", Integer.class, loadCity, out.runId());
        assertThat(suburbs).isEqualTo(250);
    }

    private AnalyticsEngine.Outcome rebuildToday() {
        Instant start = time.today().atStartOfDay(time.zone()).toInstant();
        return engine.rebuild(start, Instant.now().plusMillis(1));
    }

    private MetricValue day(String metric, String dimsKey) {
        Instant start = time.today().atStartOfDay(time.zone()).toInstant();
        List<MetricValue> found = jdbc.query("select * from metric_values where metric_key = ? and grain = 'DAY' and period_start = ? "
                + "and dims_key = ?", (rs, i) -> {
            MetricValue v = new MetricValue();
            v.setValue(rs.getBigDecimal("metric_value"));
            v.setSampleSize(rs.getInt("sample_size"));
            v.setMedian(rs.getBigDecimal("median"));
            v.setMean(rs.getBigDecimal("mean"));
            v.setP25(rs.getBigDecimal("p25"));
            v.setP75(rs.getBigDecimal("p75"));
            v.setCoverage(rs.getBigDecimal("coverage"));
            v.setLowSample(rs.getBoolean("low_sample"));
            v.setCurrency(rs.getString("currency"));
            v.setComputedAt(rs.getTimestamp("computed_at").toInstant());
            return v;
        }, metric, Timestamp.from(start), dimsKey);
        assertThat(found).as(metric + " " + dimsKey).hasSize(1);
        return found.get(0);
    }

    private long createListing(String type, long price, Integer area, String category, int bedrooms) throws Exception {
        String body = """
                {"title": "Engine test %s", "description": "d", "listingType": "%s", "city": "%s", "suburb": "Central",
                 "address": "1 Test Road", "bedrooms": %d, "bathrooms": 1, "price": %d, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d, %s "category": "%s",
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), type, city, bedrooms, price, owner.id(),
                area == null ? "" : "\"floorAreaM2\": " + area + ",", category);
        String created = call(post("/api/v1/properties"), admin, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return fees.paid(objectMapper.readTree(created).get("id").asLong());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
