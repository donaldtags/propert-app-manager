package com.example.estatex.analytics.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.support.TestAccounts;
import com.example.estatex.support.TestAccounts.Account;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Phase H: every report in every format, permissions, scheduled delivery and saved views. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReportService reports;
    @Autowired private BusinessTime time;

    private TestAccounts accounts;
    private Account admin;
    private final List<ReportService.OutgoingMail> sent = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mvc, userService, objectMapper);
        admin = accounts.platformAdmin();
        sent.clear();
        reports.mailOverride = sent::add;
        jdbc.update("delete from report_schedules");
    }

    @AfterEach
    void tearDown() {
        reports.mailOverride = null;
    }

    @Test
    void eachRoleSeesOnlyTheReportsItMayProduce() throws Exception {
        JsonNode all = json(call(get("/api/v1/admin/analytics/reports"), admin, null).andExpect(status().isOk()));
        assertThat(all.path("types")).hasSize(ReportType.values().length);
        assertThat(all.path("canSchedule").asBoolean()).isTrue();

        JsonNode analyst = json(call(get("/api/v1/admin/analytics/reports"), staff(UserRole.MARKET_ANALYST), null).andExpect(status().isOk()));
        assertThat(keys(analyst)).contains("WEEKLY_RENTAL", "AGENT_PERFORMANCE", "DAILY_PLATFORM").doesNotContain("REVENUE", "INVESTMENT_INTELLIGENCE");
        assertThat(analyst.path("canSchedule").asBoolean()).isFalse();
        Account finance = staff(UserRole.FINANCE_ADMIN);
        assertThat(keys(json(call(get("/api/v1/admin/analytics/reports"), finance, null)))).containsExactlyInAnyOrder("DAILY_PLATFORM", "REVENUE");
        call(get("/api/v1/admin/analytics/reports/WEEKLY_RENTAL?format=CSV"), finance, null).andExpect(status().isForbidden());
        // Operations staff can view analytics but not export it.
        call(get("/api/v1/admin/analytics/reports"), staff(UserRole.OPERATIONS_ADMIN), null).andExpect(status().isForbidden());
        call(get("/api/v1/admin/analytics/reports"), accounts.register("Tenant " + System.nanoTime(), UserRole.TENANT), null)
                .andExpect(status().isForbidden());

        // The default period is the last completed one.
        JsonNode weekly = type(all, "WEEKLY_RENTAL");
        LocalDate from = LocalDate.parse(weekly.path("defaultFrom").asString());
        assertThat(from.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(LocalDate.parse(weekly.path("defaultTo").asString())).isEqualTo(from.plusDays(6)).isBefore(time.today());
    }

    @Test
    void everyReportCarriesItsPeriodCoverageAndMethodInEveryFormat() throws Exception {
        for (ReportType t : ReportType.values()) {
            MockHttpServletResponse r = download(admin, t.name(), "CSV", "").andExpect(status().isOk()).andReturn().getResponse();
            String csv = r.getContentAsString(StandardCharsets.UTF_8);
            assertThat(csv).as(t.name()).startsWith("﻿" + t.label()).contains("Generated,", "Period,", "Filters,");
            assertThat(csv).as(t.name() + " coverage").contains("Data coverage\r\n");
            assertThat(csv).as(t.name() + " methodology").contains("Methodology\r\n");
            assertThat(r.getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment", ".csv");
        }
        for (String type : List.of("WEEKLY_RENTAL", "QUARTERLY_INTELLIGENCE", "REVENUE", "AGENT_PERFORMANCE")) {
            byte[] pdf = download(admin, type, "PDF", "").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
            byte[] xlsx = download(admin, type, "EXCEL", "").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertThat(ReportRendererTest.unzip(xlsx)).containsKey("xl/workbook.xml");
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'REPORT_EXPORTED'", Integer.class))
                .isGreaterThanOrEqualTo(ReportType.values().length + 8);
        assertThat(jdbc.queryForObject("select count(*) from report_runs where run_trigger = 'DOWNLOAD' and status = 'OK'", Integer.class))
                .isGreaterThanOrEqualTo(ReportType.values().length + 8);
    }

    @Test
    void periodsAndFiltersAreValidated() throws Exception {
        download(admin, "WEEKLY_RENTAL", "DOCX", "").andExpect(status().isBadRequest());
        download(admin, "WEEKLY_RENTAL", "CSV", "&from=2026-03-10&to=2026-03-01").andExpect(status().isBadRequest());
        download(admin, "WEEKLY_RENTAL", "CSV", "&from=2020-01-01&to=2026-01-01").andExpect(status().isBadRequest());
        download(admin, "WEEKLY_RENTAL", "CSV", "&category=castle").andExpect(status().isBadRequest());
        download(admin, "NO_SUCH_REPORT", "CSV", "").andExpect(status().isNotFound());
        String csv = download(admin, "MONTHLY_SALES", "CSV", "&from=2026-01-01&to=2026-01-31&city=Harare&category=house")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Period,1 Jan 2026 to 31 Jan 2026,2026-01-01,2026-01-31").contains("Filters,City: Harare; Category: House");
    }

    @Test
    void agentPerformanceCountsWhatHappenedOnTheirListings() throws Exception {
        Account agent = accounts.register("Rufaro Agent " + System.nanoTime(), UserRole.AGENT);
        String city = "Mutare" + System.nanoTime();
        long listing = createListing(agent, city);
        for (int i = 0; i < 3; i++) {
            jdbc.update("insert into property_inquiries (property_id, name, email, message, created_at) values (?, 'Buyer', 'b@example.com', 'Hi', ?)",
                    listing, Timestamp.from(Instant.now()));
        }
        LocalDate today = time.today();
        String csv = download(admin, "AGENT_PERFORMANCE", "CSV", "&from=" + today.minusDays(1) + "&to=" + today + "&city=" + city)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String row = csv.lines().filter(l -> l.startsWith("Rufaro Agent")).findFirst().orElseThrow();
        // Agent, id, live, verified live, new, enquiries, per live listing, viewings, leases, reported, verified.
        String[] cells = row.split(",");
        assertThat(cells[1]).isEqualTo(String.valueOf(agent.id()));
        assertThat(cells[4]).isEqualTo("1");
        assertThat(cells[5]).isEqualTo("3");
        assertThat(csv).contains("Agents with listings,1");
        assertThat(csv).doesNotContain("@example.com");   // contact details stay out of reports
    }

    @Test
    void schedulesOnlyGoToStaffAllowedToSeeTheReport() throws Exception {
        Account analyst = staff(UserRole.MARKET_ANALYST);
        Account finance = staff(UserRole.FINANCE_ADMIN);
        Account tenant = accounts.register("Tenant " + System.nanoTime(), UserRole.TENANT);
        String ok = schedule("Weekly rentals", "WEEKLY_RENTAL", "WEEK", analyst.id());
        call(post("/api/v1/admin/analytics/report-schedules"), analyst, ok).andExpect(status().isForbidden());
        call(post("/api/v1/admin/analytics/report-schedules"), admin, schedule("x", "WEEKLY_RENTAL", "WEEK", tenant.id()))
                .andExpect(status().isBadRequest());
        call(post("/api/v1/admin/analytics/report-schedules"), admin, schedule("x", "WEEKLY_RENTAL", "WEEK", finance.id()))
                .andExpect(status().isBadRequest());
        JsonNode recipients = json(call(get("/api/v1/admin/analytics/report-recipients?type=WEEKLY_RENTAL"), admin, null).andExpect(status().isOk()));
        assertThat(recipients.toString()).contains("\"id\":" + analyst.id()).doesNotContain("\"id\":" + finance.id() + ",");

        JsonNode created = json(call(post("/api/v1/admin/analytics/report-schedules"), admin, ok).andExpect(status().isOk()));
        long id = created.path("id").asLong();
        Instant next = Instant.parse(created.path("nextRunAt").asString());
        ZonedDateTime local = next.atZone(time.zone());
        assertThat(local.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(local.getHour()).isEqualTo(6);
        assertThat(next).isAfter(time.clock().instant());

        // Due: delivered once, even with two servers racing for it.
        jdbc.update("update report_schedules set next_run_at = ? where id = ?", Timestamp.from(time.clock().instant().minusSeconds(60)), id);
        Instant now = time.clock().instant();
        CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> reports.deliverDue(now));
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> reports.deliverDue(now));
        assertThat(a.get() + b.get()).isEqualTo(1);
        assertThat(sent).hasSize(1);
        ReportService.OutgoingMail mail = sent.get(0);
        assertThat(mail.subject()).startsWith("[EstateX] Weekly rental market report");
        assertThat(mail.fileName()).endsWith(".pdf");
        assertThat(new String(mail.attachment(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(reports.deliverDue(now)).isZero();
        JsonNode list = json(call(get("/api/v1/admin/analytics/report-schedules"), admin, null).andExpect(status().isOk()));
        assertThat(list.get(0).path("lastStatus").asString()).isEqualTo("OK");
        assertThat(Instant.parse(list.get(0).path("nextRunAt").asString())).isAfter(now);
        assertThat(jdbc.queryForObject("select count(*) from report_runs where schedule_id = ? and status = 'OK' and recipients = 1",
                Integer.class, id)).isEqualTo(1);

        // The analyst loses the role: nothing is sent any more, and the run says why.
        call(put("/api/v1/admin/staff/" + analyst.id()), admin, "{\"roles\": []}").andExpect(status().isOk());
        JsonNode skipped = json(call(post("/api/v1/admin/analytics/report-schedules/" + id + "/send-now"), admin, null).andExpect(status().isOk()));
        assertThat(skipped.path("status").asString()).isEqualTo("SKIPPED");
        assertThat(sent).hasSize(1);

        call(delete("/api/v1/admin/analytics/report-schedules/" + id), admin, null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action in ('REPORT_SCHEDULE_CREATED', 'REPORT_SCHEDULE_DELETED')",
                Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void deliveriesCoverThePeriodThatJustEnded() {
        Instant mondaySix = time.today().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atTime(6, 0).atZone(time.zone()).toInstant();
        LocalDate[] week = reports.periodFor(Grain.WEEK, mondaySix);
        assertThat(week[0]).isEqualTo(mondaySix.atZone(time.zone()).toLocalDate().minusDays(7));
        assertThat(week[1]).isEqualTo(mondaySix.atZone(time.zone()).toLocalDate().minusDays(1));
        // Just before 06:00 on a Monday, the delivery due is today's, for last week.
        assertThat(reports.nextRun(Grain.WEEK, mondaySix.minusSeconds(3600))).isEqualTo(mondaySix);
        assertThat(reports.nextRun(Grain.WEEK, mondaySix.plusSeconds(60))).isEqualTo(mondaySix.plusSeconds(7 * 86400));
        LocalDate[] month = reports.periodFor(Grain.MONTH, LocalDate.of(2026, 12, 1).atTime(6, 0).atZone(time.zone()).toInstant());
        assertThat(month).containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
    }

    @Test
    void savedViewsArePersonalUnlessShared() throws Exception {
        Account a = staff(UserRole.MARKET_ANALYST);
        Account b = staff(UserRole.MARKET_ANALYST);
        JsonNode mine = json(call(post("/api/v1/admin/analytics/saved-views"), a,
                "{\"name\": \"Harare 2-beds\", \"page\": \"market-rent\", \"filters\": {\"city\": \"Harare\", \"bedrooms\": 2}}")
                .andExpect(status().isOk()));
        assertThat(mine.path("filters").path("bedrooms").asInt()).isEqualTo(2);
        assertThat(mine.path("mine").asBoolean()).isTrue();
        assertThat(views(b, "market-rent")).isEmpty();

        long id = mine.path("id").asLong();
        call(put("/api/v1/admin/analytics/saved-views/" + id), a,
                "{\"name\": \"Harare 2-beds\", \"page\": \"market-rent\", \"filters\": {\"city\": \"Harare\", \"bedrooms\": 2}, \"shared\": true}")
                .andExpect(status().isOk());
        JsonNode seen = views(b, "market-rent");
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).path("mine").asBoolean()).isFalse();
        assertThat(views(b, "market-sale")).isEmpty();
        call(delete("/api/v1/admin/analytics/saved-views/" + id), b, null).andExpect(status().isForbidden());

        call(post("/api/v1/admin/analytics/saved-views"), a, "{\"name\": \"x\", \"page\": \"market-rent\", \"filters\": {\"city\": {\"$ne\": 1}}}")
                .andExpect(status().isBadRequest());
        call(post("/api/v1/admin/analytics/saved-views"), a, "{\"name\": \"x\", \"page\": \"users\", \"filters\": {}}")
                .andExpect(status().isBadRequest());
        call(delete("/api/v1/admin/analytics/saved-views/" + id), a, null).andExpect(status().isOk());
        assertThat(views(a, "market-rent")).isEmpty();
    }

    // ---------------------------------------------------------------------------------------

    private String schedule(String name, String type, String frequency, long recipient) {
        return """
                {"name": "%s", "reportType": "%s", "format": "PDF", "frequency": "%s", "recipientIds": [%d], "enabled": true}
                """.formatted(name, type, frequency, recipient);
    }

    private long createListing(Account landlord, String city) throws Exception {
        String body = """
                {"title": "Report test %s", "description": "d", "listingType": "RENT", "city": "%s", "suburb": "Dangamvura",
                 "address": "1 Test Road", "bedrooms": 2, "bathrooms": 1, "price": 400, "currency": "USD",
                 "escrowRequired": false, "landlordId": %d, "category": "FLAT",
                 "photoUrls": ["https://images.unsplash.com/photo-1?w=1200"]}
                """.formatted(System.nanoTime(), city, landlord.id());
        return json(call(post("/api/v1/properties"), admin, body).andExpect(status().isOk())).path("id").asLong();
    }

    private ResultActions download(Account who, String type, String format, String extra) throws Exception {
        return call(get("/api/v1/admin/analytics/reports/" + type + "?format=" + format + extra), who, null);
    }

    private JsonNode views(Account who, String page) throws Exception {
        return json(call(get("/api/v1/admin/analytics/saved-views?page=" + page), who, null).andExpect(status().isOk()));
    }

    private static List<String> keys(JsonNode catalogue) {
        List<String> out = new ArrayList<>();
        catalogue.path("types").forEach(t -> out.add(t.path("key").asString()));
        return out;
    }

    private static JsonNode type(JsonNode catalogue, String key) {
        for (JsonNode t : catalogue.path("types")) if (t.path("key").asString().equals(key)) return t;
        throw new AssertionError(key);
    }

    private Account staff(UserRole role) throws Exception {
        Account a = accounts.register(role.name() + " " + System.nanoTime(), UserRole.TENANT);
        call(put("/api/v1/admin/staff/" + a.id()), admin, "{\"roles\": [\"" + role.name() + "\"]}").andExpect(status().isOk());
        return a;
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, Account who, String body) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, who.bearer());
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }
}
