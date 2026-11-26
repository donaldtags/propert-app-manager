package com.example.estatex.analytics.insight;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.analytics.alert.AlertKind;
import com.example.estatex.analytics.alert.AlertPreference;
import com.example.estatex.analytics.alert.AlertPreferenceRepository;
import com.example.estatex.analytics.alert.AlertRule;
import com.example.estatex.analytics.alert.AlertService;
import com.example.estatex.analytics.alert.AnalyticsAlert;
import com.example.estatex.analytics.alert.AnalyticsAlertRepository;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Insights &amp; alerts (Phase G). Each viewer only sees the domains their role allows. */
@RestController
@RequestMapping("/api/v1/admin/analytics")
class InsightsController {
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "CRITICAL");

    private final InsightsService insights;
    private final MarketInsightService ai;
    private final AlertService alerts;
    private final AnalyticsAlertRepository alertRows;
    private final AlertPreferenceRepository preferences;
    private final AuditService audit;
    private final BusinessTime time;

    InsightsController(InsightsService insights, MarketInsightService ai, AlertService alerts, AnalyticsAlertRepository alertRows,
                       AlertPreferenceRepository preferences, AuditService audit, BusinessTime time) {
        this.insights = insights;
        this.ai = ai;
        this.alerts = alerts;
        this.alertRows = alertRows;
        this.preferences = preferences;
        this.audit = audit;
        this.time = time;
    }

    @GetMapping("/insights")
    InsightsService.Insights insights(@RequestParam(defaultValue = "WEEK") String grain, @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        Grain g;
        try {
            g = Grain.valueOf(grain.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown grouping " + grain);
        }
        if (!ObservationService.GRAINS.contains(g)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose WEEK or MONTH.");
        return insights.insights(user, g);
    }

    record AiToggle(@NotNull Boolean enabled) {
    }

    @PutMapping("/insights/ai")
    Map<String, Object> toggleAi(@Valid @RequestBody AiToggle r, @AuthenticationPrincipal AppUser user) {
        requireConfigure(user);
        ai.setEnabled(r.enabled(), user.getId(), time.clock().instant());
        audit.record(user, entry("ANALYTICS_AI_INSIGHTS_" + (r.enabled() ? "ENABLED" : "DISABLED"), "AnalyticsSetting", null));
        return Map.of("enabled", r.enabled());
    }

    // ---- Alerts --------------------------------------------------------------------------------

    record AlertView(Long id, String ruleKey, String ruleLabel, String severity, String domain, String title, String message,
                     Long observationId, Instant createdAt, Instant acknowledgedAt, Long acknowledgedByUserId, int recipients) {
    }

    record AlertList(List<AlertView> alerts, long openCount) {
    }

    @GetMapping("/alerts")
    AlertList alerts(@RequestParam(defaultValue = "false") boolean open, @RequestParam(defaultValue = "50") int limit,
                     @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        Set<String> domains = InsightsService.visibleDomains(user);
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        List<AnalyticsAlert> rows = open ? alertRows.findByDomainInAndAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc(domains, page)
                : alertRows.findByDomainInOrderByCreatedAtDescIdDesc(domains, page);
        return new AlertList(rows.stream().map(InsightsController::view).toList(), alertRows.countByDomainInAndAcknowledgedAtIsNull(domains));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    AlertView acknowledge(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        AnalyticsAlert a = alertRows.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alert not found"));
        if (!InsightsService.visibleDomains(user).contains(a.getDomain())) throw new ApiException(HttpStatus.NOT_FOUND, "Alert not found");
        if (a.getAcknowledgedAt() == null) {
            a.setAcknowledgedAt(time.clock().instant());
            a.setAcknowledgedByUserId(user.getId());
            alertRows.save(a);
            audit.record(user, entry("ANALYTICS_ALERT_ACKNOWLEDGED", "AnalyticsAlert", id).metadata(Map.of("rule", a.getRuleKey())));
        }
        return view(a);
    }

    // ---- Rules (thresholds, cooldowns) ------------------------------------------------------------

    record RuleView(String key, String label, String domain, String unit, boolean enabled, BigDecimal threshold, int minSample,
                    double maxQValue, int cooldownHours, String severity, boolean customised, boolean marketRule) {
    }

    record RuleList(List<RuleView> rules, boolean canEdit) {
    }

    @GetMapping("/alert-rules")
    RuleList rules(@AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        List<RuleView> out = new ArrayList<>();
        Map<AlertKind, AlertRule> effective = alerts.rules();
        for (AlertKind k : AlertKind.values()) {
            AlertRule r = effective.get(k);
            out.add(new RuleView(k.name(), k.label(), k.domain().name(), k.unit().name(), r.isEnabled(), r.getThreshold(), r.getMinSample(),
                    r.getMaxQValue(), r.getCooldownHours(), r.getSeverity(), r.getUpdatedAt() != null, !k.observationKinds().isEmpty()));
        }
        return new RuleList(out, user.hasPermission(Permission.ANALYTICS_CONFIGURE));
    }

    record RuleRequest(@NotNull Boolean enabled, @NotNull BigDecimal threshold, @NotNull Integer minSample, @NotNull Double maxQValue,
                       @NotNull Integer cooldownHours, @NotNull String severity) {
    }

    @PutMapping("/alert-rules/{key}")
    RuleView saveRule(@PathVariable String key, @Valid @RequestBody RuleRequest r, @AuthenticationPrincipal AppUser user) {
        requireConfigure(user);
        AlertKind kind;
        try {
            kind = AlertKind.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown alert rule " + key);
        }
        String severity = r.severity().toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(severity)) throw new ApiException(HttpStatus.BAD_REQUEST, "Severity must be INFO, WARNING or CRITICAL.");
        if (r.threshold().signum() < 0 || r.threshold().compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The threshold must be between 0 and 1000.");
        }
        if (r.minSample() < 1 || r.minSample() > 100_000) throw new ApiException(HttpStatus.BAD_REQUEST, "Minimum sample must be 1 or more.");
        if (r.maxQValue() <= 0 || r.maxQValue() > 0.10 && !kind.observationKinds().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The significance level must be above 0 and at most 0.10.");
        }
        if (r.cooldownHours() < 1 || r.cooldownHours() > 24 * 90) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The cooldown must be between 1 hour and 90 days.");
        }
        AlertRule before = alerts.rules().get(kind);
        AlertRule rule = new AlertRule();
        rule.setRuleKey(kind.name());
        rule.setEnabled(r.enabled());
        rule.setThreshold(r.threshold());
        rule.setMinSample(r.minSample());
        rule.setMaxQValue(kind.observationKinds().isEmpty() ? 1 : r.maxQValue());
        rule.setCooldownHours(r.cooldownHours());
        rule.setSeverity(severity);
        rule.setUpdatedByUserId(user.getId());
        rule.setUpdatedAt(time.clock().instant());
        alerts.save(rule);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("rule", kind.name());
        meta.put("before", summary(before));
        meta.put("after", summary(rule));
        audit.record(user, entry("ANALYTICS_ALERT_RULE_CHANGED", "AlertRule", null).metadata(meta));
        return new RuleView(kind.name(), kind.label(), kind.domain().name(), kind.unit().name(), rule.isEnabled(), rule.getThreshold(),
                rule.getMinSample(), rule.getMaxQValue(), rule.getCooldownHours(), rule.getSeverity(), true, !kind.observationKinds().isEmpty());
    }

    // ---- My notification preferences ------------------------------------------------------------

    record PreferenceView(String key, String label, String domain, boolean inApp, boolean email) {
    }

    @GetMapping("/alert-preferences")
    List<PreferenceView> preferences(@AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        Set<String> domains = InsightsService.visibleDomains(user);
        Map<String, AlertPreference> mine = new LinkedHashMap<>();
        for (AlertPreference p : preferences.findByUserId(user.getId())) mine.put(p.getRuleKey(), p);
        List<PreferenceView> out = new ArrayList<>();
        for (AlertKind k : AlertKind.values()) {
            if (!domains.contains(k.domain().name())) continue;
            AlertPreference p = mine.get(k.name());
            out.add(new PreferenceView(k.name(), k.label(), k.domain().name(), p == null || p.isInApp(), p != null && p.isEmail()));
        }
        return out;
    }

    record PreferenceRequest(@NotNull Boolean inApp, @NotNull Boolean email) {
    }

    @PutMapping("/alert-preferences/{key}")
    PreferenceView savePreference(@PathVariable String key, @Valid @RequestBody PreferenceRequest r, @AuthenticationPrincipal AppUser user) {
        requireAnalytics(user);
        AlertKind kind;
        try {
            kind = AlertKind.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown alert rule " + key);
        }
        if (!InsightsService.visibleDomains(user).contains(kind.domain().name())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your role does not receive this alert.");
        }
        AlertPreference p = preferences.findByUserIdAndRuleKey(user.getId(), kind.name()).orElseGet(AlertPreference::new);
        p.setUserId(user.getId());
        p.setRuleKey(kind.name());
        p.setInApp(r.inApp());
        p.setEmail(r.email());
        preferences.save(p);
        return new PreferenceView(kind.name(), kind.label(), kind.domain().name(), p.isInApp(), p.isEmail());
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private static AlertView view(AnalyticsAlert a) {
        String label;
        try {
            label = AlertKind.valueOf(a.getRuleKey()).label();
        } catch (IllegalArgumentException e) {
            label = a.getRuleKey();
        }
        return new AlertView(a.getId(), a.getRuleKey(), label, a.getSeverity(), a.getDomain(), a.getTitle(), a.getMessage(),
                a.getObservationId(), a.getCreatedAt(), a.getAcknowledgedAt(), a.getAcknowledgedByUserId(), a.getRecipients());
    }

    private static String summary(AlertRule r) {
        return (r.isEnabled() ? "on" : "off") + ", threshold " + r.getThreshold().stripTrailingZeros().toPlainString() + ", min n "
                + r.getMinSample() + ", q " + r.getMaxQValue() + ", cooldown " + r.getCooldownHours() + "h, " + r.getSeverity();
    }

    private static void requireAnalytics(AppUser user) {
        if (user == null || InsightsService.visibleDomains(user).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to analytics.");
        }
    }

    private static void requireConfigure(AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_CONFIGURE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can change this.");
        }
    }
}
