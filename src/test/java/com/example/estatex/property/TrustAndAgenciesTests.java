package com.example.estatex.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Licensed agents, self-registered companies, "listed by", listing reports and the REIT fee. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrustAndAgenciesTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;

    private TestAccounts accounts;
    private Account admin;

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
    }

    // --- Agents -------------------------------------------------------------------------------

    @Test
    void anUnlicensedAgentsListingWaitsAsAHiddenDraftUntilTheLicenceIsApproved() throws Exception {
        Account agent = accounts.register("Agent " + System.nanoTime(), UserRole.AGENT);
        long id = createListing(agent);

        JsonNode draft = json(call(get("/api/v1/properties/" + id), agent, null).andExpect(status().isOk()));
        assertEquals("DRAFT", draft.path("status").asString());
        mvc.perform(get("/api/v1/properties/" + id)).andExpect(status().isNotFound());
        call(patch("/api/v1/properties/" + id), agent, "{\"status\": \"AVAILABLE\"}").andExpect(status().isConflict());

        long licenceId = json(submitLicence(agent, "EAC/2024/0457", new MockMultipartFile("certificate", "cert.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))).path("id").asLong();

        call(patch("/api/v1/agents/licences/" + licenceId), agent, "{\"approved\": true}").andExpect(status().isForbidden());
        // The agent's ID must be checked before their licence can be approved.
        call(patch("/api/v1/agents/licences/" + licenceId), admin, "{\"approved\": true}").andExpect(status().isConflict());
        accounts.verifyIdentity(agent, "Agent Licensed", admin);
        call(patch("/api/v1/agents/licences/" + licenceId), admin, "{\"approved\": true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/v1/properties/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.listedBy").value("AGENT"))
                .andExpect(jsonPath("$.listerLicensed").value(true))
                .andExpect(jsonPath("$.listerIdVerified").value(true));
    }

    @Test
    void theLicenceCertificateMustBeARealDocumentAndRejectionsNeedAReason() throws Exception {
        Account agent = accounts.register("Agent " + System.nanoTime(), UserRole.AGENT);
        submitLicence(agent, "EAC/1", new MockMultipartFile("certificate", "cert.pdf", "application/pdf",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).andExpect(status().isBadRequest());

        long licenceId = json(submitLicence(agent, "EAC/2024/0001", new MockMultipartFile("certificate", "c.pdf", "application/pdf", PDF))
                .andExpect(status().isOk())).path("id").asLong();
        call(patch("/api/v1/agents/licences/" + licenceId), admin, "{\"approved\": false}").andExpect(status().isBadRequest());
        call(patch("/api/v1/agents/licences/" + licenceId), admin, "{\"approved\": false, \"note\": \"Certificate has expired\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void aPrivateOwnersListingIsLiveAndSaysSo() throws Exception {
        Account owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        long id = createListing(owner);
        mvc.perform(get("/api/v1/properties/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.listedBy").value("OWNER"));
    }

    // --- Companies ----------------------------------------------------------------------------

    @Test
    void aCompanyRegistersItselfAndGoesPublicOnlyAfterApproval() throws Exception {
        Account founder = accounts.register("Founder " + System.nanoTime(), UserRole.AGENT);
        String slug = TestAccounts.uniqueSlug("acme");
        call(post("/api/v1/orgs"), founder, "{\"slug\": \"" + slug + "\", \"name\": \"Acme Realty\"}")
                .andExpect(status().isBadRequest()); // EAC number required for self sign-up
        call(post("/api/v1/orgs"), founder, """
                {"slug": "%s", "name": "Acme Realty", "eacRegistrationNumber": "EAC-F-0099", "phone": "+263771000999"}
                """.formatted(slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        mvc.perform(get("/api/v1/orgs/" + slug)).andExpect(status().isNotFound());
        assertFalse(directorySlugs().contains(slug));
        call(get("/api/v1/orgs/" + slug + "/me"), founder, null).andExpect(status().isOk()); // founder runs the workspace

        call(patch("/api/v1/orgs/" + slug + "/approval"), founder, "{\"approved\": true}").andExpect(status().isForbidden());
        call(patch("/api/v1/orgs/" + slug + "/approval"), admin, "{\"approved\": true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        mvc.perform(get("/api/v1/orgs/" + slug)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Acme Realty"));
        assertTrue(directorySlugs().contains(slug));
        mvc.perform(get("/api/v1/orgs/" + slug + "/public-listings")).andExpect(status().isOk());
    }

    // --- Reports ------------------------------------------------------------------------------

    @Test
    void threeDifferentPeopleReportingAListingSendItForReviewAndStaffCanTakeItDown() throws Exception {
        Account owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        long id = createListing(owner);
        String body = "{\"reason\": \"FAKE_OR_SCAM\", \"details\": \"Asked for a deposit before any viewing\"}";

        Account first = accounts.register("Visitor " + System.nanoTime(), UserRole.TENANT);
        long reportId = json(call(post("/api/v1/properties/" + id + "/reports"), first, body).andExpect(status().isOk())).path("id").asLong();
        call(post("/api/v1/properties/" + id + "/reports"), first, body).andExpect(status().isConflict());
        call(post("/api/v1/properties/" + id + "/reports"), owner, body).andExpect(status().isBadRequest());
        for (int i = 0; i < 2; i++) {
            Account other = accounts.register("Visitor " + System.nanoTime(), UserRole.TENANT);
            call(post("/api/v1/properties/" + id + "/reports"), other, body).andExpect(status().isOk());
        }
        assertEquals("PENDING", json(mvc.perform(get("/api/v1/properties/" + id))).path("verificationStatus").asString());

        call(get("/api/v1/listing-reports"), first, null).andExpect(status().isForbidden());
        call(patch("/api/v1/listing-reports/" + reportId), admin, "{\"action\": \"TAKE_DOWN\"}").andExpect(status().isBadRequest());
        call(patch("/api/v1/listing-reports/" + reportId), admin, "{\"action\": \"TAKE_DOWN\", \"note\": \"Confirmed scam\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TAKEN_DOWN"));
        JsonNode after = json(mvc.perform(get("/api/v1/properties/" + id)));
        assertEquals("INACTIVE", after.path("status").asString());
        assertEquals("REJECTED", after.path("verificationStatus").asString());
    }

    @Test
    void anAnonymousVisitorCanReportAListing() throws Exception {
        Account owner = accounts.register("Owner " + System.nanoTime(), UserRole.LANDLORD);
        long id = createListing(owner);
        mvc.perform(post("/api/v1/properties/" + id + "/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"NOT_AVAILABLE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/properties/" + id + "/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"MADE_UP\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- REIT fee -----------------------------------------------------------------------------

    @Test
    void reitFeeIsOnePointFivePercentOnBuysAndSells() throws Exception {
        JsonNode reits = json(mvc.perform(get("/api/v1/investments/reits")).andExpect(status().isOk()));
        JsonNode reit = reits.get(0);
        BigDecimal price = new BigDecimal(reit.path("unitPrice").asString());
        BigDecimal gross = price.multiply(BigDecimal.TEN).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal fee = gross.multiply(new BigDecimal("0.015")).setScale(2, java.math.RoundingMode.HALF_UP);

        JsonNode buy = json(mvc.perform(get("/api/v1/investments/fee-quote")
                .param("reitId", reit.path("id").asString()).param("units", "10")).andExpect(status().isOk()));
        assertEquals(0, gross.compareTo(new BigDecimal(buy.path("gross").asString())));
        assertEquals(0, fee.compareTo(new BigDecimal(buy.path("fee").asString())));
        assertEquals(0, gross.add(fee).compareTo(new BigDecimal(buy.path("investorAmount").asString())));

        JsonNode sell = json(mvc.perform(get("/api/v1/investments/fee-quote")
                .param("reitId", reit.path("id").asString()).param("units", "10").param("side", "SELL")).andExpect(status().isOk()));
        assertEquals(0, gross.subtract(fee).compareTo(new BigDecimal(sell.path("investorAmount").asString())));
    }

    // --- helpers ------------------------------------------------------------------------------

    private java.util.List<String> directorySlugs() throws Exception {
        java.util.List<String> slugs = new java.util.ArrayList<>();
        json(mvc.perform(get("/api/v1/orgs")).andExpect(status().isOk())).forEach(o -> slugs.add(o.path("slug").asString()));
        return slugs;
    }

    private ResultActions submitLicence(Account agent, String number, MockMultipartFile certificate) throws Exception {
        return mvc.perform(multipart("/api/v1/agents/licence").file(certificate).param("registrationNumber", number)
                .header(HttpHeaders.AUTHORIZATION, agent.bearer()));
    }

    private long createListing(Account owner) throws Exception {
        String body = """
                {"title": "Cottage %s", "description": "Two rooms", "listingType": "RENT", "city": "Harare",
                 "suburb": "Avondale", "bedrooms": 2, "bathrooms": 1, "price": 600, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d,
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), owner.id());
        return json(call(post("/api/v1/properties"), owner, body).andExpect(status().isOk())).get("id").asLong();
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
