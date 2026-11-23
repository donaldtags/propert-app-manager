package com.example.estatex.property;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.time.LocalDate;
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

/** What signed-out visitors can and can't learn from public endpoints (QA report EX-03, EX-04, EX-10). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicExposureTests {
    private static final String ADDRESS = "4 Lunar Road";

    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;

    private TestAccounts accounts;
    private Account host;
    private Account guest;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        host = accounts.register("Host " + System.nanoTime(), UserRole.LANDLORD);
        guest = accounts.register("Guest " + System.nanoTime(), UserRole.TENANT);
    }

    @Test
    void shortStayAddressIsOnlyForTheHostAndPaidGuests() throws Exception {
        long stayId = createListing(host, "SHORT_STAY");

        mvc.perform(get("/api/v1/properties/" + stayId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suburb").value("Avondale"))
                .andExpect(jsonPath("$.address").doesNotExist());
        call(get("/api/v1/properties/" + stayId), host, null).andExpect(jsonPath("$.address").value(ADDRESS));
        mvc.perform(get("/api/v1/stays/search").param("city", "Harare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + stayId + ")].address").isEmpty());

        // A request that isn't paid yet doesn't reveal it; a confirmed booking does.
        LocalDate in = LocalDate.now().plusDays(30);
        String body = "{\"checkIn\": \"%s\", \"checkOut\": \"%s\", \"guests\": 1}".formatted(in, in.plusDays(2));
        long bookingId = objectMapper.readTree(call(post("/api/v1/stays/" + stayId + "/bookings"), guest, body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        call(get("/api/v1/properties/" + stayId), guest, null).andExpect(jsonPath("$.address").doesNotExist());

        call(post("/api/v1/stays/bookings/" + bookingId + "/accept"), host, "{}").andExpect(status().isOk());
        call(post("/api/v1/stays/bookings/" + bookingId + "/mark-paid"), host, "{\"method\": \"EcoCash\"}")
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        call(get("/api/v1/properties/" + stayId), guest, null).andExpect(jsonPath("$.address").value(ADDRESS));
    }

    @Test
    void longTermListingsKeepTheirAddressPublic() throws Exception {
        long rentId = createListing(host, "RENT");
        mvc.perform(get("/api/v1/properties/" + rentId)).andExpect(jsonPath("$.address").value(ADDRESS));
    }

    @Test
    void landlordPassportIsPublicOnlyForPeopleWhoList() throws Exception {
        createListing(host, "RENT");
        mvc.perform(get("/api/v1/users/" + host.id() + "/landlord-passport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyCount").value(1));

        // Someone with no listings looks exactly like a missing account to strangers...
        mvc.perform(get("/api/v1/users/" + guest.id() + "/landlord-passport")).andExpect(status().isNotFound());
        call(get("/api/v1/users/" + guest.id() + "/landlord-passport"), host, null).andExpect(status().isNotFound());
        // ...but can still see their own.
        call(get("/api/v1/users/" + guest.id() + "/landlord-passport"), guest, null).andExpect(status().isOk());
    }

    @Test
    void missingOrMalformedParametersAreBadRequestsNotServerErrors() throws Exception {
        mvc.perform(get("/api/v1/ratings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: landlordId"));
        mvc.perform(get("/api/v1/ratings").param("landlordId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: landlordId"));
    }

    private long createListing(Account owner, String type) throws Exception {
        String body = """
                {"title": "Garden cottage %s", "description": "Quiet cottage", "listingType": "%s", "city": "Harare",
                 "suburb": "Avondale", "address": "%s", "bedrooms": 1, "bathrooms": 1, "price": 80, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d,
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), type, ADDRESS, owner.id());
        String created = call(post("/api/v1/properties"), owner, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }
}
