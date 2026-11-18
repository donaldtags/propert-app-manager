package com.example.estatex.listing;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.common.BusinessTime;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end publication gates for organisation listings, plus trust-ledger and commission rules.
 * Each test builds its own organisation so tests never share state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListingComplianceTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MandateService mandateService;
    @Autowired private BusinessTime time;

    private TestAccounts accounts;
    private Account platformAdmin;
    private Account orgAdmin;
    private Account prea;
    private Account negotiator;
    private Account outsider;
    private String slug;
    private String base;

    @BeforeEach
    void setUpOrganisation() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        platformAdmin = accounts.platformAdmin();
        orgAdmin = accounts.register("Org Admin", UserRole.AGENT);
        prea = accounts.register("Principal Agent", UserRole.AGENT);
        negotiator = accounts.register("Sales Negotiator", UserRole.TENANT); // org role, not platform role, grants access
        outsider = accounts.register("Someone Else", UserRole.LANDLORD);
        slug = TestAccounts.uniqueSlug("gates");
        base = "/api/v1/orgs/" + slug;
        call(post("/api/v1/orgs"), platformAdmin, """
                {"slug": "%s", "name": "Gate Agency", "divisions": ["PROPERTIES", "MANAGEMENT"]}
                """.formatted(slug)).andExpect(status().isOk());
        addMember(platformAdmin, orgAdmin, "ORG_ADMIN");
        addMember(orgAdmin, prea, "PREA");
        addMember(orgAdmin, negotiator, "NEGOTIATOR");
    }

    @Test
    void anUnverifiedUnmandatedUnapprovedListingCannotBePublishedAndEachGateMustBeMet() throws Exception {
        long id = createListing("CESSION");

        // Fresh listing: draft, hidden from the public, and every gate blocks publication.
        call(get(base + "/listings/" + id), negotiator, null)
                .andExpect(jsonPath("$.property.status").value("DRAFT"))
                .andExpect(jsonPath("$.compliance.publishable").value(false));
        mvc.perform(get("/api/v1/properties/" + id)).andExpect(status().isNotFound());
        call(get("/api/v1/properties/" + id), outsider, null).andExpect(status().isNotFound());
        call(get("/api/v1/properties/" + id), negotiator, null).andExpect(status().isOk());
        publish(id, negotiator).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Verify the owner's title documents")));

        // Bypassing the workspace via the general property endpoint is refused.
        call(patch("/api/v1/properties/" + id), negotiator, "{\"status\": \"AVAILABLE\"}").andExpect(status().isConflict());

        // Title: negotiator records it, but only ORG_ADMIN/PREA may verify.
        call(put(base + "/listings/" + id + "/title"), negotiator, """
                {"titleType": "CESSION", "titleReference": "CES-2026-0042"}
                """).andExpect(status().isOk());
        call(post(base + "/listings/" + id + "/title-verification"), negotiator, "{\"status\": \"VERIFIED\"}")
                .andExpect(status().isForbidden());
        // Can't verify until the checklist for this title type is complete.
        call(post(base + "/listings/" + id + "/title-verification"), orgAdmin, "{\"status\": \"VERIFIED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Cession agreement sighted")));
        checklist(id, CESSION_ITEMS);
        call(post(base + "/listings/" + id + "/title-verification"), orgAdmin, "{\"status\": \"VERIFIED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.property.verificationStatus").value("VERIFIED"));

        // Still no mandate.
        publish(id, negotiator).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Attach a signed mandate")));

        long mandateId = createMandate(id, time.today(), time.today().plusDays(90));
        call(post(base + "/mandates/" + mandateId + "/sign-off"), prea, null)
                .andExpect(status().isConflict()); // owner hasn't signed yet
        attachSignedDocument(mandateId);
        // Only the PREA signs off: not the workspace admin, not a platform admin.
        call(post(base + "/mandates/" + mandateId + "/sign-off"), orgAdmin, null).andExpect(status().isForbidden());
        call(post(base + "/mandates/" + mandateId + "/sign-off"), platformAdmin, null).andExpect(status().isForbidden());
        call(post(base + "/mandates/" + mandateId + "/sign-off"), prea, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Consent missing.
        publish(id, negotiator).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("consent")));
        call(put(base + "/listings/" + id + "/consent"), negotiator, "{\"photos\": true, \"address\": false}")
                .andExpect(status().isOk());
        publish(id, negotiator).andExpect(status().isConflict());
        call(put(base + "/listings/" + id + "/consent"), negotiator, "{\"photos\": true, \"address\": true}")
                .andExpect(status().isOk());

        // PREA approval missing; only the PREA may approve.
        publish(id, negotiator).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("PREA approval")));
        call(post(base + "/listings/" + id + "/approve"), orgAdmin, null).andExpect(status().isForbidden());
        call(post(base + "/listings/" + id + "/approve"), platformAdmin, null).andExpect(status().isForbidden());
        call(post(base + "/listings/" + id + "/approve"), prea, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.compliance.publishable").value(true));

        publish(id, negotiator).andExpect(status().isOk())
                .andExpect(jsonPath("$.property.status").value("AVAILABLE"));

        // Live: publicly visible, carries the cession risk flag, and every step is audited.
        mvc.perform(get("/api/v1/properties/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleType").value("CESSION"))
                .andExpect(jsonPath("$.riskFlags", hasItem("CESSION")));
        call(get(base + "/audit"), prea, null)
                .andExpect(jsonPath("$[*].action", hasItems("LISTING_CREATED", "LISTING_TITLE_VERIFICATION",
                        "MANDATE_CREATED", "MANDATE_OWNER_SIGNED", "MANDATE_SIGNED_OFF", "LISTING_OWNER_CONSENT",
                        "LISTING_PREA_APPROVED", "LISTING_PUBLISHED")));
    }

    @Test
    void approvalIsRefusedWhileOtherGatesAreOpen() throws Exception {
        long id = createListing("TITLE_DEED");
        call(post(base + "/listings/" + id + "/approve"), prea, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Not ready for approval")));
    }

    @Test
    void priceAddressOrTitleTypeChangesWithdrawApprovalAndTakeTheListingOffTheMarket() throws Exception {
        long id = publishedListing(time.today().plusDays(60));

        // Price change through the general property endpoint.
        call(patch("/api/v1/properties/" + id), negotiator, "{\"price\": 245000.00}").andExpect(status().isOk());
        call(get(base + "/listings/" + id), negotiator, null)
                .andExpect(jsonPath("$.property.status").value("DRAFT"))
                .andExpect(jsonPath("$.compliance.preaApprovedAt").doesNotExist())
                .andExpect(jsonPath("$.compliance.checks[?(@.key == 'PREA_APPROVAL')].met", hasItem(false)));
        call(get(base + "/audit"), prea, null)
                .andExpect(jsonPath("$[*].action", hasItems("LISTING_APPROVAL_WITHDRAWN", "LISTING_UNPUBLISHED")));

        // Re-approve, re-publish; then an address change does the same.
        call(post(base + "/listings/" + id + "/approve"), prea, null).andExpect(status().isOk());
        publish(id, negotiator).andExpect(status().isOk());
        call(patch("/api/v1/properties/" + id), negotiator, "{\"address\": \"12 New Road\"}").andExpect(status().isOk());
        call(get(base + "/listings/" + id), negotiator, null).andExpect(jsonPath("$.property.status").value("DRAFT"));

        // And a title-type change also resets title verification.
        call(post(base + "/listings/" + id + "/approve"), prea, null).andExpect(status().isOk());
        publish(id, negotiator).andExpect(status().isOk());
        call(put(base + "/listings/" + id + "/title"), negotiator, """
                {"titleType": "SECTIONAL_TITLE", "titleReference": "ST-77"}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.property.status").value("DRAFT"))
                .andExpect(jsonPath("$.compliance.titleVerificationStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.compliance.preaApprovedAt").doesNotExist());

        // A non-material edit (description) leaves a live listing alone.
        long other = publishedListing(time.today().plusDays(60));
        call(patch("/api/v1/properties/" + other), negotiator, "{\"description\": \"Freshly painted\"}").andExpect(status().isOk());
        call(get(base + "/listings/" + other), negotiator, null).andExpect(jsonPath("$.property.status").value("AVAILABLE"));
    }

    @Test
    void expiredMandatesAreUnpublishedAutomaticallyAndPeopleAreNotified() throws Exception {
        LocalDate lastDay = time.today().plusDays(3);
        long id = publishedListing(lastDay);

        // Still in force on its last day.
        mandateService.expireMandates(lastDay);
        call(get(base + "/listings/" + id), negotiator, null).andExpect(jsonPath("$.property.status").value("AVAILABLE"));

        // The job running the morning after takes it down.
        mandateService.expireMandates(lastDay.plusDays(1));
        call(get(base + "/listings/" + id), negotiator, null)
                .andExpect(jsonPath("$.property.status").value("DRAFT"))
                .andExpect(jsonPath("$.mandates[0].status").value("EXPIRED"));
        mvc.perform(get("/api/v1/properties/" + id)).andExpect(status().isNotFound());
        call(get("/api/v1/notifications/mine"), negotiator, null)
                .andExpect(jsonPath("$[*].type", hasItem("LISTING_COMPLIANCE")));
        call(get("/api/v1/notifications/mine"), prea, null)
                .andExpect(jsonPath("$[*].title", hasItem("Listing taken off the market")));
        call(get(base + "/audit"), prea, null)
                .andExpect(jsonPath("$[*].action", hasItems("MANDATE_EXPIRED", "LISTING_UNPUBLISHED")));
    }

    @Test
    void cancellingTheMandateOrWithdrawingConsentUnpublishes() throws Exception {
        long id = publishedListing(time.today().plusDays(30));
        call(put(base + "/listings/" + id + "/consent"), negotiator, "{\"photos\": false, \"address\": true}")
                .andExpect(jsonPath("$.property.status").value("DRAFT"));

        long id2 = publishedListing(time.today().plusDays(30));
        long mandateId = objectMapper.readTree(call(get(base + "/listings/" + id2), negotiator, null)
                .andReturn().getResponse().getContentAsString()).path("compliance").path("activeMandateId").asLong();
        call(post(base + "/mandates/" + mandateId + "/cancel"), negotiator, "{\"reason\": \"Owner withdrew\"}")
                .andExpect(status().isOk());
        call(get(base + "/listings/" + id2), negotiator, null).andExpect(jsonPath("$.property.status").value("DRAFT"));
    }

    @Test
    void independentListingsKeepWorkingAsBefore() throws Exception {
        String raw = call(post("/api/v1/properties"), outsider, """
                {"title": "Independent flat", "listingType": "RENT", "city": "Harare", "suburb": "Avondale",
                 "bedrooms": 2, "bathrooms": 1, "price": 650, "currency": "USD", "landlordId": %d}
                """.formatted(outsider.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.organisationId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(raw).path("id").asLong();
        mvc.perform(get("/api/v1/properties/" + id)).andExpect(status().isOk());
        // Organisation members get no special rights over listings outside their organisation.
        call(patch("/api/v1/properties/" + id), negotiator, "{\"price\": 1}").andExpect(status().isForbidden());
        call(get(base + "/listings/" + id), negotiator, null).andExpect(status().isNotFound());
    }

    @Test
    void photoUploadRequiresTheListingOwnerOrItsAgency() throws Exception {
        long id = createListing("TITLE_DEED");
        MockMultipartFile photo = new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/properties/" + id + "/photos").file(photo)
                        .header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void trustLedgerCannotBeOverdrawnNeedsAFeeReferenceAndIsIdempotent() throws Exception {
        long id = createListing("TITLE_DEED");
        call(post(base + "/trust-ledger"), negotiator, """
                {"type": "DEPOSIT_RECEIVED", "amountCents": 100000, "clientName": "Buyer A"}
                """).andExpect(status().isForbidden());

        String deposit = """
                {"clientId": "c-%s", "type": "DEPOSIT_RECEIVED", "amountCents": 100000, "currency": "USD",
                 "propertyId": %d, "clientName": "Buyer A", "reference": "EFT 991"}
                """.formatted(slug, id);
        long first = objectMapper.readTree(call(post(base + "/trust-ledger"), orgAdmin, deposit)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        call(post(base + "/trust-ledger"), orgAdmin, deposit).andExpect(jsonPath("$.id").value(first));

        call(post(base + "/trust-ledger"), orgAdmin, """
                {"type": "DISBURSEMENT_TO_OWNER", "amountCents": 150000, "propertyId": %d, "clientName": "Owner"}
                """.formatted(id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Not enough client money")));
        call(post(base + "/trust-ledger"), orgAdmin, """
                {"type": "TRANSFER_TO_OPERATING", "amountCents": 5000, "propertyId": %d, "clientName": "Owner"}
                """.formatted(id)).andExpect(status().isBadRequest());
        call(post(base + "/trust-ledger"), orgAdmin, """
                {"type": "TRANSFER_TO_OPERATING", "amountCents": 5000, "propertyId": %d, "clientName": "Owner",
                 "reference": "Letting fee Nov 2026"}
                """.formatted(id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.signedAmountCents").value(-5000));

        call(get(base + "/trust-ledger"), orgAdmin, null)
                .andExpect(jsonPath("$.balancesCents.USD").value(95000))
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    void commissionIsEarnedOnlyOnConveyancerConfirmation() throws Exception {
        long id = publishedListing(time.today().plusDays(60));
        long mandateId = objectMapper.readTree(call(get(base + "/listings/" + id), negotiator, null)
                .andReturn().getResponse().getContentAsString()).path("compliance").path("activeMandateId").asLong();
        String raw = call(post(base + "/commissions"), negotiator, """
                {"propertyId": %d, "mandateId": %d, "salePriceCents": 25000000}
                """.formatted(id, mandateId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPECTED"))
                .andExpect(jsonPath("$.amountCents").value(1250000)) // 5% default
                .andReturn().getResponse().getContentAsString();
        long commissionId = objectMapper.readTree(raw).path("id").asLong();
        String cBase = base + "/commissions/" + commissionId;

        call(post(cBase + "/received"), orgAdmin, """
                {"receivedOn": "%s", "reference": "EFT"}
                """.formatted(time.today())).andExpect(status().isConflict());
        String confirmation = """
                {"conveyancerName": "T. Chikore", "conveyancerFirm": "Chikore Legal", "deedsReference": "DT 1234/2026",
                 "transferRegisteredOn": "%s"}
                """;
        call(post(cBase + "/conveyancer-confirmation"), negotiator, confirmation.formatted(time.today()))
                .andExpect(status().isForbidden());
        call(post(cBase + "/conveyancer-confirmation"), orgAdmin, confirmation.formatted(time.today().plusDays(1)))
                .andExpect(status().isBadRequest());
        call(post(cBase + "/conveyancer-confirmation"), orgAdmin, confirmation.formatted(time.today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EARNED"));
        call(post(cBase + "/received"), orgAdmin, """
                {"receivedOn": "%s", "reference": "EFT 5521"}
                """.formatted(time.today())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
        call(get(base + "/audit"), prea, null)
                .andExpect(jsonPath("$[?(@.action == 'COMMISSION_EARNED')].amountCents", hasItem(1250000)))
                .andExpect(jsonPath("$[*].action", not(hasItem("COMMISSION_STATUS_CHANGED"))));
    }

    // -- helpers ----------------------------------------------------------------------------

    private long createListing(String titleType) throws Exception {
        String raw = call(post(base + "/listings"), negotiator, """
                {"title": "4 bed in Borrowdale", "listingType": "SALE", "city": "Harare", "suburb": "Borrowdale",
                 "address": "1 Test Lane", "bedrooms": 4, "bathrooms": 3, "priceCents": 25000000,
                 "currency": "USD", "titleType": "%s"}
                """.formatted(titleType)).andExpect(status().isOk())
                .andExpect(jsonPath("$.property.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(raw).path("property").path("id").asLong();
    }

    private long createMandate(long propertyId, LocalDate start, LocalDate expiry) throws Exception {
        String raw = call(post(base + "/listings/" + propertyId + "/mandates"), negotiator, """
                {"type": "SOLE", "startDate": "%s", "expiryDate": "%s", "ownerName": "Mrs Owner",
                 "ownerPhone": "+263771234567", "minimumPriceCents": 23000000}
                """.formatted(start, expiry)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.commissionBps").value(500))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(raw).path("id").asLong();
    }

    private void attachSignedDocument(long mandateId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "mandate.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        mvc.perform(multipart(base + "/mandates/" + mandateId + "/signed-document").file(file)
                        .header(HttpHeaders.AUTHORIZATION, negotiator.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_SIGN_OFF"))
                .andExpect(jsonPath("$.hasSignedDocument").value(true));
    }

    /** A listing that passed every gate and is live, with a mandate valid until {@code expiry}. */
    private long publishedListing(LocalDate expiry) throws Exception {
        long id = createListing("TITLE_DEED");
        call(put(base + "/listings/" + id + "/title"), negotiator, """
                {"titleType": "TITLE_DEED", "titleReference": "DT 99/2001"}
                """).andExpect(status().isOk());
        checklist(id, TITLE_DEED_ITEMS);
        call(post(base + "/listings/" + id + "/title-verification"), prea, "{\"status\": \"VERIFIED\"}").andExpect(status().isOk());
        long mandateId = createMandate(id, time.today(), expiry);
        attachSignedDocument(mandateId);
        call(post(base + "/mandates/" + mandateId + "/sign-off"), prea, null).andExpect(status().isOk());
        call(put(base + "/listings/" + id + "/consent"), negotiator, "{\"photos\": true, \"address\": true}").andExpect(status().isOk());
        call(post(base + "/listings/" + id + "/approve"), prea, null).andExpect(status().isOk());
        publish(id, negotiator).andExpect(status().isOk());
        return id;
    }

    static final String TITLE_DEED_ITEMS = "\"TITLE_DEED_SIGHTED\", \"DEEDS_SEARCH\", \"OWNER_ID_MATCHES\", \"RATES_CLEARANCE\", \"NO_ENCUMBRANCES\"";
    static final String CESSION_ITEMS = "\"CESSION_AGREEMENT_SIGHTED\", \"DEVELOPER_CONFIRMATION\", \"CESSION_CHAIN\", \"OWNER_ID_MATCHES\", \"RATES_CLEARANCE\", \"NO_ENCUMBRANCES\"";

    private void checklist(long id, String items) throws Exception {
        call(put(base + "/listings/" + id + "/verification-checklist"), negotiator, "{\"checked\": [" + items + "]}")
                .andExpect(status().isOk());
    }

    private ResultActions publish(long id, Account who) throws Exception {
        return call(post(base + "/listings/" + id + "/publish"), who, null);
    }

    private void addMember(Account actor, Account member, String role) throws Exception {
        call(post(base + "/members"), actor, """
                {"email": "%s", "roles": ["%s"]}
                """.formatted(member.email(), role)).andExpect(status().isOk());
    }

    private ResultActions call(MockHttpServletRequestBuilder builder, Account account, String body) throws Exception {
        builder.header(HttpHeaders.AUTHORIZATION, account.bearer());
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder);
    }
}
