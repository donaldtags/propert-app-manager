package com.example.estatex.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.auth.sms.LoggingSmsGateway;
import com.example.estatex.auth.twofactor.Totp;
import com.example.estatex.notification.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** Phone-only accounts, two-step sign-in (app, text, backup codes), lockout and session revocation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityTests {
    private static final String PASSWORD = "AfricaProp123!";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationRepository notifications;

    // ---- phone-only accounts ----

    @Test
    void someoneWithoutAnEmailSignsUpAndSignsInWithTheirPhoneTypedAnyWay() throws Exception {
        String local = "078" + String.format("%07d", 3_000_000 + SEQ.incrementAndGet());   // 078xxxxxxx
        String e164 = "+263" + local.substring(1);
        JsonNode reg = json(register(null, local).andExpect(status().isOk()));
        assertEquals(e164, reg.path("user").path("phone").asString(), "stored in international form");

        for (String typed : List.of(e164, local, "263 " + local.substring(1), local.substring(0, 3) + " " + local.substring(3))) {
            send("/api/v1/auth/login", null, "{\"identifier\": \"" + typed + "\", \"password\": \"" + PASSWORD + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isString());
        }
    }

    @Test
    void signUpNeedsAnEmailOrAPhone() throws Exception {
        register(null, null).andExpect(status().isBadRequest());
        register(null, "not a phone").andExpect(status().isBadRequest());
    }

    @Test
    void aPhoneOnlyUserResetsTheirPasswordWithATextedCode() throws Exception {
        String phone = newPhone();
        register(null, phone).andExpect(status().isOk());
        send("/api/v1/auth/forgot-password", null, "{\"identifier\": \"" + phone + "\"}").andExpect(status().isOk());
        String code = lastCodeTo(phone);

        send("/api/v1/auth/reset-password", null, "{\"identifier\": \"" + phone + "\", \"code\": \"000000\", \"password\": \"NewPassword123!\"}")
                .andExpect(status().isBadRequest());
        send("/api/v1/auth/reset-password", null,
                "{\"identifier\": \"" + phone + "\", \"code\": \"" + code + "\", \"password\": \"NewPassword123!\"}")
                .andExpect(status().isOk());
        login(phone, "NewPassword123!").andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        // The code was single-use.
        send("/api/v1/auth/reset-password", null,
                "{\"identifier\": \"" + phone + "\", \"code\": \"" + code + "\", \"password\": \"Another123!pass\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordAnswersTheSameForAccountsThatDontExist() throws Exception {
        String phone = newPhone();
        register(null, phone).andExpect(status().isOk());
        String known = json(send("/api/v1/auth/forgot-password", null, "{\"identifier\": \"" + phone + "\"}")
                .andExpect(status().isOk())).path("message").asString();
        String unknown = json(send("/api/v1/auth/forgot-password", null, "{\"identifier\": \"nobody-" + SEQ.incrementAndGet() + "@example.test\"}")
                .andExpect(status().isOk())).path("message").asString();
        assertEquals(known, unknown);
    }

    // ---- authenticator app ----

    @Test
    void withTheAuthenticatorAppOnSignInNeedsACodeAndCodesCantBeReused() throws Exception {
        User u = newUser();
        String secret = enableTotp(u);

        JsonNode challenge = json(login(u.email, PASSWORD).andExpect(status().isOk()));
        assertTrue(challenge.path("twoFactorRequired").asBoolean());
        assertFalse(challenge.has("token"), "no session before the code");
        assertTrue(challenge.path("methods").toString().contains("TOTP"));

        String code = Totp.codeAt(secret, Totp.stepAt(Instant.now().getEpochSecond()) + 1);
        verify(challenge, "000000", "TOTP").andExpect(status().isUnauthorized());
        String token = json(verify(challenge, code, "TOTP").andExpect(status().isOk())).path("token").asString();
        call(get("/api/v1/auth/me"), token, null).andExpect(status().isOk());

        // Same code, fresh sign-in: refused (replay).
        JsonNode again = json(login(u.email, PASSWORD).andExpect(status().isOk()));
        verify(again, code, "TOTP").andExpect(status().isUnauthorized());
        // The challenge can't be reused after success either.
        verify(challenge, code, "TOTP").andExpect(status().isUnauthorized());
    }

    @Test
    void aBackupCodeWorksOnce() throws Exception {
        User u = newUser();
        startTotp(u);
        String secret = json(call(post("/api/v1/auth/2fa/totp/start"), u.token, null)).path("secret").asString();
        JsonNode codes = json(call(post("/api/v1/auth/2fa/totp/confirm"), u.token,
                "{\"code\": \"" + Totp.codeAt(secret, Totp.stepAt(Instant.now().getEpochSecond())) + "\"}").andExpect(status().isOk()));
        assertEquals(10, codes.path("backupCodes").size());
        String backup = codes.path("backupCodes").get(0).asString();

        verify(json(login(u.email, PASSWORD)), backup, "BACKUP").andExpect(status().isOk());
        verify(json(login(u.email, PASSWORD)), backup, "BACKUP").andExpect(status().isUnauthorized());
    }

    @Test
    void theOldSettingsToggleCanNoLongerSwitchTwoStepSignInOnOrOff() throws Exception {
        User u = newUser();
        call(patch("/api/v1/users/" + u.id + "/profile"), u.token, "{\"twoFactorEnabled\": true}").andExpect(status().isOk());
        login(u.email, PASSWORD).andExpect(jsonPath("$.token").isString());
    }

    @Test
    void turningTwoStepSignInOffNeedsThePasswordAndACode() throws Exception {
        User u = newUser();
        String secret = enableTotp(u);
        String code = Totp.codeAt(secret, Totp.stepAt(Instant.now().getEpochSecond()) + 1);
        call(post("/api/v1/auth/2fa/disable"), u.token, "{\"password\": \"wrong\", \"code\": \"" + code + "\", \"method\": \"TOTP\"}")
                .andExpect(status().isBadRequest());
        call(post("/api/v1/auth/2fa/disable"), u.token, "{\"password\": \"" + PASSWORD + "\", \"code\": \"" + code + "\", \"method\": \"TOTP\"}")
                .andExpect(status().isOk());
        login(u.email, PASSWORD).andExpect(jsonPath("$.token").isString());
    }

    // ---- text-message codes ----

    @Test
    void withTextMessagesOnTheCodeIsSentAtSignIn() throws Exception {
        String phone = newPhone();
        JsonNode reg = json(register(null, phone).andExpect(status().isOk()));
        String token = reg.path("token").asString();

        call(post("/api/v1/auth/2fa/sms/start"), token, null).andExpect(status().isOk());
        JsonNode codes = json(call(post("/api/v1/auth/2fa/sms/confirm"), token, "{\"code\": \"" + lastCodeTo(phone) + "\"}")
                .andExpect(status().isOk()));
        assertEquals(10, codes.path("backupCodes").size());

        JsonNode challenge = json(login(phone, PASSWORD).andExpect(status().isOk()));
        assertTrue(challenge.path("twoFactorRequired").asBoolean());
        assertTrue(challenge.path("codeSent").asBoolean(), "text-only accounts get the code straight away");
        assertTrue(challenge.path("maskedPhone").asString().endsWith(phone.substring(phone.length() - 4)));
        verify(challenge, lastCodeTo(phone), "SMS").andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }

    @Test
    void textMessageCodesAreThrottled() throws Exception {
        String phone = newPhone();
        String token = json(register(null, phone)).path("token").asString();
        call(post("/api/v1/auth/2fa/sms/start"), token, null).andExpect(status().isOk());
        call(post("/api/v1/auth/2fa/sms/start"), token, null).andExpect(status().isTooManyRequests());
    }

    // ---- lockout ----

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
        User u = newUser();
        for (int i = 0; i < 5; i++) {
            login(u.email, "WrongPassword1!").andExpect(status().isUnauthorized());
        }
        login(u.email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownAccountsLockTheSameWaySoNothingIsRevealed() throws Exception {
        String ghost = "ghost-" + SEQ.incrementAndGet() + "@example.test";
        for (int i = 0; i < 5; i++) {
            login(ghost, "WrongPassword1!").andExpect(status().isUnauthorized());
        }
        login(ghost, "WrongPassword1!").andExpect(status().isTooManyRequests());
    }

    @Test
    void wrongCodesCountTowardsTheLock() throws Exception {
        User u = newUser();
        String secret = enableTotp(u);
        JsonNode challenge = json(login(u.email, PASSWORD));
        for (int i = 0; i < 5; i++) {
            verify(challenge, "000000", "TOTP").andExpect(status().isUnauthorized());
        }
        String good = Totp.codeAt(secret, Totp.stepAt(Instant.now().getEpochSecond()) + 1);
        login(u.email, PASSWORD).andExpect(status().isTooManyRequests());
        verify(challenge, good, "TOTP").andExpect(status().isUnauthorized());
    }

    // ---- sessions ----

    @Test
    void signOutEverywhereEndsEverySession() throws Exception {
        User u = newUser();
        String second = json(login(u.email, PASSWORD)).path("token").asString();
        call(post("/api/v1/auth/logout-all"), u.token, null).andExpect(status().isOk());
        call(get("/api/v1/auth/me"), u.token, null).andExpect(status().isUnauthorized());
        call(get("/api/v1/auth/me"), second, null).andExpect(status().isUnauthorized());
        // Also for endpoints served from the cached-user path.
        call(get("/api/v1/notifications/unread-count"), second, null).andExpect(status().isUnauthorized());
    }

    @Test
    void changingThePasswordSignsOutOtherDevicesButNotThisOne() throws Exception {
        User u = newUser();
        String other = json(login(u.email, PASSWORD)).path("token").asString();
        call(get("/api/v1/notifications/unread-count"), other, null).andExpect(status().isOk()); // warm the cache

        call(post("/api/v1/auth/change-password"), u.token, "{\"currentPassword\": \"nope\", \"newPassword\": \"BrandNew123!x\"}")
                .andExpect(status().isBadRequest());
        call(post("/api/v1/auth/change-password"), u.token, "{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"BrandNew123!x\"}")
                .andExpect(status().isOk());
        call(get("/api/v1/auth/me"), u.token, null).andExpect(status().isOk());
        call(get("/api/v1/notifications/unread-count"), other, null).andExpect(status().isUnauthorized());
        login(u.email, "BrandNew123!x").andExpect(jsonPath("$.token").isString());
    }

    @Test
    void aSignInFromANewDeviceRaisesAnAlertButTheUsualDeviceDoesNot() throws Exception {
        User u = newUser();
        long before = notifications.count();
        loginFrom(u.email, "device-A").andExpect(status().isOk());   // the device used at sign-up
        assertEquals(before, notifications.count());
        loginFrom(u.email, "device-B").andExpect(status().isOk());
        assertEquals(before + 1, notifications.count());
        loginFrom(u.email, "device-B").andExpect(status().isOk());
        assertEquals(before + 1, notifications.count(), "only the first time");
    }

    // ---- helpers ----

    private record User(long id, String email, String token) {}

    private User newUser() throws Exception {
        String email = "sec-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@example.test";
        String body = """
                {"fullName": "Security Tester", "email": "%s", "password": "%s", "country": "Zimbabwe",
                 "roles": ["TENANT"], "deviceId": "device-A"}
                """.formatted(email, PASSWORD);
        JsonNode reg = json(send("/api/v1/auth/register", null, body).andExpect(status().isOk()));
        return new User(reg.path("user").path("id").asLong(), email, reg.path("token").asString());
    }

    private String newPhone() {
        return "+26378" + String.format("%07d", 5_000_000 + SEQ.incrementAndGet());
    }

    private ResultActions register(String email, String phone) throws Exception {
        String body = "{\"fullName\": \"Phone Person\", \"password\": \"" + PASSWORD + "\", \"roles\": [\"TENANT\"]"
                + (email == null ? "" : ", \"email\": \"" + email + "\"")
                + (phone == null ? "" : ", \"phone\": \"" + phone + "\"") + "}";
        return send("/api/v1/auth/register", null, body);
    }

    private void startTotp(User u) throws Exception {
        call(post("/api/v1/auth/2fa/totp/start"), u.token, null).andExpect(status().isOk());
    }

    /** Turns the authenticator app on and returns its secret. */
    private String enableTotp(User u) throws Exception {
        JsonNode setup = json(call(post("/api/v1/auth/2fa/totp/start"), u.token, null).andExpect(status().isOk()));
        assertTrue(setup.path("otpauthUri").asString().startsWith("otpauth://totp/"));
        assertTrue(setup.path("qrDataUrl").asString().startsWith("data:image/png;base64,"));
        String secret = setup.path("secret").asString();
        call(post("/api/v1/auth/2fa/totp/confirm"), u.token, "{\"code\": \"000000\"}").andExpect(status().isBadRequest());
        call(post("/api/v1/auth/2fa/totp/confirm"), u.token,
                "{\"code\": \"" + Totp.codeAt(secret, Totp.stepAt(Instant.now().getEpochSecond())) + "\"}")
                .andExpect(status().isOk());
        return secret;
    }

    private ResultActions login(String identifier, String password) throws Exception {
        return send("/api/v1/auth/login", null, "{\"identifier\": \"" + identifier + "\", \"password\": \"" + password + "\", \"deviceId\": \"device-A\"}");
    }

    private ResultActions loginFrom(String identifier, String deviceId) throws Exception {
        return send("/api/v1/auth/login", null, "{\"identifier\": \"" + identifier + "\", \"password\": \"" + PASSWORD + "\", \"deviceId\": \"" + deviceId + "\"}");
    }

    private ResultActions verify(JsonNode challenge, String code, String method) throws Exception {
        return send("/api/v1/auth/2fa/verify", null, "{\"challengeToken\": \"" + challenge.path("challengeToken").asString()
                + "\", \"code\": \"" + code + "\", \"method\": \"" + method + "\"}");
    }

    private String lastCodeTo(String phone) {
        String message = LoggingSmsGateway.lastMessageTo(phone).orElseThrow(() -> new AssertionError("no text sent to " + phone));
        Matcher m = SIX_DIGITS.matcher(message);
        assertTrue(m.find(), message);
        return m.group(1);
    }

    private ResultActions send(String url, String token, String body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), token, body);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        if (token != null) req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
