package com.example.estatex.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** A fresh agency (admin, PREA, negotiator) and helpers to take a listing through every gate. */
public final class AgencyFixture {
    public static final String TITLE_DEED_ITEMS =
            "\"TITLE_DEED_SIGHTED\", \"DEEDS_SEARCH\", \"OWNER_ID_MATCHES\", \"RATES_CLEARANCE\", \"NO_ENCUMBRANCES\"";

    public final MockMvc mvc;
    public final TestAccounts accounts;
    public final Account platformAdmin;
    public final Account orgAdmin;
    public final Account prea;
    public final Account negotiator;
    public final String slug;
    public final String base;

    public AgencyFixture(MockMvc mvc, TestAccounts accounts) throws Exception {
        this.mvc = mvc;
        this.accounts = accounts;
        platformAdmin = accounts.platformAdmin();
        orgAdmin = accounts.register("Org Admin", UserRole.AGENT);
        prea = accounts.register("Principal Agent", UserRole.AGENT);
        negotiator = accounts.register("Negotiator", UserRole.AGENT);
        slug = TestAccounts.uniqueSlug("p2");
        base = "/api/v1/orgs/" + slug;
        call(post("/api/v1/orgs"), platformAdmin, "{\"slug\": \"%s\", \"name\": \"Phase Two Agency\"}".formatted(slug))
                .andExpect(status().isOk());
        member(platformAdmin, orgAdmin, "ORG_ADMIN");
        member(orgAdmin, prea, "PREA");
        member(orgAdmin, negotiator, "NEGOTIATOR");
    }

    public void member(Account actor, Account who, String role) throws Exception {
        call(post(base + "/members"), actor, "{\"email\": \"%s\", \"roles\": [\"%s\"]}".formatted(who.email(), role))
                .andExpect(status().isOk());
    }

    public long draftListing(String title, String suburb, int bedrooms, long priceCents) throws Exception {
        return json(call(post(base + "/listings"), negotiator, """
                {"title": "%s", "listingType": "SALE", "city": "Harare", "suburb": "%s", "address": "1 Fixture Rd",
                 "bedrooms": %d, "bathrooms": 2, "priceCents": %d, "titleType": "TITLE_DEED"}
                """.formatted(title, suburb, bedrooms, priceCents)).andExpect(status().isOk()))
                .path("property").path("id").asLong();
    }

    /** Draft -> verified title, signed-off mandate (minimum price given), consent, PREA approval, published. */
    public long publishedListing(String title, long priceCents, long minimumCents, LocalDate expiry) throws Exception {
        long id = draftListing(title, "Greendale", 3, priceCents);
        call(put(base + "/listings/" + id + "/title"), negotiator, "{\"titleType\": \"TITLE_DEED\", \"titleReference\": \"DT 1/2000\"}")
                .andExpect(status().isOk());
        call(put(base + "/listings/" + id + "/verification-checklist"), negotiator, "{\"checked\": [" + TITLE_DEED_ITEMS + "]}")
                .andExpect(status().isOk());
        call(post(base + "/listings/" + id + "/title-verification"), prea, "{\"status\": \"VERIFIED\"}").andExpect(status().isOk());
        long mandateId = json(call(post(base + "/listings/" + id + "/mandates"), negotiator, """
                {"type": "SOLE", "startDate": "%s", "expiryDate": "%s", "ownerName": "Fixture Owner",
                 "ownerPhone": "0772 000 111"%s}
                """.formatted(LocalDate.now().minusDays(1), expiry,
                        minimumCents > 0 ? ", \"minimumPriceCents\": " + minimumCents : "")).andExpect(status().isOk())).path("id").asLong();
        mvc.perform(multipart(base + "/mandates/" + mandateId + "/signed-document")
                        .file(new MockMultipartFile("file", "m.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, negotiator.bearer()))
                .andExpect(status().isOk());
        call(post(base + "/mandates/" + mandateId + "/sign-off"), prea, null).andExpect(status().isOk());
        call(put(base + "/listings/" + id + "/consent"), negotiator, "{\"photos\": true, \"address\": true}").andExpect(status().isOk());
        call(post(base + "/listings/" + id + "/approve"), prea, null).andExpect(status().isOk());
        call(post(base + "/listings/" + id + "/publish"), negotiator, null).andExpect(status().isOk());
        return id;
    }

    public long contact(String name, String phone) throws Exception {
        return json(call(post(base + "/contacts"), negotiator, """
                {"fullName": "%s", "phone": "%s", "source": "NETWORK"}
                """.formatted(name, phone)).andExpect(status().isOk())).path("id").asLong();
    }

    public ResultActions call(MockHttpServletRequestBuilder builder, Account account, String body) throws Exception {
        if (account != null) {
            builder.header(HttpHeaders.AUTHORIZATION, account.bearer());
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder);
    }

    public JsonNode json(ResultActions result) throws Exception {
        return accounts.json(result.andReturn().getResponse().getContentAsString());
    }
}
