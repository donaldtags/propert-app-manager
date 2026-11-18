package com.example.estatex.pipeline;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.support.AgencyFixture;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Phase 2: contacts, pipeline, intake, campaign, viewings, offers, seller report, share image. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseTwoTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;

    private AgencyFixture f;
    private Account negotiator;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        f = new AgencyFixture(mvc, new TestAccounts(mvc, userService, objectMapper));
        negotiator = f.negotiator;
        base = f.base;
    }

    private LocalDate in(int days) {
        return LocalDate.now().plusDays(days);
    }

    // -- contacts ---------------------------------------------------------------------------

    @Test
    void contactsMatchOnPhoneHowEverItsTypedAndEnquiriesAutoLink() throws Exception {
        long rudo = f.contact("Rudo Moyo", "0772 123 456");
        f.call(post(base + "/contacts"), negotiator, "{\"fullName\": \"Dup\", \"phone\": \"+263 772 123456\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Rudo Moyo")));
        f.call(get(base + "/contacts?q=263772123456"), negotiator, null)
                .andExpect(jsonPath("$[*].id", hasItem((int) rudo)));

        // Enquiry from a known number lands on the same contact and marks her a buyer.
        f.call(post(base + "/enquiries"), negotiator, """
                {"channel": "WHATSAPP", "intent": "BUY", "name": "Rudo M", "phone": "263772123456", "message": "3 bed?"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.contactId").value(rudo));
        // Unknown number creates a new contact with the source and type from the enquiry.
        long newId = f.json(f.call(post(base + "/enquiries"), negotiator, """
                {"channel": "INSTAGRAM", "intent": "RENT", "name": "Tafadzwa N", "phone": "0773 999 000"}
                """)).path("contactId").asLong();
        f.call(get(base + "/contacts/" + newId), negotiator, null)
                .andExpect(jsonPath("$.contact.source").value("INSTAGRAM"))
                .andExpect(jsonPath("$.contact.types", hasItem("TENANT")))
                .andExpect(jsonPath("$.activity[0].kind").value("ENQUIRY"));
        f.call(get(base + "/contacts/" + rudo), negotiator, null)
                .andExpect(jsonPath("$.contact.types", hasItem("BUYER")))
                .andExpect(jsonPath("$.activity", hasSize(1)));

        // Qualification: qualified once all five answers are in.
        f.call(put(base + "/contacts/" + rudo + "/qualification"), negotiator, """
                {"budgetCents": 20000000, "timeline": "WITHIN_3_MONTHS", "financing": "CASH", "preferredAreas": "Greendale, Highlands"}
                """).andExpect(jsonPath("$.qualified").value(false));
        f.call(put(base + "/contacts/" + rudo + "/qualification"), negotiator, "{\"decisionMaker\": true}")
                .andExpect(jsonPath("$.qualified").value(true))
                .andExpect(jsonPath("$.qualifiedAt").exists());
    }

    // -- intake -----------------------------------------------------------------------------

    @Test
    void titleCantBeVerifiedUntilTheChecklistForItsTypeIsComplete() throws Exception {
        long id = f.publishedListing("Checklist home", 20_000_000, 18_000_000, in(60));
        f.call(put(base + "/listings/" + id + "/verification-checklist"), negotiator, "{\"checked\": [\"CESSION_CHAIN\"]}")
                .andExpect(status().isBadRequest());
        // Unticking an item on a verified title drops it back to in-progress and takes the live listing down.
        f.call(put(base + "/listings/" + id + "/verification-checklist"), negotiator, "{\"checked\": [\"TITLE_DEED_SIGHTED\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliance.titleVerificationStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.property.status").value("DRAFT"));
    }

    @Test
    void inspectionSavesRefusesStaleOfflineEditsAndStoresPhotosOnce() throws Exception {
        long id = f.draftListing("Inspected home", "Avondale", 3, 18_000_000);
        f.call(get(base + "/listings/" + id + "/inspection"), negotiator, null)
                .andExpect(jsonPath("$.items[0].key").value("ROOF"));
        JsonNode saved = f.json(f.call(put(base + "/listings/" + id + "/inspection"), negotiator, """
                {"items": [{"key": "ROOF", "label": "Roof", "condition": "POOR", "note": "Leaks over kitchen"},
                           {"key": "WATER", "label": "Water", "condition": "GOOD"}], "standSizeM2": 1200}
                """).andExpect(status().isOk()));
        assertEquals(1, saved.path("poorItems").asInt());
        long v = saved.path("version").asLong();
        f.call(put(base + "/listings/" + id + "/inspection"), negotiator, """
                {"items": [], "baseVersion": %d}
                """.formatted(v)).andExpect(status().isOk());
        f.call(put(base + "/listings/" + id + "/inspection"), negotiator, """
                {"items": [], "baseVersion": %d}
                """.formatted(v)).andExpect(status().isConflict()); // stale: server wins

        String clientId = UUID.randomUUID().toString();
        MockMultipartFile photo = new MockMultipartFile("file", "roof.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2});
        for (int i = 0; i < 2; i++) {
            mvc.perform(multipart(base + "/listings/" + id + "/inspection/photos").file(photo)
                            .param("clientId", clientId).param("itemKey", "ROOF")
                            .header(HttpHeaders.AUTHORIZATION, negotiator.bearer()))
                    .andExpect(status().isOk());
        }
        JsonNode inspection = f.json(f.call(get(base + "/listings/" + id + "/inspection"), negotiator, null));
        assertEquals(1, inspection.path("photos").size());
        long photoId = inspection.path("photos").get(0).path("id").asLong();
        byte[] body = f.call(get(base + "/inspection-photos/" + photoId), negotiator, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2}, body);
        mvc.perform(get(base + "/inspection-photos/" + photoId)).andExpect(status().isUnauthorized());
    }

    @Test
    void pricingUsesComparableAskingPricesInTheSameSuburb() throws Exception {
        // One landlord per comparable: independent landlords' free plan limits how many they can list.
        for (int price : new int[]{150_000, 180_000, 200_000, 400_000}) {
            Account landlord = f.accounts.register("Independent Landlord " + price, UserRole.LANDLORD);
            f.call(post("/api/v1/properties"), landlord, """
                    {"title": "Comp %d", "listingType": "SALE", "city": "Harare", "suburb": "Mabelreign",
                     "bedrooms": 3, "bathrooms": 2, "price": %d, "currency": "USD", "landlordId": %d}
                    """.formatted(price, price, landlord.id())).andExpect(status().isOk());
        }
        long id = f.draftListing("Subject", "Mabelreign", 3, 19_000_000);
        f.call(get(base + "/listings/" + id + "/pricing"), negotiator, null)
                .andExpect(jsonPath("$.enoughData").value(true))
                .andExpect(jsonPath("$.basis", containsString("not achieved")))
                .andExpect(jsonPath("$.suggestedCents").exists());
        f.call(post(base + "/listings/" + id + "/pricing"), negotiator, "{\"recommendedCents\": 18500000, \"rationale\": \"Mid-range\"}")
                .andExpect(jsonPath("$.recommendedCents").value(18500000));
        f.call(get(base + "/listings/" + id + "/pricing"), negotiator, null)
                .andExpect(jsonPath("$.latest.recommendedCents").value(18500000));
    }

    // -- viewings, offers, pipeline ---------------------------------------------------------

    @Test
    void offersStayWithinTheMandatesAuthority() throws Exception {
        long draft = f.draftListing("No mandate", "Eastlea", 3, 10_000_000);
        long buyer = f.contact("Buyer One", "0771 222 333");
        f.call(post(base + "/listings/" + draft + "/offers"), negotiator, "{\"buyerContactId\": %d, \"amountCents\": 9000000}".formatted(buyer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("no authority")));

        long id = f.publishedListing("Offer home", 25_000_000, 23_000_000, in(60));
        JsonNode low = f.json(f.call(post(base + "/listings/" + id + "/offers"), negotiator,
                "{\"buyerContactId\": %d, \"amountCents\": 22000000, \"financing\": \"CASH\"}".formatted(buyer)).andExpect(status().isOk()));
        assertEquals(false, low.path("withinAuthority").asBoolean());
        long lowId = low.path("id").asLong();
        f.call(post(base + "/offers/" + lowId + "/present"), negotiator, "{\"via\": \"WHATSAPP\"}")
                .andExpect(jsonPath("$.status").value("PRESENTED"));
        // Can't accept or counter below the owner's minimum without the owner's own written instruction.
        f.call(post(base + "/offers/" + lowId + "/owner-response"), negotiator, "{\"decision\": \"ACCEPT\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("below the owner's minimum")));
        f.call(post(base + "/offers/" + lowId + "/owner-response"), negotiator, "{\"decision\": \"COUNTER\", \"counterAmountCents\": 22500000}")
                .andExpect(status().isConflict());
        f.call(post(base + "/offers/" + lowId + "/owner-response"), negotiator,
                        "{\"decision\": \"ACCEPT\", \"ownerInstructionReference\": \"WhatsApp from owner 17 Nov 14:02\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        f.call(get("/api/v1/properties/" + id), negotiator, null).andExpect(jsonPath("$.status").value("RESERVED"));

        long other = f.json(f.call(post(base + "/listings/" + id + "/offers"), negotiator,
                "{\"buyerContactId\": %d, \"amountCents\": 24000000}".formatted(f.contact("Buyer Two", "0771 444 555"))))
                .path("id").asLong();
        f.call(post(base + "/offers/" + other + "/owner-response"), negotiator, "{\"decision\": \"ACCEPT\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("already been accepted")));

        // Accepted offer falls through: listing goes back on the market (every gate still met).
        f.call(post(base + "/offers/" + lowId + "/withdraw"), negotiator, "{\"note\": \"Finance fell through\"}")
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.events[*].type", hasItem("WITHDRAWN")));
        f.call(get("/api/v1/properties/" + id), negotiator, null).andExpect(jsonPath("$.status").value("AVAILABLE"));
        f.call(get(base + "/audit"), f.prea, null)
                .andExpect(jsonPath("$[*].action", hasItem("OFFER_ACCEPTED")))
                .andExpect(jsonPath("$[?(@.action == 'OFFER_ACCEPTED')].metadataJson", hasItem(containsString("WhatsApp from owner"))));
    }

    @Test
    void pipelineStagesNeedRealEvidence() throws Exception {
        long owner = f.contact("Owner Prospect", "0772 555 666");
        long card = f.json(f.call(post(base + "/pipeline"), negotiator, """
                {"clientId": "%s", "title": "Mrs Prospect, Greendale", "ownerContactId": %d, "estimatedValueCents": 25000000}
                """.formatted(UUID.randomUUID(), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PROSPECT"))).path("id").asLong();
        String move = base + "/pipeline/" + card + "/move";
        f.call(post(move), negotiator, "{\"stage\": \"MEETING\"}").andExpect(jsonPath("$.stage").value("MEETING"));
        f.call(post(move), negotiator, "{\"stage\": \"MANDATE_SIGNED\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("Link the listing")));

        long draft = f.draftListing("Unpublished", "Greendale", 3, 25_000_000);
        f.call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(base + "/pipeline/" + card), negotiator,
                "{\"propertyId\": %d}".formatted(draft)).andExpect(status().isOk());
        f.call(post(move), negotiator, "{\"stage\": \"MANDATE_SIGNED\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("no signed-off mandate")));

        long live = f.publishedListing("Pipeline home", 25_000_000, 0, in(60));
        f.call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(base + "/pipeline/" + card), negotiator,
                "{\"propertyId\": %d}".formatted(live)).andExpect(status().isOk());
        f.call(post(move), negotiator, "{\"stage\": \"MARKETING\"}").andExpect(jsonPath("$.stage").value("MARKETING"));
        f.call(post(move), negotiator, "{\"stage\": \"CONVEYANCING\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("accepted offer")));
        f.call(post(move), negotiator, "{\"stage\": \"COMPLETED\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("conveyancer")));
        f.call(post(move), negotiator, "{\"stage\": \"LOST\"}").andExpect(status().isBadRequest());
        f.call(post(move), negotiator, "{\"stage\": \"LOST\", \"lostReason\": \"Owner sold privately\"}")
                .andExpect(jsonPath("$.stage").value("LOST"));
        f.call(get(base + "/contacts/" + owner), negotiator, null)
                .andExpect(jsonPath("$.contact.types", hasItem("OWNER")))
                .andExpect(jsonPath("$.activity[*].kind", hasItem("DEAL")));
        f.call(get(base + "/audit"), f.prea, null)
                .andExpect(jsonPath("$[?(@.action == 'PIPELINE_STAGE_CHANGED')].toState", hasItem("LOST")));
    }

    // -- campaign, viewings, seller report, share image -------------------------------------

    @Test
    void campaignViewingsAndTheWeeklySellerReport() throws Exception {
        long draft = f.draftListing("Not live", "Hatfield", 3, 15_000_000);
        f.call(post(base + "/listings/" + draft + "/campaign"), negotiator, null).andExpect(status().isConflict());

        long id = f.publishedListing("Report home", 25_000_000, 22_000_000, in(60));
        JsonNode campaign = f.json(f.call(post(base + "/listings/" + id + "/campaign"), negotiator, null).andExpect(status().isOk()));
        assertEquals(1, campaign.path("today").asInt());
        assertEquals(25, campaign.path("tasksTotal").asInt());
        long taskId = campaign.path("tasks").get(0).path("id").asLong();
        for (int i = 0; i < 2; i++) { // setting state twice (offline replay) is harmless
            f.call(put(base + "/campaign-tasks/" + taskId), negotiator, "{\"done\": true}").andExpect(jsonPath("$.tasksDone").value(1));
        }
        f.call(post(base + "/listings/" + id + "/campaign"), negotiator, null)
                .andExpect(jsonPath("$.id").value(campaign.path("id").asLong())); // already running: same campaign

        // Enquiries on the listing count per channel.
        f.call(post(base + "/enquiries"), negotiator, """
                {"channel": "WHATSAPP", "name": "W", "phone": "0771 100 100", "propertyId": %d}
                """.formatted(id)).andExpect(status().isOk());
        f.call(post(base + "/enquiries"), negotiator, """
                {"channel": "INSTAGRAM", "name": "I", "phone": "0771 100 101", "propertyId": %d}
                """.formatted(id)).andExpect(status().isOk());
        f.call(get(base + "/listings/" + id + "/campaign"), negotiator, null)
                .andExpect(jsonPath("$.enquiriesByChannel.WHATSAPP").value(1))
                .andExpect(jsonPath("$.enquiriesTotal").value(2));

        // Three completed viewings, all "too high", earlier today.
        for (int i = 0; i < 3; i++) {
            long buyer = f.contact("Viewer " + i, "0778 00" + i + " 00" + i);
            String clientId = UUID.randomUUID().toString();
            String body = "{\"clientId\": \"%s\", \"contactId\": %d, \"scheduledAt\": \"%s\"}"
                    .formatted(clientId, buyer, Instant.now().minus(1, ChronoUnit.HOURS));
            long viewing = f.json(f.call(post(base + "/listings/" + id + "/viewings"), negotiator, body).andExpect(status().isOk())).path("id").asLong();
            assertEquals(viewing, f.json(f.call(post(base + "/listings/" + id + "/viewings"), negotiator, body)).path("id").asLong());
            f.call(put(base + "/viewings/" + viewing + "/outcome"), negotiator, """
                    {"status": "COMPLETED", "interest": "WARM", "priceOpinion": "TOO_HIGH", "feedback": "Lovely but pricey <b>&</b>"}
                    """).andExpect(jsonPath("$.status").value("COMPLETED"));
        }
        f.call(post(base + "/listings/" + draft + "/viewings"), negotiator,
                        "{\"contactId\": %d, \"scheduledAt\": \"%s\"}".formatted(f.contact("Nobody", "0778 999 111"), Instant.now()))
                .andExpect(status().isConflict()); // no mandate, no viewing

        JsonNode report = f.json(f.call(post(base + "/listings/" + id + "/seller-reports"), negotiator, null).andExpect(status().isOk()));
        assertEquals("REVIEW_DOWN", report.path("adviceKind").asString());
        String token = report.path("shareToken").asString();
        assertTrue(token.length() >= 40);
        byte[] pdf = mvc.perform(get("/api/v1/shared/seller-reports/" + token)) // no auth: the owner opens the link
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(Arrays.copyOf(pdf, 4)));
        mvc.perform(get("/api/v1/shared/seller-reports/not-a-real-token")).andExpect(status().isNotFound());

        byte[] png = f.call(get(base + "/listings/" + id + "/share-image.png"), negotiator, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(1080, img.getWidth());
        assertEquals(1080, img.getHeight());
        mvc.perform(get(base + "/listings/" + id + "/share-image.png")).andExpect(status().isUnauthorized());
    }
}
