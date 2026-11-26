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
    private final OverviewService overview;
    private final DrillDownService drillDown;

    AnalyticsEngineController(AnalyticsEngine engine, AnalyticsEngineStatusService status, MetricValueRepository values,
                              AuditService audit, OverviewService overview, DrillDownService drillDown) {
        this.engine = engine;
        this.status = status;
        this.values = values;
        this.audit = audit;
        this.overview = overview;
        this.drillDown = drillDown;
    }

    /** The executive overview. Sections the user may not see are left out (and marked restricted). */
    @GetMapping("/overview")
    OverviewService.Overview overview(@RequestParam(defaultValue = "MONTH") String grain,
                                      @RequestParam(defaultValue = "TO_DATE") String compare,
                                      @RequestParam(required = false) String city,
                                      @RequestParam(required = false) String suburb,
                                      @AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_MARKET_VIEW, Permission.ANALYTICS_DEMAND_VIEW,
                Permission.ANALYTICS_FINANCE_VIEW, Permission.ANALYTICS_OPS_VIEW);
        com.example.estatex.analytics.metrics.Grain g = parseGrain(grain);
        if (g == com.example.estatex.analytics.metrics.Grain.HOUR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a day, week, month, quarter or year.");
        }
        OverviewService.Compare c;
        try {
            c = OverviewService.Compare.valueOf(compare.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "compare must be TO_DATE or FULL");
        }
        String cleanCity = blank(city);
        String cleanSuburb = cleanCity == null ? null : blank(suburb);
        if (user.hasPermission(Permission.ANALYTICS_FINANCE_VIEW)) {
            audit.record(user, entry("ANALYTICS_FINANCE_VIEWED", "AnalyticsOverview", null)
                    .metadata(Map.of("grain", g.name())));
        }
        return overview.overview(user, g, c, new OverviewService.Location(cleanCity, cleanSuburb));
    }

    /** The source records behind one figure. Same permissions as the figure itself; finance access is audited. */
    @GetMapping("/records")
    DrillDownService.Records records(@RequestParam String metric, @RequestParam String grain, @RequestParam Instant periodStart,
                                     @RequestParam(required = false) String listingType,
                                     @RequestParam(required = false) String city,
                                     @RequestParam(required = false) String suburb,
                                     @AuthenticationPrincipal AppUser user) {
        MetricDefinition m = parseMetric(metric);
        if (!OverviewService.canSee(user, m)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to these analytics.");
        }
        if (m.domain() == MetricDefinition.Domain.FINANCE) {
            audit.record(user, entry("ANALYTICS_FINANCE_RECORDS_VIEWED", "AnalyticsMetric", null)
                    .metadata(Map.of("metric", m.name(), "periodStart", periodStart.toString())));
        }
        String type = blank(listingType);
        if (type != null && !java.util.Set.of("RENT", "SALE", "SHORT_STAY").contains(type.toUpperCase(java.util.Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "listingType must be RENT, SALE or SHORT_STAY");
        }
        String cleanCity = blank(city);
        return drillDown.records(m, parseGrain(grain), periodStart, type == null ? null : type.toUpperCase(java.util.Locale.ROOT),
                cleanCity, cleanCity == null ? null : blank(suburb));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static com.example.estatex.analytics.metrics.Grain parseGrain(String grain) {
        try {
            return com.example.estatex.analytics.metrics.Grain.valueOf(grain.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown period " + grain);
        }
    }

    private static MetricDefinition parseMetric(String metric) {
        try {
            return MetricDefinition.valueOf(metric);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown metric " + metric);
        }
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

    public record DefinitionView(String key, int version, String kind, String domain, String description, String source) {
    }

    @GetMapping("/definitions")
    List<DefinitionView> definitions(@AuthenticationPrincipal AppUser user) {
        requireAny(user, Permission.ANALYTICS_MARKET_VIEW, Permission.ANALYTICS_DEMAND_VIEW, Permission.ANALYTICS_OPS_VIEW,
                Permission.ANALYTICS_FINANCE_VIEW);
        return Arrays.stream(MetricDefinition.values())
                .map(m -> new DefinitionView(m.name(), m.version(), m.kind().name(), m.domain().name(), m.description(), m.source())).toList();
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
        MetricDefinition m = parseMetric(metric);
        if (!OverviewService.canSee(user, m)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to these analytics.");
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
