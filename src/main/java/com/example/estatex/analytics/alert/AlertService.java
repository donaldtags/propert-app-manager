package com.example.estatex.analytics.alert;

import com.example.estatex.analytics.insight.MarketObservation;
import com.example.estatex.analytics.metrics.MetricDefinition.Domain;
import com.example.estatex.auth.EmailService;
import com.example.estatex.notification.Notification;
import com.example.estatex.notification.NotificationRepository;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.RolePermissions;
import com.example.estatex.user.UserRepository;
import com.example.estatex.user.UserRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Raises alerts (Phase G). Three guards keep the noise down:
 * <ol>
 *   <li>Market rules only consider observations that already passed the significance test (or,
 *       for supply, the stock threshold), and then apply the rule's own threshold and minimum
 *       sample size.</li>
 *   <li>A dedupe key: the same finding (rule, place, period) is never raised twice.</li>
 *   <li>A cooldown: a rule stays quiet for a place for the configured hours after it fires.</li>
 * </ol>
 * At most {@value #MAX_PER_RULE_PER_RUN} alerts per rule per run; the rest are summarised.
 * Each alert goes to staff whose role can see its domain, in-app and/or by email per their
 * preferences.
 */
@Service
public class AlertService {
    static final int MAX_PER_RULE_PER_RUN = 5;
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRuleRepository rules;
    private final AnalyticsAlertRepository alerts;
    private final AlertPreferenceRepository preferences;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final EmailService email;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AlertService(AlertRuleRepository rules, AnalyticsAlertRepository alerts, AlertPreferenceRepository preferences,
                        UserRepository users, NotificationRepository notifications, EmailService email, JdbcTemplate jdbc,
                        TransactionTemplate tx) {
        this.rules = rules;
        this.alerts = alerts;
        this.preferences = preferences;
        this.users = users;
        this.notifications = notifications;
        this.email = email;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** The effective rule for every kind: the saved override, or the defaults. */
    public Map<AlertKind, AlertRule> rules() {
        Map<AlertKind, AlertRule> out = new EnumMap<>(AlertKind.class);
        for (AlertKind k : AlertKind.values()) out.put(k, k.defaults());
        for (AlertRule r : rules.findAll()) {
            try {
                out.put(AlertKind.valueOf(r.getRuleKey()), r);
            } catch (IllegalArgumentException ignored) {
                // a rule for a kind that no longer exists
            }
        }
        return out;
    }

    public AlertRule save(AlertRule r) {
        return rules.save(r);
    }

    public record Raised(int raised, int suppressed) {
    }

    /** After each run: alerts from the current observations and the data-quality check. */
    public Raised evaluate(List<MarketObservation> observations, int newDuplicatePairs, Long runId, Instant now) {
        Map<AlertKind, AlertRule> effective = rules();
        int raised = 0;
        int suppressed = 0;
        for (AlertKind kind : AlertKind.values()) {
            AlertRule rule = effective.get(kind);
            if (!rule.isEnabled() || kind.observationKinds().isEmpty()) continue;
            List<MarketObservation> matching = observations.stream()
                    .filter(o -> kind.observationKinds().contains(o.getKind()))
                    .filter(o -> passes(rule, o))
                    // strongest first, so the cap keeps the most important ones
                    .sorted(Comparator.comparing((MarketObservation o) -> o.getQValue() == null ? 1.0 : o.getQValue())
                            .thenComparing(o -> -Math.abs(o.getChangePct() == null ? 0 : o.getChangePct().doubleValue())))
                    .toList();
            int count = 0;
            for (MarketObservation o : matching) {
                if (count == MAX_PER_RULE_PER_RUN) {
                    suppressed += matching.size() - count;
                    break;
                }
                String subject = kind.name() + "|" + o.getDimsKey();
                String dedupe = subject + "|" + o.getGrain() + "|" + o.getPeriodStart().getEpochSecond();
                String title = kind.label() + where(o);
                if (raise(kind, rule, subject, dedupe, title, o.getStatement(), o.getId(), runId, now)) {
                    raised++;
                    count++;
                } else {
                    suppressed++;
                }
            }
        }
        AlertRule dup = effective.get(AlertKind.DUPLICATE_LISTINGS);
        if (dup.isEnabled() && newDuplicatePairs >= dup.getThreshold().intValue()) {
            String subject = AlertKind.DUPLICATE_LISTINGS.name();
            if (raise(AlertKind.DUPLICATE_LISTINGS, dup, subject, subject + "|run|" + runId,
                    newDuplicatePairs + " new possible duplicate listings",
                    newDuplicatePairs + " new pairs of listings look like the same property (same owner, suburb and bedrooms, "
                            + "price within 3% and a similar title or photo). Review them in the Analytics Engine data-quality list.",
                    null, runId, now)) raised++;
            else suppressed++;
        }
        return new Raised(raised, suppressed);
    }

    /** An analytics run failed after its retries. */
    public void jobFailed(String job, Long runId, String error, Instant now) {
        AlertRule rule = rules().get(AlertKind.JOB_FAILED);
        if (!rule.isEnabled()) return;
        String subject = AlertKind.JOB_FAILED.name() + "|" + job;
        raise(AlertKind.JOB_FAILED, rule, subject, subject + "|" + runId, "The " + job + " analytics run failed",
                "Run #" + runId + " failed after its retries" + (error == null ? "." : ": " + error)
                        + " The last good figures are still shown; the next run will include this window.", null, runId, now);
    }

    /** Called every 15 minutes: raises DATA_STALE when the last successful run is too old. */
    public void checkStale(Instant now) {
        AlertRule rule = rules().get(AlertKind.DATA_STALE);
        if (!rule.isEnabled()) return;
        Instant last = jdbc.query("select max(finished_at) from analytics_job_runs where status = 'SUCCEEDED'",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
        if (last == null) return;   // never run yet: nothing has gone stale
        Duration age = Duration.between(last, now);
        if (age.toMinutes() < rule.getThreshold().multiply(BigDecimal.valueOf(60)).longValue()) return;
        String subject = AlertKind.DATA_STALE.name();
        raise(AlertKind.DATA_STALE, rule, subject, subject + "|" + last.getEpochSecond(), "Analytics figures are stale",
                "The last successful analytics run finished " + age.toHours() + " hour(s) ago. Dashboards show the last good figures. "
                        + "Check the Analytics Engine page.", null, null, now);
    }

    static boolean passes(AlertRule rule, MarketObservation o) {
        if (o.getQValue() != null && o.getQValue() > rule.getMaxQValue()) return false;
        long sample = o.getSampleCurrent() + o.getSamplePrevious();
        if (sample < rule.getMinSample()) return false;
        // A rise from zero has no percentage; the significance test already vouched for it.
        return o.getChangePct() == null || o.getChangePct().abs().compareTo(rule.getThreshold()) >= 0;
    }

    private boolean raise(AlertKind kind, AlertRule rule, String subject, String dedupe, String title, String message,
                          Long observationId, Long runId, Instant now) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            if (alerts.existsByDedupeKey(dedupe)) return false;
            if (alerts.existsByRuleKeyAndSubjectKeyAndCreatedAtAfter(kind.name(), subject, now.minus(Duration.ofHours(rule.getCooldownHours())))) {
                return false;
            }
            AnalyticsAlert a = new AnalyticsAlert();
            a.setRuleKey(kind.name());
            a.setSeverity(rule.getSeverity());
            a.setDomain(kind.domain().name());
            a.setTitle(cut(title, 200));
            a.setMessage(cut(message, 1000));
            a.setSubjectKey(cut(subject, 200));
            a.setDedupeKey(cut(dedupe, 250));
            a.setObservationId(observationId);
            a.setJobRunId(runId);
            a.setCreatedAt(now);
            alerts.save(a);
            a.setRecipients(deliver(kind, a));
            return true;
        }));
    }

    /** In-app notification and/or email to every staff member who can see this domain. */
    private int deliver(AlertKind kind, AnalyticsAlert a) {
        Map<Long, AppUser> recipients = new LinkedHashMap<>();
        Permission needed = permissionFor(kind.domain());
        for (UserRole role : UserRole.values()) {
            if (!RolePermissions.of(role).contains(needed)) continue;
            for (AppUser u : users.findByRolesContaining(role)) recipients.putIfAbsent(u.getId(), u);
        }
        Map<Long, AlertPreference> prefs = new LinkedHashMap<>();
        for (AlertPreference p : preferences.findAll()) {
            if (p.getRuleKey().equals(kind.name())) prefs.put(p.getUserId(), p);
        }
        int delivered = 0;
        List<String> emails = new ArrayList<>();
        for (AppUser u : recipients.values()) {
            AlertPreference p = prefs.get(u.getId());
            boolean inApp = p == null || p.isInApp();
            boolean byEmail = p != null && p.isEmail();
            if (inApp) {
                Notification n = new Notification();
                n.setUser(u);
                n.setType(NotificationType.MARKET_ALERT);
                n.setRelatedId(a.getId());
                n.setTitle(a.getTitle());
                n.setMessage(a.getMessage());
                notifications.save(n);
            }
            if (byEmail && u.getEmail() != null) emails.add(u.getEmail());
            if (inApp || byEmail) delivered++;
        }
        for (String to : emails) {
            try {
                email.send(to, "[EstateX analytics] " + a.getTitle(), a.getMessage() + "\n\nSeverity: " + a.getSeverity()
                        + "\nOpen the admin dashboard > Insights & alerts to review or acknowledge it.");
            } catch (RuntimeException e) {
                log.warn("Could not email alert {}: {}", a.getId(), e.getMessage());
            }
        }
        return delivered;
    }

    public static Permission permissionFor(Domain domain) {
        return switch (domain) {
            case MARKET -> Permission.ANALYTICS_MARKET_VIEW;
            case DEMAND -> Permission.ANALYTICS_DEMAND_VIEW;
            case PLATFORM -> Permission.ANALYTICS_OPS_VIEW;
            case FINANCE -> Permission.ANALYTICS_FINANCE_VIEW;
        };
    }

    private static String where(MarketObservation o) {
        if (o.getSuburb() != null) return " in " + o.getSuburb() + ", " + o.getCity();
        if (o.getCity() != null) return " in " + o.getCity();
        return "";
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
