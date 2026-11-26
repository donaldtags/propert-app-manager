package com.example.estatex.analytics.market;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.property.PropertyCategory;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The market map (market permission; demand layers need demand permission) and customer demand analytics. */
@RestController
class GeoDemandController {
    private final GeoService geo;
    private final DemandService demand;
    private final BusinessTime time;

    GeoDemandController(GeoService geo, DemandService demand, BusinessTime time) {
        this.geo = geo;
        this.demand = demand;
        this.time = time;
    }

    @GetMapping("/api/v1/admin/analytics/geo/{type}")
    GeoService.GeoData map(@PathVariable String type,
                           @RequestParam(required = false) String country,
                           @RequestParam(required = false) String city,
                           @RequestParam(required = false) PropertyCategory category,
                           @RequestParam(required = false) Integer bedrooms,
                           @RequestParam(required = false) BigDecimal minPrice,
                           @RequestParam(required = false) BigDecimal maxPrice,
                           @AuthenticationPrincipal AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_MARKET_VIEW)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to market analytics.");
        }
        String t = switch (type.toUpperCase(Locale.ROOT)) {
            case "RENT" -> "RENT";
            case "SALE" -> "SALE";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose rent or sale.");
        };
        MarketFilter f = MarketDashboardController.filter(t, country, city, null, category, bedrooms, minPrice, maxPrice);
        return geo.suburbs(f, user.hasPermission(Permission.ANALYTICS_DEMAND_VIEW));
    }

    @GetMapping("/api/v1/admin/analytics/demand")
    DemandService.Demand demand(@RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to,
                                @RequestParam(required = false) String city,
                                @AuthenticationPrincipal AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_DEMAND_VIEW)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to demand analytics.");
        }
        LocalDate end = to == null ? time.today() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 366) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of up to a year, with 'from' before 'to'.");
        }
        return demand.demand(start, end, city == null || city.isBlank() ? null : city.trim());
    }
}
