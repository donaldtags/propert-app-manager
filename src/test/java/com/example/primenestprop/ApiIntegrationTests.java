package com.example.primenestprop;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {
    private static final String PASSWORD = "AfricaProp123!";

    @Autowired
    private MockMvc mvc;

    private int sequence;

    @Test
    void apiCreatedDataSupportsFrontendCoreLists() throws Exception {
        long landlordId = registerUser("Landlord User", "LANDLORD");
        long tenantId = registerUser("Tenant User", "TENANT");
        long agentId = registerUser("Agent User", "AGENT");
        long investorId = registerUser("Investor User", "INVESTOR");

        mvc.perform(patch("/api/v1/users/{id}/verify", landlordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
        mvc.perform(patch("/api/v1/users/{id}/verify", agentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        long propertyId = createProperty(landlordId, agentId, "Borrowdale API Apartment");
        mvc.perform(patch("/api/v1/properties/{id}/verify", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifierId": %s,
                                  "note": "Verified after API creation"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        long leaseId = createLease(propertyId, tenantId);
        long escrowId = createEscrow(propertyId, leaseId, tenantId);
        mvc.perform(patch("/api/v1/escrows/{id}/fund", escrowId))
                .andExpect(status().isOk());
        long reitId = createReit();
        createInvestment(investorId, reitId);
        createPayment(tenantId, landlordId, propertyId, leaseId);
        createMaintenance(propertyId, tenantId);

        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
        mvc.perform(get("/api/v1/properties")
                        .param("listingType", "RENT")
                        .param("city", "Harare")
                        .param("maxPrice", "600")
                        .param("bedrooms", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/leases").param("tenantId", String.valueOf(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/escrows").param("userId", String.valueOf(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/payments").param("userId", String.valueOf(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/maintenance").param("propertyId", String.valueOf(propertyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/investments/reits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/investments").param("investorId", String.valueOf(investorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
        mvc.perform(get("/api/v1/dashboards/landlords/{landlordId}", landlordId))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboards/tenants/{tenantId}", tenantId))
                .andExpect(status().isOk());
    }

    @Test
    void authAndProfileEndpointsWorkForApiCreatedUsers() throws Exception {
        String email = uniqueEmail("tenant");
        long tenantId = registerUser("Tenant Auth", email, "TENANT");

        String loginResponse = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
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
        String landlordEmail = uniqueEmail("mutation-landlord");
        String tenantEmail = uniqueEmail("mutation-tenant");
        long landlordId = registerUser("Mutation Landlord", landlordEmail, "LANDLORD");
        long tenantId = registerUser("Mutation Tenant", tenantEmail, "TENANT");
        long agentId = registerUser("Mutation Agent", "AGENT");
        long propertyId = createProperty(landlordId, agentId, "Mount Pleasant API Studio");

        mvc.perform(patch("/api/v1/properties/{id}/verify", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifierId": %s,
                                  "note": "Verified in integration test"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        long leaseId = createLease(propertyId, tenantId);
        mvc.perform(patch("/api/v1/leases/{id}/sign", leaseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %s
                                }
                                """.formatted(tenantId)))
                .andExpect(status().isOk());

        long escrowId = createEscrow(propertyId, leaseId, tenantId);
        mvc.perform(patch("/api/v1/escrows/{id}/fund", escrowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FUNDED"));

        long paymentId = createPayment(tenantId, landlordId, propertyId, leaseId);
        mvc.perform(patch("/api/v1/payments/{id}/success", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"));

        long maintenanceId = createMaintenance(propertyId, tenantId);
        mvc.perform(patch("/api/v1/maintenance/{id}/status", maintenanceId)
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        String tenantToken = loginToken(tenantEmail);
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

        String landlordToken = loginToken(landlordEmail);
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

        mvc.perform(post("/api/v1/users/{id}/roles", tenantId)
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

        mvc.perform(post("/api/v1/users/{id}/admin-request", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value((int) tenantId))
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
                                """.formatted(landlordId, tenantId, propertyId, leaseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Responsive landlord"));

        mvc.perform(get("/api/v1/ratings").param("landlordId", String.valueOf(landlordId)))
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
                                """.formatted(landlordId, propertyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Reference request"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long conversationId = extractLong(conversationResponse, "id");

        mvc.perform(get("/api/v1/messages/conversations")
                        .param("userId", String.valueOf(tenantId))
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
    void configuredCorsOriginsAllowFrontendDevelopmentHosts() throws Exception {
        mvc.perform(options("/api/v1/users")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:3001")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:3001"));
    }

    private long registerUser(String fullName, String role) throws Exception {
        return registerUser(fullName, uniqueEmail(role.toLowerCase()), role);
    }

    private long registerUser(String fullName, String email, String role) throws Exception {
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
                                """.formatted(fullName, email, ++sequence, PASSWORD, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createProperty(long landlordId, long agentId, String title) throws Exception {
        String response = mvc.perform(post("/api/v1/properties")
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

    private long createLease(long propertyId, long tenantId) throws Exception {
        String response = mvc.perform(post("/api/v1/leases")
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

    private long createEscrow(long propertyId, long leaseId, long payerId) throws Exception {
        String response = mvc.perform(post("/api/v1/escrows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %s,
                                  "leaseId": %s,
                                  "payerId": %s,
                                  "amount": 550,
                                  "currency": "USD",
                                  "purpose": "Deposit"
                                }
                                """.formatted(propertyId, leaseId, payerId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createPayment(long payerId, long payeeId, long propertyId, long leaseId) throws Exception {
        String response = mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payerId": %s,
                                  "payeeId": %s,
                                  "propertyId": %s,
                                  "leaseId": %s,
                                  "amount": 550,
                                  "currency": "USD",
                                  "provider": "manual",
                                  "purpose": "Rent payment"
                                }
                                """.formatted(payerId, payeeId, propertyId, leaseId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createMaintenance(long propertyId, long requesterId) throws Exception {
        String response = mvc.perform(post("/api/v1/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %s,
                                  "requesterId": %s,
                                  "category": "Electrical",
                                  "priority": "HIGH",
                                  "description": "Socket needs inspection"
                                }
                                """.formatted(propertyId, requesterId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createReit() throws Exception {
        String response = mvc.perform(post("/api/v1/investments/reits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Residential REIT",
                                  "description": "API-created investment product.",
                                  "country": "Zimbabwe",
                                  "unitPrice": 10,
                                  "projectedYield": 8.5,
                                  "riskLevel": "MEDIUM",
                                  "vexEligible": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractLong(response, "id");
    }

    private long createInvestment(long investorId, long reitId) throws Exception {
        String response = mvc.perform(post("/api/v1/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "investorId": %s,
                                  "reitId": %s,
                                  "units": 25,
                                  "currency": "USD"
                                }
                                """.formatted(investorId, reitId)))
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
                                  "email": "%s",
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
        return prefix + "-" + System.nanoTime() + "-" + (++sequence) + "@example.com";
    }

    private long extractLong(String json, String field) {
        return Long.parseLong(json.replaceAll(".*\"" + field + "\":([0-9]+).*", "$1"));
    }

    private String extractString(String json, String field) {
        return json.replaceAll(".*\"" + field + "\":\"([^\"]+)\".*", "$1");
    }
}
