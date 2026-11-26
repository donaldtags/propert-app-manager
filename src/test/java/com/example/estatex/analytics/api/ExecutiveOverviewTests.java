package com.example.estatex.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.analytics.engine.AnalyticsEngine;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

/** Phase C of docs/admin-analytics-plan.md: staff roles, the executive overview and drill-down. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutiveOverviewTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AnalyticsEngine engine;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BusinessTime time;

    private TestAccounts accounts;
    private Account admin;
    private Account owner;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
    }

    @Test
    void staffRolesAreOnlyEverGrantedByAnAdmin() throws Exception {
        String body = """
                {"fullName": "Sneaky", "email": "%s", "phone": "+2637%08d", "password": "%s", "country": "Zimbabwe",
                 "roles": ["FINANCE_ADMIN"]}
                """.formatted(TestAccounts.uniqueEmail("sneaky"), System.nanoTime() % 100_000_000L, TestAccounts.PASSWORD);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        Account tenant = accounts.register("Tenant " + System.nanoTime(), UserRole.TENANT);
        call(post("/api/v1/users/" + tenant.id() + "/roles"), tenant,
                "{\"role\": \"MARKET_ANALYST\", \"password\": \"" + TestAccounts.PASSWORD + "\"}").andExpect(status().isForbidden());
        call(put("/api/v1/admin/staff/" + tenant.id()), owner, "{\"roles\": [\"MARKET_ANALYST\"]}").andExpect(status().isForbidden());

        call(put("/api/v1/admin/staff/" + tenant.id()), admin, "{\"roles\": [\"MARKET_ANALYST\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.staffRoles[0]").value("MARKET_ANALYST"));
        call(get("/api/v1/admin/staff"), admin, null).andExpect(jsonPath("$[*].id", hasItem((int) tenant.id())));
        call(put("/api/v1/admin/staff/" + tenant.id()), admin, "{\"roles\": [\"TENANT\"]}").andExpect(status().isBadRequest());
        call(put("/api/v1/admin/staff/" + admin.id()), admin, "{\"roles\": []}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'STAFF_ROLES_CHANGED' and entity_id = ?",
                Integer.class, tenant.id())).isEqualTo(1);

        // Removing the role takes the access away again; the tenant role stays.
        call(put("/api/v1/admin/staff/" + tenant.id()), admin, "{\"roles\": []}").andExpect(jsonPath("$.staffRoles.length()").value(0));
        call(get("/api/v1/admin/analytics/overview"), tenant, null).andExpect(status().isForbidden());
    }

    @Test
    void eachStaffRoleSeesOnlyItsOwnFigures() throws Exception {
        Account analyst = staff(UserRole.MARKET_ANALYST);
        Account finance = staff(UserRole.FINANCE_ADMIN);
        rebuildToday();

        JsonNode a = overview(analyst, "grain=DAY");
        assertThat(section(a, "business").path("restricted").asBoolean()).isTrue();
        assertThat(section(a, "business").path("kpis").size()).isZero();
        // Platform user figures need operations access; verified listings are market data and stay visible.
        assertThat(keys(section(a, "platform"))).containsExactly("verified_listings");
        assertThat(keys(section(a, "headline"))).contains("active_listings", "rental_enquiries").doesNotContain("platform_revenue");
        assertThat(keys(section(a, "marketplace"))).contains("new_listings", "verified_sales");
        call(get("/api/v1/admin/analytics/records?metric=PLATFORM_REVENUE&grain=DAY&periodStart=" + todayStart()), analyst, null)
                .andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/metrics?metric=PLATFORM_REVENUE&grain=DAY&from=2026-01-01T00:00:00Z&to=2030-01-01T00:00:00Z"),
                analyst, null).andExpect(status().isForbidden());

        int auditsBefore = jdbc.queryForObject("select count(*) from audit_logs where action = 'ANALYTICS_FINANCE_VIEWED' and actor_user_id = ?",
                Integer.class, finance.id());
        JsonNode f = overview(finance, "grain=DAY");
        assertThat(keys(section(f, "business"))).contains("platform_revenue", "mrr", "payments_failed", "lead_revenue");
        assertThat(keys(section(f, "headline"))).containsExactly("platform_revenue");
        assertThat(section(f, "marketplace").path("restricted").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ANALYTICS_FINANCE_VIEWED' and actor_user_id = ?",
                Integer.class, finance.id())).isEqualTo(auditsBefore + 1);
        JsonNode lead = kpi(section(f, "business"), "lead_revenue");
        assertThat(lead.path("available").asBoolean()).isFalse();
        assertThat(lead.path("value").isNull() || lead.path("value").isMissingNode()).isTrue();
    }

    @Test
    void kpisComeFromPublishedFiguresAndFilterByLocation() throws Exception {
        String city = "Overviewcity" + System.nanoTime();
        List<Long> ids = new ArrayList<>();
        ids.add(createListing(city, "Belgravia", "RENT", 700));
        ids.add(createListing(city, "Belgravia", "RENT", 900));
        ids.add(createListing(city, "Avenues", "RENT", 650));
        ids.add(createListing(city, "Belgravia", "SALE", 150_000));
        rebuildToday();

        JsonNode o = overview(admin, "grain=DAY&city=" + city);
        JsonNode headline = section(o, "headline");
        assertThat(kpi(headline, "active_listings").path("value").decimalValue()).isEqualByComparingTo("4");
        assertThat(kpi(headline, "rental_listings").path("value").decimalValue()).isEqualByComparingTo("3");
        assertThat(kpi(headline, "sale_listings").path("value").decimalValue()).isEqualByComparingTo("1");
        assertThat(kpi(headline, "active_listings").path("locationApplies").asBoolean()).isTrue();
        // Yesterday began before listing history was recorded: no fake "+400%".
        JsonNode active = kpi(headline, "active_listings");
        assertThat(active.path("comparable").asBoolean()).isFalse();
        assertThat(active.path("changePercent").isNull() || active.path("changePercent").isMissingNode()).isTrue();
        assertThat(active.path("note").asString()).isNotBlank();

        // Last month was calculated, but nothing was listed in this city then: that's 0, not "missing".
        JsonNode month = kpi(section(overview(admin, "grain=MONTH&city=" + city), "headline"), "rental_listings");
        assertThat(month.path("previous").decimalValue()).isEqualByComparingTo("0");

        JsonNode suburb = overview(admin, "grain=DAY&city=" + city + "&suburb=belgravia");
        assertThat(kpi(section(suburb, "headline"), "active_listings").path("value").decimalValue()).isEqualByComparingTo("3");
        // Platform figures don't depend on location and say so.
        assertThat(kpi(section(o, "platform"), "total_users").path("locationApplies").asBoolean()).isFalse();

        JsonNode records = json(call(get("/api/v1/admin/analytics/records?metric=ACTIVE_LISTINGS&grain=DAY&periodStart="
                + todayStart() + "&city=" + city), admin, null).andExpect(status().isOk()));
        assertThat(records.path("total").asInt()).isEqualTo(4);
        List<Long> found = new ArrayList<>();
        records.path("records").forEach(r -> found.add(r.path("id").asLong()));
        assertThat(found).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(records.path("records").get(0).path("href").asString()).startsWith("/properties/");

        JsonNode views = json(call(get("/api/v1/admin/analytics/records?metric=LISTING_VIEWS&grain=DAY&periodStart=" + todayStart()),
                admin, null));
        assertThat(views.path("note").asString()).contains("anonymous");
    }

    @Test
    void changesAreComparedLikeForLike() throws Exception {
        Period year = Period.containing(Grain.YEAR, Instant.now(), time.zone());
        String up = "Upcity" + System.nanoTime();
        String fresh = "Newcity" + System.nanoTime();
        insert("ACTIVE_LISTINGS", year.start(), "FULL", "RENT", up, 12, true);
        insert("ACTIVE_LISTINGS", year.previous(time.zone()).start(), "TO_DATE", "RENT", up, 8, true);
        insert("ACTIVE_LISTINGS", year.previous(time.zone()).start(), "FULL", "RENT", up, 20, true);
        insert("ACTIVE_LISTINGS", year.start(), "FULL", "RENT", fresh, 5, true);
        insert("ACTIVE_LISTINGS", year.previous(time.zone()).start(), "TO_DATE", "RENT", fresh, 0, true);

        JsonNode toDate = kpi(section(overview(admin, "grain=YEAR&city=" + up), "headline"), "rental_listings");
        assertThat(toDate.path("value").decimalValue()).isEqualByComparingTo("12");
        assertThat(toDate.path("previous").decimalValue()).isEqualByComparingTo("8");
        assertThat(toDate.path("change").decimalValue()).isEqualByComparingTo("4");
        assertThat(toDate.path("changePercent").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(toDate.path("direction").asString()).isEqualTo("UP");
        assertThat(toDate.path("comparable").asBoolean()).isTrue();

        JsonNode full = overview(admin, "grain=YEAR&compare=FULL&city=" + up);
        assertThat(full.path("comparisonLabel").asString()).contains("whole previous year");
        JsonNode fullKpi = kpi(section(full, "headline"), "rental_listings");
        assertThat(fullKpi.path("previous").decimalValue()).isEqualByComparingTo("20");
        assertThat(fullKpi.path("changePercent").decimalValue()).isEqualByComparingTo("-40.0");
        assertThat(fullKpi.path("direction").asString()).isEqualTo("DOWN");

        JsonNode fromZero = kpi(section(overview(admin, "grain=YEAR&city=" + fresh), "headline"), "rental_listings");
        assertThat(fromZero.path("direction").asString()).isEqualTo("NEW");
        assertThat(fromZero.path("changePercent").isNull() || fromZero.path("changePercent").isMissingNode()).isTrue();

        call(get("/api/v1/admin/analytics/overview?grain=HOUR"), admin, null).andExpect(status().isBadRequest());
    }

    @Test
    void revenueCountsOnlyEstateXProductsAndReconcilesWithPayments() throws Exception {
        Account tenant = accounts.register("Payer " + System.nanoTime(), UserRole.TENANT);
        long sub = payment(tenant, "SUBSCRIPTION", "SUCCESSFUL", "25.00");
        long featured = payment(tenant, "FEATURED_LISTING", "SUCCESSFUL", "15.00");
        long rent = payment(tenant, "RENT", "SUCCESSFUL", "600.00");
        long failed = payment(tenant, "SUBSCRIPTION", "FAILED", "25.00");
        rebuildToday();

        JsonNode revenue = json(call(get("/api/v1/admin/analytics/records?metric=PLATFORM_REVENUE&grain=DAY&periodStart="
                + todayStart()), admin, null).andExpect(status().isOk()));
        List<Long> ids = new ArrayList<>();
        revenue.path("records").forEach(r -> ids.add(r.path("id").asLong()));
        assertThat(ids).contains(sub, featured).doesNotContain(rent, failed);

        JsonNode volume = json(call(get("/api/v1/admin/analytics/records?metric=PAYMENT_VOLUME&grain=DAY&periodStart="
                + todayStart()), admin, null));
        List<Long> volumeIds = new ArrayList<>();
        volume.path("records").forEach(r -> volumeIds.add(r.path("id").asLong()));
        assertThat(volumeIds).contains(rent).doesNotContain(sub, featured);

        // Reconciliation: the published figure equals the payments it stands for.
        Instant start = Instant.parse(todayStart());
        BigDecimal expected = jdbc.queryForObject("select coalesce(sum(amount), 0) from payments where status = 'SUCCESSFUL' "
                        + "and purpose_type in ('SUBSCRIPTION', 'FEATURED_LISTING') and currency = 'USD' "
                        + "and coalesce(paid_at, created_at) >= ? and coalesce(paid_at, created_at) < ?",
                BigDecimal.class, Timestamp.from(start), Timestamp.from(Instant.now()));
        JsonNode kpi = kpi(section(overview(admin, "grain=DAY"), "business"), "platform_revenue");
        assertThat(kpi.path("value").decimalValue()).isEqualByComparingTo(expected);
        call(get("/api/v1/admin/analytics/records?metric=PLATFORM_REVENUE&grain=DAY&periodStart=" + todayStart()), admin, null);
        // The older operations dashboard lists recent payments with the payer's name; it must not fail.
        call(get("/api/v1/dashboards/admin"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.recentActivity[?(@.type == 'PAYMENT')]").exists());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ANALYTICS_FINANCE_RECORDS_VIEWED' "
                + "and actor_user_id = ?", Integer.class, admin.id())).isGreaterThanOrEqualTo(2);
    }

    @Test
    void signedInUseIsRecordedOncePerDay() throws Exception {
        Account tenant = accounts.register("Active " + System.nanoTime(), UserRole.TENANT);
        call(get("/api/v1/saved-properties/ids"), tenant, null).andExpect(status().isOk());
        call(get("/api/v1/saved-properties/ids"), tenant, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from user_activity_days where user_id = ? and activity_date = ?",
                Integer.class, tenant.id(), Date.valueOf(time.today()))).isEqualTo(1);
    }

    private Account staff(UserRole role) throws Exception {
        Account a = accounts.register(role.name() + " " + System.nanoTime(), UserRole.TENANT);
        call(put("/api/v1/admin/staff/" + a.id()), admin, "{\"roles\": [\"" + role.name() + "\"]}").andExpect(status().isOk());
        return a;
    }

    private long payment(Account payer, String purpose, String status, String amount) {
        Instant now = Instant.now();
        jdbc.update("insert into payments (payer_id, payee_id, status, amount, currency, purpose, purpose_type, created_at, paid_at) "
                        + "values (?, ?, ?, ?, 'USD', ?, ?, ?, ?)", payer.id(), owner.id(), status, new BigDecimal(amount),
                purpose.toLowerCase(Locale.ROOT), purpose, Timestamp.from(now),
                "SUCCESSFUL".equals(status) ? Timestamp.from(now) : null);
        return jdbc.queryForObject("select max(id) from payments where payer_id = ?", Long.class, payer.id());
    }

    private void insert(String metric, Instant periodStart, String variant, String type, String city, int value, boolean history) {
        String dims = type.toLowerCase(Locale.ROOT) + "|zimbabwe|" + city.toLowerCase(Locale.ROOT) + "|*|*|*";
        jdbc.update("insert into metric_values (metric_key, definition_version, grain, period_start, period_end, as_of, complete, "
                        + "variant, history_complete, dims_key, dim_listing_type, dim_country, dim_city, metric_value, sample_size, "
                        + "excluded_count, low_sample, computed_at) values (?, 1, 'YEAR', ?, ?, ?, false, ?, ?, ?, ?, 'Zimbabwe', ?, ?, ?, 0, false, ?)",
                metric, Timestamp.from(periodStart), Timestamp.from(periodStart.plusSeconds(1)), Timestamp.from(Instant.now()),
                variant, history, dims, type, city, BigDecimal.valueOf(value), value, Timestamp.from(Instant.now()));
    }

    private void rebuildToday() {
        AnalyticsEngine.Outcome out = engine.rebuild(Instant.parse(todayStart()), Instant.now().plusMillis(1));
        assertThat(out.status()).isEqualTo("SUCCEEDED");
    }

    private String todayStart() {
        return time.today().atStartOfDay(time.zone()).toInstant().toString();
    }

    private long createListing(String city, String suburb, String type, long price) throws Exception {
        String body = """
                {"title": "Overview test %s", "description": "d", "listingType": "%s", "city": "%s", "suburb": "%s",
                 "address": "1 Test Road", "bedrooms": 2, "bathrooms": 1, "price": %d, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d, "category": "FLAT",
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), type, city, suburb, price, owner.id());
        return json(call(post("/api/v1/properties"), admin, body).andExpect(status().isOk())).path("id").asLong();
    }

    private JsonNode overview(Account who, String query) throws Exception {
        return json(call(get("/api/v1/admin/analytics/overview?" + query), who, null).andExpect(status().isOk()));
    }

    private static JsonNode section(JsonNode overview, String key) {
        for (JsonNode s : overview.path("sections")) if (s.path("key").asString().equals(key)) return s;
        throw new AssertionError("no section " + key);
    }

    private static List<String> keys(JsonNode section) {
        List<String> out = new ArrayList<>();
        section.path("kpis").forEach(k -> out.add(k.path("key").asString()));
        return out;
    }

    private static JsonNode kpi(JsonNode section, String key) {
        for (JsonNode k : section.path("kpis")) if (k.path("key").asString().equals(key)) return k;
        throw new AssertionError("no kpi " + key + " in " + keys(section));
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
