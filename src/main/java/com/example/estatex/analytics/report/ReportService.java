package com.example.estatex.analytics.report;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.auth.EmailService;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates reports (download, "send now" and scheduled delivery) and records every one. A
 * scheduled report is built with its owner's permissions and only sent to staff who may see it;
 * both are checked again at every delivery, so a role change takes effect immediately.
 */
@Service
public class ReportService {
    /** Scheduled reports go out at 06:00 (Harare) after the period they cover ends. */
    static final Duration DELIVERY_OFFSET = Duration.ofHours(6);
    static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportBuilder builder;
    private final ReportRunRepository runs;
    private final ReportScheduleRepository schedules;
    private final UserRepository users;
    private final EmailService email;
    private final AuditService audit;
    private final BusinessTime time;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public ReportService(ReportBuilder builder, ReportRunRepository runs, ReportScheduleRepository schedules, UserRepository users,
                         EmailService email, AuditService audit, BusinessTime time, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.builder = builder;
        this.runs = runs;
        this.schedules = schedules;
        this.users = users;
        this.email = email;
        this.audit = audit;
        this.time = time;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public record Generated(ReportDocument document, byte[] content, String fileName, String contentType) {
    }

    /** One outgoing email, as handed to the mailer. */
    record OutgoingMail(String to, String subject, String body, String fileName, byte[] attachment) {
    }

    /** Test hook: receives emails instead of the mail server. */
    volatile java.util.function.Consumer<OutgoingMail> mailOverride;

    /** A download. Audited, recorded, and only for people allowed to see the report. */
    public Generated download(ReportBuilder.Request request, ReportFormat format, AppUser user) {
        Generated g = generate(request, format, user, name(user));
        record(null, request.type(), format, "DOWNLOAD", user.getId(), g.document().from(), g.document().to(), "OK",
                (long) g.content().length, 0, null);
        audit.record(user, entry("REPORT_EXPORTED", "Report", null).metadata(Map.of("type", request.type().name(), "format", format.name(),
                "from", g.document().from().toString(), "to", g.document().to().toString(), "filters", g.document().filtersLabel())));
        return g;
    }

    Generated generate(ReportBuilder.Request request, ReportFormat format, AppUser user, String generatedBy) {
        ReportDocument doc = builder.build(request, user, generatedBy);
        byte[] bytes = ReportRenderer.render(doc, format, time.zone());
        return new Generated(doc, bytes, ReportRenderer.fileName(doc, format), format.contentType());
    }

    // ---- Schedules ------------------------------------------------------------------------------

    /** The first delivery: 06:00 after the period that is under way now ends. */
    public Instant nextRun(Grain frequency, Instant after) {
        ZoneId zone = time.zone();
        Period p = Period.containing(frequency, after, zone);
        Instant due = p.end().plus(DELIVERY_OFFSET);
        // Before 06:00 on the first day of a period, the previous period's report is still due today.
        Instant previousDue = p.start().plus(DELIVERY_OFFSET);
        return after.isBefore(previousDue) ? previousDue : due;
    }

    /** The period a delivery at {@code at} covers: the one that ended most recently. */
    public LocalDate[] periodFor(Grain frequency, Instant at) {
        ZoneId zone = time.zone();
        Period last = Period.containing(frequency, at.minus(DELIVERY_OFFSET), zone).previous(zone);
        return new LocalDate[]{last.start().atZone(zone).toLocalDate(), last.end().atZone(zone).toLocalDate().minusDays(1)};
    }

    /** Called by the scheduler: delivers every due schedule once, even with several servers running. */
    public int deliverDue(Instant now) {
        int delivered = 0;
        for (ReportSchedule s : schedules.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now)) {
            Instant due = s.getNextRunAt();
            Instant next = nextRun(Grain.valueOf(s.getFrequency()), now);
            // Claim it: only the server whose update succeeds sends it.
            int claimed = jdbc.update("update report_schedules set next_run_at = ? where id = ? and next_run_at = ? and enabled = ?",
                    java.sql.Timestamp.from(next), s.getId(), java.sql.Timestamp.from(due), true);
            if (claimed == 0) continue;
            deliver(s.getId(), "SCHEDULE", due);
            delivered++;
        }
        return delivered;
    }

    public record Delivery(String status, int recipients, String detail) {
    }

    /** Builds and emails one schedule's report for the period that ended before {@code at}. */
    public Delivery deliver(Long scheduleId, String trigger, Instant at) {
        ReportSchedule s = schedules.findById(scheduleId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Schedule not found"));
        ReportType type = ReportType.valueOf(s.getReportType());
        ReportFormat format = ReportFormat.valueOf(s.getFormat());
        LocalDate[] period = periodFor(Grain.valueOf(s.getFrequency()), at);
        Delivery result;
        Long size = null;
        AppUser owner = users.findById(s.getOwnerUserId()).orElse(null);
        if (owner == null || !type.allowedFor(owner)) {
            result = new Delivery("SKIPPED", 0, "The schedule's owner can no longer produce this report.");
        } else {
            List<AppUser> to = recipients(s, type);
            if (to.isEmpty()) {
                result = new Delivery("SKIPPED", 0, "None of the recipients may receive this report any more.");
            } else {
                try {
                    Generated g = generate(new ReportBuilder.Request(type, period[0], period[1], s.getCity(), s.getSuburb(), s.getCategory()),
                            format, owner, "scheduled delivery \"" + s.getName() + "\" (owner: " + name(owner) + ")");
                    size = (long) g.content().length;
                    int sent = 0;
                    List<String> failures = new ArrayList<>();
                    String body = g.document().title() + "\n" + g.document().periodLabel() + " (" + period[0] + " to " + period[1] + ")\n"
                            + "Filters: " + g.document().filtersLabel() + "\n\n"
                            + (g.content().length > MAX_ATTACHMENT_BYTES
                            ? "The report is too large to attach. Download it from the admin dashboard > Reports."
                            : "The report is attached.")
                            + "\n\nYou receive this because you're on the schedule \"" + s.getName() + "\". Coverage and method notes are in the report.";
                    for (AppUser u : to) {
                        try {
                            String subject = "[EstateX] " + g.document().title() + " - " + g.document().periodLabel();
                            boolean attach = g.content().length <= MAX_ATTACHMENT_BYTES;
                            if (mailOverride != null) {
                                mailOverride.accept(new OutgoingMail(u.getEmail(), subject, body, attach ? g.fileName() : null,
                                        attach ? g.content() : null));
                            } else if (!attach) {
                                email.send(u.getEmail(), "[EstateX] " + g.document().title() + " - " + g.document().periodLabel(), body);
                            } else {
                                email.sendWithAttachment(u.getEmail(), "[EstateX] " + g.document().title() + " - " + g.document().periodLabel(),
                                        body, g.fileName(), g.content(), format.contentType());
                            }
                            sent++;
                        } catch (RuntimeException e) {
                            failures.add(e.getMessage());
                        }
                    }
                    result = sent == 0 ? new Delivery("FAILED", 0, "Email failed: " + String.join("; ", failures))
                            : new Delivery("OK", sent, failures.isEmpty() ? null : failures.size() + " recipient(s) failed: " + String.join("; ", failures));
                } catch (RuntimeException e) {
                    log.warn("Scheduled report {} failed", s.getId(), e);
                    result = new Delivery("FAILED", 0, e.getMessage());
                }
            }
        }
        Delivery r = result;
        Long bytes = size;
        tx.executeWithoutResult(st -> {
            ReportSchedule fresh = schedules.findById(s.getId()).orElse(null);
            if (fresh != null) {
                fresh.setLastRunAt(time.clock().instant());
                fresh.setLastStatus(r.status());
                fresh.setLastError(cut(r.detail(), 500));
                schedules.save(fresh);
            }
            record(s.getId(), type, format, trigger, owner == null ? null : owner.getId(), period[0], period[1], r.status(), bytes,
                    r.recipients(), r.detail());
        });
        if (owner != null) {
            audit.record(owner, entry("REPORT_DELIVERED", "ReportSchedule", s.getId()).metadata(Map.of("status", r.status(),
                    "recipients", r.recipients(), "trigger", trigger, "from", period[0].toString(), "to", period[1].toString())));
        }
        return r;
    }

    /** The schedule's recipients who may still receive its report and have an email address. */
    List<AppUser> recipients(ReportSchedule s, ReportType type) {
        List<AppUser> out = new ArrayList<>();
        for (Long id : ids(s.getRecipientIds())) {
            users.findById(id).filter(type::allowedFor).filter(u -> u.getEmail() != null && !u.getEmail().isBlank()).ifPresent(out::add);
        }
        return out;
    }

    static List<Long> ids(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).distinct().toList();
    }

    /** Staff who may receive this report type: candidates for a schedule's recipient list. */
    public List<AppUser> eligibleRecipients(ReportType type) {
        Map<Long, AppUser> out = new LinkedHashMap<>();
        for (com.example.estatex.user.UserRole role : com.example.estatex.user.UserRole.values()) {
            if (!com.example.estatex.user.RolePermissions.of(role).contains(com.example.estatex.user.Permission.REPORTS_EXPORT)) continue;
            for (AppUser u : users.findByRolesContaining(role)) {
                if (type.allowedFor(u) && u.getEmail() != null && !u.getEmail().isBlank()) out.putIfAbsent(u.getId(), u);
            }
        }
        return new ArrayList<>(out.values());
    }

    private void record(Long scheduleId, ReportType type, ReportFormat format, String trigger, Long userId, LocalDate from, LocalDate to,
                        String status, Long size, int recipients, String error) {
        ReportRun run = new ReportRun();
        run.setScheduleId(scheduleId);
        run.setReportType(type.name());
        run.setFormat(format.name());
        run.setTrigger(trigger);
        run.setRequestedByUserId(userId);
        run.setGeneratedAt(time.clock().instant());
        run.setPeriodFrom(from);
        run.setPeriodTo(to);
        run.setStatus(status);
        run.setSizeBytes(size);
        run.setRecipients(recipients);
        run.setError(cut(error, 500));
        runs.save(run);
    }

    static String name(AppUser u) {
        return Optional.ofNullable(u.getFullName()).filter(n -> !n.isBlank()).orElse("user #" + u.getId());
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
