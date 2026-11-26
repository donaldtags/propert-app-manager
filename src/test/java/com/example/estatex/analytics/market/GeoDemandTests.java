package com.example.estatex.analytics.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.analytics.engine.AnalyticsEngine;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Phase E of docs/admin-analytics-plan.md: the market map and customer demand analytics. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeoDemandTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AnalyticsEngine engine;
    @Autowired private BusinessTime time;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private TestAccounts accounts;
    private Account admin;
    private Account owner;
    private String city;
    private final List<Long> alpha = new ArrayList<>();

    /** Alpha: three pinned rentals (500, 600, 700). Beta: two rentals with no pin and no known centre. */
    @BeforeEach
    void dataset() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        city = "Geocity" + System.nanoTime();
        alpha.add(listing("Alpha", 500, "-17.800", "31.050"));
        alpha.add(listing("Alpha", 600, "-17.802", "31.052"));
        alpha.add(listing("Alpha", 700, "-17.804", "31.054"));
        listing("Beta", 400, null, null);
        listing("Beta", 450, null, null);
    }

    @Test
    void suburbsArePlacedOnlyWhereTheirLocationIsKnownAndCoverageIsShown() throws Exception {
        rebuildToday();
        JsonNode g = json(call(get("/api/v1/admin/analytics/geo/rent?city=" + city), admin).andExpect(status().isOk()));
        JsonNode a = suburb(g, "Alpha");
        assertThat(a.path("active").asInt()).isEqualTo(3);
        assertThat(a.path("askingMedian").decimalValue()).isEqualByComparingTo("600");
        assertThat(a.path("confidence").asString()).isEqualTo("LOW");
        assertThat(a.path("placedBy").asString()).isEqualTo("middle of its listings");
        assertThat(a.path("latitude").decimalValue()).isEqualByComparingTo("-17.802");

        JsonNode b = suburb(g, "Beta");
        assertThat(b.path("latitude").isNull() || b.path("latitude").isMissingNode()).isTrue();
        JsonNode cov = g.path("coverage");
        assertThat(cov.path("liveListings").asInt()).isEqualTo(5);
        assertThat(cov.path("placedOnMap").asInt()).isEqualTo(3);
        assertThat(cov.path("inUnplacedSuburbs").asInt()).isEqualTo(2);
        assertThat(cov.path("withOwnPin").asInt()).isEqualTo(3);
        assertThat(g.path("points").size()).isEqualTo(3);

        // A suburb whose centre is on file is drawn there. (The seeded reference data has city centres
        // but no suburb centres yet, so set one.)
        jdbc.update("update locations set latitude = -17.7960, longitude = 31.0350 where type = 'SUBURB' and name = 'Avondale' and city = 'Harare'");
        listingIn("Harare", "Avondale", 800);
        rebuildToday();
        JsonNode harare = json(call(get("/api/v1/admin/analytics/geo/rent?city=Harare"), admin).andExpect(status().isOk()));
        assertThat(suburb(harare, "Avondale").path("placedBy").asString()).isEqualTo("suburb centre");
    }

    @Test
    void demandCountsInterestAndFlagsSupplyGaps() throws Exception {
        rebuildToday();
        // 12 people search for 2-bedroom cottages to rent in Alpha; none is listed.
        for (int i = 0; i < 12; i++) {
            track("{\"type\": \"SEARCH\", \"visitorId\": \"gap-%d-%d\", \"listingType\": \"RENT\", \"city\": \"%s\", \"suburb\": \"Alpha\", \"category\": \"COTTAGE\", \"bedrooms\": 2, \"maxPrice\": 650, \"resultCount\": 0}"
                    .formatted(System.nanoTime(), i, city));
        }
        // One more person searches the city, then opens a listing.
        String browser = "follow-" + System.nanoTime();
        track("{\"type\": \"SEARCH\", \"visitorId\": \"%s\", \"listingType\": \"RENT\", \"city\": \"%s\", \"resultCount\": 5}".formatted(browser, city));
        track("{\"type\": \"VIEW\", \"visitorId\": \"%s\", \"propertyId\": %d}".formatted(browser, alpha.get(0)));
        mvc.perform(post("/api/v1/properties/" + alpha.get(0) + "/inquiries").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Chipo\", \"email\": \"chipo@example.com\", \"message\": \"Is it available?\"}"))
                .andExpect(status().is2xxSuccessful());
        rebuildToday();

        JsonNode d = json(call(get("/api/v1/admin/analytics/demand?city=" + city), admin).andExpect(status().isOk()));
        assertThat(d.path("searches").asInt()).isEqualTo(13);
        assertThat(d.path("noResultsPercent").decimalValue()).isEqualByComparingTo("92.3");  // 12 of 13
        assertThat(d.path("ledToViewPercent").decimalValue()).isEqualByComparingTo("7.7");   // 1 of 13
        JsonNode rent = funnel(d, "RENT");
        assertThat(rent.get(0).path("count").asInt()).isEqualTo(13);
        assertThat(rent.get(1).path("count").asInt()).isEqualTo(1);
        assertThat(rent.get(2).path("count").asInt()).isEqualTo(1);
        assertThat(rent.get(1).path("fromPrevious").decimalValue()).isEqualByComparingTo("7.7");
        assertThat(d.path("topSuburbs").get(0).path("label").asString()).isEqualTo("Alpha");
        assertThat(d.path("topSuburbs").get(0).path("searches").asInt()).isEqualTo(12);
        assertThat(d.path("mostViewed").get(0).path("id").asLong()).isEqualTo(alpha.get(0));
        assertThat(d.path("mostViewed").get(0).path("enquiries").asInt()).isEqualTo(1);
        assertThat(d.path("rentPriceBands").get(0).path("label").asString()).isEqualTo("$500–$800");

        JsonNode gap = d.path("gaps").get(0);
        assertThat(gap.path("searches").asInt()).isEqualTo(12);
        assertThat(gap.path("liveListings").asInt()).isZero();
        assertThat(gap.path("explanation").asString()).contains("2+ bedroom cottages to rent in Alpha").contains("no matching listing");

        JsonNode enquiryReplies = d.path("responseTimes").get(0);
        assertThat(enquiryReplies.path("medianHours").isNull() || enquiryReplies.path("medianHours").isMissingNode()).isTrue();
        assertThat(enquiryReplies.path("note").asString()).contains("aren't time-stamped");

        // Demand layers on the map appear with demand permission.
        JsonNode g = json(call(get("/api/v1/admin/analytics/geo/rent?city=" + city), admin));
        assertThat(suburb(g, "Alpha").path("searches").asInt()).isEqualTo(12);
        assertThat(suburb(g, "Alpha").path("demand").asInt()).isEqualTo(1);
    }

    @Test
    void fewerThanTenSearchesIsNotEnoughToFlagAGap() throws Exception {
        rebuildToday();
        for (int i = 0; i < 9; i++) {
            track("{\"type\": \"SEARCH\", \"visitorId\": \"few-%d-%d\", \"listingType\": \"RENT\", \"city\": \"%s\", \"category\": \"FARM\", \"resultCount\": 0}"
                    .formatted(System.nanoTime(), i, city));
        }
        JsonNode d = json(call(get("/api/v1/admin/analytics/demand?city=" + city), admin));
        assertThat(d.path("gaps").size()).isZero();
    }

    @Test
    void permissions() throws Exception {
        Account finance = staff(UserRole.FINANCE_ADMIN);
        call(get("/api/v1/admin/analytics/demand"), finance).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/geo/rent"), finance).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/geo/rent"), staff(UserRole.VERIFICATION_OFFICER)).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/demand"), staff(UserRole.MARKET_ANALYST)).andExpect(status().isOk());
        call(get("/api/v1/admin/analytics/geo/sale"), staff(UserRole.OPERATIONS_ADMIN)).andExpect(status().isOk());
        call(get("/api/v1/admin/analytics/geo/lease"), admin).andExpect(status().isBadRequest());
        call(get("/api/v1/admin/analytics/demand?from=2024-01-01&to=2026-01-01"), admin).andExpect(status().isBadRequest());
    }

    private void rebuildToday() {
        engine.rebuild(time.today().atStartOfDay(time.zone()).toInstant(), Instant.now().plusMillis(1));
    }

    private Account staff(UserRole role) throws Exception {
        Account a = accounts.register(role.name() + " " + System.nanoTime(), UserRole.TENANT);
        mvc.perform(put("/api/v1/admin/staff/" + a.id()).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\": [\"" + role.name() + "\"]}")).andExpect(status().isOk());
        return a;
    }

    private long listing(String suburb, int price, String lat, String lng) throws Exception {
        String pin = lat == null ? "" : "\"latitude\": " + lat + ", \"longitude\": " + lng + ",";
        String body = """
                {"title": "Geo %s", "listingType": "RENT", "city": "%s", "suburb": "%s", "bedrooms": 2, "bathrooms": 1, %s
                 "price": %d, "currency": "USD", "escrowRequired": false, "landlordId": %d, "category": "FLAT"}
                """.formatted(System.nanoTime(), city, suburb, pin, price, owner.id());
        return json(mvc.perform(post("/api/v1/properties").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())).path("id").asLong();
    }

    private void listingIn(String c, String suburb, int price) throws Exception {
        String body = """
                {"title": "Geo %s", "listingType": "RENT", "city": "%s", "suburb": "%s", "bedrooms": 2, "bathrooms": 1,
                 "price": %d, "currency": "USD", "escrowRequired": false, "landlordId": %d, "category": "FLAT"}
                """.formatted(System.nanoTime(), c, suburb, price, owner.id());
        mvc.perform(post("/api/v1/properties").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private void track(String body) throws Exception {
        mvc.perform(post("/api/v1/marketplace-events").header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .with(r -> { r.setRemoteAddr("10.0.0." + (System.nanoTime() % 200)); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private static JsonNode suburb(JsonNode g, String name) {
        for (JsonNode s : g.path("suburbs")) if (s.path("suburb").asString().equals(name)) return s;
        throw new AssertionError("no suburb " + name);
    }

    private static JsonNode funnel(JsonNode d, String type) {
        for (JsonNode f : d.path("funnels")) if (f.path("type").asString().equals(type)) return f.path("steps");
        throw new AssertionError("no funnel " + type);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, who.bearer()));
    }
}
