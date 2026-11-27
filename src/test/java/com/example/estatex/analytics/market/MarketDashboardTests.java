package com.example.estatex.analytics.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.analytics.engine.AnalyticsEngine;
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
 * Phase D of docs/admin-analytics-plan.md: rental and sales dashboards. One known dataset in its
 * own city; every figure is checked against values worked out by hand, and every chart and table
 * must describe the same filtered set of homes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketDashboardTests {
    @Autowired private com.example.estatex.support.ListingFees fees;
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AnalyticsEngine engine;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BusinessTime time;

    private TestAccounts accounts;
    private Account admin;
    private Account owner;
    private String city;

    /**
     * Avondale: rentals (2-bed flats) at 400, 500, 600, 700, 800 (the 800 is cut to 720), and 2-bed
     * flats for sale at 60k–100k (the 100k is reported sold, then verified at 85k from the Deeds
     * Registry). Belgravia: one 3-bed house to rent at 1,500, then withdrawn. One lease at 650.
     */
    @BeforeEach
    void dataset() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        Account tenant = accounts.register("Tenant " + System.nanoTime(), UserRole.TENANT);
        city = "Marketcity" + System.nanoTime();
        List<Long> rents = new ArrayList<>();
        int[] rentPrices = {400, 500, 600, 700, 800};
        for (int p : rentPrices) rents.add(listing("RENT", "Avondale", "FLAT", 2, p, 50 + p / 100));
        long house = listing("RENT", "Belgravia", "HOUSE", 3, 1500, 200);
        List<Long> sales = new ArrayList<>();
        for (int p : new int[]{60_000, 70_000, 80_000, 90_000, 100_000}) sales.add(listing("SALE", "Avondale", "FLAT", 2, p, 70));

        call(patch("/api/v1/properties/" + rents.get(4)), admin, "{\"price\": 720}").andExpect(status().isOk());
        call(patch("/api/v1/properties/" + house), admin, "{\"status\": \"WITHDRAWN\"}").andExpect(status().isOk());
        jdbc.update("insert into leases (property_id, tenant_id, landlord_id, status, monthly_rent, currency, signed_at, created_at) "
                        + "values (?, ?, ?, 'SIGNED', 650, 'USD', ?, ?)", rents.get(2), tenant.id(), owner.id(),
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        String sale = json(call(post("/api/v1/properties/" + sales.get(4) + "/sale-transactions"), admin,
                "{\"price\": 85000, \"completionDate\": \"" + time.today() + "\"}").andExpect(status().isCreated())).path("id").asString();
        Account verifier = accounts.platformAdmin(); // someone other than the person who reported it
        call(post("/api/v1/admin/sale-transactions/" + sale + "/verify"), verifier,
                "{\"approved\": true, \"source\": \"DEEDS_REGISTRY\", \"evidenceReference\": \"DT 77/2026\"}").andExpect(status().isOk());

        AnalyticsEngine.Outcome out = engine.rebuild(time.today().atStartOfDay(time.zone()).toInstant(), Instant.now().plusMillis(1));
        assertThat(out.status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void rentalFiguresMatchTheKnownDataset() throws Exception {
        JsonNode d = rent("");
        assertThat(kpi(d, "active").path("value").decimalValue()).isEqualByComparingTo("5");      // the house was withdrawn
        assertThat(kpi(d, "asking_median").path("value").decimalValue()).isEqualByComparingTo("600"); // 400 500 600 700 720
        assertThat(kpi(d, "asking_mean").path("value").decimalValue()).isEqualByComparingTo("584");
        assertThat(kpi(d, "new_listings").path("value").asInt()).isEqualTo(6);
        assertThat(kpi(d, "withdrawn").path("value").asInt()).isEqualTo(1);
        assertThat(kpi(d, "leases_signed").path("value").asInt()).isEqualTo(1);
        assertThat(kpi(d, "contracted_median").path("value").decimalValue()).isEqualByComparingTo("650");
        assertThat(kpi(d, "days_to_close").path("sampleSize").asInt()).isEqualTo(1);
        // Asking and agreed rent are separate series on the chart.
        JsonNode last = lastPointWithSnapshot(d);
        assertThat(last.path("values").path("askingMedian").decimalValue()).isEqualByComparingTo("600");
        assertThat(last.path("values").path("contractedMedian").decimalValue()).isEqualByComparingTo("650");
        assertThat(last.path("values").path("priceCuts").asInt()).isEqualTo(1);
        // A year ago, before listing history began: no listing figures at all, but leases (full history) are a real 0.
        JsonNode first = d.path("series").get(0);
        assertThat(first.path("values").has("newListings")).isFalse();
        assertThat(first.path("values").has("active")).isFalse();
        assertThat(first.path("values").path("leasesSigned").asInt()).isZero();
    }

    @Test
    void everyChartAndTableDescribesTheSameFilteredHomes() throws Exception {
        JsonNode d = rent("&category=FLAT&bedrooms=2&maxPrice=650");
        int active = kpi(d, "active").path("value").asInt();
        assertThat(active).isEqualTo(3);                                                         // 400 500 600
        assertThat(kpi(d, "asking_median").path("value").decimalValue()).isEqualByComparingTo("500");
        assertThat(lastPointWithSnapshot(d).path("values").path("active").asInt()).isEqualTo(active);
        assertThat(sumActive(d.path("bySuburb"))).isEqualTo(active);
        assertThat(sumActive(d.path("byCategory"))).isEqualTo(active);
        assertThat(sumActive(d.path("byBedrooms"))).isEqualTo(active);
        assertThat(d.path("byCategory").size()).isEqualTo(1);
        assertThat(d.path("byCategory").get(0).path("label").asString()).isEqualTo("Flat / apartment");
        // The lease (650) is inside the price range, so it still counts.
        assertThat(kpi(d, "leases_signed").path("value").asInt()).isEqualTo(1);

        JsonNode tighter = rent("&maxPrice=550");
        assertThat(kpi(tighter, "leases_signed").path("value").asInt()).isZero();
        assertThat(kpi(tighter, "contracted_median").path("value").isNull()
                || kpi(tighter, "contracted_median").path("value").isMissingNode()).isTrue();

        JsonNode listings = json(call(get("/api/v1/admin/analytics/market/rent/listings?city=" + city
                + "&category=FLAT&bedrooms=2&maxPrice=650"), admin, null).andExpect(status().isOk()));
        assertThat(listings.size()).isEqualTo(active);
        assertThat(listings.get(0).path("price").decimalValue()).isEqualByComparingTo("400");
    }

    @Test
    void salesSeparateReportedFromVerified() throws Exception {
        JsonNode d = sale("");
        assertThat(kpi(d, "active").path("value").asInt()).isEqualTo(4);                          // the sold one left the market
        assertThat(kpi(d, "asking_median").path("value").decimalValue()).isEqualByComparingTo("75000");
        assertThat(kpi(d, "reported_sales").path("value").asInt()).isEqualTo(1);
        assertThat(kpi(d, "verified_sales").path("value").asInt()).isEqualTo(1);
        assertThat(kpi(d, "verified_median").path("value").decimalValue()).isEqualByComparingTo("85000");
        assertThat(kpi(d, "verified_value").path("value").decimalValue()).isEqualByComparingTo("85000");
        assertThat(kpi(d, "verified_median").path("lowSample").asBoolean()).isTrue();
        assertThat(kpi(d, "per_m2").path("value").decimalValue()).isEqualByComparingTo("1071.43"); // 75000 / 70
        JsonNode bands = d.path("byPriceBand");
        assertThat(bands.get(0).path("label").asString()).isEqualTo("$50k–$100k");
        assertThat(bands.get(0).path("active").asInt()).isEqualTo(4);
        assertThat(bands.get(0).path("transactions").asInt()).isEqualTo(1);
    }

    @Test
    void yieldNeedsEnoughListingsAndNetYieldNeedsAConfirmedCostRatio() throws Exception {
        // Avondale had 5 rentals and 5 sales listed; one sale has since sold, so it falls below the minimum.
        JsonNode y = rent("").path("yields");
        JsonNode avondale = suburb(y, "Avondale");
        assertThat(avondale.path("saleN").asInt()).isEqualTo(4);
        assertThat(avondale.path("grossYield").isNull() || avondale.path("grossYield").isMissingNode()).isTrue();
        assertThat(avondale.path("note").asString()).contains("at least 5");

        // A fifth sale listing brings it back: gross = 600 × 12 ÷ 80,000 = 9.00%.
        listing("SALE", "Avondale", "FLAT", 2, 100_000, 70);
        engine.rebuild(time.today().atStartOfDay(time.zone()).toInstant(), Instant.now().plusMillis(1));
        avondale = suburb(rent("").path("yields"), "Avondale");
        assertThat(avondale.path("rentMedian").decimalValue()).isEqualByComparingTo("600");
        assertThat(avondale.path("saleMedian").decimalValue()).isEqualByComparingTo("80000");
        assertThat(avondale.path("grossYield").decimalValue()).isEqualByComparingTo("9.00");
        assertThat(avondale.path("netYield").isNull() || avondale.path("netYield").isMissingNode()).isTrue();

        Account analyst = staff(UserRole.MARKET_ANALYST);
        call(put("/api/v1/admin/analytics/market/settings/yield-cost-ratio"), analyst, "{\"ratio\": 0.3}").andExpect(status().isForbidden());
        call(put("/api/v1/admin/analytics/market/settings/yield-cost-ratio"), admin, "{\"ratio\": 1.5}").andExpect(status().isBadRequest());
        call(put("/api/v1/admin/analytics/market/settings/yield-cost-ratio"), admin, "{\"ratio\": 0.3}").andExpect(status().isOk());
        JsonNode after = rent("").path("yields");
        assertThat(after.path("costRatioConfirmed").asBoolean()).isTrue();
        assertThat(suburb(after, "Avondale").path("netYield").decimalValue()).isEqualByComparingTo("6.30");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ANALYTICS_SETTING_CHANGED' and actor_user_id = ?",
                Integer.class, admin.id())).isGreaterThanOrEqualTo(1);
    }

    @Test
    void onlyMarketStaffSeeTheDashboardsAndDemandNeedsItsOwnPermission() throws Exception {
        call(get("/api/v1/admin/analytics/market/rent?city=" + city), staff(UserRole.FINANCE_ADMIN), null).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/market/rent?city=" + city), staff(UserRole.VERIFICATION_OFFICER), null).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/market/rent?city=" + city), owner, null).andExpect(status().isForbidden());
        JsonNode analyst = json(call(get("/api/v1/admin/analytics/market/rent?city=" + city), staff(UserRole.MARKET_ANALYST), null)
                .andExpect(status().isOk()));
        assertThat(analyst.path("demandIncluded").asBoolean()).isTrue();
        assertThat(keys(analyst)).contains("enquiries", "applications");
        call(get("/api/v1/admin/analytics/market/lettings"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/v1/admin/analytics/market/rent?from=2026-05-01&to=2026-01-01"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/v1/admin/analytics/market/rent?bedrooms=9"), admin, null).andExpect(status().isBadRequest());
    }

    private Account staff(UserRole role) throws Exception {
        Account a = accounts.register(role.name() + " " + System.nanoTime(), UserRole.TENANT);
        call(put("/api/v1/admin/staff/" + a.id()), admin, "{\"roles\": [\"" + role.name() + "\"]}").andExpect(status().isOk());
        return a;
    }

    private long listing(String type, String suburb, String category, int beds, int price, int area) throws Exception {
        String body = """
                {"title": "%s %s", "listingType": "%s", "city": "%s", "suburb": "%s", "bedrooms": %d, "bathrooms": 1,
                 "price": %d, "currency": "USD", "escrowRequired": false, "landlordId": %d, "category": "%s", "floorAreaM2": %d}
                """.formatted(category, System.nanoTime(), type, city, suburb, beds, price, owner.id(), category, area);
        return fees.paid(json(call(post("/api/v1/properties"), admin, body).andExpect(status().isOk())).path("id").asLong());
    }

    private JsonNode rent(String extra) throws Exception {
        return json(call(get("/api/v1/admin/analytics/market/rent?city=" + city + extra), admin, null).andExpect(status().isOk()));
    }

    private JsonNode sale(String extra) throws Exception {
        return json(call(get("/api/v1/admin/analytics/market/sale?city=" + city + extra), admin, null).andExpect(status().isOk()));
    }

    private static JsonNode kpi(JsonNode d, String key) {
        for (JsonNode k : d.path("kpis")) if (k.path("key").asString().equals(key)) return k;
        throw new AssertionError("no kpi " + key + " in " + keys(d));
    }

    private static List<String> keys(JsonNode d) {
        List<String> out = new ArrayList<>();
        d.path("kpis").forEach(k -> out.add(k.path("key").asString()));
        return out;
    }

    private static JsonNode lastPointWithSnapshot(JsonNode d) {
        JsonNode last = null;
        for (JsonNode p : d.path("series")) if (!p.path("snapshotDate").isNull() && !p.path("snapshotDate").isMissingNode()) last = p;
        assertThat(last).as("a period with a snapshot").isNotNull();
        return last;
    }

    private static int sumActive(JsonNode groups) {
        int n = 0;
        for (JsonNode g : groups) n += g.path("active").asInt();
        return n;
    }

    private static JsonNode suburb(JsonNode yields, String name) {
        for (JsonNode r : yields.path("bySuburb")) if (r.path("suburb").asString().equals(name)) return r;
        throw new AssertionError("no yield row for " + name);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    @SuppressWarnings("unused")
    private static BigDecimal usd(long v) {
        return BigDecimal.valueOf(v);
    }
}
