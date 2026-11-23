package com.example.estatex.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.user.UserDtos;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Registers users and logs them in through the real API, for integration tests. */
public final class TestAccounts {
    public static final String PASSWORD = "AfricaProp123!";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(500_000);

    private final MockMvc mvc;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public record Account(long id, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    public TestAccounts(MockMvc mvc, UserService userService, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    public Account platformAdmin() throws Exception {
        String email = uniqueEmail("admin");
        long id = userService.create(new UserDtos.CreateUserRequest(
                "Platform Admin", email, phone(), PASSWORD, "Zimbabwe", Set.of(UserRole.ADMIN))).getId();
        return new Account(id, email, login(email));
    }

    public Account register(String fullName, UserRole role) throws Exception {
        String email = uniqueEmail(role.name().toLowerCase());
        String body = """
                {"fullName": "%s", "email": "%s", "phone": "%s", "password": "%s",
                 "country": "Zimbabwe", "roles": ["%s"]}
                """.formatted(fullName, email, phone(), PASSWORD, role.name());
        JsonNode response = json(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return new Account(response.path("user").path("id").asLong(), email, response.path("token").asString());
    }

    public String login(String email) throws Exception {
        String body = """
                {"identifier": "%s", "password": "%s"}
                """.formatted(email, PASSWORD);
        return json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("token").asString();
    }

    public JsonNode json(String raw) {
        return objectMapper.readTree(raw);
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + SEQUENCE.incrementAndGet() + "@example.com";
    }

    public static String uniqueSlug(String prefix) {
        return prefix + "-" + SEQUENCE.incrementAndGet();
    }

    /** Tiny valid PNG (signature + IHDR) - enough for the upload's file-type check. */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };
    private static final AtomicInteger ID_SEQUENCE = new AtomicInteger(1_000_000);

    /**
     * Puts {@code who} through the real ID check: uploads ID front/back and selfies, then an admin
     * approves it after confirming every manual check. Afterwards who.identityVerified is true.
     */
    public void verifyIdentity(Account who, String fullName, Account admin) throws Exception {
        String idNumber = "63-" + ID_SEQUENCE.incrementAndGet() + " F 42";
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/kyc/submissions")
                .file(new org.springframework.mock.web.MockMultipartFile("files", "front.png", "image/png", PNG))
                .file(new org.springframework.mock.web.MockMultipartFile("files", "back.png", "image/png", PNG))
                .file(new org.springframework.mock.web.MockMultipartFile("files", "selfie.png", "image/png", PNG))
                .file(new org.springframework.mock.web.MockMultipartFile("files", "selfie-id.png", "image/png", PNG))
                .param("legalFullName", fullName)
                .param("dateOfBirth", "1990-05-17")
                .param("nationalIdNumber", idNumber)
                .param("idDocumentType", "NATIONAL_ID")
                .param("documentTypes", "ID_FRONT", "ID_BACK", "SELFIE", "SELFIE_WITH_ID")
                .header("Authorization", who.bearer());
        long submissionId = json(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/kyc/submissions/" + submissionId + "/review")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"APPROVED\", \"confirmedChecks\": "
                                + "[\"NAME_MATCHES\", \"NUMBER_MATCHES\", \"FACE_MATCHES\", \"DOCUMENT_GENUINE\"]}"))
                .andExpect(status().isOk());
    }

    private static String phone() {
        return "+26371" + String.format("%07d", SEQUENCE.incrementAndGet());
    }
}
