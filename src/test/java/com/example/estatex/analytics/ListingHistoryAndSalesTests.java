package com.example.estatex.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.analytics.capture.MarketplaceEventRepository;
import com.example.estatex.analytics.capture.PropertyEvent;
import com.example.estatex.analytics.capture.PropertyEventRepository;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
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
import tools.jackson.databind.ObjectMapper;

/** Phase A of docs/admin-analytics-plan.md: listing history, separate sale statuses, demand capture. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingHistoryAndSalesTests {
    @Autowired private com.example.estatex.support.ListingFees fees;
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PropertyEventRepository propertyEvents;
    @Autowired private MarketplaceEventRepository marketplaceEvents;

    private TestAccounts accounts;
    private Account owner;
    private Account admin;
    private Account visitor;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        visitor = accounts.register("Visitor " + System.nanoTime(), UserRole.TENANT);
        admin = accounts.platformAdmin();
    }

    @Test
    void everyPriceAndStatusChangeIsKeptAndNothingIsOverwritten() throws Exception {
        long id = createListing("SALE", 120000);

        call(patch("/api/v1/properties/" + id), owner, "{\"price\": 110000}").andExpect(status().isOk());
        call(patch("/api/v1/properties/" + id), owner, "{\"price\": 110000.00}").andExpect(status().isOk());
        call(patch("/api/v1/properties/" + id), owner, "{\"status\": \"WITHDRAWN\"}").andExpect(status().isOk());
        call(patch("/api/v1/properties/" + id), owner, "{\"bedrooms\": 4, \"category\": \"HOUSE\"}").andExpect(status().isOk());

        List<PropertyEvent> history = propertyEvents.findByPropertyIdOrderByOccurredAtAscIdAsc(id);
        // Created as a draft, published when the first listing fee was paid, then the owner's changes.
        assertThat(history).extracting(PropertyEvent::getEventType)
                .containsExactly("CREATED", "STATUS_CHANGED", "PRICE_CHANGED", "STATUS_CHANGED", "CHARACTERISTICS_CHANGED");
        assertThat(history.get(0).getPrice()).isEqualByComparingTo("120000");
        assertThat(history.get(0).getStatus()).isEqualTo("DRAFT");
        assertThat(history.get(1).getPreviousStatus()).isEqualTo("DRAFT");
        assertThat(history.get(1).getStatus()).isEqualTo("AVAILABLE");
        assertThat(history.get(2).getPreviousPrice()).isEqualByComparingTo("120000");
        assertThat(history.get(2).getPrice()).isEqualByComparingTo("110000");
        assertThat(history.get(3).getPreviousStatus()).isEqualTo("AVAILABLE");
        assertThat(history.get(3).getStatus()).isEqualTo("WITHDRAWN");
        assertThat(history.get(4).getCategory()).isEqualTo("HOUSE");
        assertThat(history.get(4).getChangeSummary()).contains("bedrooms 3→4");
        assertThat(history).allSatisfy(e -> assertThat(e.getSource()).isEqualTo("APP"));
        assertThat(history.get(2).getActorUserId()).isEqualTo(owner.id());
    }

    @Test
    void oldStatusNamesStillWorkAndExpiredIsSystemOnly() throws Exception {
        long id = createListing("SALE", 90000);
        call(patch("/api/v1/properties/" + id), owner, "{\"status\": \"SOLD\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REPORTED_SOLD"));
        call(patch("/api/v1/properties/" + id), owner, "{\"status\": \"EXPIRED\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aReportedSaleIsNeverVerifiedWithoutIndependentEvidence() throws Exception {
        long id = createListing("SALE", 150000);
        String created = call(post("/api/v1/properties/" + id + "/sale-transactions"), owner,
                "{\"price\": 142500, \"completionDate\": \"2026-11-20\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verification").value("REPORTED"))
                .andExpect(jsonPath("$.source").value("OWNER_REPORTED"))
                .andExpect(jsonPath("$.askingPrice").value(150000.0))
                .andReturn().getResponse().getContentAsString();
        long saleId = objectMapper.readTree(created).get("id").asLong();
        call(get("/api/v1/properties/" + id), owner, null).andExpect(jsonPath("$.status").value("REPORTED_SOLD"));

        call(post("/api/v1/properties/" + id + "/sale-transactions"), owner,
                "{\"price\": 1, \"completionDate\": \"2026-11-20\"}").andExpect(status().isConflict());
        String verifyUrl = "/api/v1/admin/sale-transactions/" + saleId + "/verify";
        call(post(verifyUrl), owner, "{\"approved\": true, \"source\": \"DEEDS_REGISTRY\", \"evidenceReference\": \"x\"}")
                .andExpect(status().isForbidden());
        call(post(verifyUrl), admin, "{\"approved\": true, \"source\": \"AGENT_REPORTED\", \"evidenceReference\": \"x\"}")
                .andExpect(status().isBadRequest());
        call(post(verifyUrl), admin, "{\"approved\": true, \"source\": \"DEEDS_REGISTRY\"}")
                .andExpect(status().isBadRequest());
        call(post(verifyUrl), admin, "{\"approved\": true, \"source\": \"DEEDS_REGISTRY\", \"evidenceReference\": \"DT 1234/2026\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedByUserId").value(admin.id()));
        call(post(verifyUrl), admin, "{\"approved\": false, \"note\": \"again\"}").andExpect(status().isConflict());
    }

    @Test
    void rentalsCannotBeReportedAsSold() throws Exception {
        long id = createListing("RENT", 800);
        call(post("/api/v1/properties/" + id + "/sale-transactions"), owner,
                "{\"price\": 800, \"completionDate\": \"2026-11-20\"}").andExpect(status().isBadRequest());
    }

    @Test
    void viewsAreDeduplicatedAndOwnViewsAndCrawlersAreIgnored() throws Exception {
        long id = createListing("RENT", 700);
        String view = "{\"type\": \"VIEW\", \"propertyId\": %d, \"visitorId\": \"%s\"}";
        String browser = "visitor-" + System.nanoTime();
        track(view.formatted(id, browser), null, "Mozilla/5.0").andExpect(jsonPath("$.counted").value(true));
        track(view.formatted(id, browser), null, "Mozilla/5.0").andExpect(jsonPath("$.counted").value(false));
        track(view.formatted(id, "other-" + System.nanoTime()), null, "Googlebot/2.1").andExpect(jsonPath("$.counted").value(false));
        track(view.formatted(id, "own-" + System.nanoTime()), owner, "Mozilla/5.0").andExpect(jsonPath("$.counted").value(false));
        track("{\"type\": \"VIEW\", \"propertyId\": %d, \"visitorId\": \"x\"}".formatted(id), null, "Mozilla/5.0")
                .andExpect(status().isBadRequest());

        String search = "{\"type\": \"SEARCH\", \"visitorId\": \"%s\", \"listingType\": \"RENT\", \"city\": \"Harare\", \"bedrooms\": 3, \"resultCount\": 0}";
        track(search.formatted(browser), null, "Mozilla/5.0").andExpect(jsonPath("$.counted").value(true));
        track(search.formatted(browser), null, "Mozilla/5.0").andExpect(jsonPath("$.counted").value(false));
    }

    @Test
    void savingTwiceKeepsOneSaveAndRecordsTheEvent() throws Exception {
        long id = createListing("RENT", 650);
        call(put("/api/v1/saved-properties/" + id), visitor, null).andExpect(status().isOk());
        call(put("/api/v1/saved-properties/" + id), visitor, null).andExpect(status().isOk());
        call(get("/api/v1/saved-properties/ids"), visitor, null).andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/saved-properties"), visitor, null).andExpect(jsonPath("$[0].id").value(id));
        call(delete("/api/v1/saved-properties/" + id), visitor, null).andExpect(status().isOk());
        call(get("/api/v1/saved-properties/ids"), visitor, null).andExpect(jsonPath("$.length()").value(0));
        assertThat(marketplaceEvents.findAll()).filteredOn(e -> Long.valueOf(id).equals(e.getPropertyId()))
                .extracting(e -> e.getEventType()).containsExactly("SAVE", "UNSAVE");
    }

    private long createListing(String type, int price) throws Exception {
        String body = """
                {"title": "History test %s", "description": "d", "listingType": "%s", "city": "Harare",
                 "suburb": "Avondale", "address": "1 Test Road", "bedrooms": 3, "bathrooms": 2, "price": %d,
                 "currency": "USD", "escrowRequired": false, "landlordId": %d, "floorAreaM2": 180,
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), type, price, owner.id());
        String created = call(post("/api/v1/properties"), owner, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.floorAreaM2").value(180))
                .andReturn().getResponse().getContentAsString();
        return fees.paid(objectMapper.readTree(created).get("id").asLong());
    }

    private ResultActions track(String body, Account who, String userAgent) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/v1/marketplace-events").header(HttpHeaders.USER_AGENT, userAgent)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (who != null) req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        return mvc.perform(req);
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
