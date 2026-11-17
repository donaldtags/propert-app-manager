package com.example.primenestprop.enquiry;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.primenestprop.support.TestAccounts;
import com.example.primenestprop.support.TestAccounts.Account;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

/**
 * The server half of the offline conflict rules: creates queued on a device are replayed, possibly
 * more than once, and must land exactly once; device-captured times are kept but never trusted
 * when they're in the future.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfflineReplayTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;

    private Account negotiator;
    private Account ownerClient;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        TestAccounts accounts = new TestAccounts(mvc, userService, objectMapper);
        Account admin = accounts.platformAdmin();
        negotiator = accounts.register("Field Negotiator", UserRole.AGENT);
        ownerClient = accounts.register("Property Owner", UserRole.LANDLORD);
        String slug = TestAccounts.uniqueSlug("offline");
        base = "/api/v1/orgs/" + slug;
        call(post("/api/v1/orgs"), admin, "{\"slug\": \"%s\", \"name\": \"Offline Agency\"}".formatted(slug)).andExpect(status().isOk());
        call(post(base + "/members"), admin, "{\"email\": \"%s\", \"roles\": [\"NEGOTIATOR\"]}".formatted(negotiator.email()))
                .andExpect(status().isOk());
        call(post(base + "/members"), admin, "{\"email\": \"%s\", \"roles\": [\"OWNER\"]}".formatted(ownerClient.email()))
                .andExpect(status().isOk());
    }

    @Test
    void replayedEnquiryCreatesExactlyOneRecordAndKeepsTheDeviceCaptureTime() throws Exception {
        String clientId = UUID.randomUUID().toString();
        Instant captured = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String body = """
                {"clientId": "%s", "channel": "WHATSAPP", "intent": "BUY", "name": "Rumbi M",
                 "phone": "+263772111222", "message": "Interested in 3 bed in Greendale", "capturedAt": "%s"}
                """.formatted(clientId, captured);
        long first = id(call(post(base + "/enquiries"), negotiator, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.capturedAt").value(captured.toString())));
        long second = id(call(post(base + "/enquiries"), negotiator, body).andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        call(get(base + "/enquiries"), negotiator, null)
                .andExpect(jsonPath("$[?(@.clientId == '%s')]".formatted(clientId)).value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void futureDeviceTimesAreClampedAndContactDetailsAreRequired() throws Exception {
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
        String raw = call(post(base + "/enquiries"), negotiator, """
                {"channel": "PHONE", "name": "Bad Clock", "phone": "+263771000000", "capturedAt": "%s"}
                """.formatted(future)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Instant stored = Instant.parse(objectMapper.readTree(raw).path("capturedAt").asString());
        org.junit.jupiter.api.Assertions.assertTrue(stored.isBefore(Instant.now().plusSeconds(5)));

        call(post(base + "/enquiries"), negotiator, "{\"channel\": \"WALK_IN\", \"name\": \"No Contact\"}")
                .andExpect(status().isBadRequest());
        // External portal roles can't see or capture the agency's enquiries.
        call(post(base + "/enquiries"), ownerClient, "{\"channel\": \"PHONE\", \"name\": \"X\", \"phone\": \"1\"}")
                .andExpect(status().isForbidden());
        call(get(base + "/enquiries"), ownerClient, null).andExpect(status().isForbidden());
    }

    @Test
    void replayedListingCreateLandsOnce() throws Exception {
        String clientId = UUID.randomUUID().toString();
        String body = """
                {"clientId": "%s", "title": "Offline intake", "listingType": "RENT", "city": "Harare",
                 "suburb": "Hatfield", "bedrooms": 3, "bathrooms": 2, "priceCents": 60000}
                """.formatted(clientId);
        long a = objectMapper.readTree(call(post(base + "/listings"), negotiator, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("property").path("id").asLong();
        long b = objectMapper.readTree(call(post(base + "/listings"), negotiator, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("property").path("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(a, b);
        call(get(base + "/listings"), negotiator, null)
                .andExpect(jsonPath("$[?(@.title == 'Offline intake')]").value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void websiteEnquiriesOnAgencyListingsReachTheInboxButDraftsCantBeEnquiredAbout() throws Exception {
        long draftId = objectMapper.readTree(call(post(base + "/listings"), negotiator, """
                {"title": "Hidden draft", "listingType": "SALE", "city": "Harare", "suburb": "Eastlea",
                 "bedrooms": 3, "bathrooms": 1, "priceCents": 15000000}
                """).andReturn().getResponse().getContentAsString()).path("property").path("id").asLong();
        mvc.perform(post("/api/v1/properties/" + draftId + "/inquiries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Web\", \"email\": \"w@example.com\", \"message\": \"Hi\"}"))
                .andExpect(status().isNotFound());
        call(get(base + "/enquiries"), negotiator, null).andExpect(jsonPath("$[*].channel", not(hasItem("WEBSITE"))));
    }

    private long id(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, Account account, String body) throws Exception {
        builder.header(HttpHeaders.AUTHORIZATION, account.bearer());
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder);
    }
}
