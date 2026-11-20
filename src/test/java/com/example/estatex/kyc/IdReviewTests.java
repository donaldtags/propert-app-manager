package com.example.estatex.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Admins approve an ID only after checking it, and one ID can't verify two accounts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdReviewTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;

    private TestAccounts accounts;
    private Account admin;
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };
    private static final String ALL_CHECKS = "[\"NAME_MATCHES\", \"NUMBER_MATCHES\", \"FACE_MATCHES\", \"DOCUMENT_GENUINE\"]";

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
    }

    @Test
    void theReviewerSeesAutomaticChecksAndMustConfirmEveryManualOne() throws Exception {
        Account user = accounts.register("Rudo Moyo", UserRole.TENANT);
        long id = submit(user, "Rudo Moyo", "1999-02-01", "63-2233445 K 07");

        JsonNode mine = find(id);
        assertEquals("PASS", result(mine, "ID_FORMAT"));
        assertEquals("PASS", result(mine, "AGE"));
        assertEquals("PASS", result(mine, "NAME"));
        assertEquals("PASS", result(mine, "DUPLICATE_ID"));

        review(id, "{\"status\": \"APPROVED\"}").andExpect(status().isBadRequest());
        review(id, "{\"status\": \"APPROVED\", \"confirmedChecks\": [\"NAME_MATCHES\"]}").andExpect(status().isBadRequest());
        review(id, "{\"status\": \"APPROVED\", \"confirmedChecks\": " + ALL_CHECKS + "}").andExpect(status().isOk());
    }

    @Test
    void badFormatUnderageAndOtherNamesAreFlagged() throws Exception {
        Account user = accounts.register("Chipo Ndlovu", UserRole.TENANT);
        long id = submit(user, "Peter Banda", java.time.LocalDate.now().minusYears(16).toString(), "12345");
        JsonNode s = find(id);
        assertEquals("FAIL", result(s, "ID_FORMAT"));
        assertEquals("FAIL", result(s, "AGE"));
        assertEquals("FAIL", result(s, "NAME"));
        review(id, "{\"status\": \"REJECTED\"}").andExpect(status().isBadRequest()); // a reason is required
        review(id, "{\"status\": \"REJECTED\", \"reviewNote\": \"The ID belongs to someone else\"}").andExpect(status().isOk());
    }

    @Test
    void oneIdNumberCannotVerifyTwoAccounts() throws Exception {
        Account first = accounts.register("Tafadzwa Shumba", UserRole.TENANT);
        long a = submit(first, "Tafadzwa Shumba", "1988-07-07", "08-7654321 Z 08");
        review(a, "{\"status\": \"APPROVED\", \"confirmedChecks\": " + ALL_CHECKS + "}").andExpect(status().isOk());

        Account copycat = accounts.register("Tafadzwa Shumba", UserRole.LANDLORD);
        long b = submit(copycat, "Tafadzwa Shumba", "1988-07-07", "087654321Z08"); // same number, typed differently
        assertEquals("FAIL", result(find(b), "DUPLICATE_ID"));
        review(b, "{\"status\": \"APPROVED\", \"confirmedChecks\": " + ALL_CHECKS + "}").andExpect(status().isConflict());
    }

    @Test
    void theShortcutVerifyButtonNoLongerMeansIdChecked() throws Exception {
        Account user = accounts.register("No Id Person", UserRole.LANDLORD);
        mvc.perform(patch("/api/v1/users/" + user.id() + "/verify").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        JsonNode me = accounts.json(mvc.perform(get("/api/v1/auth/me").header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString());
        assertEquals(false, me.path("identityVerified").asBoolean(false));
    }

    // --- helpers ---

    private long submit(Account who, String legalName, String dob, String idNumber) throws Exception {
        String body = mvc.perform(multipart("/api/v1/kyc/submissions")
                        .file(new MockMultipartFile("files", "f.png", "image/png", PNG))
                        .file(new MockMultipartFile("files", "b.png", "image/png", PNG))
                        .file(new MockMultipartFile("files", "s.png", "image/png", PNG))
                        .file(new MockMultipartFile("files", "si.png", "image/png", PNG))
                        .param("legalFullName", legalName).param("dateOfBirth", dob)
                        .param("nationalIdNumber", idNumber).param("idDocumentType", "NATIONAL_ID")
                        .param("documentTypes", "ID_FRONT", "ID_BACK", "SELFIE", "SELFIE_WITH_ID")
                        .header("Authorization", who.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return accounts.json(body).path("id").asLong();
    }

    private JsonNode find(long submissionId) throws Exception {
        JsonNode all = accounts.json(mvc.perform(get("/api/v1/kyc/submissions").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode s : all) if (s.path("id").asLong() == submissionId) return s;
        throw new AssertionError("submission not listed");
    }

    private static String result(JsonNode submission, String key) {
        for (JsonNode c : submission.path("automatedChecks")) if (key.equals(c.path("key").asString())) return c.path("result").asString();
        return "MISSING";
    }

    private ResultActions review(long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/kyc/submissions/" + id + "/review").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
