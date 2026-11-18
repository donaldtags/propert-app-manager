package com.example.estatex.stay;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Short stays: calendar, quotes, requests vs instant book, double-booking protection, expiry. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StayBookingTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StayService stayService;
    @Autowired private StayBookingRepository bookingRepo;

    private TestAccounts accounts;
    private Account host;
    private Account guest;
    private long stayId;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        host = accounts.register("Host " + System.nanoTime(), UserRole.LANDLORD);
        guest = accounts.register("Guest " + System.nanoTime(), UserRole.TENANT);
        stayId = createStay(host, 80);
        call(put("/api/v1/stays/" + stayId + "/settings"), host, """
                {"maxGuests": 4, "minNights": 2, "maxNights": 14, "cleaningFee": 25, "checkInTime": "14:00",
                 "checkOutTime": "10:00", "instantBook": false, "houseRules": "No parties"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.maxGuests").value(4));
    }

    private LocalDate in(int days) {
        return LocalDate.now().plusDays(days);
    }

    private String bookBody(LocalDate in, LocalDate out, int guests) {
        return "{\"checkIn\": \"%s\", \"checkOut\": \"%s\", \"guests\": %d, \"message\": \"Hi!\"}".formatted(in, out, guests);
    }

    @Test
    void quoteAddsNightsAndCleaningAndChecksTheRules() throws Exception {
        mvc.perform(get("/api/v1/stays/" + stayId + "/quote").param("checkIn", in(10).toString())
                        .param("checkOut", in(13).toString()).param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.nights").value(3))
                .andExpect(jsonPath("$.accommodation").value(240.0))
                .andExpect(jsonPath("$.total").value(265.0));
        mvc.perform(get("/api/v1/stays/" + stayId + "/quote").param("checkIn", in(10).toString())
                        .param("checkOut", in(11).toString()).param("guests", "2"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.unavailableReason").value("This stay needs at least 2 nights"));
        mvc.perform(get("/api/v1/stays/" + stayId + "/quote").param("checkIn", in(10).toString())
                        .param("checkOut", in(12).toString()).param("guests", "5"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void requestToBookFlowHoldsDatesThenHostAcceptsAndMarksPaid() throws Exception {
        JsonNode booking = json(call(post("/api/v1/stays/" + stayId + "/bookings"), guest, bookBody(in(20), in(23), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(265.0))
                .andExpect(jsonPath("$.host.phone").doesNotExist()));
        long id = booking.get("id").asLong();

        // The nights now show as busy on the public calendar and can't be taken by someone else.
        mvc.perform(get("/api/v1/stays/" + stayId + "/availability").param("from", in(0).toString()).param("to", in(60).toString()))
                .andExpect(jsonPath("$[0].start").value(in(20).toString()))
                .andExpect(jsonPath("$[0].end").value(in(23).toString()));
        Account other = accounts.register("Other " + System.nanoTime(), UserRole.TENANT);
        call(post("/api/v1/stays/" + stayId + "/bookings"), other, bookBody(in(22), in(25), 1)).andExpect(status().isConflict());
        // ...but the checkout morning is free for the next guest.
        call(post("/api/v1/stays/" + stayId + "/bookings"), other, bookBody(in(23), in(25), 1))
                .andExpect(status().isOk());

        // Only the host decides.
        call(post("/api/v1/stays/bookings/" + id + "/accept"), guest, "{}").andExpect(status().isNotFound());
        call(post("/api/v1/stays/bookings/" + id + "/accept"), host, "{\"note\": \"Welcome!\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        call(post("/api/v1/stays/bookings/" + id + "/mark-paid"), host, "{\"method\": \"EcoCash\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paidVia").value("EcoCash"))
                .andExpect(jsonPath("$.host.email").exists());

        call(get("/api/v1/stays/bookings/mine"), guest, null).andExpect(jsonPath("$[0].reference").value(booking.get("reference").asText()));
        call(get("/api/v1/stays/hosting"), host, null)
                .andExpect(jsonPath("$.bookings", hasSize(2)))
                .andExpect(jsonPath("$.stays[0].settings.houseRules").value("No parties"));
    }

    @Test
    void instantBookSkipsTheHostAndCancellingReleasesTheDates() throws Exception {
        call(put("/api/v1/stays/" + stayId + "/settings"), host, """
                {"maxGuests": 4, "minNights": 1, "maxNights": 14, "cleaningFee": 0, "checkInTime": "15:00",
                 "checkOutTime": "11:00", "instantBook": true}
                """).andExpect(status().isOk());
        long id = json(call(post("/api/v1/stays/" + stayId + "/bookings"), guest, bookBody(in(5), in(6), 1))
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.total").value(80.0))).get("id").asLong();
        call(post("/api/v1/stays/bookings/" + id + "/cancel"), guest, "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/stays/" + stayId + "/availability").param("from", in(0).toString()).param("to", in(30).toString()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void hostsCanBlockDatesAndSearchHidesBusyStays() throws Exception {
        long blockId = json(call(post("/api/v1/stays/" + stayId + "/blocks"), host,
                "{\"start\": \"%s\", \"end\": \"%s\", \"note\": \"Family visit\"}".formatted(in(40), in(45)))
                .andExpect(status().isOk())).get("id").asLong();
        call(post("/api/v1/stays/" + stayId + "/blocks"), guest,
                "{\"start\": \"%s\", \"end\": \"%s\"}".formatted(in(50), in(52))).andExpect(status().isForbidden());
        call(post("/api/v1/stays/" + stayId + "/bookings"), guest, bookBody(in(42), in(44), 2)).andExpect(status().isConflict());

        mvc.perform(get("/api/v1/stays/search").param("checkIn", in(41).toString()).param("checkOut", in(43).toString()))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) stayId))));
        mvc.perform(get("/api/v1/stays/search").param("checkIn", in(60).toString()).param("checkOut", in(62).toString())
                        .param("guests", "3"))
                .andExpect(jsonPath("$[*].id", hasItem((int) stayId)));
        mvc.perform(get("/api/v1/stays/search").param("guests", "9"))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) stayId))));

        call(delete("/api/v1/stays/" + stayId + "/blocks/" + blockId), host, null).andExpect(status().isNoContent());
        call(post("/api/v1/stays/" + stayId + "/bookings"), guest, bookBody(in(42), in(44), 2)).andExpect(status().isOk());
    }

    @Test
    void rulesForGuestsAndOwners() throws Exception {
        call(post("/api/v1/stays/" + stayId + "/bookings"), host, bookBody(in(5), in(8), 1)).andExpect(status().isBadRequest());
        call(post("/api/v1/stays/" + stayId + "/bookings"), guest, bookBody(in(-2), in(1), 1)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/stays/" + stayId + "/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(in(5), in(8), 1))).andExpect(status().isUnauthorized());
        // Someone else's booking is invisible.
        long id = json(call(post("/api/v1/stays/" + stayId + "/bookings"), guest, bookBody(in(70), in(72), 1))).get("id").asLong();
        Account stranger = accounts.register("Stranger " + System.nanoTime(), UserRole.TENANT);
        call(get("/api/v1/stays/bookings/" + id), stranger, null).andExpect(status().isNotFound());
        call(post("/api/v1/stays/bookings/" + id + "/cancel"), stranger, "{}").andExpect(status().isNotFound());
        // RENT listings aren't bookable stays.
        long rent = createListing(accounts.register("Landlord " + System.nanoTime(), UserRole.LANDLORD), "RENT", 500);
        mvc.perform(get("/api/v1/stays/" + rent)).andExpect(status().isNotFound());
    }

    @Test
    void twoGuestsRacingForTheSameNightsOnlyOneWins() throws Exception {
        List<Account> racers = new ArrayList<>();
        for (int i = 0; i < 6; i++) racers.add(accounts.register("Racer " + i + " " + System.nanoTime(), UserRole.TENANT));
        ExecutorService pool = Executors.newFixedThreadPool(racers.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Account racer : racers) {
                Callable<Integer> task = () -> call(post("/api/v1/stays/" + stayId + "/bookings"), racer, bookBody(in(90), in(93), 1))
                        .andReturn().getResponse().getStatus();
                results.add(pool.submit(task));
            }
            int ok = 0;
            for (Future<Integer> r : results) if (r.get() == 200) ok++;
            assertEquals(1, ok, "exactly one guest gets the nights");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void unansweredRequestsExpireAndFreeTheCalendar() throws Exception {
        long id = json(call(post("/api/v1/stays/" + stayId + "/bookings"), guest, bookBody(in(30), in(33), 2))).get("id").asLong();
        StayBooking b = bookingRepo.findById(id).orElseThrow();
        b.setHoldExpiresAt(java.time.Instant.now().minusSeconds(60));
        bookingRepo.save(b);
        stayService.housekeeping();
        assertEquals(StayBookingStatus.EXPIRED, bookingRepo.findById(id).orElseThrow().getStatus());
        mvc.perform(get("/api/v1/stays/" + stayId + "/availability").param("from", in(0).toString()).param("to", in(60).toString()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void busyRangesMerge() {
        List<StayDtos.BusyRange> merged = StayService.merge(List.of(
                new StayDtos.BusyRange(in(5), in(8)), new StayDtos.BusyRange(in(1), in(3)),
                new StayDtos.BusyRange(in(8), in(10)), new StayDtos.BusyRange(in(6), in(7))));
        assertEquals(List.of(new StayDtos.BusyRange(in(1), in(3)), new StayDtos.BusyRange(in(5), in(10))), merged);
    }

    // ---------------------------------------------------------------- helpers

    private long createStay(Account owner, int nightly) throws Exception {
        return createListing(owner, "SHORT_STAY", nightly);
    }

    private long createListing(Account owner, String type, int price) throws Exception {
        String body = """
                {"title": "Garden cottage %s", "description": "Quiet cottage", "listingType": "%s", "city": "Harare",
                 "suburb": "Avondale", "bedrooms": 1, "bathrooms": 1, "price": %d, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d,
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), type, price, owner.id());
        return json(call(post("/api/v1/properties"), owner, body).andExpect(status().isOk())).get("id").asLong();
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
