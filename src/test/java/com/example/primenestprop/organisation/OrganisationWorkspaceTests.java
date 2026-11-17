package com.example.primenestprop.organisation;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.primenestprop.support.TestAccounts;
import com.example.primenestprop.support.TestAccounts.Account;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganisationWorkspaceTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(mvc, userService, objectMapper);
    }

    @Test
    void estatexIsSeededWithFiveDivisionsBrandAndContactAndHidesInternalFields() throws Exception {
        mvc.perform(get("/api/v1/orgs/estatex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ESTATEX Group"))
                .andExpect(jsonPath("$.slogan").value("More Than Property. Building Possibilities."))
                .andExpect(jsonPath("$.divisions.length()").value(5))
                .andExpect(jsonPath("$.divisions", hasItems("PROPERTIES", "MANAGEMENT", "INTELLIGENCE", "DEVELOPMENTS", "INVESTMENTS")))
                .andExpect(jsonPath("$.whatsappNumber").value("263784542297"))
                .andExpect(jsonPath("$.instagramHandle").value("estatexgroup"))
                .andExpect(jsonPath("$.theme.lightPrimary").value("#0B1D32"))
                .andExpect(jsonPath("$.theme.lightSurface").value("#F5F2EA"))
                .andExpect(jsonPath("$.theme.headingFont").value("Montserrat"))
                .andExpect(jsonPath("$.theme.bodyFont").value("Inter"))
                .andExpect(jsonPath("$.timezone").value("Africa/Harare"))
                .andExpect(jsonPath("$.saleCommissionBps").doesNotExist())
                .andExpect(jsonPath("$.trustAccountReference").doesNotExist());
    }

    @Test
    void flywayHasAppliedTheBaselineMigration() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, applied);
    }

    /** New enums are stored via converters so adding a value later never trips a CHECK constraint. */
    @Test
    void newEstatexTablesHaveNoEnumCheckConstraints() {
        Integer checks = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_type = 'CHECK'
                  AND LOWER(table_name) IN ('organisations', 'organisation_memberships', 'locations')
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, checks);
    }

    @Test
    void membershipRolesPreaRulesAndAuditTrail() throws Exception {
        Account platformAdmin = accounts.platformAdmin();
        Account orgAdmin = accounts.register("Tariro Admin", UserRole.AGENT);
        Account prea = accounts.register("Nyasha Principal", UserRole.AGENT);
        Account negotiator = accounts.register("Farai Negotiator", UserRole.AGENT);
        Account outsider = accounts.register("Outside Person", UserRole.TENANT);
        Account secondPrea = accounts.register("Second Principal", UserRole.AGENT);

        String slug = TestAccounts.uniqueSlug("agency");
        perform(post("/api/v1/orgs"), platformAdmin, """
                {"slug": "%s", "name": "Test Agency", "divisions": ["PROPERTIES", "MANAGEMENT"]}
                """.formatted(slug)).andExpect(status().isOk());
        // Only platform admins create organisations.
        perform(post("/api/v1/orgs"), orgAdmin, """
                {"slug": "%s-x", "name": "Nope"}
                """.formatted(slug)).andExpect(status().isForbidden());

        // Platform admin bootstraps the workspace admin.
        long adminMembershipId = addMember(slug, platformAdmin, orgAdmin, "ORG_ADMIN");
        addMember(slug, orgAdmin, prea, "PREA");
        addMember(slug, orgAdmin, negotiator, "NEGOTIATOR");

        // One PREA per organisation.
        perform(post("/api/v1/orgs/" + slug + "/members"), orgAdmin, """
                {"email": "%s", "roles": ["PREA"]}
                """.formatted(secondPrea.email())).andExpect(status().isConflict());
        // Divisions must be enabled on the org.
        perform(post("/api/v1/orgs/" + slug + "/members"), orgAdmin, """
                {"email": "%s", "roles": ["ANALYST"], "divisions": ["INTELLIGENCE"]}
                """.formatted(secondPrea.email())).andExpect(status().isBadRequest());

        // The PREA is recorded on the organisation for EAC purposes.
        mvc.perform(get("/api/v1/orgs/" + slug)).andExpect(jsonPath("$.preaName").value("Nyasha Principal"));

        // Effective permissions: PREA-reserved only for the PREA, never for admins.
        perform(get("/api/v1/orgs/" + slug + "/me"), prea, null)
                .andExpect(jsonPath("$.permissions", hasItems("LISTING_APPROVE_PUBLISH", "MANDATE_SIGN_OFF")));
        perform(get("/api/v1/orgs/" + slug + "/me"), orgAdmin, null)
                .andExpect(jsonPath("$.permissions", not(hasItem("LISTING_APPROVE_PUBLISH"))));
        perform(get("/api/v1/orgs/" + slug + "/me"), platformAdmin, null)
                .andExpect(jsonPath("$.platformOverride").value(true))
                .andExpect(jsonPath("$.permissions", not(hasItem("MANDATE_SIGN_OFF"))));

        // Negotiators can see colleagues but not change settings or read the audit log.
        perform(get("/api/v1/orgs/" + slug + "/members"), negotiator, null).andExpect(status().isOk());
        perform(patch("/api/v1/orgs/" + slug), negotiator, """
                {"slogan": "hijacked"}
                """).andExpect(status().isForbidden());
        perform(get("/api/v1/orgs/" + slug + "/audit"), negotiator, null).andExpect(status().isForbidden());

        // Outsiders and anonymous callers get nothing.
        perform(get("/api/v1/orgs/" + slug + "/members"), outsider, null).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orgs/mine")).andExpect(status().isUnauthorized());

        // Settings: fonts are validated; internal fields visible to staff.
        perform(patch("/api/v1/orgs/" + slug), orgAdmin, """
                {"theme": {"headingFont": "Comic Sans"}}
                """).andExpect(status().isBadRequest());
        perform(patch("/api/v1/orgs/" + slug), orgAdmin, """
                {"slogan": "Homes you can trust", "trustAccountReference": "TA-001", "saleCommissionBps": 500}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.trustAccountReference").value("TA-001"));
        // Stale version is rejected instead of silently overwriting.
        perform(patch("/api/v1/orgs/" + slug), orgAdmin, """
                {"slogan": "stale write", "version": 0}
                """).andExpect(status().isConflict());

        // Can't remove the last workspace admin.
        perform(patch("/api/v1/orgs/" + slug + "/members/" + adminMembershipId), orgAdmin, """
                {"roles": ["NEGOTIATOR"]}
                """).andExpect(status().isConflict());

        // My workspaces.
        perform(get("/api/v1/orgs/mine"), negotiator, null)
                .andExpect(jsonPath("$[0].organisation.slug").value(slug))
                .andExpect(jsonPath("$[0].roles", hasItem("NEGOTIATOR")));

        // Audit trail captures approvals of roles and settings changes.
        perform(get("/api/v1/orgs/" + slug + "/audit"), prea, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("ORG_MEMBER_ADDED", "ORG_PREA_ASSIGNED", "ORG_SETTINGS_UPDATED")))
                .andExpect(jsonPath("$[?(@.action == 'ORG_PREA_ASSIGNED')].actorName", hasItem("Tariro Admin")));
    }

    private long addMember(String slug, Account actor, Account member, String role) throws Exception {
        String raw = perform(post("/api/v1/orgs/" + slug + "/members"), actor, """
                {"email": "%s", "roles": ["%s"]}
                """.formatted(member.email(), role))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return accounts.json(raw).path("id").asLong();
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                  Account account, String body) throws Exception {
        builder.header(HttpHeaders.AUTHORIZATION, account.bearer());
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder);
    }
}
