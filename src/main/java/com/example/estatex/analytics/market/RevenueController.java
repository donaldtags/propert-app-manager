package com.example.estatex.analytics.market;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
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

/** Revenue analytics, marketing spend and the commission-share setting. Finance access; every view is audited. */
@RestController
@RequestMapping("/api/v1/admin/analytics")
class RevenueController {
    private final RevenueService revenue;
    private final MarketingSpendRepository spend;
    private final AuditService audit;
    private final BusinessTime time;

    RevenueController(RevenueService revenue, MarketingSpendRepository spend, AuditService audit, BusinessTime time) {
        this.revenue = revenue;
        this.spend = spend;
        this.audit = audit;
        this.time = time;
    }

    @GetMapping("/revenue")
    RevenueService.Revenue revenue(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                   @RequestParam(required = false) String grain, @AuthenticationPrincipal AppUser user) {
        requireFinance(user);
        LocalDate end = to == null ? time.today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1).minusMonths(11) : from;
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > 3 * 366) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of up to three years.");
        Grain g;
        try {
            g = grain == null ? (days <= 45 ? Grain.DAY : days <= 190 ? Grain.WEEK : Grain.MONTH) : Grain.valueOf(grain.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown grouping " + grain);
        }
        if (g == Grain.HOUR || (g == Grain.DAY && days > 400)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a coarser grouping.");
        audit.record(user, entry("ANALYTICS_REVENUE_VIEWED", "Revenue", null).metadata(Map.of("from", start.toString(), "to", end.toString())));
        return revenue.revenue(start, end, g);
    }

    record SpendRequest(@NotNull LocalDate month, @NotBlank @Size(max = 60) String channel,
                        @NotNull @DecimalMin("0.00") BigDecimal amount, @Size(max = 300) String note) {
    }

    /** Finance staff record what was spent on marketing in a month (needed for acquisition cost). */
    @PostMapping("/marketing-spend")
    RevenueService.Spend addSpend(@Valid @RequestBody SpendRequest r, @AuthenticationPrincipal AppUser user) {
        requireFinance(user);
        MarketingSpend s = new MarketingSpend();
        s.setMonth(r.month().withDayOfMonth(1));
        s.setChannel(r.channel().trim());
        s.setAmount(r.amount());
        s.setNote(r.note());
        s.setEnteredByUserId(user.getId());
        spend.save(s);
        audit.record(user, entry("MARKETING_SPEND_ADDED", "MarketingSpend", s.getId())
                .money(r.amount().movePointRight(2).longValue(), "USD").metadata(Map.of("month", s.getMonth().toString(), "channel", s.getChannel())));
        return new RevenueService.Spend(s.getId(), s.getMonth(), s.getChannel(), s.getAmount(), s.getCurrency(), s.getNote());
    }

    @DeleteMapping("/marketing-spend/{id}")
    Map<String, Boolean> deleteSpend(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        requireFinance(user);
        MarketingSpend s = spend.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found"));
        spend.delete(s);
        audit.record(user, entry("MARKETING_SPEND_DELETED", "MarketingSpend", id)
                .money(s.getAmount().movePointRight(2).longValue(), s.getCurrency()));
        return Map.of("deleted", true);
    }

    record ShareRequest(@NotNull BigDecimal percent) {
    }

    /** The EstateX share of agency commission counted as estimated revenue. 0 = off. Admin only. */
    @PutMapping("/revenue/commission-share")
    Map<String, BigDecimal> commissionShare(@Valid @RequestBody ShareRequest r, @AuthenticationPrincipal AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_CONFIGURE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can change this.");
        }
        if (r.percent().signum() < 0 || r.percent().compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The share must be between 0% and 50%.");
        }
        BigDecimal before = revenue.commissionSharePercent();
        BigDecimal after = revenue.setCommissionSharePercent(r.percent(), user.getId());
        audit.record(user, entry("COMMISSION_SHARE_CHANGED", "AnalyticsSetting", null).transition(before.toPlainString(), after.toPlainString()));
        return Map.of("percent", after);
    }

    private static void requireFinance(AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_FINANCE_VIEW)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to revenue analytics.");
        }
    }
}
