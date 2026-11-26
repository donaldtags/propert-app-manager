package com.example.estatex.analytics.market;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.property.PropertyCategory;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rental and sales market dashboards. Market analytics permission required; the demand figures
 * (enquiries, viewings, applications, offers) are included only with demand permission.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics/market")
class MarketDashboardController {
    private static final int MAX_DAYS = 3 * 366;

    private final MarketDashboardService service;
    private final com.example.estatex.common.BusinessTime time;
    private final AuditService audit;

    MarketDashboardController(MarketDashboardService service, com.example.estatex.common.BusinessTime time, AuditService audit) {
        this.service = service;
        this.time = time;
        this.audit = audit;
    }

    @GetMapping("/{type}")
    MarketDashboardService.Dashboard dashboard(@PathVariable String type,
                                               @RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to,
                                               @RequestParam(required = false) String grain,
                                               @RequestParam(required = false) String country,
                                               @RequestParam(required = false) String city,
                                               @RequestParam(required = false) String suburb,
                                               @RequestParam(required = false) PropertyCategory category,
                                               @RequestParam(required = false) Integer bedrooms,
                                               @RequestParam(required = false) BigDecimal minPrice,
                                               @RequestParam(required = false) BigDecimal maxPrice,
                                               @AuthenticationPrincipal AppUser user) {
        requireMarket(user);
        String t = listingType(type);
        LocalDate end = to == null ? time.today() : to;
        LocalDate start = from == null ? end.minusMonths(12).plusDays(1) : from;
        if (start.isAfter(end)) throw new ApiException(HttpStatus.BAD_REQUEST, "'from' must be on or before 'to'.");
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_DAYS) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of up to three years.");
        Grain g = grain == null ? (days <= 45 ? Grain.DAY : days <= 190 ? Grain.WEEK : Grain.MONTH) : parseGrain(grain);
        if (g == Grain.HOUR) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose days, weeks, months, quarters or years.");
        if (periodCount(start, end, g) > 400) throw new ApiException(HttpStatus.BAD_REQUEST, "Too many periods; choose a coarser grouping.");
        MarketFilter f = filter(t, country, city, suburb, category, bedrooms, minPrice, maxPrice);
        boolean demand = user.hasPermission(Permission.ANALYTICS_DEMAND_VIEW);
        return service.dashboard(new MarketDashboardService.Query(t, start, end, g, f, demand));
    }

    /** The live listings behind a dashboard figure (latest snapshot, same filters). */
    @GetMapping("/{type}/listings")
    List<MarketDashboardService.ListingRow> listings(@PathVariable String type,
                                                    @RequestParam(required = false) String country,
                                                    @RequestParam(required = false) String city,
                                                    @RequestParam(required = false) String suburb,
                                                    @RequestParam(required = false) PropertyCategory category,
                                                    @RequestParam(required = false) Integer bedrooms,
                                                    @RequestParam(required = false) BigDecimal minPrice,
                                                    @RequestParam(required = false) BigDecimal maxPrice,
                                                    @AuthenticationPrincipal AppUser user) {
        requireMarket(user);
        String t = listingType(type);
        return service.listings(filter(t, country, city, suburb, category, bedrooms, minPrice, maxPrice), 300);
    }

    record CostRatioRequest(BigDecimal ratio) {
    }

    @GetMapping("/settings/yield-cost-ratio")
    MarketDashboardService.CostRatio costRatio(@AuthenticationPrincipal AppUser user) {
        requireMarket(user);
        return service.costRatio();
    }

    /** The share of rent assumed to go on costs (rates, maintenance, management, vacancy) for net yield. */
    @PutMapping("/settings/yield-cost-ratio")
    MarketDashboardService.CostRatio setCostRatio(@RequestBody CostRatioRequest request, @AuthenticationPrincipal AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_CONFIGURE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can change analytics assumptions.");
        }
        BigDecimal r = request.ratio();
        if (r == null || r.signum() < 0 || r.compareTo(new BigDecimal("0.9")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The cost ratio must be between 0 and 0.9 (0% to 90% of rent).");
        }
        MarketDashboardService.CostRatio before = service.costRatio();
        MarketDashboardService.CostRatio after = service.setCostRatio(r, user.getId());
        audit.record(user, entry("ANALYTICS_SETTING_CHANGED", "AnalyticsSetting", null)
                .transition(before.confirmed() ? before.value().toPlainString() : "unset", after.value().toPlainString())
                .metadata(Map.of("setting", MarketDashboardService.COST_RATIO_KEY)));
        return after;
    }

    private static long periodCount(LocalDate start, LocalDate end, Grain g) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return switch (g) {
            case DAY -> days;
            case WEEK -> days / 7 + 2;
            default -> days / 28 + 2;
        };
    }

    static MarketFilter filter(String type, String country, String city, String suburb, PropertyCategory category,
                               Integer bedrooms, BigDecimal minPrice, BigDecimal maxPrice) {
        if (bedrooms != null && (bedrooms < 0 || bedrooms > 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bedrooms must be 0 to 5 (5 means 5 or more).");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            BigDecimal swap = minPrice;
            minPrice = maxPrice;
            maxPrice = swap;
        }
        String c = blank(city);
        return new MarketFilter(type, blank(country), c, c == null ? null : blank(suburb),
                category == null ? null : category.name(), bedrooms, minPrice, maxPrice);
    }

    private static String listingType(String type) {
        String t = type.toUpperCase(Locale.ROOT);
        return switch (t) {
            case "RENT", "RENTAL", "RENTALS" -> "RENT";
            case "SALE", "SALES" -> "SALE";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose rent or sale.");
        };
    }

    private static Grain parseGrain(String g) {
        try {
            return Grain.valueOf(g.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown grouping " + g);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void requireMarket(AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_MARKET_VIEW)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to market analytics.");
        }
    }
}
