package com.example.estatex.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.common.PlatformAccountService;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentRepository;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.payment.PaymentStatus;
import com.example.estatex.subscription.LandlordSubscription;
import com.example.estatex.subscription.LandlordSubscriptionRepository;
import com.example.estatex.subscription.SubscriptionPlan;
import com.example.estatex.subscription.SubscriptionService;
import com.example.estatex.subscription.SubscriptionStatus;
import com.example.estatex.support.ListingFees;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRepository;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Listing fees and plan renewals are real bills since 2026-11-27: nothing is marked paid without
 * money arriving (before, the system confirmed its own charges). Bills are paid through checkout or
 * the wallet; here the payment is completed through the same success path a gateway confirmation
 * takes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ListingFees fees;
    @Autowired private PropertyRepository properties;
    @Autowired private PropertyBillingRepository billings;
    @Autowired private PropertyBillingService billingService;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentService paymentService;
    @Autowired private PlatformAccountService platform;
    @Autowired private LandlordSubscriptionRepository subscriptions;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private UserRepository users;

    private Account owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = new TestAccounts(mvc, userService, objectMapper).register("Billing Owner " + System.nanoTime(), UserRole.LANDLORD);
    }

    @Test
    void anUnpaidRenewalIsABillNotAPaymentAndTheListingStaysLiveDuringGrace() throws Exception {
        long id = fees.paid(listing());
        overdue(id);
        long successfulBefore = successfulPayments();

        billingService.renewDueBillings();

        PropertyBilling b = billing(id);
        assertThat(b.getStatus()).isEqualTo(PropertyBillingStatus.PAST_DUE);
        Payment bill = payments.findById(b.getPendingPaymentId()).orElseThrow();
        assertThat(bill.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(bill.getPurposeType()).isEqualTo(PaymentPurpose.LISTING_FEE);
        assertThat(bill.getPayer().getId()).isEqualTo(owner.id());
        assertThat(successfulPayments()).as("no payment marked successful by the system").isEqualTo(successfulBefore);
        assertThat(properties.findById(id).orElseThrow().getStatus()).isEqualTo(PropertyStatus.AVAILABLE);
        // Running the sweep again doesn't raise a second bill.
        billingService.renewDueBillings();
        assertThat(billing(id).getPendingPaymentId()).isEqualTo(bill.getId());
    }

    @Test
    void afterTheGracePeriodTheListingComesOffAndPayingPutsItBack() throws Exception {
        long id = fees.paid(listing());
        overdue(id);
        billingService.renewDueBillings();
        pastGrace(id);

        billingService.deactivateStalePastDue();
        assertThat(properties.findById(id).orElseThrow().getStatus()).isEqualTo(PropertyStatus.INACTIVE);
        // The owner can't switch it back on without paying.
        mvc.perform(patch("/api/v1/properties/" + id).header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"AVAILABLE\"}"))
                .andExpect(status().isPaymentRequired());

        fees.paid(id);
        assertThat(properties.findById(id).orElseThrow().getStatus()).isEqualTo(PropertyStatus.AVAILABLE);
        PropertyBilling b = billing(id);
        assertThat(b.getStatus()).isEqualTo(PropertyBillingStatus.ACTIVE);
        assertThat(b.getPendingPaymentId()).isNull();
        assertThat(b.getCurrentPeriodEnd()).isAfter(Instant.now().plus(Duration.ofDays(29)));
    }

    @Test
    void aListingTheOwnerWithdrewIsLeftAloneByTheSweepAndByPaying() throws Exception {
        long id = fees.paid(listing());
        mvc.perform(patch("/api/v1/properties/" + id).header(HttpHeaders.AUTHORIZATION, owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"WITHDRAWN\"}")).andExpect(status().isOk());
        overdue(id);
        billingService.renewDueBillings();
        pastGrace(id);
        billingService.deactivateStalePastDue();
        assertThat(properties.findById(id).orElseThrow().getStatus()).isEqualTo(PropertyStatus.WITHDRAWN);
        fees.paid(id);
        assertThat(properties.findById(id).orElseThrow().getStatus()).isEqualTo(PropertyStatus.WITHDRAWN);
    }

    @Test
    void aPlanRenewalIsABillAndPayingItRenewsThePlan() throws Exception {
        var user = users.findById(owner.id()).orElseThrow();
        LandlordSubscription s = subscriptionService.subscriptionFor(user);
        s.setPlan(SubscriptionPlan.GROWTH);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setCurrentPeriodEnd(Instant.now().minus(Duration.ofHours(1)));
        subscriptions.save(s);
        long successfulBefore = successfulPayments();

        subscriptionService.renewDuePaidSubscriptions();

        s = subscriptions.findByUser(user).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(s.getPlan()).isEqualTo(SubscriptionPlan.GROWTH);
        Payment bill = payments.findById(s.getPendingPaymentId()).orElseThrow();
        assertThat(bill.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(bill.getPurposeType()).isEqualTo(PaymentPurpose.SUBSCRIPTION);
        assertThat(successfulPayments()).isEqualTo(successfulBefore);

        paymentService.markSuccessful(bill.getId(), platform.billingAccount()); // the money arrived
        s = subscriptions.findByUser(user).orElseThrow();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.getPlan()).isEqualTo(SubscriptionPlan.GROWTH);
        assertThat(s.getPendingPaymentId()).isNull();
        assertThat(s.getCurrentPeriodEnd()).isAfter(Instant.now().plus(Duration.ofDays(29)));
    }

    private long listing() throws Exception {
        String body = """
                {"title": "Billing %d", "listingType": "RENT", "city": "Harare", "suburb": "Avondale", "bedrooms": 2,
                 "bathrooms": 1, "price": 600, "currency": "USD", "landlordId": %d}
                """.formatted(System.nanoTime(), owner.id());
        String created = mvc.perform(post("/api/v1/properties").header(HttpHeaders.AUTHORIZATION, owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("id").asLong();
    }

    private PropertyBilling billing(long propertyId) {
        return billings.findByProperty(properties.findById(propertyId).orElseThrow()).orElseThrow();
    }

    private void overdue(long propertyId) {
        PropertyBilling b = billing(propertyId);
        b.setCurrentPeriodEnd(Instant.now().minus(Duration.ofHours(1)));
        billings.save(b);
    }

    private void pastGrace(long propertyId) {
        PropertyBilling b = billing(propertyId);
        b.setPastDueSince(Instant.now().minus(Duration.ofDays(4)));
        billings.save(b);
    }

    private long successfulPayments() {
        return payments.findAll().stream().filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL).count();
    }
}
