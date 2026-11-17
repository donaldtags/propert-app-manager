package com.example.primenestprop.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.bootstrap.admin-emails=QA-Admin@Example.com, other@example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBootstrapTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    private String register(String email, String phone) throws Exception {
        String body = """
                {"fullName": "QA", "email": "%s", "phone": "%s", "password": "AfricaProp123!", "country": "Zimbabwe", "roles": ["AGENT"]}
                """.formatted(email, phone);
        String raw = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(raw).path("token").asString();
    }

    @Test
    void listedEmailBecomesAdminOthersDoNot() throws Exception {
        String admin = register("qa-admin@example.com", "+263710000901");
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.roles", Matchers.hasItem("ADMIN")));
        // ...and can do admin-only things, e.g. create an organisation.
        mvc.perform(post("/api/v1/orgs").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slug\": \"qa-bootstrap-org\", \"name\": \"QA\"}"))
                .andExpect(status().isOk());

        String normal = register("someone-else@example.com", "+263710000902");
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + normal))
                .andExpect(jsonPath("$.roles", Matchers.not(Matchers.hasItem("ADMIN"))));
    }
}
