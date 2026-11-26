package com.example.estatex.analytics.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.analytics.alert.AlertService;
import com.example.estatex.analytics.metrics.Dims;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase G: observations from a known fixture of published figures, the AI interpretation guard
 * rails, alert thresholds / dedupe / cooldown / preferences, and per-role visibility.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsightsTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObservationService observationService;
    @Autowired private MarketInsightService insightService;
    @Autowired private AlertService alertService;
    @Autowired private MarketObservationRepository observationRepo;
    @Autowired private BusinessTime time;

    private TestAccounts accounts;
    private Account admin;
    private Period cur;
    private Period prev;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        Instant now = time.clock().instant();
        cur = Period.containing(Grain.WEEK, now, time.zone()).previous(time.zone());
        prev = cur.previous(time.zone());
        // The two latest completed weeks hold exactly this fixture (the current week is never complete).
        jdbc.update("delete from metric_values where grain = 'WEEK' and period_start >= ?", Timestamp.from(prev.start()));
        jdbc.update("delete from market_observations where grain = 'WEEK'");
        jdbc.update("delete from analytics_alerts");
        jdbc.update("delete from market_insights");
        jdbc.update("delete from alert_rules");
        jdbc.update("delete from alert_preferences");
        jdbc.update("delete from analytics_settings where setting_key = ?", MarketInsightService.AI_SETTING);
        // Capture starts are left alone: other tests rely on listing history starting recently. Enquiries
        // have full history; supply (listing history) is simply not tested here.
        Dims rentAvondale = new Dims("RENT", "Zimbabwe", "Harare", "Avondale", null, null);
        Dims rentBorrowdale = new Dims("RENT", "Zimbabwe", "Harare", "Borrowdale", null, null);
        Dims rentHarare = new Dims("RENT", "Zimbabwe", "Harare", null, null, null);
        Dims rent = new Dims("RENT", null, null, null, null, null);
        Dims cottage = new Dims("RENT", null, null, null, "COTTAGE", null);
        Dims house = new Dims("RENT", null, null, null, "HOUSE", null);
        // Rental enquiries in Avondale: 10 → 40 (significant). Borrowdale: 3 → 6 (too few to test).
        count("ENQUIRIES", rentAvondale, 40, 10);
        count("ENQUIRIES", rentBorrowdale, 6, 3);
        count("ENQUIRIES", rent, 60, 50);              // +20%, not significant
        count("ENQUIRIES", cottage, 30, 20);
        count("ENQUIRIES", house, 20, 25);
        // Supply: 100 rental listings both weeks; cottages 10, houses 60.
        count("ACTIVE_LISTINGS", rent, 100, 100);
        count("ACTIVE_LISTINGS", cottage, 10, 10);
        count("ACTIVE_LISTINGS", house, 60, 60);
        // Asking rent in Harare: median 500 → 600, n = 30 each.
        median("ASKING_PRICE", rentHarare, 600, 540, 660, 30, 500, 450, 550, 30);
        // Failed payments (finance): 5 → 25.
        count("PAYMENTS_FAILED", Dims.ALL, 25, 5);
    }

    @AfterEach
    void tearDown() {
        insightService.writerOverride = null;
    }

    @Test
    void significantChangesBecomeObservationsAndThinSlicesDoNot() {
        ObservationService.Result r = observationService.generate(time.clock().instant(), null);
        List<MarketObservation> week = r.observations().stream().filter(o -> o.getGrain().equals("WEEK")).toList();

        MarketObservation avondale = only(week, "ENQUIRIES_UP", "Avondale");
        assertThat(avondale.getMethod()).isEqualTo("POISSON_RATE");
        assertThat(avondale.getSampleCurrent()).isEqualTo(40);
        assertThat(avondale.getSamplePrevious()).isEqualTo(10);
        assertThat(avondale.getChangePct()).isEqualByComparingTo("300");
        assertThat(avondale.getQValue()).isLessThanOrEqualTo(0.05);
        assertThat(avondale.getStatement()).startsWith("Enquiries for rentals in Avondale, Harare rose from 10 to 40 (+300%)");
        assertThat(avondale.getComparisonStart()).isEqualTo(prev.start());
        assertThat(avondale.getDomain()).isEqualTo("DEMAND");

        assertThat(week).noneMatch(o -> "Borrowdale".equals(o.getSuburb()));                   // 9 events < minimum 20
        assertThat(week).noneMatch(o -> o.getKind().startsWith("ENQUIRIES") && o.getCity() == null && o.getCategory() == null
                && "RENT".equals(o.getListingType()));                                             // 50 → 60 is noise
        assertThat(week).noneMatch(o -> o.getKind().startsWith("ACTIVE_LISTINGS_"));              // supply unchanged

        MarketObservation price = only(week, "ASKING_PRICE_UP", null);
        assertThat(price.getMethod()).isEqualTo("MEDIAN_SHIFT");
        assertThat(price.getCity()).isEqualTo("Harare");
        assertThat(price.getStatement()).contains("from $500 to $600 (+20%)");

        MarketObservation under = only(week, "CATEGORY_UNDERSUPPLIED", null);
        assertThat(under.getCategory()).isEqualTo("COTTAGE");
        assertThat(under.getStatement()).contains("Cottage listings drew 50% of rental enquiries (30 of 60) but are 10% of rental listings (10 of 100)");
        MarketObservation top = only(week, "TOP_SUPPLY_CATEGORY", null);
        assertThat(top.getCategory()).isEqualTo("HOUSE");
        assertThat(top.getMethod()).isEqualTo("DESCRIPTIVE");

        assertThat(only(week, "PAYMENTS_FAILED_UP", null).getDomain()).isEqualTo("FINANCE");

        ObservationCoverage cov = r.coverage().stream().filter(c -> c.getGrain().equals("WEEK")).findFirst().orElseThrow();
        assertThat(cov.getBelowMinimum()).isGreaterThanOrEqualTo(1);
        assertThat(cov.getTested()).isGreaterThanOrEqualTo(5);
        assertThat(cov.getReported()).isEqualTo(week.size());
    }

    @Test
    void rerunsKeepIdsAndDropFindingsThatNoLongerHold() {
        Instant now = time.clock().instant();
        long id = only(observationService.generate(now, null).observations(), "ENQUIRIES_UP", "Avondale").getId();
        assertThat(only(observationService.generate(now, null).observations(), "ENQUIRIES_UP", "Avondale").getId()).isEqualTo(id);

        // A recalculation brings Avondale back in line: the observation goes.
        jdbc.update("update metric_values set metric_value = 12, sample_size = 12 where metric_key = 'ENQUIRIES' and grain = 'WEEK' "
                + "and period_start = ? and dim_suburb = 'Avondale'", Timestamp.from(cur.start()));
        observationService.generate(now, null);
        assertThat(observationRepo.findById(id)).isEmpty();
    }

    @Test
    void eachRoleSeesOnlyItsDomainsAndQuestionsSayWhenThereIsNoConclusion() throws Exception {
        observationService.generate(time.clock().instant(), null);
        Account analyst = staff(UserRole.MARKET_ANALYST);
        Account finance = staff(UserRole.FINANCE_ADMIN);
        Account tenant = accounts.register("Tenant " + System.nanoTime(), UserRole.TENANT);

        JsonNode a = insights(analyst, "WEEK");
        assertThat(kinds(a)).contains("ENQUIRIES_UP", "ASKING_PRICE_UP", "CATEGORY_UNDERSUPPLIED").doesNotContain("PAYMENTS_FAILED_UP");
        JsonNode f = insights(finance, "WEEK");
        assertThat(kinds(f)).containsExactly("PAYMENTS_FAILED_UP");
        call(get("/api/v1/admin/analytics/insights"), tenant, null).andExpect(status().isForbidden());

        JsonNode suburbs = question(a, "rental_enquiry_suburbs");
        assertThat(suburbs.path("answer").asString("")).isEmpty();                // answered with evidence, not "no conclusion"
        long avondaleId = suburbs.path("observationIds").get(0).asLong();
        assertThat(observationRepo.findById(avondaleId).orElseThrow().getSuburb()).isEqualTo("Avondale");
        assertThat(question(a, "yields").path("answer").asString()).startsWith("No reliable conclusion can be drawn");
        assertThat(question(a, "underrepresented").path("observationIds")).hasSize(1);
        assertThat(a.path("minimums").path("events").asInt()).isEqualTo(ObservationService.MIN_EVENTS);
    }

    @Test
    void aiInterpretationOnlyKeepsItemsThatCiteRealObservationsAndIsNotRepeated() throws Exception {
        List<MarketObservation> obs = observationService.generate(time.clock().instant(), null).observations();
        long avondaleId = only(obs, "ENQUIRIES_UP", "Avondale").getId();
        AtomicInteger calls = new AtomicInteger();
        List<List<Long>> seen = new ArrayList<>();
        insightService.writerOverride = input -> {
            calls.incrementAndGet();
            seen.add(input.stream().map(MarketObservation::getId).toList());
            return new MarketInsightService.Draft("Rental demand rose in Avondale.", List.of(
                    new MarketInsightService.DraftItem("opportunity", "Avondale rentals", "Enquiries rose sharply; worth checking supply.",
                            "Recruit landlords in Avondale.", List.of(avondaleId, 999_999L)),
                    new MarketInsightService.DraftItem("CHANGE", "Invented", "Something with no evidence.", "None.", List.of(999_999L))));
        };

        MarketInsightService.Outcome first = insightService.refresh(obs, time.clock().instant(), null);
        assertThat(first.status()).isEqualTo("OK");
        assertThat(seen.get(0)).contains(avondaleId);
        // Finance observations are never sent to the AI.
        assertThat(seen.get(0)).doesNotContain(only(obs, "PAYMENTS_FAILED_UP", null).getId());
        assertThat(insightService.refresh(obs, time.clock().instant(), null).status()).isEqualTo("UNCHANGED");
        assertThat(calls.get()).isEqualTo(1);

        JsonNode ai = insights(admin, "WEEK").path("ai");
        assertThat(ai.path("status").asString()).isEqualTo("OK");
        assertThat(ai.path("headline").asString()).isEqualTo("Rental demand rose in Avondale.");
        assertThat(ai.path("items")).hasSize(1);                                    // the uncited item was dropped
        assertThat(ai.path("items").get(0).path("section").asString()).isEqualTo("OPPORTUNITY");
        assertThat(ai.path("items").get(0).path("observationIds").toString()).isEqualTo("[" + avondaleId + "]");
        assertThat(question(insights(admin, "WEEK"), "opportunities").path("answer").asString()).contains("AI interpretation");

        // Turned off: observations are still served, the AI is not called.
        call(put("/api/v1/admin/analytics/insights/ai"), admin, "{\"enabled\": false}").andExpect(status().isOk());
        JsonNode off = insights(admin, "WEEK");
        assertThat(off.path("ai").path("status").asString()).isEqualTo("OFF");
        assertThat(off.path("observations").size()).isGreaterThan(0);
        jdbc.update("delete from market_insights");
        assertThat(insightService.refresh(obs, time.clock().instant(), null).status()).isEqualTo("OFF");
        assertThat(calls.get()).isEqualTo(1);
        Account analyst = staff(UserRole.MARKET_ANALYST);
        call(put("/api/v1/admin/analytics/insights/ai"), analyst, "{\"enabled\": true}").andExpect(status().isForbidden());
    }

    @Test
    void aFailedOrRefusedInterpretationIsRecordedAndObservationsRemain() {
        List<MarketObservation> obs = observationService.generate(time.clock().instant(), null).observations();
        insightService.writerOverride = input -> {
            throw new MarketInsightService.Refused();
        };
        assertThat(insightService.refresh(obs, time.clock().instant(), null).status()).isEqualTo("REFUSED");
        insightService.writerOverride = input -> new MarketInsightService.Draft("x", List.of());
        jdbc.update("delete from market_insights");
        assertThat(insightService.refresh(obs, time.clock().instant(), null).status()).isEqualTo("FAILED");
    }

    @Test
    void theClaudeRequestBuildsWithAStructuredOutputSchema() {
        List<MarketObservation> obs = observationService.generate(time.clock().instant(), null).observations();
        var params = insightService.params(MarketInsightService.input(obs));
        assertThat(params.rawParams().model().asString()).isEqualTo(insightService.model());
        assertThat(params.rawParams().outputConfig()).isPresent();
        assertThat(params.rawParams().messages().get(0).content().toString()).contains("Avondale").doesNotContain("PAYMENTS_FAILED");
    }

    @Test
    void alertsRespectThresholdsDedupeCooldownAndPreferences() throws Exception {
        Account analyst = staff(UserRole.MARKET_ANALYST);
        Account finance = staff(UserRole.FINANCE_ADMIN);
        List<MarketObservation> obs = observationService.generate(time.clock().instant(), null).observations();
        Instant now = time.clock().instant();

        AlertService.Raised first = alertService.evaluate(obs, 0, null, now);
        assertThat(first.raised()).isGreaterThanOrEqualTo(3);                    // demand surge, price jump, payment failures, …
        long surge = alertId("DEMAND_SURGE");
        assertThat(notified(analyst.id(), surge)).isTrue();
        assertThat(notified(admin.id(), surge)).isTrue();
        assertThat(notified(finance.id(), surge)).isFalse();                     // finance can't see demand
        assertThat(notified(finance.id(), alertId("PAYMENT_FAILURES_UP"))).isTrue();

        // Same findings again: nothing new.
        assertThat(alertService.evaluate(obs, 0, null, now).raised()).isZero();

        // A new period for the same place inside the cooldown: held back.
        MarketObservation avondale = only(obs, "ENQUIRIES_UP", "Avondale");
        avondale.setPeriodStart(avondale.getPeriodStart().plusSeconds(7 * 86400));
        AlertService.Raised again = alertService.evaluate(List.of(avondale), 0, null, now.plusSeconds(3600));
        assertThat(again.raised()).isZero();
        assertThat(again.suppressed()).isEqualTo(1);

        // Raise the threshold above +300%: the rule no longer fires (after the cooldown).
        String body = "{\"enabled\": true, \"threshold\": 400, \"minSample\": 30, \"maxQValue\": 0.05, \"cooldownHours\": 72, \"severity\": \"INFO\"}";
        call(put("/api/v1/admin/analytics/alert-rules/DEMAND_SURGE"), analyst, body).andExpect(status().isForbidden());
        call(put("/api/v1/admin/analytics/alert-rules/DEMAND_SURGE"), admin, body).andExpect(status().isOk());
        avondale.setPeriodStart(avondale.getPeriodStart().plusSeconds(7 * 86400));
        assertThat(alertService.evaluate(List.of(avondale), 0, null, now.plusSeconds(200 * 3600)).raised()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ANALYTICS_ALERT_RULE_CHANGED'", Integer.class))
                .isPositive();

        // Back to defaults; the analyst turns in-app off for this rule and gets nothing.
        jdbc.update("delete from alert_rules");
        call(put("/api/v1/admin/analytics/alert-preferences/DEMAND_SURGE"), analyst, "{\"inApp\": false, \"email\": false}")
                .andExpect(status().isOk());
        call(put("/api/v1/admin/analytics/alert-preferences/PAYMENT_FAILURES_UP"), analyst, "{\"inApp\": true, \"email\": false}")
                .andExpect(status().isForbidden());                             // not a finance role
        avondale.setPeriodStart(avondale.getPeriodStart().plusSeconds(7 * 86400));
        assertThat(alertService.evaluate(List.of(avondale), 0, null, now.plusSeconds(400 * 3600)).raised()).isEqualTo(1);
        long later = jdbc.queryForObject("select max(id) from analytics_alerts where rule_key = 'DEMAND_SURGE'", Long.class);
        assertThat(notified(analyst.id(), later)).isFalse();
        assertThat(notified(admin.id(), later)).isTrue();
    }

    @Test
    void operationalAlertsAndAcknowledgement() throws Exception {
        Instant now = time.clock().instant();
        alertService.jobFailed("hourly", 42L, "boom", now);
        alertService.jobFailed("hourly", 42L, "boom", now);                     // same run: once
        alertService.evaluate(List.of(), 4, 43L, now);                          // 4 new duplicate pairs ≥ default 3
        Account ops = staff(UserRole.OPERATIONS_ADMIN);
        Account analyst = staff(UserRole.MARKET_ANALYST);

        JsonNode list = json(call(get("/api/v1/admin/analytics/alerts?open=true"), ops, null).andExpect(status().isOk()));
        assertThat(list.path("openCount").asLong()).isEqualTo(2);
        JsonNode failed = null;
        for (JsonNode a : list.path("alerts")) if (a.path("ruleKey").asString().equals("JOB_FAILED")) failed = a;
        assertThat(failed).isNotNull();
        assertThat(failed.path("severity").asString()).isEqualTo("CRITICAL");
        // The analyst's role has no operations access: these alerts are not theirs.
        JsonNode theirs = json(call(get("/api/v1/admin/analytics/alerts"), analyst, null).andExpect(status().isOk()));
        assertThat(theirs.path("alerts")).isEmpty();
        call(post("/api/v1/admin/analytics/alerts/" + failed.path("id").asLong() + "/acknowledge"), analyst, null)
                .andExpect(status().isNotFound());

        call(post("/api/v1/admin/analytics/alerts/" + failed.path("id").asLong() + "/acknowledge"), ops, null).andExpect(status().isOk());
        assertThat(json(call(get("/api/v1/admin/analytics/alerts?open=true"), ops, null)).path("openCount").asLong()).isEqualTo(1);

        // Stale data: last success three hours ago.
        jdbc.update("insert into analytics_job_runs (job_name, window_start, window_end, attempt, status, started_at, finished_at, "
                        + "heartbeat_at, triggered_by, worker, records_read, records_rejected, metrics_written, step_retries) "
                        + "values ('test-stale', ?, ?, 1, 'SUCCEEDED', ?, ?, ?, 'TEST', 'test', 0, 0, 0, 0)",
                Timestamp.from(now.minusSeconds(400_000)), Timestamp.from(now.minusSeconds(396_400)), Timestamp.from(now.minusSeconds(400_000)),
                Timestamp.from(now.minusSeconds(399_000)), Timestamp.from(now.minusSeconds(399_000)));
        Long newest = jdbc.queryForObject("select count(*) from analytics_job_runs where status = 'SUCCEEDED' and finished_at > ?",
                Long.class, Timestamp.from(now.minusSeconds(3 * 3600)));
        alertService.checkStale(now);
        int stale = jdbc.queryForObject("select count(*) from analytics_alerts where rule_key = 'DATA_STALE'", Integer.class);
        assertThat(stale).isEqualTo(newest == 0 ? 1 : 0);
        call(get("/api/v1/admin/analytics/alert-rules"), ops, null).andExpect(status().isOk());
        JsonNode prefs = json(call(get("/api/v1/admin/analytics/alert-preferences"), analyst, null).andExpect(status().isOk()));
        assertThat(prefs.toString()).contains("DEMAND_SURGE").doesNotContain("PAYMENT_FAILURES_UP").doesNotContain("JOB_FAILED");
    }

    // ---------------------------------------------------------------------------------------

    private void count(String metric, Dims d, long current, long previous) {
        insert(metric, d, cur, BigDecimal.valueOf(current), (int) current, null, null, null);
        insert(metric, d, prev, BigDecimal.valueOf(previous), (int) previous, null, null, null);
    }

    private void median(String metric, Dims d, double m1, double p25a, double p75a, int n1, double m0, double p25b, double p75b, int n0) {
        insert(metric, d, cur, BigDecimal.valueOf(m1), n1, BigDecimal.valueOf(m1), BigDecimal.valueOf(p25a), BigDecimal.valueOf(p75a));
        insert(metric, d, prev, BigDecimal.valueOf(m0), n0, BigDecimal.valueOf(m0), BigDecimal.valueOf(p25b), BigDecimal.valueOf(p75b));
    }

    private void insert(String metric, Dims d, Period p, BigDecimal value, int n, BigDecimal median, BigDecimal p25, BigDecimal p75) {
        jdbc.update("insert into metric_values (metric_key, definition_version, grain, period_start, period_end, as_of, complete, variant, "
                        + "history_complete, dims_key, dim_listing_type, dim_country, dim_city, dim_suburb, dim_category, dim_bedrooms, "
                        + "metric_value, sample_size, median, p25, p75, excluded_count, low_sample, currency, computed_at) "
                        + "values (?, 1, 'WEEK', ?, ?, ?, true, 'FULL', true, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, false, ?, ?)",
                metric, Timestamp.from(p.start()), Timestamp.from(p.end()), Timestamp.from(p.end()), d.key(), d.listingType(), d.country(),
                d.city(), d.suburb(), d.category(), d.bedrooms(), value, n, median, p25, p75, median == null ? null : "USD",
                Timestamp.from(time.clock().instant()));
    }

    private static MarketObservation only(List<MarketObservation> obs, String kind, String suburb) {
        List<MarketObservation> m = obs.stream().filter(o -> o.getKind().equals(kind) && o.getGrain().equals("WEEK")
                && (suburb == null || suburb.equals(o.getSuburb()))).toList();
        assertThat(m).as(kind + " " + suburb + " in " + obs.stream().map(MarketObservation::getKind).toList()).hasSize(1);
        return m.get(0);
    }

    private long alertId(String rule) {
        return jdbc.queryForObject("select max(id) from analytics_alerts where rule_key = ?", Long.class, rule);
    }

    private boolean notified(long userId, long alertId) {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'MARKET_ALERT' and related_id = ?",
                Integer.class, userId, alertId) > 0;
    }

    private JsonNode insights(Account who, String grain) throws Exception {
        return json(call(get("/api/v1/admin/analytics/insights?grain=" + grain), who, null).andExpect(status().isOk()));
    }

    private static List<String> kinds(JsonNode insights) {
        List<String> out = new ArrayList<>();
        insights.path("observations").forEach(o -> out.add(o.path("kind").asString()));
        return out;
    }

    private static JsonNode question(JsonNode insights, String key) {
        for (JsonNode q : insights.path("questions")) if (q.path("key").asString().equals(key)) return q;
        throw new AssertionError("no question " + key);
    }

    private Account staff(UserRole role) throws Exception {
        Account a = accounts.register(role.name() + " " + System.nanoTime(), UserRole.TENANT);
        call(put("/api/v1/admin/staff/" + a.id()), admin, "{\"roles\": [\"" + role.name() + "\"]}").andExpect(status().isOk());
        return a;
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }
}
