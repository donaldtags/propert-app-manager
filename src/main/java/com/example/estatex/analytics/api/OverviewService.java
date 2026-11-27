package com.example.estatex.analytics.api;

import com.example.estatex.analytics.engine.AnalyticsEngineStatusService;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.MetricDefinition;
import com.example.estatex.analytics.metrics.MetricDefinition.Domain;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The executive overview: every KPI with its value, the comparison period's value, the change,
 * a 12-period trend, sample size and data freshness. Reads only published figures
 * ({@code metric_values}), so it always agrees with the engine's last successful run; "live"
 * counts (approval queues, subscriptions) are read directly and labelled as such.
 */
@Service
public class OverviewService {
    private static final int SPARK_POINTS = 12;

    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final AnalyticsEngineStatusService engineStatus;

    OverviewService(JdbcTemplate jdbc, BusinessTime time, AnalyticsEngineStatusService engineStatus) {
        this.jdbc = jdbc;
        this.time = time;
        this.engineStatus = engineStatus;
    }

    public enum Compare { TO_DATE, FULL }

    public record Location(String city, String suburb) {
        boolean any() {
            return city != null;
        }
    }

    public record PeriodView(String grain, Instant start, Instant end, Instant asOf, boolean complete, String label) {
    }

    public record Point(Instant start, BigDecimal value) {
    }

    public record Kpi(String key, String label, String help, String metric, String listingType, String domain,
                      String unit, String better, BigDecimal value, BigDecimal previous, BigDecimal change,
                      BigDecimal changePercent, String direction, boolean comparable, String note, Integer sampleSize,
                      boolean lowSample, boolean live, boolean available, boolean locationApplies, boolean drillable,
                      List<Point> trend) {
    }

    public record Section(String key, String title, String subtitle, boolean restricted, List<Kpi> kpis) {
    }

    public record Overview(PeriodView period, PeriodView comparison, String comparisonLabel, Location location,
                           AnalyticsEngineStatusService.Status freshness, List<Section> sections) {
    }

    /** One KPI's recipe. */
    record Spec(String key, String label, MetricDefinition metric, String listingType, String unit, String better,
                String help) {
        Spec(String key, String label, MetricDefinition metric, String listingType, String unit, String better) {
            this(key, label, metric, listingType, unit, better, null);
        }
    }

    public Overview overview(AppUser user, Grain grain, Compare compare, Location location) {
        return overview(user, grain, compare, location, null);
    }

    /** As above, for the period containing {@code at} (reports pick a completed period); null means the latest. */
    public Overview overview(AppUser user, Grain grain, Compare compare, Location location, Instant at) {
        ZoneId zone = time.zone();
        AnalyticsEngineStatusService.Status freshness = engineStatus.status();
        Instant now = time.clock().instant();
        // Show the period the published figures belong to: the one containing the last successful update.
        Instant reference = at != null ? at : freshness.lastSuccessAt() != null ? freshness.lastSuccessAt() : now;
        Period current = Period.containing(grain, reference, zone);
        Period previous = current.previous(zone);
        Instant currentAsOf = asOfOf(current, "FULL");
        boolean incomplete = currentAsOf == null ? !current.isComplete(now) : currentAsOf.isBefore(current.end());
        String variant = incomplete && compare == Compare.TO_DATE ? "TO_DATE" : "FULL";
        Instant previousEnd = "TO_DATE".equals(variant) ? asOfOf(previous, "TO_DATE") : previous.end();

        PeriodView pv = new PeriodView(grain.name(), current.start(), current.end(), currentAsOf, !incomplete,
                label(current, zone, incomplete));
        PeriodView cv = new PeriodView(grain.name(), previous.start(), previous.end(), previousEnd, true, label(previous, zone, false));
        String comparisonLabel = !incomplete ? "Compared with the previous " + noun(grain)
                : "TO_DATE".equals(variant) ? "Compared with the same point in the previous " + noun(grain)
                : "Compared with the whole previous " + noun(grain) + " (this one isn't finished, so expect it to look lower)";

        Ctx c = new Ctx(user, grain, current, previous, variant, location, zone);
        List<Section> sections = new ArrayList<>();
        sections.add(section(c, "headline", "At a glance", null, List.of(
                new Spec("active_listings", "Active listings", MetricDefinition.ACTIVE_LISTINGS, null, "COUNT", "UP"),
                new Spec("rental_listings", "Rental listings", MetricDefinition.ACTIVE_LISTINGS, "RENT", "COUNT", "UP"),
                new Spec("sale_listings", "Properties for sale", MetricDefinition.ACTIVE_LISTINGS, "SALE", "COUNT", "UP"),
                new Spec("verified_sales", "Verified sales", MetricDefinition.VERIFIED_SALES, null, "COUNT", "UP",
                        "Sales confirmed by EstateX from conveyancer or Deeds Registry evidence. Reported sales aren't included."),
                new Spec("rental_enquiries", "Rental enquiries", MetricDefinition.ENQUIRIES, "RENT", "COUNT", "UP",
                        "Enquiries about rental listings. Shows interest, not signed leases."),
                new Spec("platform_revenue", "Platform revenue", MetricDefinition.PLATFORM_REVENUE, null, "USD", "UP")), true));
        sections.add(section(c, "platform", "Platform", "Who is using EstateX", List.of(
                new Spec("total_users", "Registered users", MetricDefinition.TOTAL_USERS, null, "COUNT", "UP"),
                new Spec("active_users", "Active users", MetricDefinition.ACTIVE_USERS, null, "COUNT", "UP"),
                new Spec("new_registrations", "New registrations", MetricDefinition.NEW_REGISTRATIONS, null, "COUNT", "UP"),
                new Spec("new_agents", "New agents", MetricDefinition.NEW_AGENTS, null, "COUNT", "UP"),
                new Spec("new_landlords", "New landlords", MetricDefinition.NEW_LANDLORDS, null, "COUNT", "UP"),
                new Spec("new_developers", "New developers", MetricDefinition.NEW_DEVELOPERS, null, "COUNT", "UP"),
                new Spec("referral_signups", "Referred sign-ups", MetricDefinition.REFERRAL_SIGNUPS, null, "COUNT", "UP"),
                new Spec("verified_listings", "Verified live listings", MetricDefinition.VERIFIED_LISTINGS, null, "COUNT", "UP")), false));
        sections.add(section(c, "marketplace", "Marketplace", "Supply and transactions", List.of(
                new Spec("sale_listings", "For sale", MetricDefinition.ACTIVE_LISTINGS, "SALE", "COUNT", "UP"),
                new Spec("rental_listings", "For rent", MetricDefinition.ACTIVE_LISTINGS, "RENT", "COUNT", "UP"),
                new Spec("new_listings", "New listings", MetricDefinition.NEW_LISTINGS, null, "COUNT", "UP"),
                new Spec("updated_listings", "Updated listings", MetricDefinition.UPDATED_LISTINGS, null, "COUNT", "NEUTRAL"),
                new Spec("withdrawn", "Withdrawn", MetricDefinition.WITHDRAWN_LISTINGS, null, "COUNT", "DOWN"),
                new Spec("expired", "Expired", MetricDefinition.EXPIRED_LISTINGS, null, "COUNT", "DOWN"),
                new Spec("under_offer", "Went under offer", MetricDefinition.UNDER_OFFER, null, "COUNT", "UP"),
                new Spec("reported_sold", "Reported sold", MetricDefinition.REPORTED_SOLD, null, "COUNT", "UP",
                        "Marked sold by the owner or agent. Not independently verified."),
                new Spec("verified_sales", "Verified sales", MetricDefinition.VERIFIED_SALES, null, "COUNT", "UP"),
                new Spec("leases_signed", "Rental agreements signed", MetricDefinition.RENTAL_AGREEMENTS_SIGNED, null, "COUNT", "UP")), false));
        sections.add(section(c, "activity", "Customer activity", "Interest shown by buyers and tenants", List.of(
                new Spec("views", "Listing views", MetricDefinition.LISTING_VIEWS, null, "COUNT", "UP"),
                new Spec("searches", "Searches", MetricDefinition.SEARCHES, null, "COUNT", "UP"),
                new Spec("buyer_enquiries", "Buyer enquiries", MetricDefinition.ENQUIRIES, "SALE", "COUNT", "UP"),
                new Spec("rental_enquiries", "Rental enquiries", MetricDefinition.ENQUIRIES, "RENT", "COUNT", "UP"),
                new Spec("viewing_requests", "Viewing requests", MetricDefinition.VIEWING_REQUESTS, null, "COUNT", "UP"),
                new Spec("applications", "Rental applications", MetricDefinition.RENTAL_APPLICATIONS, null, "COUNT", "UP"),
                new Spec("saves", "Saved properties", MetricDefinition.SAVES, null, "COUNT", "UP"),
                new Spec("offers", "Offers made", MetricDefinition.OFFERS, null, "COUNT", "UP")), false));
        sections.add(section(c, "business", "Business performance", "Money EstateX earns, and money it handles for clients", List.of(
                new Spec("platform_revenue", "Platform revenue", MetricDefinition.PLATFORM_REVENUE, null, "USD", "UP"),
                new Spec("revenue_subscriptions", "Subscriptions", MetricDefinition.REVENUE_SUBSCRIPTIONS, null, "USD", "UP"),
                new Spec("revenue_featured", "Featured listings", MetricDefinition.REVENUE_FEATURED_LISTINGS, null, "USD", "UP"),
                new Spec("mrr", "Monthly recurring revenue", MetricDefinition.MRR, null, "USD", "UP"),
                new Spec("paying_subscribers", "Paying subscribers", MetricDefinition.PAYING_SUBSCRIBERS, null, "COUNT", "UP"),
                new Spec("cancellations", "Subscription cancellations", MetricDefinition.SUBSCRIPTION_CANCELLATIONS, null, "COUNT", "DOWN"),
                new Spec("uncollected", "Charges recorded without payment", MetricDefinition.UNCOLLECTED_CHARGES, null, "COUNT", "DOWN"),
                new Spec("payments_ok", "Successful payments", MetricDefinition.PAYMENTS_SUCCESSFUL, null, "COUNT", "UP"),
                new Spec("payments_failed", "Failed payments", MetricDefinition.PAYMENTS_FAILED, null, "COUNT", "DOWN"),
                new Spec("payment_volume", "Client money handled", MetricDefinition.PAYMENT_VOLUME, null, "USD", "NEUTRAL"),
                new Spec("referral_discounts", "Referral discounts given", MetricDefinition.REFERRAL_DISCOUNTS, null, "USD", "NEUTRAL"),
                new Spec("referral_rewards", "Referral rewards paid", MetricDefinition.REFERRAL_REWARDS, null, "USD", "NEUTRAL")), false));

        addLive(c, sections);
        return new Overview(pv, cv, comparisonLabel, location, freshness, sections);
    }

    record Ctx(AppUser user, Grain grain, Period current, Period previous, String variant, Location location, ZoneId zone) {
    }

    static Permission permissionFor(Domain domain) {
        return switch (domain) {
            case MARKET -> Permission.ANALYTICS_MARKET_VIEW;
            case DEMAND -> Permission.ANALYTICS_DEMAND_VIEW;
            case PLATFORM -> Permission.ANALYTICS_OPS_VIEW;
            case FINANCE -> Permission.ANALYTICS_FINANCE_VIEW;
        };
    }

    static boolean canSee(AppUser user, MetricDefinition m) {
        return user != null && user.hasPermission(permissionFor(m.domain()));
    }

    private Section section(Ctx c, String key, String title, String subtitle, List<Spec> specs, boolean mixed) {
        List<Kpi> kpis = new ArrayList<>();
        boolean anyVisible = false;
        for (Spec s : specs) {
            if (!canSee(c.user(), s.metric())) continue;
            anyVisible = true;
            kpis.add(kpi(c, s));
        }
        return new Section(key, title, subtitle, !anyVisible && !mixed, kpis);
    }

    private Kpi kpi(Ctx c, Spec s) {
        MetricDefinition m = s.metric();
        boolean locational = (m.domain() == Domain.MARKET || m.domain() == Domain.DEMAND);
        Location loc = locational ? c.location() : null;
        Figure cur = figure(m, s.listingType(), c.grain(), c.current().start(), "FULL", loc);
        Figure prev = figure(m, s.listingType(), c.grain(), c.previous().start(), c.variant(), loc);
        List<Point> trend = trend(m, s.listingType(), c, loc);
        String help = s.help() != null ? s.help() : m.description();
        if (cur == null) {
            return new Kpi(s.key(), s.label(), help, m.name(), s.listingType(), m.domain().name(), s.unit(), s.better(),
                    null, null, null, null, "NONE", false, "Not calculated yet. Figures appear after the next hourly update.",
                    null, false, false, true, locational, true, trend);
        }
        boolean comparable = prev != null && prev.historyComplete && cur.historyComplete;
        String note = null;
        if (prev == null) note = "No figure for the comparison period yet.";
        else if (!comparable) note = "No comparison yet: recording began after the comparison period started.";
        BigDecimal change = null;
        BigDecimal pct = null;
        String direction = "NONE";
        if (comparable) {
            change = cur.value.subtract(prev.value);
            direction = change.signum() > 0 ? "UP" : change.signum() < 0 ? "DOWN" : "FLAT";
            if (prev.value.signum() != 0) {
                pct = change.multiply(BigDecimal.valueOf(100)).divide(prev.value.abs(), 1, RoundingMode.HALF_UP);
            } else if (cur.value.signum() != 0) {
                direction = "NEW";
            }
        }
        boolean drillable = m.captureSource() == null || !"marketplace_events".equals(m.captureSource());
        if (m == MetricDefinition.TOTAL_USERS) drillable = false;
        return new Kpi(s.key(), s.label(), help, m.name(), s.listingType(), m.domain().name(), s.unit(), s.better(),
                cur.value, prev == null ? null : prev.value, change, pct, direction, comparable, note, cur.sampleSize,
                cur.lowSample, false, true, locational, drillable, trend);
    }

    record Figure(BigDecimal value, int sampleSize, boolean lowSample, boolean historyComplete) {
    }

    /** One published figure, summed over listing types when a location is chosen without a type. */
    Figure figure(MetricDefinition m, String listingType, Grain grain, Instant periodStart, String variant, Location loc) {
        List<Map<String, Object>> rows = rows(m, listingType, grain, variant, loc, "period_start = ?", Timestamp.from(periodStart));
        if (rows.isEmpty()) {
            // The period was calculated but nothing happened in this slice (no row is stored for an
            // empty slice): that is a real zero. Only an uncalculated period is "missing".
            List<Boolean> computed = jdbc.query("select history_complete from metric_values where metric_key = ? and grain = ? "
                            + "and period_start = ? and variant = ? and dims_key = '*|*|*|*|*|*'",
                    (rs, i) -> rs.getBoolean(1), m.name(), grain.name(), Timestamp.from(periodStart), variant);
            if (computed.isEmpty()) {
                computed = jdbc.query("select history_complete from metric_values where metric_key = 'TOTAL_USERS' and grain = ? "
                        + "and period_start = ? and variant = ?", (rs, i) -> rs.getBoolean(1), grain.name(), Timestamp.from(periodStart), variant);
                if (computed.isEmpty()) return null;
                // Distribution metrics have no all-slice row; their history follows the metric's own source.
                return new Figure(BigDecimal.ZERO, 0, false, m.captureSource() == null);
            }
            return new Figure(BigDecimal.ZERO, 0, false, computed.get(0));
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        boolean low = false;
        boolean history = true;
        for (Map<String, Object> r : rows) {
            BigDecimal v = (BigDecimal) r.get("metric_value");
            sum = sum.add(v == null ? BigDecimal.ZERO : v);
            n += ((Number) r.get("sample_size")).intValue();
            low |= Boolean.TRUE.equals(r.get("low_sample"));
            history &= Boolean.TRUE.equals(r.get("history_complete"));
        }
        return new Figure(sum, n, low, history);
    }

    private List<Point> trend(MetricDefinition m, String listingType, Ctx c, Location loc) {
        Period first = c.current();
        for (int i = 1; i < SPARK_POINTS; i++) first = first.previous(c.zone());
        List<Map<String, Object>> rows = rows(m, listingType, c.grain(), "FULL", loc, "period_start >= ? and period_start <= ?",
                Timestamp.from(first.start()), Timestamp.from(c.current().start()));
        Map<Instant, BigDecimal> byStart = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            if (!Boolean.TRUE.equals(r.get("history_complete"))) continue; // never plot a half-measured period
            Instant start = com.example.estatex.analytics.engine.JobLockAccess.instant(r.get("period_start"));
            BigDecimal v = (BigDecimal) r.get("metric_value");
            byStart.merge(start, v == null ? BigDecimal.ZERO : v, BigDecimal::add);
        }
        return byStart.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new Point(e.getKey(), e.getValue())).toList();
    }

    private List<Map<String, Object>> rows(MetricDefinition m, String listingType, Grain grain, String variant, Location loc,
                                           String periodClause, Object... periodArgs) {
        List<Object> args = new ArrayList<>(List.of(m.name(), grain.name(), variant));
        StringBuilder sql = new StringBuilder("select period_start, metric_value, sample_size, low_sample, history_complete "
                + "from metric_values where metric_key = ? and grain = ? and variant = ? and " + periodClause);
        args.addAll(List.of(periodArgs));
        if (loc == null || !loc.any()) {
            sql.append(" and dims_key = ?");
            args.add(listingType == null ? "*|*|*|*|*|*" : listingType.toLowerCase(Locale.ROOT) + "|*|*|*|*|*");
        } else {
            sql.append(" and lower(dim_city) = ? and dim_category is null and dim_bedrooms is null");
            args.add(loc.city().trim().toLowerCase(Locale.ROOT));
            if (loc.suburb() != null) {
                sql.append(" and lower(dim_suburb) = ?");
                args.add(loc.suburb().trim().toLowerCase(Locale.ROOT));
            } else {
                sql.append(" and dim_suburb is null");
            }
            if (listingType != null) {
                sql.append(" and dim_listing_type = ?");
                args.add(listingType);
            } else {
                sql.append(" and dim_listing_type is not null");
            }
        }
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private Instant asOfOf(Period p, String variant) {
        List<Instant> found = jdbc.query("select max(as_of) from metric_values where grain = ? and period_start = ? and variant = ?",
                (rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                p.grain().name(), Timestamp.from(p.start()), variant);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Counts read straight from the source tables right now; no history, so no comparison. */
    private void addLive(Ctx c, List<Section> sections) {
        for (Section s : sections) {
            if (s.key().equals("platform") && c.user().hasPermission(Permission.ANALYTICS_OPS_VIEW)) {
                s.kpis().add(live("pending_listings", "Listings awaiting verification",
                        "select count(*) from properties where verification_status = 'PENDING'", "COUNT", "DOWN",
                        "Listings waiting for EstateX to verify them, right now."));
                s.kpis().add(live("pending_sales", "Sales awaiting verification",
                        "select count(*) from sale_transactions where verification = 'REPORTED'", "COUNT", "DOWN",
                        "Reported sales waiting for conveyancer or Deeds Registry evidence to be checked."));
                s.kpis().add(live("active_agencies", "Active agencies",
                        "select count(*) from organisations where active = true and (approval_status is null or approval_status = 'APPROVED')",
                        "COUNT", "UP", "Agencies approved by EstateX and active, right now."));
            }
            if (s.key().equals("business") && c.user().hasPermission(Permission.ANALYTICS_FINANCE_VIEW)) {
                s.kpis().add(live("past_due", "Subscriptions past due",
                        "select count(*) from landlord_subscriptions where status = 'PAST_DUE'", "COUNT", "DOWN",
                        "Subscriptions whose renewal payment hasn't come in."));
                s.kpis().add(unavailable("lead_revenue", "Lead generation revenue", "No lead-generation product exists yet."));
                s.kpis().add(unavailable("developer_ads", "Developer advertising", "No developer advertising product exists yet."));
                s.kpis().add(unavailable("pm_revenue", "Property management fees", "Property management fees aren't charged through EstateX yet."));
            }
            if (s.key().equals("activity") && c.user().hasPermission(Permission.ANALYTICS_DEMAND_VIEW)) {
                s.kpis().add(unavailable("agent_responses", "Agent responses",
                        "Agent replies to enquiries aren't recorded yet, so response rates can't be measured."));
            }
        }
    }

    private Kpi live(String key, String label, String sql, String unit, String better, String help) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class);
        return new Kpi(key, label, help, null, null, null, unit, better, v == null ? BigDecimal.ZERO : v, null, null, null,
                "NONE", false, "Live count; no history is kept, so there's no comparison.", null, false, true, true, false, false,
                List.of());
    }

    private static Kpi unavailable(String key, String label, String why) {
        return new Kpi(key, label, why, null, null, null, "USD", "UP", null, null, null, null, "NONE", false, why, null,
                false, false, false, false, false, List.of());
    }

    private static String noun(Grain g) {
        return switch (g) {
            case HOUR -> "hour";
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
            case QUARTER -> "quarter";
            case YEAR -> "year";
        };
    }

    public static String label(Period p, ZoneId zone, boolean soFar) {
        var s = p.start().atZone(zone);
        String base = switch (p.grain()) {
            case HOUR -> s.format(DateTimeFormatter.ofPattern("d MMM, HH:00", Locale.ENGLISH));
            case DAY -> s.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH));
            case WEEK -> "Week of " + s.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
            case MONTH -> s.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
            case QUARTER -> "Q" + ((s.getMonthValue() - 1) / 3 + 1) + " " + s.getYear();
            case YEAR -> String.valueOf(s.getYear());
        };
        return soFar ? base + " (so far)" : base;
    }
}
