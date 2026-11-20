package com.example.estatex.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.common.ApiException;
import com.example.estatex.investment.InvestmentService;
import com.example.estatex.investment.Reit;
import com.example.estatex.investment.ReitRepository;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.payment.PaymentSucceededEvent;
import com.example.estatex.payment.gateway.CheckoutSession;
import com.example.estatex.payment.gateway.GatewayStatus;
import com.example.estatex.payment.gateway.PaymentGateway;
import com.example.estatex.payment.gateway.WebhookOutcome;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Wallet: top-ups, paying from the balance, payouts, withdrawals, and the ledger's guarantees. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WalletTests.FakeGatewayConfig.class)
class WalletTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WalletService walletService;
    @Autowired private WalletPaymentListener walletListener;
    @Autowired private PaymentService paymentService;
    @Autowired private InvestmentService investmentService;
    @Autowired private ReitRepository reits;

    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(mvc, userService, objectMapper);
    }

    // --- top-ups -------------------------------------------------------------------------------

    @Test
    void aTopUpIsCreditedOnlyOnceTheGatewayConfirmsIt_andReplaysNeverCreditTwice() throws Exception {
        Account user = accounts.register("Saver " + System.nanoTime(), UserRole.TENANT);
        JsonNode checkout = json(call(post("/api/v1/wallet/top-ups"), user, "{\"amount\": 50, \"provider\": \"testpay\"}")
                .andExpect(status().isOk()));
        long paymentId = checkout.path("paymentId").asLong();
        assertTrue(checkout.path("redirectUrl").asString().startsWith("https://testpay.example/"));

        assertBalance(user, "0.00"); // nothing until the gateway says paid

        FakeGateway.STATUS.put("tp-" + paymentId, GatewayStatus.PAID);
        webhook(paymentId);
        assertBalance(user, "50.00");

        webhook(paymentId);
        webhook(paymentId);
        // Even if the success event itself were delivered again, the idempotency key holds.
        walletListener.onPaymentSucceeded(new PaymentSucceededEvent(paymentService.require(paymentId)));
        assertBalance(user, "50.00");

        Wallet wallet = walletService.find(user.id(), "USD").orElseThrow();
        assertTrue(walletService.balanceMatchesLedger(wallet.getId()));
    }

    @Test
    void topUpsMustGoThroughAGatewayAndCantBeConfirmedByHand() throws Exception {
        Account user = accounts.register("Sneaky " + System.nanoTime(), UserRole.TENANT);
        call(post("/api/v1/wallet/top-ups"), user, "{\"amount\": 50, \"provider\": \"manual\"}").andExpect(status().isBadRequest());
        call(post("/api/v1/wallet/top-ups"), user, "{\"amount\": 50, \"provider\": \"wallet\"}").andExpect(status().isBadRequest());
        call(post("/api/v1/wallet/top-ups"), user, "{\"amount\": 0.5, \"provider\": \"testpay\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aPayeeCannotConfirmAWalletPaymentByHandToMintMoney() throws Exception {
        Account landlord = accounts.register("Landlord " + System.nanoTime(), UserRole.LANDLORD);
        Account accomplice = accounts.register("Accomplice " + System.nanoTime(), UserRole.TENANT);
        long paymentId = json(call(post("/api/v1/payments"), accomplice, """
                {"payeeId": %d, "amount": 900, "currency": "USD", "provider": "wallet", "purpose": "Rent payment"}
                """.formatted(landlord.id())).andExpect(status().isOk())).path("id").asLong();
        call(patch("/api/v1/payments/" + paymentId + "/success"), landlord, null).andExpect(status().isBadRequest());
        assertBalance(landlord, "0.00");
    }

    // --- paying from the wallet ----------------------------------------------------------------

    @Test
    void payingAStayFromTheWalletConfirmsItAndCreditsTheHost() throws Exception {
        Account host = accounts.register("Host " + System.nanoTime(), UserRole.LANDLORD);
        Account guest = accounts.register("Guest " + System.nanoTime(), UserRole.TENANT);
        topUp(guest, "300");
        long bookingId = acceptedBooking(host, guest); // 2 nights x $80 + $25 cleaning = $185

        JsonNode checkout = json(call(post("/api/v1/stays/bookings/" + bookingId + "/pay"), guest, "{\"provider\": \"wallet\"}")
                .andExpect(status().isOk()));
        assertTrue(checkout.path("redirectUrl").asString().endsWith("/payments/" + checkout.path("paymentId").asLong() + "/return"));

        call(get("/api/v1/stays/bookings/" + bookingId), guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertBalance(guest, "115.00");
        assertBalance(host, "185.00");
        // Paying the same booking again is refused (it's no longer awaiting payment).
        call(post("/api/v1/stays/bookings/" + bookingId + "/pay"), guest, "{\"provider\": \"wallet\"}")
                .andExpect(status().is4xxClientError());
        assertBalance(guest, "115.00");
    }

    @Test
    void notEnoughInTheWalletIsAClearErrorAndChangesNothing() throws Exception {
        Account host = accounts.register("Host " + System.nanoTime(), UserRole.LANDLORD);
        Account guest = accounts.register("Guest " + System.nanoTime(), UserRole.TENANT);
        topUp(guest, "10");
        long bookingId = acceptedBooking(host, guest);

        call(post("/api/v1/stays/bookings/" + bookingId + "/pay"), guest, "{\"provider\": \"wallet\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("isn't enough")));
        call(get("/api/v1/stays/bookings/" + bookingId), guest, null).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        assertBalance(guest, "10.00");
        assertBalance(host, "0.00");
    }

    @Test
    void concurrentSpendingNeverOverdrawsTheWallet() throws Exception {
        Account user = accounts.register("Racer " + System.nanoTime(), UserRole.TENANT);
        topUp(user, "100");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<Boolean>> spends = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String key = "race-" + user.id() + "-" + i;
            spends.add(() -> {
                try {
                    walletService.post(user.id(), "USD", -3000, WalletEntryType.PAYMENT, WalletService.PLATFORM_COLLECTIONS,
                            "Race test", null, null, key, null);
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            });
        }
        int succeeded = 0;
        for (Future<Boolean> f : pool.invokeAll(spends)) if (f.get()) succeeded++;
        pool.shutdown();

        assertEquals(3, succeeded, "only three $30 spends fit in $100");
        assertBalance(user, "10.00");
        assertTrue(walletService.balanceMatchesLedger(walletService.find(user.id(), "USD").orElseThrow().getId()));
    }

    // --- payouts -------------------------------------------------------------------------------

    @Test
    void sellingReitUnitsPaysTheInvestorIntoTheirWallet() throws Exception {
        Account investor = accounts.register("Investor " + System.nanoTime(), UserRole.INVESTOR);
        Reit reit = reits.findByActiveTrue().get(0);
        investmentService.seedActiveInvestment(reit.getId(), new BigDecimal("10"), "USD", userService.require(investor.id()));

        call(patch("/api/v1/investments/sell"), investor, "{\"reitId\": %d, \"units\": 4}".formatted(reit.getId()))
                .andExpect(status().isOk());
        // Proceeds at today's price less EstateX's 1.5% REIT fee.
        BigDecimal gross = reit.getUnitPrice().multiply(new BigDecimal("4")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal fee = gross.multiply(new BigDecimal("1.5")).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        assertBalance(investor, gross.subtract(fee).toPlainString());
    }

    // --- withdrawals ---------------------------------------------------------------------------

    @Test
    void withdrawalsNeedIdentityAndPassword_holdTheMoney_andStaffPayOrReturnIt() throws Exception {
        Account admin = accounts.platformAdmin();
        Account user = accounts.register("Withdrawer " + System.nanoTime(), UserRole.LANDLORD);
        topUp(user, "100");
        String ecocash = "{\"amount\": 40, \"method\": \"ECOCASH\", \"ecocashPhone\": \"0771 234 567\", \"password\": \"%s\"}";

        call(post("/api/v1/wallet/withdrawals"), user, ecocash.formatted(TestAccounts.PASSWORD)).andExpect(status().isForbidden());
        userService.verify(user.id()); // identity verified
        call(post("/api/v1/wallet/withdrawals"), user, ecocash.formatted("wrong-password")).andExpect(status().isUnauthorized());
        call(post("/api/v1/wallet/withdrawals"), user,
                "{\"amount\": 2, \"method\": \"ECOCASH\", \"ecocashPhone\": \"0771234567\", \"password\": \"%s\"}"
                        .formatted(TestAccounts.PASSWORD)).andExpect(status().isBadRequest());

        long paidId = json(call(post("/api/v1/wallet/withdrawals"), user, ecocash.formatted(TestAccounts.PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED"))).path("id").asLong();
        call(get("/api/v1/wallet"), user, null)
                .andExpect(jsonPath("$.balance").value(60.00))
                .andExpect(jsonPath("$.pendingWithdrawals").value(40.00));

        call(patch("/api/v1/admin/wallet/withdrawals/" + paidId), user, "{\"action\": \"PAID\", \"reference\": \"X\"}")
                .andExpect(status().isForbidden());
        call(patch("/api/v1/admin/wallet/withdrawals/" + paidId), admin, "{\"action\": \"PAID\"}").andExpect(status().isBadRequest());
        call(patch("/api/v1/admin/wallet/withdrawals/" + paidId), admin, "{\"action\": \"PAID\", \"reference\": \"MP2611.1234\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        call(patch("/api/v1/admin/wallet/withdrawals/" + paidId), admin, "{\"action\": \"REJECTED\", \"reason\": \"late\"}")
                .andExpect(status().isConflict());
        assertBalance(user, "60.00");

        long rejectedId = json(call(post("/api/v1/wallet/withdrawals"), user, """
                {"amount": 50, "method": "BANK", "bankAccountName": "T Moyo", "bankAccountNumber": "1234567890",
                 "bankName": "CBZ", "password": "%s"}""".formatted(TestAccounts.PASSWORD))
                .andExpect(status().isOk())).path("id").asLong();
        assertBalance(user, "10.00");
        call(patch("/api/v1/admin/wallet/withdrawals/" + rejectedId), admin,
                "{\"action\": \"REJECTED\", \"reason\": \"Account name doesn't match your ID\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertBalance(user, "60.00");

        call(post("/api/v1/wallet/withdrawals"), user,
                "{\"amount\": 61, \"method\": \"ECOCASH\", \"ecocashPhone\": \"0771234567\", \"password\": \"%s\"}"
                        .formatted(TestAccounts.PASSWORD)).andExpect(status().isConflict());
        assertTrue(walletService.balanceMatchesLedger(walletService.find(user.id(), "USD").orElseThrow().getId()));
        assertTrue(walletService.systemBalanceCents(WalletService.EXTERNAL_PAYOUTS, "USD") >= 40_00,
                "the paid withdrawal is recorded as having left EstateX");
    }

    // --- helpers -------------------------------------------------------------------------------

    private void topUp(Account who, String amount) throws Exception {
        long paymentId = json(call(post("/api/v1/wallet/top-ups"), who,
                "{\"amount\": " + amount + ", \"provider\": \"testpay\"}").andExpect(status().isOk())).path("paymentId").asLong();
        FakeGateway.STATUS.put("tp-" + paymentId, GatewayStatus.PAID);
        webhook(paymentId);
    }

    private void webhook(long paymentId) throws Exception {
        mvc.perform(post("/api/v1/payments/webhooks/testpay").contentType(MediaType.TEXT_PLAIN).content("tp-" + paymentId))
                .andExpect(status().is2xxSuccessful());
    }

    private long acceptedBooking(Account host, Account guest) throws Exception {
        String listing = """
                {"title": "Wallet cottage %s", "description": "Quiet", "listingType": "SHORT_STAY", "city": "Harare",
                 "suburb": "Avondale", "bedrooms": 1, "bathrooms": 1, "price": 80, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d, "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), host.id());
        long stayId = json(call(post("/api/v1/properties"), host, listing).andExpect(status().isOk())).path("id").asLong();
        call(put("/api/v1/stays/" + stayId + "/settings"), host, """
                {"maxGuests": 2, "minNights": 1, "maxNights": 14, "cleaningFee": 25, "checkInTime": "14:00",
                 "checkOutTime": "10:00", "instantBook": false}""").andExpect(status().isOk());
        LocalDate in = LocalDate.now().plusDays(30);
        long bookingId = json(call(post("/api/v1/stays/" + stayId + "/bookings"), guest,
                "{\"checkIn\": \"%s\", \"checkOut\": \"%s\", \"guests\": 1}".formatted(in, in.plusDays(2)))
                .andExpect(status().isOk())).path("id").asLong();
        call(post("/api/v1/stays/bookings/" + bookingId + "/accept"), host, "{}")
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        return bookingId;
    }

    private void assertBalance(Account who, String expected) throws Exception {
        String balance = json(call(get("/api/v1/wallet"), who, null).andExpect(status().isOk())).path("balance").decimalValue()
                .setScale(2).toPlainString();
        assertEquals(expected, balance);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    /** A stand-in payment provider: checkouts stay pending until a test marks them paid. */
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
}
