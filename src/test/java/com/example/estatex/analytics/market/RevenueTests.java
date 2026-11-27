package com.example.estatex.analytics.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.subscription.LandlordSubscription;
import com.example.estatex.subscription.LandlordSubscriptionRepository;
import com.example.estatex.subscription.SubscriptionPlan;
import com.example.estatex.subscription.SubscriptionService;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase F of docs/admin-analytics-plan.md: revenue. The fixture lives in 2025, a period no other
 * test writes to, and every figure is checked against values worked out by hand.
 *
 * <p>March 2025: landlord A pays $30 for Growth on 16 Mar 10:00Z; agent B pays $15 for a 14-day
 * featured listing on 10 Mar; someone pays $500 rent (not revenue); a subscription payment fails;
 * the renewal sweep marks a $15 renewal paid without collecting money. Subscriptions: A on Growth
 * ($15) from 1 Feb, B on Professional ($49) from 5 Mar, A drops to Starter on 10 Apr.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevenueTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SubscriptionService subscriptions;
    @Autowired private LandlordSubscriptionRepository subscriptionRepo;
    @Autowired private TransactionTemplate tx;

    private TestAccounts accounts;
    private Account admin;
    private Account finance;
    private static boolean seeded;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        finance = staff(UserRole.FINANCE_ADMIN);
        synchronized (RevenueTests.class) {
            if (!seeded) {
                seed();
                seeded = true;
            }
        }
    }

    private void seed() throws Exception {
        Account a = accounts.register("Landlord A " + System.nanoTime(), UserRole.LANDLORD);
        Account b = accounts.register("Agent B " + System.nanoTime(), UserRole.AGENT);
        // Written here, not just updated: with create-drop a second test context recreates the table empty,
        // and Flyway (whose history survives) never re-runs the V10 insert.
        Timestamp historyStart = Timestamp.from(Instant.parse("2024-12-01T00:00:00Z"));
        if (jdbc.update("update analytics_capture_starts set started_at = ? where source = 'subscription_events'", historyStart) == 0) {
            jdbc.update("insert into analytics_capture_starts (source, started_at) values ('subscription_events', ?)", historyStart);
        }
        event(900_001L, a.id(), "2025-02-01T08:00:00Z", "GROWTH", "ACTIVE", "15.00");
        event(900_002L, b.id(), "2025-03-05T08:00:00Z", "PROFESSIONAL", "ACTIVE", "49.00");
        event(900_001L, a.id(), "2025-04-10T08:00:00Z", "STARTER", "ACTIVE", "0.00");
        payment(a.id(), "SUBSCRIPTION", "SUCCESSFUL", "stripe", "30.00", "2025-03-16T10:00:00Z", "plan=GROWTH");
        payment(b.id(), "FEATURED_LISTING", "SUCCESSFUL", "paynow", "15.00", "2025-03-10T10:00:00Z", "durationDays=14");
        payment(a.id(), "RENT", "SUCCESSFUL", "paynow", "500.00", "2025-03-12T10:00:00Z", null);
        payment(a.id(), "SUBSCRIPTION", "FAILED", "stripe", "30.00", "2025-03-20T10:00:00Z", "plan=GROWTH");
        payment(a.id(), "OTHER", "SUCCESSFUL", "platform", "15.00", "2025-03-25T10:00:00Z", null);
        payment(b.id(), "OTHER", "SUCCESSFUL", "platform", "7.00", "2025-03-26T10:00:00Z", null, "Listing fee: Cottage in Avondale");
        jdbc.update("insert into commissions (organisation_id, property_id, amount_cents, currency, status, received_on, created_at) "
                + "values (1, 1, 300000, 'USD', 'RECEIVED', ?, ?)", Date.valueOf(LocalDate.of(2025, 3, 20)), Timestamp.from(Instant.now()));
        call(post("/api/v1/admin/analytics/marketing-spend"), finance,
                "{\"month\": \"2025-03-01\", \"channel\": \"Facebook ads\", \"amount\": 100}").andExpect(status().isOk());
    }

    @Test
    void cashRecognisedAndSubscriptionsMatchTheFixture() throws Exception {
        JsonNode r = revenue("from=2025-03-01&to=2025-04-30&grain=MONTH");
        assertThat(kpi(r, "cash")).isEqualByComparingTo("45.00");            // 30 + 15; rent and the unpaid renewal excluded
        assertThat(kpi(r, "recognised")).isEqualByComparingTo("45.00");      // all of it earned by 15 April
        assertThat(kpi(r, "failed")).isEqualByComparingTo("1");
        assertThat(kpi(r, "uncollected")).isEqualByComparingTo("22.00");     // $15 renewal + $7 listing fee
        assertThat(kpiNode(r, "uncollected").path("note").asString()).isEqualTo("1 listing fee(s), 1 subscription renewal(s)");
        JsonNode march = r.path("series").get(0).path("values");
        JsonNode april = r.path("series").get(1).path("values");
        assertThat(march.path("cash").decimalValue()).isEqualByComparingTo("45");
        // Growth: 15.5 of its 30 days fall in March (Harare month ends 31 Mar 22:00Z) → 15.50; featured: all 15.
        assertThat(march.path("recognised").decimalValue()).isEqualByComparingTo("30.50");
        assertThat(april.path("recognised").decimalValue()).isEqualByComparingTo("14.50");
        assertThat(march.path("uncollectedCharges").asInt()).isEqualTo(2);

        assertThat(march.path("mrr").decimalValue()).isEqualByComparingTo("64");  // 15 + 49
        assertThat(march.path("newMrr").decimalValue()).isEqualByComparingTo("49");
        assertThat(april.path("mrr").decimalValue()).isEqualByComparingTo("49");
        assertThat(april.path("churnedMrr").decimalValue()).isEqualByComparingTo("15");
        assertThat(april.path("churnPercent").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(kpi(r, "mrr")).isEqualByComparingTo("49");
        assertThat(kpi(r, "arr")).isEqualByComparingTo("588");
        assertThat(kpi(r, "churn")).isEqualByComparingTo("50.0");             // 1 of (1 at start + 1 new)
        assertThat(kpi(r, "cac")).isEqualByComparingTo("100.00");             // $100 spend ÷ 1 new paying subscriber
        assertThat(kpi(r, "payback")).isEqualByComparingTo("2.0");             // 100 ÷ 49
        assertThat(kpi(r, "ltv")).isPositive();

        Map<String, BigDecimal> products = slices(r.path("byProduct"));
        assertThat(products.get("Subscriptions: Growth")).isEqualByComparingTo("30.00");
        assertThat(products.get("Featured listings")).isEqualByComparingTo("15.00");
        Map<String, BigDecimal> categories = slices(r.path("byCategory"));
        assertThat(categories.get("Landlords")).isEqualByComparingTo("30.00");
        assertThat(categories.get("Agents and agencies")).isEqualByComparingTo("15.00");
        assertThat(r.path("notes").toString()).contains("marked paid by the system without collecting money");

        // Reconciliation with the payments table.
        BigDecimal expected = jdbc.queryForObject("select coalesce(sum(amount), 0) from payments where status = 'SUCCESSFUL' "
                + "and purpose_type in ('SUBSCRIPTION', 'FEATURED_LISTING') and provider <> 'platform' and currency = 'USD' "
                + "and paid_at >= ? and paid_at < ?", BigDecimal.class,
                Timestamp.from(Instant.parse("2025-02-28T22:00:00Z")), Timestamp.from(Instant.parse("2025-04-30T22:00:00Z")));
        assertThat(kpi(r, "cash")).isEqualByComparingTo(expected);
    }

    @Test
    void deferredRevenueIsWhatHasBeenPaidButNotYetEarned() throws Exception {
        JsonNode march = revenue("from=2025-03-01&to=2025-03-31&grain=MONTH");
        assertThat(kpi(march, "deferred")).isEqualByComparingTo("14.50");      // the April part of the Growth payment
        assertThat(kpi(march, "recognised").add(kpi(march, "deferred"))).isEqualByComparingTo(kpi(march, "cash"));
    }

    @Test
    void commissionShareIsAnEstimateAndOffUntilAnAdminSetsIt() throws Exception {
        call(put("/api/v1/admin/analytics/revenue/commission-share"), admin, "{\"percent\": 0}").andExpect(status().isOk());
        JsonNode off = revenue("from=2025-03-01&to=2025-03-31&grain=MONTH");
        assertThat(kpiNode(off, "commission_share").path("note").asString()).contains("Not enabled");
        call(put("/api/v1/admin/analytics/revenue/commission-share"), finance, "{\"percent\": 10}").andExpect(status().isForbidden());
        call(put("/api/v1/admin/analytics/revenue/commission-share"), admin, "{\"percent\": 80}").andExpect(status().isBadRequest());
        call(put("/api/v1/admin/analytics/revenue/commission-share"), admin, "{\"percent\": 10}").andExpect(status().isOk());
        JsonNode on = revenue("from=2025-03-01&to=2025-03-31&grain=MONTH");
        assertThat(kpi(on, "commission_share")).isEqualByComparingTo("300.00");
        assertThat(kpi(on, "cash")).isEqualByComparingTo("45.00");             // never added to cash
        call(put("/api/v1/admin/analytics/revenue/commission-share"), admin, "{\"percent\": 0}").andExpect(status().isOk());
    }

    @Test
    void everySubscriptionChangeIsRecordedWithItsPrice() {
        Account owner;
        try {
            owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Long subId = tx.execute(s -> subscriptions.subscriptionFor(userService.require(owner.id())).getId());
        tx.executeWithoutResult(s -> {
            LandlordSubscription sub = subscriptionRepo.findById(subId).orElseThrow();
            sub.setPlan(SubscriptionPlan.GROWTH);
        });
        tx.executeWithoutResult(s -> subscriptionRepo.findById(subId).orElseThrow().setCurrentPeriodEnd(Instant.now()));
        List<Map<String, Object>> rows = jdbc.queryForList("select event_type, plan, monthly_price, previous_plan from subscription_events "
                + "where subscription_id = ? order by id", subId);
        assertThat(rows).extracting(m -> m.get("event_type")).containsExactly("CREATED", "CHANGED"); // the date change isn't an event
        assertThat((BigDecimal) rows.get(1).get("monthly_price")).isEqualByComparingTo("15.00");
        assertThat(rows.get(1).get("previous_plan")).isEqualTo("STARTER");
    }

    @Test
    void onlyFinanceStaffSeeRevenueAndEveryViewIsAudited() throws Exception {
        Account analyst = staff(UserRole.MARKET_ANALYST);
        call(get("/api/v1/admin/analytics/revenue"), analyst, null).andExpect(status().isForbidden());
        call(post("/api/v1/admin/analytics/marketing-spend"), analyst,
                "{\"month\": \"2025-03-01\", \"channel\": \"x\", \"amount\": 1}").andExpect(status().isForbidden());
        int before = jdbc.queryForObject("select count(*) from audit_logs where action = 'ANALYTICS_REVENUE_VIEWED' and actor_user_id = ?",
                Integer.class, finance.id());
        revenue("from=2025-03-01&to=2025-03-31");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ANALYTICS_REVENUE_VIEWED' and actor_user_id = ?",
                Integer.class, finance.id())).isEqualTo(before + 1);
        call(get("/api/v1/admin/analytics/revenue?from=2020-01-01&to=2025-01-01"), finance, null).andExpect(status().isBadRequest());
    }

    @Test
    void recognitionSharesAreExact() {
        Instant paid = Instant.parse("2025-03-16T10:00:00Z");
        Instant end = paid.plusSeconds(30L * 86400);
        BigDecimal march = RevenueService.share(new BigDecimal("30"), paid, end, Instant.parse("2025-02-28T22:00:00Z"),
                Instant.parse("2025-03-31T22:00:00Z"));
        BigDecimal april = RevenueService.share(new BigDecimal("30"), paid, end, Instant.parse("2025-03-31T22:00:00Z"),
                Instant.parse("2025-04-30T22:00:00Z"));
        assertThat(march).isEqualByComparingTo("15.5");
        assertThat(march.add(april)).isEqualByComparingTo("30");
    }

    // ---------------------------------------------------------------------------------------

    private void event(long sub, long user, String at, String plan, String status, String price) {
        jdbc.update("insert into subscription_events (subscription_id, user_id, event_type, plan, status, monthly_price, currency, "
                        + "occurred_at, recorded_at, source) values (?, ?, 'CHANGED', ?, ?, ?, 'USD', ?, ?, 'TEST')",
                sub, user, plan, status, new BigDecimal(price), Timestamp.from(Instant.parse(at)), Timestamp.from(Instant.parse(at)));
    }

    private void payment(long payer, String purpose, String status, String provider, String amount, String at, String metadata) {
        payment(payer, purpose, status, provider, amount, at, metadata, purpose.toLowerCase());
    }

    private void payment(long payer, String purpose, String status, String provider, String amount, String at, String metadata,
                         String description) {
        Timestamp t = Timestamp.from(Instant.parse(at));
        jdbc.update("insert into payments (payer_id, payee_id, status, amount, currency, provider, purpose, purpose_type, metadata, "
                        + "created_at, paid_at) values (?, ?, ?, ?, 'USD', ?, ?, ?, ?, ?, ?)",
                payer, admin.id(), status, new BigDecimal(amount), provider, description, purpose, metadata, t,
                "SUCCESSFUL".equals(status) ? t : null);
    }

    private Account staff(UserRole role) throws Exception {
        Account a = accounts.register(role.name() + " " + System.nanoTime(), UserRole.TENANT);
        call(put("/api/v1/admin/staff/" + a.id()), admin, "{\"roles\": [\"" + role.name() + "\"]}").andExpect(status().isOk());
        return a;
    }

    private JsonNode revenue(String query) throws Exception {
        return json(call(get("/api/v1/admin/analytics/revenue?" + query), finance, null).andExpect(status().isOk()));
    }

    private static JsonNode kpiNode(JsonNode r, String key) {
        for (JsonNode k : r.path("kpis")) if (k.path("key").asString().equals(key)) return k;
        throw new AssertionError("no kpi " + key);
    }

    private static BigDecimal kpi(JsonNode r, String key) {
        JsonNode v = kpiNode(r, key).path("value");
        if (v.isNull() || v.isMissingNode()) throw new AssertionError("kpi " + key + " has no value: " + kpiNode(r, key));
        return v.decimalValue();
    }

    private static Map<String, BigDecimal> slices(JsonNode list) {
        Map<String, BigDecimal> out = new java.util.HashMap<>();
        list.forEach(s -> out.put(s.path("label").asString(), s.path("cash").decimalValue()));
        return out;
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
