package com.example.primenestprop;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.primenestprop.user.UserDtos;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {
    private static final String PASSWORD = "AfricaProp123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();

    private record Registered(long id, String token) {
    }

    @Test
    void apiCreatedDataSupportsFrontendCoreLists() throws Exception {
        Registered admin = bootstrapAdmin();
        Registered landlord = registerUser("Landlord User", "LANDLORD");
        Registered tenant = registerUser("Tenant User", "TENANT");
        Registered agent = registerUser("Agent User", "AGENT");
        Registered investor = registerUser("Investor User", "INVESTOR");

        mvc.perform(patch("/api/v1/users/{id}/verify", landlord.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
        mvc.perform(patch("/api/v1/users/{id}/verify", agent.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        long propertyId = createProperty(landlord.id(), agent.id(), "Borrowdale API Apartment", landlord.token());
        mvc.perform(patch("/api/v1/properties/{id}/verify", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifierId": %s,
                                  "note": "Verified after API creation"
                                }
                                """.formatted(agent.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        long leaseId = createLease(propertyId, tenant.id(), landlord.token());
        long escrowId = createEscrow(propertyId, leaseId, tenant.token());
        mvc.perform(patch("/api/v1/escrows/{id}/fund", escrowId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "ECOCASH"
                                }
                                """))
                .andExpect(status().isOk());
        long reitId = createReit(admin.token());
        createInvestment(reitId, investor.token());
        createPayment(landlord.id(), propertyId, leaseId, tenant.token());
        createMaintenance(propertyId, tenant.token());

        mvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
        mvc.perform(get("/api/v1/properties")
                        .param("listingType", "RENT")
                        .param("city", "Harare")
                        .param("maxPrice", "600")
                        .param("bedrooms", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/leases").param("tenantId", String.valueOf(tenant.id()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/escrows").param("userId", String.valueOf(tenant.id()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/payments").param("userId", String.valueOf(tenant.id()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/maintenance").param("propertyId", String.valueOf(propertyId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/investments/reits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/investments").param("investorId", String.valueOf(investor.id()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/dashboards/landlords/{landlordId}", landlord.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlord.token()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboards/tenants/{tenantId}", tenant.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk());
    }

    @Test
    void authAndProfileEndpointsWorkForApiCreatedUsers() throws Exception {
        String email = uniqueEmail("tenant");
        long tenantId = registerUser("Tenant Auth", email, "TENANT").id();

        String loginResponse = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.id").value((int) tenantId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = extractString(loginResponse, "token");

        mvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mvc.perform(patch("/api/v1/users/{id}/profile", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "city": "Harare",
                                  "twoFactorEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Harare"))
                .andExpect(jsonPath("$.twoFactorEnabled").value(true));

        mvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void writeEndpointsSupportFrontendMutationsWithApiCreatedData() throws Exception {
        Registered admin = bootstrapAdmin();
        String landlordEmail = uniqueEmail("mutation-landlord");
        String tenantEmail = uniqueEmail("mutation-tenant");
        Registered landlord = registerUser("Mutation Landlord", landlordEmail, "LANDLORD");
        Registered tenant = registerUser("Mutation Tenant", tenantEmail, "TENANT");
        Registered agent = registerUser("Mutation Agent", "AGENT");
        long propertyId = createProperty(landlord.id(), agent.id(), "Mount Pleasant API Studio", landlord.token());

        mvc.perform(patch("/api/v1/properties/{id}/verify", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifierId": %s,
                                  "note": "Verified in integration test"
                                }
                                """.formatted(agent.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        long leaseId = createLease(propertyId, tenant.id(), landlord.token());
        mvc.perform(patch("/api/v1/leases/{id}/sign", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
        // A lease only reaches SIGNED - and only then does the Property Passport timeline
        // record a LEASE_SIGNED event - once both the tenant and the landlord have signed.
        mvc.perform(patch("/api/v1/leases/{id}/sign", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlord.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));

        long escrowId = createEscrow(propertyId, leaseId, tenant.token());
        mvc.perform(patch("/api/v1/escrows/{id}/fund", escrowId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "ECOCASH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FUNDED"));

        long paymentId = createPayment(landlord.id(), propertyId, leaseId, tenant.token());
        mvc.perform(patch("/api/v1/payments/{id}/success", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlord.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"));

        long maintenanceId = createMaintenance(propertyId, tenant.token());
        mvc.perform(patch("/api/v1/maintenance/{id}/status", maintenanceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlord.token())
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // Property Passport: the Digital Home Timeline must reflect every lifecycle event above,
        // in chronological order, alongside the per-category history lists it is built from.
        mvc.perform(get("/api/v1/properties/{id}/passport", propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value((int) propertyId))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.timeline", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$.timeline[*].type", hasItems(
                        "LISTED", "VERIFIED", "LEASE_CREATED", "LEASE_SIGNED",
                        "MAINTENANCE_REQUESTED", "MAINTENANCE_RESOLVED", "ESCROW_CREATED")))
                .andExpect(jsonPath("$.leaseHistory", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.leaseHistory[0].status").value("SIGNED"))
                .andExpect(jsonPath("$.maintenanceHistory", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.maintenanceHistory[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.escrowHistory", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.escrowHistory[0].status").value("FUNDED"))
                .andExpect(jsonPath("$.healthScore.overall", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.healthScore.verification").value(100));
        assertTimelineIsChronological(propertyId);

        String tenantToken = tenant.token();
        MockMultipartFile payslip = new MockMultipartFile(
                "files",
                "payslip.pdf",
                "application/pdf",
                "%PDF-1.4 test".getBytes()
        );
        String documentResponse = mvc.perform(multipart("/api/v1/leases/{leaseId}/documents", leaseId)
                        .file(payslip)
                        .param("documentTypes", "PAYSLIP")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value("PAYSLIP"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long documentId = extractLong(documentResponse, "id");

        mvc.perform(get("/api/v1/leases/{leaseId}/documents", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mvc.perform(get("/api/v1/leases/{leaseId}/documents/{documentId}/download", leaseId, documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));

        String landlordToken = landlord.token();
        mvc.perform(patch("/api/v1/leases/{leaseId}/documents/{documentId}/review", leaseId, documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlordToken)
                        .content("""
                                {
                                  "status": "APPROVED",
                                  "reviewNote": "Verified"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewNote").value("Verified"));

        mvc.perform(post("/api/v1/users/{id}/roles", tenant.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .content("""
                                {
                                  "role": "DIASPORA",
                                  "password": "%s"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray());

        mvc.perform(post("/api/v1/users/{id}/admin-request", tenant.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value((int) tenant.id()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/api/v1/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .content("""
                                {
                                  "landlordId": %s,
                                  "tenantId": %s,
                                  "propertyId": %s,
                                  "leaseId": %s,
                                  "rating": 5,
                                  "comment": "Responsive landlord"
                                }
                                """.formatted(landlord.id(), tenant.id(), propertyId, leaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Responsive landlord"));

        mvc.perform(get("/api/v1/ratings").param("landlordId", String.valueOf(landlord.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        String conversationResponse = mvc.perform(post("/api/v1/messages/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .content("""
                                {
                                  "recipientId": %s,
                                  "subject": "Reference request",
                                  "content": "Please confirm my rental history.",
                                  "messageType": "REFERENCE_REQUEST",
                                  "propertyId": %s
                                }
                                """.formatted(landlord.id(), propertyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Reference request"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long conversationId = extractLong(conversationResponse, "id");

        mvc.perform(get("/api/v1/messages/conversations")
                        .param("userId", String.valueOf(tenant.id()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlordToken)
                        .content("""
                                {
                                  "conversationId": %s,
                                  "content": "Reference confirmed."
                                }
                                """.formatted(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Reference confirmed."));

        mvc.perform(get("/api/v1/messages/conversations/{conversationId}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        mvc.perform(patch("/api/v1/messages/conversations/{conversationId}/read", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void propertyPassportReflectsAFreshUnverifiedPropertyWithNoHistory() throws Exception {
        Registered landlord = registerUser("Fresh Landlord", "LANDLORD");
        Registered agent = registerUser("Fresh Agent", "AGENT");
        long propertyId = createProperty(landlord.id(), agent.id(), "Newly Listed Cottage", landlord.token());

        mvc.perform(get("/api/v1/properties/{id}/passport", propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value((int) propertyId))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.timeline", hasSize(2)))
                .andExpect(jsonPath("$.timeline[*].type", hasItems("LISTED", "PHOTO_ADDED")))
                .andExpect(jsonPath("$.leaseHistory", empty()))
                .andExpect(jsonPath("$.maintenanceHistory", empty()))
                .andExpect(jsonPath("$.escrowHistory", empty()))
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.healthScore.verification").value(40));
    }

    @Test
    void tenantAndLandlordPassportsReflectHistoryAndEnforceTenantPassportAccessControl() throws Exception {
        Registered admin = bootstrapAdmin();
        Registered landlord = registerUser("Passport Landlord", "LANDLORD");
        Registered agent = registerUser("Passport Agent", "AGENT");
        // A diaspora user - not TENANT - must still be able to hold a lease and have a passport;
        // Permission.TENANT_APPLY (not the raw role) is what actually gates lease eligibility.
        Registered diasporaTenant = registerUser("Diaspora Tenant", "DIASPORA");
        Registered stranger = registerUser("Unrelated Tenant", "TENANT");

        long propertyId = createProperty(landlord.id(), agent.id(), "Passport Test Villa", landlord.token());
        mvc.perform(patch("/api/v1/properties/{id}/verify", propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifierId": %s,
                                  "note": "Verified for passport test"
                                }
                                """.formatted(agent.id())))
                .andExpect(status().isOk());

        long leaseId = createLease(propertyId, diasporaTenant.id(), landlord.token());
        mvc.perform(patch("/api/v1/leases/{id}/sign", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + diasporaTenant.token()))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/leases/{id}/sign", leaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlord.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));

        long maintenanceId = createMaintenance(propertyId, diasporaTenant.token());
        mvc.perform(patch("/api/v1/maintenance/{id}/status", maintenanceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlord.token())
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk());

        // The tenant can view their own passport.
        mvc.perform(get("/api/v1/users/{id}/tenant-passport", diasporaTenant.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + diasporaTenant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value((int) diasporaTenant.id()))
                .andExpect(jsonPath("$.completedLeaseCount").value(1))
                .andExpect(jsonPath("$.totalRentInvoices").value(0))
                .andExpect(jsonPath("$.onTimePaymentRatePercent").doesNotExist());

        // The landlord they have a lease with can view it too.
        mvc.perform(get("/api/v1/users/{id}/tenant-passport", diasporaTenant.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlord.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLeaseCount").value(1));

        // An admin can always view it.
        mvc.perform(get("/api/v1/users/{id}/tenant-passport", diasporaTenant.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk());

        // A tenant with no business relationship to this tenant cannot.
        mvc.perform(get("/api/v1/users/{id}/tenant-passport", diasporaTenant.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.token()))
                .andExpect(status().isForbidden());

        // The landlord passport is public and reflects the same history.
        mvc.perform(get("/api/v1/users/{id}/landlord-passport", landlord.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value((int) landlord.id()))
                .andExpect(jsonPath("$.propertyCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.completedLeaseCount").value(1))
                .andExpect(jsonPath("$.resolvedMaintenanceCount").value(1))
                .andExpect(jsonPath("$.totalMaintenanceCount").value(1))
                .andExpect(jsonPath("$.maintenanceResolutionRatePercent").value(100));
    }

    @Test
    void configuredCorsOriginsAllowFrontendDevelopmentHosts() throws Exception {
        mvc.perform(options("/api/v1/users")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:3001")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:3001"));
    }

    private Registered bootstrapAdmin() throws Exception {
        String email = uniqueEmail("admin");
        userService.create(new UserDtos.CreateUserRequest(
                "Platform Admin",
                email,
                "+26377" + String.format("%06d", sequence.incrementAndGet()),
                PASSWORD,
                "Zimbabwe",
                Set.of(UserRole.ADMIN)
        ));
        return new Registered(0L, loginToken(email));
    }

    private Registered registerUser(String fullName, String role) throws Exception {
        return registerUser(fullName, uniqueEmail(role.toLowerCase()), role);
    }

    private Registered registerUser(String fullName, String email, String role) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s",
                                  "phone": "+26377%06d",
                                  "password": "%s",
                                  "country": "Zimbabwe",
                                  "roles": ["%s"]
                                }
                                """.formatted(fullName, email, sequence.incrementAndGet(), PASSWORD, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Registered(extractLong(response, "id"), extractString(response, "token"));
    }

    private long createProperty(long landlordId, long agentId, String title, String landlordToken) throws Exception {
        String response = mvc.perform(post("/api/v1/properties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlordToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Created through the API for integration testing.",
                                  "listingType": "RENT",
                                  "city": "Harare",
                                  "suburb": "Borrowdale",
                                  "bedrooms": 2,
                                  "bathrooms": 2,
                                  "price": 550,
                                  "currency": "USD",
                                  "diasporaFriendly": true,
                                  "escrowRequired": true,
                                  "landlordId": %s,
                                  "agentId": %s,
                                  "photoUrls": ["https://cdn.example.test/%s.jpg"]
                                }
                                """.formatted(title, landlordId, agentId, title.toLowerCase().replaceAll("[^a-z0-9]+", "-"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.photoUrls[0]").exists())
                .andExpect(jsonPath("$.photos[0]").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createLease(long propertyId, long tenantId, String landlordToken) throws Exception {
        String response = mvc.perform(post("/api/v1/leases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + landlordToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %s,
                                  "tenantId": %s,
                                  "startDate": "2026-06-01",
                                  "endDate": "2027-05-31",
                                  "monthlyRent": 550,
                                  "depositAmount": 550,
                                  "currency": "USD",
                                  "terms": "API-created lease"
                                }
                                """.formatted(propertyId, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createEscrow(long propertyId, long leaseId, String payerToken) throws Exception {
        String response = mvc.perform(post("/api/v1/escrows")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + payerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %s,
                                  "leaseId": %s,
                                  "amount": 550,
                                  "currency": "USD",
                                  "purpose": "Deposit"
                                }
                                """.formatted(propertyId, leaseId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createPayment(long payeeId, long propertyId, long leaseId, String payerToken) throws Exception {
        String response = mvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + payerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payeeId": %s,
                                  "propertyId": %s,
                                  "leaseId": %s,
                                  "amount": 550,
                                  "currency": "USD",
                                  "provider": "manual",
                                  "purpose": "Rent payment"
                                }
                                """.formatted(payeeId, propertyId, leaseId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createMaintenance(long propertyId, String requesterToken) throws Exception {
        String response = mvc.perform(post("/api/v1/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %s,
                                  "category": "Electrical",
                                  "priority": "HIGH",
                                  "description": "Socket needs inspection"
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createReit(String adminToken) throws Exception {
        String response = mvc.perform(post("/api/v1/investments/reits")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Residential REIT",
                                  "description": "API-created investment product.",
                                  "market": "Zimbabwe",
                                  "unitPrice": 10,
                                  "projectedAnnualYield": 8.5,
                                  "riskLevel": "MEDIUM",
                                  "vexEligible": true,
                                  "totalUnits": 50000
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createInvestment(long reitId, String investorToken) throws Exception {
        String response = mvc.perform(post("/api/v1/investments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reitId": %s,
                                  "units": 25,
                                  "currency": "USD"
                                }
                                """.formatted(reitId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private String loginToken(String email) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractString(response, "token");
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + sequence.incrementAndGet() + "@example.com";
    }

    private void assertTimelineIsChronological(long propertyId) throws Exception {
        String response = mvc.perform(get("/api/v1/properties/{id}/passport", propertyId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode timeline = objectMapper.readTree(response).get("timeline");
        Instant previous = Instant.EPOCH;
        for (JsonNode event : timeline) {
            Instant occurredAt = Instant.parse(event.get("occurredAt").asString());
            assertFalse(occurredAt.isBefore(previous),
                    "Timeline events must be sorted chronologically, but " + event + " came after " + previous);
            previous = occurredAt;
        }
    }

    private long extractLong(String json, String field) {
        return Long.parseLong(json.replaceAll(".*\"" + field + "\":([0-9]+).*", "$1"));
    }

    private String extractString(String json, String field) {
        return json.replaceAll(".*\"" + field + "\":\"([^\"]+)\".*", "$1");
    }
}
