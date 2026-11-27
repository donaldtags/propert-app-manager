package com.example.estatex.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.common.AuditLogRepository;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
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

/** Who can put a "Verified" badge on a listing, and what takes it away again. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingVerificationSecurityTests {
    @Autowired private com.example.estatex.support.ListingFees fees;
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogRepository auditLogs;

    private TestAccounts accounts;
    private Account admin;
    private Account landlord;

    /** Smallest valid PNG header + IHDR: enough for the signature check. */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        String name = "Owner " + System.nanoTime();
        landlord = accounts.register(name, UserRole.LANDLORD);
        accounts.verifyIdentity(landlord, name, admin);
    }

    @Test
    void aSelfRegisteredAgentCannotVerifyAnyListing() throws Exception {
        Account agent = accounts.register("Agent " + System.nanoTime(), UserRole.AGENT);
        long own = createListing(agent);
        long other = createListing(landlord);

        call(patch("/api/v1/properties/" + own + "/verify"), agent, "{\"note\": \"mine\"}").andExpect(status().isForbidden());
        call(patch("/api/v1/properties/" + other + "/verify"), agent, "{}").andExpect(status().isForbidden());
        assertEquals("UNVERIFIED", badge(own));
    }

    @Test
    void theVerifierIsTheSignedInAdminNotAnIdInTheBody() throws Exception {
        long id = createListing(landlord);
        // A spoofed verifierId (here: the owner) is ignored; the admin making the call is recorded.
        call(patch("/api/v1/properties/" + id + "/verify"), admin,
                "{\"verifierId\": " + landlord.id() + ", \"note\": \"Visited 12 May\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        assertTrue(auditLogs.findAll().stream().anyMatch(l -> "property.verified".equals(l.getAction())
                && Long.valueOf(id).equals(l.getEntityId()) && Long.valueOf(admin.id()).equals(l.getActorUserId())));
    }

    @Test
    void anAdminCannotVerifyTheirOwnListing() throws Exception {
        long id = createListing(admin);
        call(patch("/api/v1/properties/" + id + "/verify"), admin, "{}").andExpect(status().isForbidden());
    }

    @Test
    void rejectingNeedsAReason() throws Exception {
        long id = createListing(landlord);
        call(patch("/api/v1/properties/" + id + "/verify"), admin, "{\"approved\": false}").andExpect(status().isBadRequest());
        call(patch("/api/v1/properties/" + id + "/verify"), admin, "{\"approved\": false, \"note\": \"Photos are of another house\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"));
    }

    @Test
    void changingTheAddressOfAVerifiedListingSendsItBackForChecking() throws Exception {
        long id = verifiedListing(landlord);
        call(patch("/api/v1/properties/" + id), landlord, "{\"price\": 999}").andExpect(status().isOk());
        assertEquals("VERIFIED", badge(id), "a price change alone keeps the badge");

        call(patch("/api/v1/properties/" + id), landlord, "{\"suburb\": \"Borrowdale\", \"address\": \"1 Other Road\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
    }

    @Test
    void newPhotosOnAVerifiedListingSendItBackForChecking() throws Exception {
        long id = verifiedListing(landlord);
        upload(id, landlord, new MockMultipartFile("files", "house.png", "image/png", PNG)).andExpect(status().isOk());
        assertEquals("PENDING", badge(id));
    }

    @Test
    void aWebPageDisguisedAsAPhotoIsRejected() throws Exception {
        long id = createListing(landlord);
        byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);
        upload(id, landlord, new MockMultipartFile("files", "x.html", "image/png", html)).andExpect(status().isBadRequest());
    }

    @Test
    void uploadedPhotosAreStoredUnderOurOwnNameNotTheUploaders() throws Exception {
        long id = createListing(landlord);
        JsonNode body = json(upload(id, landlord, new MockMultipartFile("files", "evil.html", "image/png", PNG))
                .andExpect(status().isOk()));
        String url = body.path("photoUrls").get(body.path("photoUrls").size() - 1).asString();
        assertTrue(url.endsWith(".png") && !url.contains("evil"), url);
    }

    @Test
    void thePhotoOfSomeoneElsesListingFlagsTheCopyForReview() throws Exception {
        long original = createListing(landlord);
        upload(original, landlord, new MockMultipartFile("files", "a.png", "image/png", PNG)).andExpect(status().isOk());

        Account scammer = accounts.register("Copycat " + System.nanoTime(), UserRole.LANDLORD);
        long copy = createListing(scammer);
        upload(copy, scammer, new MockMultipartFile("files", "renamed.png", "image/png", PNG)).andExpect(status().isOk());

        assertEquals("PENDING", badge(copy));
        assertTrue(auditLogs.findAll().stream().anyMatch(l -> "fraud.duplicate_photo".equals(l.getAction())
                && Long.valueOf(copy).equals(l.getEntityId())));
    }

    // --- helpers ---------------------------------------------------------------------------

    private long verifiedListing(Account owner) throws Exception {
        long id = createListing(owner);
        call(patch("/api/v1/properties/" + id + "/verify"), admin, "{\"note\": \"Inspected\"}").andExpect(status().isOk());
        return id;
    }

    private long createListing(Account owner) throws Exception {
        String body = """
                {"title": "Garden flat %s", "description": "Two rooms", "listingType": "RENT", "city": "Harare",
                 "suburb": "Avondale", "bedrooms": 2, "bathrooms": 1, "price": 600, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d,
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), owner.id());
        return fees.paid(json(call(post("/api/v1/properties"), owner, body).andExpect(status().isOk())).get("id").asLong());
    }

    private String badge(long id) throws Exception {
        return json(call(get("/api/v1/properties/" + id), admin, null).andExpect(status().isOk()))
                .path("verificationStatus").asString();
    }

    private ResultActions upload(long id, Account who, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/v1/properties/" + id + "/photos").file(file)
                .header(HttpHeaders.AUTHORIZATION, who.bearer()));
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
