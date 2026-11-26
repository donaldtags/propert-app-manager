package com.example.estatex.analytics.api;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.analytics.engine.AnalyticsEngine;
import com.example.estatex.analytics.engine.AnalyticsEngineStatusService;
import com.example.estatex.analytics.metrics.MetricDefinition;
import com.example.estatex.analytics.metrics.MetricValue;
import com.example.estatex.analytics.metrics.MetricValueRepository;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Engine status, run history, manual runs and the metric catalogue. */
@RestController
@RequestMapping("/api/v1/admin/analytics")
class AnalyticsEngineController {
    private final AnalyticsEngine engine;
    private final AnalyticsEngineStatusService status;
    private final MetricValueRepository values;
    private final AuditService audit;

    AnalyticsEngineController(AnalyticsEngine engine, AnalyticsEngineStatusService status, MetricValueRepository values,
                              AuditService audit) {
        this.engine = engine;
        this.status = status;
        this.values = values;
        this.audit = audit;
    }

    /** Freshness: anyone who can see any analytics needs this. */
    @GetMapping("/engine/status")
    AnalyticsEngineStatusService.Status engineStatus(@AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_MARKET_VIEW, Permission.ANALYTICS_DEMAND_VIEW,
                Permission.ANALYTICS_FINANCE_VIEW, Permission.ANALYTICS_OPS_VIEW);
        return status.status();
    }

    @GetMapping("/engine/runs")
    List<AnalyticsEngineStatusService.RunView> runs(@RequestParam(defaultValue = "50") int limit,
                                                    @AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_OPS_VIEW);
        return status.recent(limit);
    }

    @PostMapping("/engine/run")
    AnalyticsEngine.Outcome runNow(@AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_CONFIGURE);
        audit.record(user, entry("ANALYTICS_RUN_REQUESTED", "AnalyticsEngine", null));
        return engine.runHourly("MANUAL");
    }

    /** Recalculates everything between two moments (at most 400 days), e.g. after fixing bad data. */
    @PostMapping("/engine/rebuild")
    AnalyticsEngine.Outcome rebuild(@RequestParam Instant from, @RequestParam Instant to,
                                    @AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_CONFIGURE);
        if (!from.isBefore(to) || Duration.between(from, to).toDays() > 400) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of up to 400 days, with 'from' before 'to'.");
        }
        audit.record(user, entry("ANALYTICS_REBUILD_REQUESTED", "AnalyticsEngine", null)
                .metadata(Map.of("from", from.toString(), "to", to.toString())));
        return engine.rebuild(from, to);
    }

    public record DefinitionView(String key, int version, String kind, String description, String source) {
    }

    @GetMapping("/definitions")
    List<DefinitionView> definitions(@AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_MARKET_VIEW, Permission.ANALYTICS_DEMAND_VIEW, Permission.ANALYTICS_OPS_VIEW);
        return Arrays.stream(MetricDefinition.values())
                .map(m -> new DefinitionView(m.name(), m.version(), m.kind().name(), m.description(), m.source())).toList();
    }

    /**
     * One metric's series for one market slice. {@code dims} is the slice key, e.g.
     * {@code rent|*|*|*|*|*} for all rentals or {@code rent|zimbabwe|harare|avondale|*|*}.
     */
    @GetMapping("/metrics")
    List<MetricValue> series(@RequestParam String metric, @RequestParam String grain,
                             @RequestParam(defaultValue = "*|*|*|*|*|*") String dims,
                             @RequestParam Instant from, @RequestParam Instant to,
                             @AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_MARKET_VIEW, Permission.ANALYTICS_DEMAND_VIEW);
        try {
            MetricDefinition.valueOf(metric);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown metric " + metric);
        }
        return values.findByMetricKeyAndGrainAndDimsKeyAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStart(
                metric, grain.toUpperCase(java.util.Locale.ROOT), dims.toLowerCase(java.util.Locale.ROOT), from, to);
    }

    private static void requireAny(AppUser user, Permission... permissions) {
        if (user != null) {
            for (Permission p : permissions) {
                if (user.hasPermission(p)) return;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to these analytics.");
    }
}
