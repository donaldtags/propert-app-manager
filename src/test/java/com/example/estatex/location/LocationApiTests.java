package com.example.estatex.location;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocationApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LocationRepository locations;

    @Test
    void referenceDataCoversAllProvincesMajorCitiesAndBriefSuburbs() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(10, locations.countByTypeCode(LocationType.PROVINCE.name()));
        mvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'CITY')].name",
                        hasItems("Harare", "Bulawayo", "Chitungwiza", "Mutare", "Gweru", "Kwekwe", "Kadoma",
                                "Masvingo", "Chinhoyi", "Victoria Falls")))
                // Every suburb in the Propertyzone Q2 2026 reference table must resolve.
                .andExpect(jsonPath("$[?(@.city == 'Harare')].name",
                        hasItems("Glen View", "Mufakose", "Glen Norah", "Warren Park", "Budiriro", "Highfield",
                                "Hatfield", "Eastlea", "Mabelreign", "Greendale", "Marlborough", "Avondale",
                                "Mount Pleasant Heights", "Vainona", "Pomona", "Mount Pleasant", "Highlands",
                                "Borrowdale", "Borrowdale Brooke")))
                .andExpect(jsonPath("$[?(@.name == 'Borrowdale')].densityTier", hasItem("LOW_DENSITY")))
                .andExpect(jsonPath("$[?(@.name == 'Warren Park')].aliases[*]", hasItem("Warren Park 1")));
    }

    @Test
    void suggestionsAreDeduplicatedByAliasAndHiddenUntilAnAdminVerifiesThem() throws Exception {
        TestAccounts accounts = new TestAccounts(mvc, userService, objectMapper);
        Account user = accounts.register("Suggesting User", UserRole.LANDLORD);
        Account admin = accounts.platformAdmin();
        long harareId = locations.findAll().stream()
                .filter(l -> l.getType() == LocationType.CITY && l.getName().equals("Harare"))
                .findFirst().orElseThrow().getId();

        // An alias resolves to the existing verified suburb instead of creating a duplicate.
        mvc.perform(post("/api/v1/locations/suggestions").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cityId\": %d, \"name\": \"Malbereign\"}".formatted(harareId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mabelreign"))
                .andExpect(jsonPath("$.verified").value(true));

        String raw = mvc.perform(post("/api/v1/locations/suggestions").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cityId\": %d, \"name\": \"  Test   Gardens \"}".formatted(harareId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Gardens"))
                .andExpect(jsonPath("$.verified").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = accounts.json(raw).path("id").asLong();

        mvc.perform(get("/api/v1/locations")).andExpect(jsonPath("$[*].name", not(hasItem("Test Gardens"))));
        mvc.perform(patch("/api/v1/locations/" + id).header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verified\": true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/locations/" + id).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verified\": true, \"densityTier\": \"MEDIUM_DENSITY\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/locations")).andExpect(jsonPath("$[*].name", hasItem("Test Gardens")));
    }
}
