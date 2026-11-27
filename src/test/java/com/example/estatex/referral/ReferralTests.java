package com.example.estatex.referral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentRepository;
import com.example.estatex.payment.gateway.CheckoutSession;
import com.example.estatex.payment.gateway.GatewayStatus;
import com.example.estatex.payment.gateway.PaymentGateway;
import com.example.estatex.payment.gateway.WebhookOutcome;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import com.example.estatex.wallet.WalletEntryType;
import com.example.estatex.wallet.WalletService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Phase R of docs/admin-analytics-plan.md: the referral discount and reward, and the rules that stop abuse. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReferralTests.FakeGatewayConfig.class)
class ReferralTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WalletService wallet;
    @Autowired private PaymentRepository payments;
    @Autowired private ReferralRepository referrals;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate tx;
    @Autowired private com.example.estatex.analytics.engine.AnalyticsEngine engine;
    @Autowired private com.example.estatex.common.BusinessTime time;

    private TestAccounts accounts;
    private Account admin;
    private Account referrer;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        referrer = accounts.register("Referrer " + System.nanoTime(), UserRole.LANDLORD);
        programme(true, 20, 1000, 10);
    }

    @Test
    void aReferredFriendPaysLessAndTheReferrerIsCreditedOnce() throws Exception {
        String code = code(referrer);
        mvc.perform(get("/api/v1/referrals/code/" + code.toLowerCase()))
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.discountPercent").value(20));
        Account friend = registerWith(code, "friend-device-" + System.nanoTime());
        call(get("/api/v1/referrals/mine"), friend, null).andExpect(jsonPath("$.referredBy.discountAvailable").value(true));

        long paymentId = checkout(friend);
        Payment p = payments.findById(paymentId).orElseThrow();
        assertThat(p.getAmount()).isEqualByComparingTo("12.00"); // GROWTH $15 less 20%
        assertThat(p.getMetadata()).contains("referralId=").contains("discountCents=300");

        pay(paymentId);
        assertThat(balance(referrer)).isEqualTo(1000);
        pay(paymentId); // a repeated webhook changes nothing
        assertThat(balance(referrer)).isEqualTo(1000);
        Referral r = referrals.findByRefereeUserId(friend.id()).orElseThrow();
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.REWARDED);
        assertThat(r.getDiscountCents()).isEqualTo(300L);
        call(get("/api/v1/subscriptions/me"), friend, null).andExpect(jsonPath("$.plan").value("GROWTH"));

        // Only the first subscription payment is discounted.
        long second = checkout(friend);
        assertThat(payments.findById(second).orElseThrow().getAmount()).isEqualByComparingTo("15.00");
        call(get("/api/v1/referrals/mine"), referrer, null)
                .andExpect(jsonPath("$.invited").value(1)).andExpect(jsonPath("$.subscribed").value(1))
                .andExpect(jsonPath("$.earnedCents").value(1000));

        // The programme's cost shows up in the business figures.
        engine.rebuild(time.today().atStartOfDay(time.zone()).toInstant(), java.time.Instant.now().plusMillis(1));
        JsonNode business = null;
        for (JsonNode sct : json(call(get("/api/v1/admin/analytics/overview?grain=DAY"), admin, null)).path("sections")) {
            if (sct.path("key").asString().equals("business")) business = sct;
        }
        java.util.Map<String, BigDecimal> kpis = new java.util.HashMap<>();
        for (JsonNode k : business.path("kpis")) if (k.has("value") && !k.path("value").isNull()) kpis.put(k.path("key").asString(), k.path("value").decimalValue());
        assertThat(kpis.get("referral_rewards")).isGreaterThanOrEqualTo(new BigDecimal("10"));
        assertThat(kpis.get("referral_discounts")).isGreaterThanOrEqualTo(new BigDecimal("3"));
    }

    @Test
    void aMistypedCodeStopsSignUpSoItCanBeFixed() throws Exception {
        String body = signup("NOSUCHCODE", null);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/referrals/code/NOSUCHCODE")).andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void referringYourselfFromTheSameBrowserGetsNothing() throws Exception {
        String device = "shared-device-" + System.nanoTime();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\": \"" + referrer.email() + "\", \"password\": \"" + TestAccounts.PASSWORD + "\", \"deviceId\": \"" + device + "\"}"))
                .andExpect(status().isOk());
        Account second = registerWith(code(referrer), device);
        Referral r = referrals.findByRefereeUserId(second.id()).orElseThrow();
        assertThat(r.getStatus()).isEqualTo(ReferralStatus.BLOCKED);
        assertThat(r.getBlockReason()).contains("Same browser");
        call(get("/api/v1/referrals/mine"), second, null).andExpect(jsonPath("$.referredBy.discountAvailable").value(false));
        long paymentId = checkout(second);
        assertThat(payments.findById(paymentId).orElseThrow().getAmount()).isEqualByComparingTo("15.00");
        pay(paymentId);
        assertThat(balance(referrer)).isZero();
    }

    @Test
    void rewardsStopAtTheMonthlyLimitButTheFriendStillGetsTheDiscount() throws Exception {
        programme(true, 20, 1000, 1);
        String code = code(referrer);
        pay(checkout(registerWith(code, "d1-" + System.nanoTime())));
        Account late = registerWith(code, "d2-" + System.nanoTime());
        long paymentId = checkout(late);
        assertThat(payments.findById(paymentId).orElseThrow().getAmount()).isEqualByComparingTo("12.00");
        pay(paymentId);
        assertThat(balance(referrer)).isEqualTo(1000);
        assertThat(referrals.findByRefereeUserId(late.id()).orElseThrow().getStatus()).isEqualTo(ReferralStatus.CAPPED);
    }

    @Test
    void aRefundTakesTheRewardBackOrFlagsItWhenTheMoneyIsGone() throws Exception {
        String code = code(referrer);
        long first = checkout(registerWith(code, "r1-" + System.nanoTime()));
        pay(first);
        events.publishEvent(new PaymentRefundedEvent(first));
        assertThat(referrals.findByQualifyingPaymentId(first).orElseThrow().getStatus()).isEqualTo(ReferralStatus.REVERSED);
        assertThat(balance(referrer)).isZero();

        // Second reward, already spent before staff reverse it: flagged, not forced negative.
        Account friend = registerWith(code, "r2-" + System.nanoTime());
        long second = checkout(friend);
        pay(second);
        tx.executeWithoutResult(s -> wallet.post(referrer.id(), "USD", -1000, WalletEntryType.ADJUSTMENT, WalletService.PLATFORM_REVENUE,
                "Spent", null, null, "spent-" + System.nanoTime(), null));
        Long referralId = referrals.findByRefereeUserId(friend.id()).orElseThrow().getId();
        call(post("/api/v1/admin/referrals/" + referralId + "/reverse"), referrer, "{\"reason\": \"x\"}").andExpect(status().isForbidden());
        call(post("/api/v1/admin/referrals/" + referralId + "/reverse"), admin, "{\"reason\": \"Refunded by EFT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingReversals").value(1));
        assertThat(referrals.findById(referralId).orElseThrow().getStatus()).isEqualTo(ReferralStatus.REVERSAL_PENDING);
        assertThat(balance(referrer)).isZero();
    }

    @Test
    void nothingIsDiscountedWhileTheProgrammeIsOff() throws Exception {
        programme(false, 20, 1000, 10);
        mvc.perform(get("/api/v1/referrals/code/" + code(referrer))).andExpect(jsonPath("$.programmeActive").value(false));
        Account friend = registerWith(code(referrer), "off-" + System.nanoTime());
        assertThat(referrals.findByRefereeUserId(friend.id()).orElseThrow().getStatus()).isEqualTo(ReferralStatus.REGISTERED);
        long paymentId = checkout(friend);
        assertThat(payments.findById(paymentId).orElseThrow().getAmount()).isEqualByComparingTo("15.00");
    }

    @Test
    void onlyAdminsChangeTheProgramme() throws Exception {
        Account tenant = accounts.register("Tenant " + System.nanoTime(), UserRole.TENANT);
        call(get("/api/v1/admin/referrals"), tenant, null).andExpect(status().isForbidden());
        call(put("/api/v1/admin/referrals/settings"), tenant, "{\"enabled\": true, \"discountPercent\": 50, \"rewardCents\": 1, \"monthlyCap\": 1}")
                .andExpect(status().isForbidden());
        call(put("/api/v1/admin/referrals/settings"), admin, "{\"enabled\": true, \"discountPercent\": 95, \"rewardCents\": 1000, \"monthlyCap\": 1}")
                .andExpect(status().isBadRequest());
        call(get("/api/v1/admin/referrals"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.settings.enabled").value(true));
    }

    /** A page asking twice at once for a new user's code must not fail either request (seen in the browser). */
    @Test
    void twoSimultaneousFirstRequestsGetTheSameCode() throws Exception {
        Account fresh = accounts.register("Fresh " + System.nanoTime(), UserRole.LANDLORD);
        java.util.concurrent.Callable<String> ask = () -> json(call(get("/api/v1/referrals/mine"), fresh, null)
                .andExpect(status().isOk())).path("code").asString();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var results = pool.invokeAll(java.util.List.of(ask, ask, ask, ask));
            java.util.Set<String> codes = new java.util.HashSet<>();
            for (var f : results) codes.add(f.get());
            assertThat(codes).hasSize(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void emailAliasesCountAsTheSamePerson() {
        assertThat(ReferralService.normaliseEmail("John.Doe+promo@GMail.com")).isEqualTo("johndoe@gmail.com");
        assertThat(ReferralService.normaliseEmail("john.doe+x@example.com")).isEqualTo("john.doe@example.com");
    }

    // ---------------------------------------------------------------------------------------

    private void programme(boolean on, int pct, long reward, int cap) throws Exception {
        call(put("/api/v1/admin/referrals/settings"), admin,
                "{\"enabled\": %s, \"discountPercent\": %d, \"rewardCents\": %d, \"monthlyCap\": %d}".formatted(on, pct, reward, cap))
                .andExpect(status().isOk());
    }

    private String code(Account who) throws Exception {
        return json(call(get("/api/v1/referrals/mine"), who, null).andExpect(status().isOk())).path("code").asString();
    }

    private String signup(String code, String device) {
        return """
                {"fullName": "Friend %d", "email": "%s", "phone": "+2637%08d", "password": "%s", "country": "Zimbabwe",
                 "roles": ["LANDLORD"], "referralCode": "%s" %s}
                """.formatted(System.nanoTime(), TestAccounts.uniqueEmail("friend"), System.nanoTime() % 100_000_000L,
                TestAccounts.PASSWORD, code, device == null ? "" : ", \"deviceId\": \"" + device + "\"");
    }

    private Account registerWith(String code, String device) throws Exception {
        JsonNode r = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(signup(code, device)))
                .andExpect(status().isOk()));
        return new Account(r.path("user").path("id").asLong(), r.path("user").path("email").asString(), r.path("token").asString());
    }

    private long checkout(Account who) throws Exception {
        return json(call(post("/api/v1/subscriptions/checkout"), who, "{\"plan\": \"GROWTH\", \"provider\": \"testpay\"}")
                .andExpect(status().isOk())).path("paymentId").asLong();
    }

    private void pay(long paymentId) throws Exception {
        FakeGateway.STATUS.put("tp-" + paymentId, GatewayStatus.PAID);
        mvc.perform(post("/api/v1/payments/webhooks/testpay").contentType(MediaType.TEXT_PLAIN).content("tp-" + paymentId))
                .andExpect(status().is2xxSuccessful());
    }

    private long balance(Account who) {
        return wallet.balanceCents(who.id(), "USD");
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    static class FakeGateway implements PaymentGateway {
        static final Map<String, GatewayStatus> STATUS = new ConcurrentHashMap<>();

        @Override public String id() { return "testpay"; }

        @Override public CheckoutSession initiateCheckout(Payment payment, String returnUrl) {
            String ref = "tp-" + payment.getId();
            STATUS.putIfAbsent(ref, GatewayStatus.PENDING);
            return new CheckoutSession("https://testpay.example/checkout/" + ref, ref, null);
        }

        @Override public GatewayStatus checkStatus(Payment payment) {
            return STATUS.getOrDefault(payment.getProviderReference(), GatewayStatus.PENDING);
        }

        @Override public WebhookOutcome handleWebhook(String rawBody, Map<String, String> headers) {
            return new WebhookOutcome(rawBody.trim());
        }
    }

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        PaymentGateway testpayGateway() {
            return new FakeGateway();
        }
    }

    @SuppressWarnings("unused")
    private static BigDecimal usd(String v) {
        return new BigDecimal(v);
    }
}
