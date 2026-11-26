package com.example.estatex.analytics.report;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reports, scheduled delivery and saved views (Phase H). */
@RestController
@RequestMapping("/api/v1/admin/analytics")
class AnalyticsReportController {
    static final Set<String> PAGES = Set.of("overview", "market-rent", "market-sale", "market-map", "demand", "revenue", "insights", "reports");
    private static final Set<String> FREQUENCIES = Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");
    private static final int MAX_RECIPIENTS = 20;
    private static final int MAX_VIEWS_PER_USER = 100;

    private final ReportService reports;
    private final ReportBuilder builder;
    private final ReportScheduleRepository schedules;
    private final ReportRunRepository runs;
    private final SavedViewRepository views;
    private final UserRepository users;
    private final AuditService audit;
    private final BusinessTime time;
    private final ObjectMapper json;

    AnalyticsReportController(ReportService reports, ReportBuilder builder, ReportScheduleRepository schedules, ReportRunRepository runs,
                     SavedViewRepository views, UserRepository users, AuditService audit, BusinessTime time, ObjectMapper json) {
        this.reports = reports;
        this.builder = builder;
        this.schedules = schedules;
        this.runs = runs;
        this.views = views;
        this.users = users;
        this.audit = audit;
        this.time = time;
        this.json = json;
    }

    // ---- Catalogue and download ----------------------------------------------------------------

    record TypeView(String key, String label, String description, String grain, LocalDate defaultFrom, LocalDate defaultTo) {
    }

    record Catalogue(List<TypeView> types, boolean canSchedule, List<String> formats) {
    }

    @GetMapping("/reports")
    Catalogue catalogue(@AuthenticationPrincipal AppUser user) {
        requireExport(user);
        List<TypeView> out = new ArrayList<>();
        for (ReportType t : ReportType.values()) {
            if (!t.allowedFor(user)) continue;
            LocalDate[] r = builder.range(t, null, null);
            out.add(new TypeView(t.name(), t.label(), t.description(), t.grain().name(), r[0], r[1]));
        }
        return new Catalogue(out, user.hasPermission(Permission.REPORTS_SCHEDULE), List.of("PDF", "XLSX", "CSV"));
    }

    @GetMapping("/reports/{type}")
    ResponseEntity<byte[]> download(@PathVariable String type, @RequestParam(defaultValue = "PDF") String format,
                                    @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                    @RequestParam(required = false) String city, @RequestParam(required = false) String suburb,
                                    @RequestParam(required = false) String category, @AuthenticationPrincipal AppUser user) {
        requireExport(user);
        ReportType t = type(type);
        ReportFormat f = format(format);
        ReportService.Generated g = reports.download(new ReportBuilder.Request(t, from, to, cut(city, 80), cut(suburb, 80), category), f, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(g.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(g.contentType()))
                .body(g.content());
    }

    record RunView(Long id, Long scheduleId, String reportType, String reportLabel, String format, String trigger, Long requestedByUserId,
                   String requestedBy, Instant generatedAt, LocalDate periodFrom, LocalDate periodTo, String status, Long sizeBytes,
                   int recipients, String error) {
    }

    @GetMapping("/reports/runs")
    List<RunView> runs(@AuthenticationPrincipal AppUser user) {
        requireExport(user);
        Map<Long, String> names = new HashMap<>();
        List<RunView> out = new ArrayList<>();
        for (ReportRun r : runs.findAllByOrderByGeneratedAtDescIdDesc(PageRequest.of(0, 100))) {
            ReportType t = ReportType.valueOf(r.getReportType());
            if (!t.allowedFor(user)) continue;   // only runs of reports this person could produce themselves
            String by = r.getRequestedByUserId() == null ? null : names.computeIfAbsent(r.getRequestedByUserId(),
                    id -> users.findById(id).map(ReportService::name).orElse("user #" + id));
            out.add(new RunView(r.getId(), r.getScheduleId(), r.getReportType(), t.label(), r.getFormat(), r.getTrigger(),
                    r.getRequestedByUserId(), by, r.getGeneratedAt(), r.getPeriodFrom(), r.getPeriodTo(), r.getStatus(), r.getSizeBytes(),
                    r.getRecipients(), r.getError()));
            if (out.size() == 50) break;
        }
        return out;
    }

    // ---- Schedules -------------------------------------------------------------------------------

    record ScheduleRequest(@NotBlank @Size(max = 80) String name, @NotNull String reportType, @NotNull String format,
                           @NotNull String frequency, @Size(max = 80) String city, @Size(max = 80) String suburb, String category,
                           @NotNull List<Long> recipientIds, @NotNull Boolean enabled) {
    }

    record Recipient(Long id, String name, String email) {
    }

    record ScheduleView(Long id, String name, String reportType, String reportLabel, String format, String frequency, String city,
                        String suburb, String category, List<Recipient> recipients, boolean enabled, Instant nextRunAt, Instant lastRunAt,
                        String lastStatus, String lastError, Long ownerUserId, String owner) {
    }

    @GetMapping("/report-schedules")
    List<ScheduleView> schedules(@AuthenticationPrincipal AppUser user) {
        requireSchedule(user);
        return schedules.findAllByOrderByNameAsc().stream().map(this::view).toList();
    }

    @GetMapping("/report-recipients")
    List<Recipient> recipients(@RequestParam String type, @AuthenticationPrincipal AppUser user) {
        requireSchedule(user);
        return reports.eligibleRecipients(type(type)).stream().map(AnalyticsReportController::recipient).toList();
    }

    @PostMapping("/report-schedules")
    ScheduleView create(@Valid @RequestBody ScheduleRequest r, @AuthenticationPrincipal AppUser user) {
        requireSchedule(user);
        ReportSchedule s = new ReportSchedule();
        s.setOwnerUserId(user.getId());
        s.setCreatedAt(time.clock().instant());
        apply(s, r, user);
        schedules.save(s);
        audit.record(user, entry("REPORT_SCHEDULE_CREATED", "ReportSchedule", s.getId()).metadata(summary(s)));
        return view(s);
    }

    @PutMapping("/report-schedules/{id}")
    ScheduleView update(@PathVariable Long id, @Valid @RequestBody ScheduleRequest r, @AuthenticationPrincipal AppUser user) {
        requireSchedule(user);
        ReportSchedule s = schedules.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schedule not found"));
        Map<String, Object> before = summary(s);
        // Whoever edits a schedule becomes its owner: it then runs with their permissions.
        s.setOwnerUserId(user.getId());
        apply(s, r, user);
        schedules.save(s);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("before", before);
        meta.put("after", summary(s));
        audit.record(user, entry("REPORT_SCHEDULE_CHANGED", "ReportSchedule", id).metadata(meta));
        return view(s);
    }

    @DeleteMapping("/report-schedules/{id}")
    Map<String, Boolean> delete(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        requireSchedule(user);
        ReportSchedule s = schedules.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schedule not found"));
        schedules.delete(s);
        audit.record(user, entry("REPORT_SCHEDULE_DELETED", "ReportSchedule", id).metadata(summary(s)));
        return Map.of("deleted", true);
    }

    @PostMapping("/report-schedules/{id}/send-now")
    ReportService.Delivery sendNow(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        requireSchedule(user);
        return reports.deliver(id, "SEND_NOW", time.clock().instant());
    }

    private void apply(ReportSchedule s, ScheduleRequest r, AppUser user) {
        ReportType type = type(r.reportType());
        if (!type.allowedFor(user)) throw new ApiException(HttpStatus.FORBIDDEN, "Your role can't produce this report.");
        ReportFormat format = format(r.format());
        String frequency = r.frequency().toUpperCase(Locale.ROOT);
        if (!FREQUENCIES.contains(frequency)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose daily, weekly, monthly, quarterly or yearly.");
        if (r.recipientIds().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose at least one recipient.");
        if (r.recipientIds().size() > MAX_RECIPIENTS) throw new ApiException(HttpStatus.BAD_REQUEST, "At most " + MAX_RECIPIENTS + " recipients.");
        List<Long> allowed = reports.eligibleRecipients(type).stream().map(AppUser::getId).toList();
        for (Long id : r.recipientIds()) {
            // Reports only go to staff accounts that may see them, never to arbitrary addresses.
            if (!allowed.contains(id)) throw new ApiException(HttpStatus.BAD_REQUEST, "User #" + id + " may not receive this report.");
        }
        ReportBuilder.category(r.category());   // rejects an unknown category now, not at 06:00 on the first delivery
        boolean timingChanged = s.getFrequency() == null || !s.getFrequency().equals(frequency) || (!s.isEnabled() && r.enabled());
        s.setName(r.name().trim());
        s.setReportType(type.name());
        s.setFormat(format.name());
        s.setFrequency(frequency);
        s.setCity(blank(r.city()));
        s.setSuburb(s.getCity() == null ? null : blank(r.suburb()));
        s.setCategory(blank(r.category()) == null ? null : r.category().trim().toUpperCase(Locale.ROOT));
        s.setRecipientIds(String.join(",", r.recipientIds().stream().distinct().map(String::valueOf).toList()));
        s.setEnabled(r.enabled());
        Instant now = time.clock().instant();
        if (timingChanged || s.getNextRunAt() == null) s.setNextRunAt(reports.nextRun(Grain.valueOf(frequency), now));
        s.setUpdatedAt(now);
    }

    private ScheduleView view(ReportSchedule s) {
        List<Recipient> to = new ArrayList<>();
        for (Long id : ReportService.ids(s.getRecipientIds())) users.findById(id).ifPresent(u -> to.add(recipient(u)));
        ReportType t = ReportType.valueOf(s.getReportType());
        String owner = users.findById(s.getOwnerUserId()).map(ReportService::name).orElse("user #" + s.getOwnerUserId());
        return new ScheduleView(s.getId(), s.getName(), s.getReportType(), t.label(), s.getFormat(), s.getFrequency(), s.getCity(),
                s.getSuburb(), s.getCategory(), to, s.isEnabled(), s.getNextRunAt(), s.getLastRunAt(), s.getLastStatus(), s.getLastError(),
                s.getOwnerUserId(), owner);
    }

    private static Recipient recipient(AppUser u) {
        return new Recipient(u.getId(), ReportService.name(u), u.getEmail());
    }

    private static Map<String, Object> summary(ReportSchedule s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.getName());
        m.put("type", s.getReportType());
        m.put("format", s.getFormat());
        m.put("frequency", s.getFrequency());
        m.put("recipients", s.getRecipientIds());
        m.put("enabled", s.isEnabled());
        return m;
    }

    // ---- Saved views -----------------------------------------------------------------------------

    record ViewRequest(@NotBlank @Size(max = 80) String name, @NotBlank String page, @NotNull JsonNode filters, Boolean shared) {
    }

    record SavedViewDto(Long id, String name, String page, JsonNode filters, boolean shared, boolean mine, String owner, Instant updatedAt) {
    }

    @GetMapping("/saved-views")
    List<SavedViewDto> savedViews(@RequestParam String page, @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        String p = page(page);
        Map<Long, String> names = new HashMap<>();
        return views.visible(p, user.getId()).stream().map(v -> dto(v, user, names)).toList();
    }

    @PostMapping("/saved-views")
    SavedViewDto createView(@Valid @RequestBody ViewRequest r, @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        if (views.countByOwnerUserId(user.getId()) >= MAX_VIEWS_PER_USER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You have " + MAX_VIEWS_PER_USER + " saved views; delete some first.");
        }
        SavedView v = new SavedView();
        v.setOwnerUserId(user.getId());
        v.setCreatedAt(time.clock().instant());
        applyView(v, r);
        views.save(v);
        return dto(v, user, new HashMap<>());
    }

    @PutMapping("/saved-views/{id}")
    SavedViewDto updateView(@PathVariable Long id, @Valid @RequestBody ViewRequest r, @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        SavedView v = ownView(id, user);
        applyView(v, r);
        views.save(v);
        return dto(v, user, new HashMap<>());
    }

    @DeleteMapping("/saved-views/{id}")
    Map<String, Boolean> deleteView(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        views.delete(ownView(id, user));
        return Map.of("deleted", true);
    }

    private SavedView ownView(Long id, AppUser user) {
        SavedView v = views.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Saved view not found"));
        if (!v.getOwnerUserId().equals(user.getId())) {
            throw new ApiException(v.isShared() ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND,
                    v.isShared() ? "Only the person who saved this view can change it." : "Saved view not found");
        }
        return v;
    }

    private void applyView(SavedView v, ViewRequest r) {
        if (!r.filters().isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "Filters must be an object.");
        if (r.filters().size() > 30) throw new ApiException(HttpStatus.BAD_REQUEST, "Too many filters.");
        for (var e : r.filters().properties()) {
            JsonNode n = e.getValue();
            // Plain values only: a saved view stores filter settings, nothing else.
            if (!(n.isString() || n.isNumber() || n.isBoolean() || n.isNull()) || e.getKey().length() > 40) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Filter \"" + cut(e.getKey(), 40) + "\" must be a plain value.");
            }
        }
        String serialised = json.writeValueAsString(r.filters());
        if (serialised.length() > 2000) throw new ApiException(HttpStatus.BAD_REQUEST, "These filters are too long to save.");
        v.setName(r.name().trim());
        v.setPage(page(r.page()));
        v.setFiltersJson(serialised);
        v.setShared(Boolean.TRUE.equals(r.shared()));
        v.setUpdatedAt(time.clock().instant());
    }

    private SavedViewDto dto(SavedView v, AppUser user, Map<Long, String> names) {
        String owner = names.computeIfAbsent(v.getOwnerUserId(), id -> users.findById(id).map(ReportService::name).orElse("user #" + id));
        return new SavedViewDto(v.getId(), v.getName(), v.getPage(), json.readTree(v.getFiltersJson()), v.isShared(),
                v.getOwnerUserId().equals(user.getId()), owner, v.getUpdatedAt());
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private static String page(String page) {
        String p = page == null ? "" : page.trim().toLowerCase(Locale.ROOT);
        if (!PAGES.contains(p)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown page " + cut(page, 40));
        return p;
    }

    private static ReportType type(String t) {
        try {
            return ReportType.valueOf(t.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown report " + cut(t, 40));
        }
    }

    private static ReportFormat format(String f) {
        String u = f.toUpperCase(Locale.ROOT);
        if (u.equals("EXCEL")) u = "XLSX";
        try {
            return ReportFormat.valueOf(u);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose PDF, XLSX or CSV.");
        }
    }

    private static void requireExport(AppUser user) {
        if (user == null || !user.hasPermission(Permission.REPORTS_EXPORT)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your role can't export reports.");
        }
    }

    private static void requireSchedule(AppUser user) {
        if (user == null || !user.hasPermission(Permission.REPORTS_SCHEDULE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can schedule reports.");
        }
    }

    private static void requireAnalytics(AppUser user) {
        if (user == null || !(user.hasPermission(Permission.ANALYTICS_MARKET_VIEW) || user.hasPermission(Permission.ANALYTICS_DEMAND_VIEW)
                || user.hasPermission(Permission.ANALYTICS_OPS_VIEW) || user.hasPermission(Permission.ANALYTICS_FINANCE_VIEW))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to analytics.");
        }
    }

    private static String blank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
