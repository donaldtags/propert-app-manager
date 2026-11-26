package com.example.estatex.analytics.report;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.user.Permission;
import java.util.Set;

/**
 * The reports administrators can generate (brief §11). {@code grain} sets the default period (the
 * last completed one); {@code permissions} are the analytics domains a person needs, on top of
 * {@link Permission#REPORTS_EXPORT}, to download or receive it.
 */
public enum ReportType {
    DAILY_PLATFORM("Daily platform report", "Yesterday's headline, platform, marketplace and demand figures against the day before.",
            Grain.DAY, Set.of()),
    WEEKLY_RENTAL("Weekly rental market report", "Rental supply, asking and agreed rents, demand and yields for the last week.",
            Grain.WEEK, Set.of(Permission.ANALYTICS_MARKET_VIEW)),
    MONTHLY_SALES("Monthly sales report", "Sale listings, asking prices, reported and verified sales for the last month.",
            Grain.MONTH, Set.of(Permission.ANALYTICS_MARKET_VIEW)),
    QUARTERLY_INTELLIGENCE("Quarterly market intelligence report",
            "Statistically tested observations, AI interpretations and alerts raised during the last quarter.",
            Grain.QUARTER, Set.of(Permission.ANALYTICS_MARKET_VIEW)),
    ANNUAL_MARKET("Annual market report", "Rental and sales markets month by month over the last year.",
            Grain.YEAR, Set.of(Permission.ANALYTICS_MARKET_VIEW)),
    GEOGRAPHIC("Geographic market report", "Supply, asking prices, demand and yield by suburb, with map coverage.",
            Grain.MONTH, Set.of(Permission.ANALYTICS_MARKET_VIEW)),
    REVENUE("Revenue report", "Cash, recognised, outstanding and estimated revenue, MRR and unit economics.",
            Grain.MONTH, Set.of(Permission.ANALYTICS_FINANCE_VIEW)),
    AGENT_PERFORMANCE("Agent performance report", "Listings, enquiries, viewings, leases and sales attributed to each agent.",
            Grain.MONTH, Set.of(Permission.ANALYTICS_MARKET_VIEW)),
    DEVELOPER_PERFORMANCE("Developer performance report", "Developers' listings, demand, sales and construction projects.",
            Grain.QUARTER, Set.of(Permission.ANALYTICS_MARKET_VIEW)),
    INVESTMENT_INTELLIGENCE("Investment intelligence report",
            "REIT offerings and take-up on EstateX, with indicative rental yields by suburb.",
            Grain.QUARTER, Set.of(Permission.ANALYTICS_MARKET_VIEW, Permission.ANALYTICS_FINANCE_VIEW));

    private final String label;
    private final String description;
    private final Grain grain;
    private final Set<Permission> permissions;

    ReportType(String label, String description, Grain grain, Set<Permission> permissions) {
        this.label = label;
        this.description = description;
        this.grain = grain;
        this.permissions = permissions;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Grain grain() {
        return grain;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** Whether this person may download or receive this report. */
    public boolean allowedFor(com.example.estatex.user.AppUser user) {
        if (user == null || !user.hasPermission(Permission.REPORTS_EXPORT)) return false;
        if (this == DAILY_PLATFORM) {
            // The overview filters its own sections; needing any analytics view is enough.
            return user.hasPermission(Permission.ANALYTICS_MARKET_VIEW) || user.hasPermission(Permission.ANALYTICS_DEMAND_VIEW)
                    || user.hasPermission(Permission.ANALYTICS_OPS_VIEW) || user.hasPermission(Permission.ANALYTICS_FINANCE_VIEW);
        }
        return permissions.stream().allMatch(user::hasPermission);
    }
}
