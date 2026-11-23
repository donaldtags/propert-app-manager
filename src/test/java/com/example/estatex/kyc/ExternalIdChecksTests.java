package com.example.estatex.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Register lookups (Dojah) and international photo-ID checks (Didit), against a local stand-in for
 * both services so the real HTTP, parsing and signature code runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExternalIdChecksTests {
    private static final String WEBHOOK_SECRET = "whsec-test-123";
    private static final HttpServer STUB = startStub();

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + STUB.getAddress().getPort();
        r.add("app.idcheck.dojah.app-id", () -> "app-test");
        r.add("app.idcheck.dojah.secret-key", () -> "sk-test");
        r.add("app.idcheck.dojah.base-url", () -> base);
        r.add("app.idcheck.didit.api-key", () -> "didit-test");
        r.add("app.idcheck.didit.workflow-id", () -> "wf-test");
        r.add("app.idcheck.didit.webhook-secret", () -> WEBHOOK_SECRET);
        r.add("app.idcheck.didit.base-url", () -> base);
    }

    /** Fake Dojah register (by ID number) and fake Didit session endpoint. */
    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/zw/kyc/nin", ex -> {
                boolean authed = "app-test".equals(ex.getRequestHeaders().getFirst("AppId"))
                        && "sk-test".equals(ex.getRequestHeaders().getFirst("Authorization"));
                String id = ex.getRequestURI().getQuery().replace("id_number=", "");
                String body;
                int code = 200;
                if (!authed) { code = 401; body = "{}"; }
                else if (id.equals("631111111A11")) body = "{\"entity\":{\"person_no\":\"631111111A11\",\"status\":\"A\",\"surname\":\"MOYO\",\"first_name\":\"RUDO\",\"sex\":\"F\",\"date_of_birth\":\"1990-05-17\"}}";
                else if (id.equals("633333333C33")) body = "{\"entity\":{\"surname\":\"DUBE\",\"first_name\":\"OLD\",\"date_of_birth\":\"1930-01-01\",\"date_of_death\":\"2001-01-01\"}}";
                else if (id.equals("634444444D44")) body = "{\"entity\":{\"surname\":\"CHIRWA\",\"first_name\":\"PETER\",\"date_of_birth\":\"1975-02-02\"}}";
                else { code = 404; body = "{\"error\":\"not found\"}"; }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(code, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            server.createContext("/v3/session/", ex -> {
                boolean authed = "didit-test".equals(ex.getRequestHeaders().getFirst("x-api-key"));
                byte[] bytes = (authed ? "{\"session_id\":\"sess-" + System.nanoTime() + "\",\"url\":\"https://verify.didit.me/session/abc\",\"status\":\"Not Started\"}" : "{}")
                        .getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(authed ? 201 : 401, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() {
        STUB.stop(0);
    }

    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    private TestAccounts accounts;
    private Account admin;
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };
    private static final String ALL = "[\"NAME_MATCHES\", \"NUMBER_MATCHES\", \"FACE_MATCHES\", \"DOCUMENT_GENUINE\"]";

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
    }

    @Test
    void bothChecksReportThemselvesAsSwitchedOn() throws Exception {
        mvc.perform(get("/api/v1/kyc/checks/available").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zimbabweRegister").value(true))
                .andExpect(jsonPath("$.internationalPhotoId").value(true));
    }

    @Test
    void aMatchingRegisterRecordPasses() throws Exception {
        Account user = accounts.register("Rudo Moyo", UserRole.TENANT);
        long id = submit(user, "Rudo Moyo", "1990-05-17", "63-1111111 A 11", true);
        JsonNode reg = check(find(id), "REGISTRY");
        assertEquals("PASS", reg.path("result").asString());
        assertTrue(reg.path("detail").asString().contains("RUDO MOYO"), reg.toString());
        review(id, "{\"status\": \"APPROVED\", \"confirmedChecks\": " + ALL + "}").andExpect(status().isOk());
    }

    @Test
    void anIdNumberTheRegisterDoesntKnowCantBeApproved() throws Exception {
        Account user = accounts.register("Made Up", UserRole.TENANT);
        long id = submit(user, "Made Up", "1990-01-01", "63-2222222 B 22", true);
        assertEquals("FAIL", check(find(id), "REGISTRY").path("result").asString());
        review(id, "{\"status\": \"APPROVED\", \"confirmedChecks\": " + ALL + "}").andExpect(status().isConflict());
    }

    @Test
    void aDeceasedPersonsIdCantBeApproved() throws Exception {
        Account user = accounts.register("Old Dube", UserRole.TENANT);
        long id = submit(user, "Old Dube", "1930-01-01", "63-3333333 C 33", true);
        JsonNode reg = check(find(id), "REGISTRY");
        assertEquals("FAIL", reg.path("result").asString());
        assertTrue(reg.path("detail").asString().contains("deceased"));
        review(id, "{\"status\": \"APPROVED\", \"confirmedChecks\": " + ALL + "}").andExpect(status().isConflict());
    }

    @Test
    void someoneElsesNumberIsFlaggedButTheReviewerDecides() throws Exception {
        Account user = accounts.register("Tendai Ncube", UserRole.TENANT);
        long id = submit(user, "Tendai Ncube", "1992-09-09", "63-4444444 D 44", true);
        assertEquals("FAIL", check(find(id), "REGISTRY").path("result").asString());
    }

    @Test
    void withoutConsentTheNumberIsNeverSentToTheRegister() throws Exception {
        Account user = accounts.register("Private Person", UserRole.TENANT);
        long id = submit(user, "Private Person", "1990-05-17", "63-1111111 A 11", false);
        assertEquals("MISSING", check(find(id), "REGISTRY").path("result").asString("MISSING"));
        mvc.perform(post("/api/v1/kyc/submissions/" + id + "/registry-check").header("Authorization", admin.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void theInternationalPhotoIdResultOnlyCountsWithAValidSignature() throws Exception {
        Account user = accounts.register("Anna Schmidt", UserRole.TENANT);
        long id = submit(user, "Anna Schmidt", "1985-04-04", "C01X00T47", false, "PASSPORT", "DE");
        JsonNode session = accounts.json(mvc.perform(post("/api/v1/kyc/document-check").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("https://verify.didit.me/session/abc", session.path("url").asString());
        assertEquals("WARN", check(find(id), "DOCUMENT").path("result").asString()); // waiting for the person

        String sessionId = sessionIdOfLatestCheck(user);
        String body = """
                {"session_id":"%s","status":"Approved","webhook_type":"status.updated","vendor_data":"user:%d",
                 "decision":{"id_verifications":[{"status":"Approved","document_type":"Passport","issuing_state":"DEU",
                 "document_number":"C01X00T47","first_name":"ANNA","last_name":"SCHMIDT","date_of_birth":"1985-04-04"}]}}
                """.formatted(sessionId, user.id());
        String now = String.valueOf(Instant.now().getEpochSecond());

        webhook(body, "0".repeat(64), now).andExpect(status().isUnauthorized());                 // forged
        webhook(body, sign(body), String.valueOf(Instant.now().getEpochSecond() - 3600)).andExpect(status().isUnauthorized()); // replayed
        webhook(body, sign(body), now).andExpect(status().isOk());

        JsonNode doc = check(find(id), "DOCUMENT");
        assertEquals("PASS", doc.path("result").asString());
        assertTrue(doc.path("detail").asString().contains("ANNA SCHMIDT"), doc.toString());
    }

    // --- helpers ---

    private long submit(Account who, String name, String dob, String number, boolean consent) throws Exception {
        return submit(who, name, dob, number, consent, "NATIONAL_ID", "ZW");
    }

    private long submit(Account who, String name, String dob, String number, boolean consent, String type, String country)
            throws Exception {
        boolean passport = type.equals("PASSPORT");
        var req = multipart("/api/v1/kyc/submissions")
                .file(new MockMultipartFile("files", "a.png", "image/png", PNG))
                .file(new MockMultipartFile("files", "s.png", "image/png", PNG))
                .file(new MockMultipartFile("files", "si.png", "image/png", PNG));
        if (!passport) req.file(new MockMultipartFile("files", "b.png", "image/png", PNG));
        req.param("legalFullName", name).param("dateOfBirth", dob).param("nationalIdNumber", number)
                .param("idDocumentType", type).param("issuingCountry", country)
                .param("registryConsent", String.valueOf(consent))
                .header("Authorization", who.bearer());
        if (passport) req.param("documentTypes", "PASSPORT", "SELFIE", "SELFIE_WITH_ID");
        else req.param("documentTypes", "ID_FRONT", "SELFIE", "SELFIE_WITH_ID", "ID_BACK");
        return accounts.json(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("id").asLong();
    }

    private JsonNode find(long submissionId) throws Exception {
        JsonNode all = accounts.json(mvc.perform(get("/api/v1/kyc/submissions").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode s : all) if (s.path("id").asLong() == submissionId) return s;
        throw new AssertionError("not listed");
    }

    private static JsonNode check(JsonNode submission, String key) {
        for (JsonNode c : submission.path("automatedChecks")) if (key.equals(c.path("key").asString())) return c;
        return objectMapperless();
    }

    private static JsonNode objectMapperless() {
        return tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }

    @Autowired private com.example.estatex.kyc.check.IdentityCheckRepository checks;

    private String sessionIdOfLatestCheck(Account user) {
        return checks.findByUserIdOrderByCreatedAtDesc(user.id()).stream()
                .filter(c -> "didit".equals(c.getProvider())).findFirst().orElseThrow().getProviderReference();
    }

    private ResultActions review(long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/kyc/submissions/" + id + "/review").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions webhook(String body, String signature, String timestamp) throws Exception {
        return mvc.perform(post("/api/v1/kyc/webhooks/didit").contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature).header("X-Timestamp", timestamp).content(body));
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
