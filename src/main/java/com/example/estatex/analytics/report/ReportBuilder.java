package com.example.estatex.analytics.report;

import com.example.estatex.analytics.alert.AlertKind;
import com.example.estatex.analytics.alert.AnalyticsAlert;
import com.example.estatex.analytics.alert.AnalyticsAlertRepository;
import com.example.estatex.analytics.api.OverviewService;
import com.example.estatex.analytics.insight.InsightsService;
import com.example.estatex.analytics.insight.MarketInsight;
import com.example.estatex.analytics.insight.MarketInsightRepository;
import com.example.estatex.analytics.insight.MarketObservation;
import com.example.estatex.analytics.insight.MarketObservationRepository;
import com.example.estatex.analytics.insight.ObservationService;
import com.example.estatex.analytics.market.GeoService;
import com.example.estatex.analytics.market.MarketDashboardService;
import com.example.estatex.analytics.market.MarketFilter;
import com.example.estatex.analytics.market.RevenueService;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.analytics.report.ReportDocument.Column;
import com.example.estatex.analytics.report.ReportDocument.Section;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.property.PropertyCategory;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds each {@link ReportType} from the same services the dashboards use, so a report always
 * matches what the screen showed for the same period and filters. Only agent, developer and
 * investment reports read source tables directly (no dashboard covers them yet).
 */
@Service
public class ReportBuilder {
    static final int MAX_DAYS = 3 * 366;
    private static final int MAX_PERFORMERS = 200;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private final OverviewService overview;
    private final MarketDashboardService market;
    private final GeoService geo;
    private final RevenueService revenue;
    private final MarketObservationRepository observations;
    private final MarketInsightRepository insights;
    private final AnalyticsAlertRepository alerts;
    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final ObjectMapper json;

    public ReportBuilder(OverviewService overview, MarketDashboardService market, GeoService geo,
                         RevenueService revenue, MarketObservationRepository observations, MarketInsightRepository insights,
                         AnalyticsAlertRepository alerts, JdbcTemplate jdbc, BusinessTime time, ObjectMapper json) {
        this.overview = overview;
        this.market = market;
        this.geo = geo;
        this.revenue = revenue;
        this.observations = observations;
        this.insights = insights;
        this.alerts = alerts;
        this.jdbc = jdbc;
        this.time = time;
        this.json = json;
    }

    /** What to report on. Nulls mean "the default": the last completed period, all areas. */
    public record Request(ReportType type, LocalDate from, LocalDate to, String city, String suburb, String category) {
    }

    public ReportDocument build(Request r, AppUser user, String generatedBy) {
        if (!r.type().allowedFor(user)) throw new ApiException(HttpStatus.FORBIDDEN, "Your role can't produce this report.");
        LocalDate[] range = range(r.type(), r.from(), r.to());
        String city = blank(r.city());
        String suburb = city == null ? null : blank(r.suburb());
        String category = category(r.category());
        Ctx c = new Ctx(r.type(), range[0], range[1], city, suburb, category, user, generatedBy, time.zone(), time.clock().instant());
        return switch (r.type()) {
            case DAILY_PLATFORM -> daily(c);
            case WEEKLY_RENTAL -> marketReport(c, List.of("RENT"), Grain.DAY);
            case MONTHLY_SALES -> marketReport(c, List.of("SALE"), Grain.WEEK);
            case ANNUAL_MARKET -> marketReport(c, List.of("RENT", "SALE"), Grain.MONTH);
            case QUARTERLY_INTELLIGENCE -> intelligence(c);
            case GEOGRAPHIC -> geographic(c);
            case REVENUE -> revenue(c);
            case AGENT_PERFORMANCE -> performance(c, "AGENT");
            case DEVELOPER_PERFORMANCE -> performance(c, "DEVELOPER");
            case INVESTMENT_INTELLIGENCE -> investment(c);
        };
    }

    /** The report's period: as asked, or the last completed day/week/month/quarter/year. */
    LocalDate[] range(ReportType type, LocalDate from, LocalDate to) {
        ZoneId zone = time.zone();
        if (from == null && to == null) {
            Period last = Period.containing(type.grain(), time.clock().instant(), zone).previous(zone);
            return new LocalDate[]{last.start().atZone(zone).toLocalDate(), last.end().atZone(zone).toLocalDate().minusDays(1)};
        }
        LocalDate a = from != null ? from : to;
        LocalDate b = type == ReportType.DAILY_PLATFORM ? a : to != null ? to : time.today();
        if (a.isAfter(b)) throw new ApiException(HttpStatus.BAD_REQUEST, "The start date must be on or before the end date.");
        if (ChronoUnit.DAYS.between(a, b) + 1 > MAX_DAYS) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period of up to three years.");
        if (a.isAfter(time.today())) throw new ApiException(HttpStatus.BAD_REQUEST, "The period can't start in the future.");
        return new LocalDate[]{a, b};
    }

    record Ctx(ReportType type, LocalDate from, LocalDate to, String city, String suburb, String category, AppUser user,
               String generatedBy, ZoneId zone, Instant now) {
        Instant start() {
            return from.atStartOfDay(zone).toInstant();
        }

        Instant end() {
            return to.plusDays(1).atStartOfDay(zone).toInstant();
        }

        String period() {
            return from.equals(to) ? DAY.format(from) : DAY.format(from) + " to " + DAY.format(to);
        }

        String filters() {
            List<String> parts = new ArrayList<>();
            if (city != null) parts.add("City: " + city);
            if (suburb != null) parts.add("Suburb: " + suburb);
            if (category != null) parts.add("Category: " + label(category));
            return parts.isEmpty() ? "All areas and property categories" : String.join("; ", parts);
        }

        ReportDocument doc(String subtitle, String periodLabel, List<Section> sections, List<String> coverage, List<String> methodology,
                           List<String> notes) {
            return new ReportDocument(type, type.label(), subtitle, now, generatedBy, from, to, periodLabel, filters(), sections,
                    coverage, methodology, notes);
        }
    }

    // ---- Daily platform ------------------------------------------------------------------------

    private ReportDocument daily(Ctx c) {
        Instant noon = c.from().atStartOfDay(c.zone()).plusHours(12).toInstant();
        OverviewService.Overview o = overview.overview(c.user(), Grain.DAY, OverviewService.Compare.FULL,
                new OverviewService.Location(c.city(), c.suburb()), noon);
        List<Section> sections = new ArrayList<>();
        List<String> coverage = new ArrayList<>();
        Set<String> methods = new LinkedHashSet<>();
        for (OverviewService.Section s : o.sections()) {
            if (s.restricted()) {
                coverage.add("\"" + s.title() + "\" is left out: your role can't see it.");
                continue;
            }
            List<List<Object>> rows = new ArrayList<>();
            for (OverviewService.Kpi k : s.kpis()) {
                if (!k.available()) continue;
                rows.add(row(k.label(), unitLabel(k.unit()), k.value(), k.comparable() ? k.previous() : null,
                        k.comparable() ? k.changePercent() : null, k.sampleSize(), note(k.note(), k.lowSample() ? "low sample" : null,
                                c.city() != null && !k.locationApplies() ? "platform-wide (no location)" : null)));
                if (k.help() != null) methods.add(k.label() + ": " + k.help());
            }
            sections.add(new Section(s.title(), s.subtitle(), List.of(Column.text("Measure"), Column.text("Unit"), Column.num("Value"),
                    Column.num("Previous day"), Column.pct("Change"), Column.count("n"), Column.text("Note")), rows,
                    "No figures were published for this day."));
        }
        engineStatus(coverage, o.freshness());
        coverage.add("Figures are those published by the analytics engine; a day is complete once the engine has run after midnight (Harare).");
        List<String> method = new ArrayList<>();
        method.add(o.comparisonLabel() + ". A blank change means the earlier day can't be compared fairly (for example, recording "
                + "began part-way through it).");
        method.addAll(methods);
        return c.doc(o.period().label(), o.period().label(), sections, coverage, method, List.of());
    }

    private static void engineStatus(List<String> coverage, com.example.estatex.analytics.engine.AnalyticsEngineStatusService.Status f) {
        coverage.add("Analytics engine: " + f.state().toLowerCase(Locale.ROOT).replace('_', ' ')
                + (f.lastSuccessAt() != null ? "; last successful update " + f.lastSuccessAt() : "; no successful update yet")
                + (f.stale() ? " (STALE: figures may be out of date)" : "") + ".");
    }

    // ---- Rental / sales / annual market --------------------------------------------------------

    private ReportDocument marketReport(Ctx c, List<String> types, Grain grain) {
        List<Section> sections = new ArrayList<>();
        List<String> coverage = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        boolean includeDemand = c.user().hasPermission(Permission.ANALYTICS_DEMAND_VIEW);
        for (String type : types) {
            String noun = type.equals("RENT") ? "Rentals" : "Sales";
            String prefix = types.size() > 1 ? noun + " - " : "";
            MarketFilter f = new MarketFilter(type, null, c.city(), c.suburb(), c.category(), null, null, null);
            MarketDashboardService.Dashboard d = market.dashboard(new MarketDashboardService.Query(type, c.from(), c.to(), grain, f, includeDemand));
            sections.add(new Section(prefix + "Key figures", null, List.of(Column.text("Measure"), Column.text("Unit"), Column.num("Value"),
                    Column.num("Previous period"), Column.pct("Change"), Column.count("n"), Column.text("Note")),
                    d.kpis().stream().map(k -> row(k.label(), unitLabel(k.unit()), k.value(), k.previous(), k.changePercent(), k.sampleSize(),
                            note(k.note(), k.lowSample() ? "low sample" : null))).toList(), null));
            sections.add(series(prefix + "By " + grain.name().toLowerCase(Locale.ROOT), d.series().stream()
                    .map(p -> new SeriesPoint(p.label() + (p.complete() ? "" : " (so far)"), p.values())).toList()));
            sections.add(groups(prefix + "By suburb", d.bySuburb(), true));
            sections.add(groups(prefix + "By property category", d.byCategory(), false));
            sections.add(groups(prefix + "By bedrooms", d.byBedrooms(), false));
            if (type.equals("SALE")) sections.add(groups(prefix + "By price band", d.byPriceBand(), false));
            if (type.equals("RENT") && d.yields() != null) {
                List<List<Object>> rows = new ArrayList<>();
                if (d.yields().overall() != null) rows.add(yieldRow(d.yields().overall(), "All areas"));
                for (MarketDashboardService.YieldRow y : d.yields().bySuburb()) rows.add(yieldRow(y, null));
                sections.add(new Section(prefix + "Indicative yields", "Gross yield = median asking rent x 12 / median asking sale price. "
                        + "Net yield deducts the cost ratio of " + ratio(d.yields().costRatio()) + (d.yields().costRatioConfirmed() ? "" : " (default, not yet confirmed by an administrator)") + ".",
                        List.of(Column.text("Suburb"), Column.text("City"), Column.usd("Median asking rent"), Column.count("Rents (n)"),
                                Column.usd("Median asking sale price"), Column.count("Sales (n)"), Column.pct("Gross yield"), Column.pct("Net yield"),
                                Column.text("Note")), rows, "Not enough rent and sale prices in the same suburbs to estimate yields."));
            }
            coverage.add(noun + ": listing figures from the daily snapshot of " + d.snapshotDate() + "; figures calculated "
                    + d.figuresCalculatedAt() + (d.historyStartsAt() != null ? "; listing history recorded since " + d.historyStartsAt() : "")
                    + (d.demandIncluded() ? "" : "; demand figures left out (your role can't see demand)") + ".");
            for (String n : d.notes()) if (!notes.contains(n)) notes.add(n);
        }
        return c.doc(null, c.period(), sections, coverage, List.of(
                "Asking prices are what landlords and sellers ask; agreed rents come from signed leases. They are always separate figures.",
                "A sale counts as verified only when EstateX verified it from conveyancer or Deeds Registry evidence; agent or owner reports are shown as reported.",
                "Prices are medians (half above, half below). Figures from fewer than " + MarketDashboardService.MIN_SAMPLE
                        + " listings are marked low sample.",
                "A blank cell means no data for that period, never zero."), notes);
    }

    private record SeriesPoint(String label, Map<String, BigDecimal> values) {
    }

    private static Section series(String title, List<SeriesPoint> points) {
        List<String> keys = new ArrayList<>();
        for (SeriesPoint p : points) for (String k : p.values().keySet()) if (!keys.contains(k)) keys.add(k);
        List<Column> cols = new ArrayList<>();
        cols.add(Column.text("Period"));
        for (String k : keys) cols.add(new Column(humanise(k), unitOfKey(k)));
        List<List<Object>> rows = new ArrayList<>();
        for (SeriesPoint p : points) {
            List<Object> row = new ArrayList<>();
            row.add(p.label());
            for (String k : keys) row.add(p.values().get(k));
            rows.add(row);
        }
        return new Section(title, null, cols, rows, "No periods in range.");
    }

    private static Section groups(String title, List<MarketDashboardService.Group> groups, boolean withCity) {
        List<Column> cols = new ArrayList<>(List.of(Column.text(withCity ? "Suburb" : "Group")));
        if (withCity) cols.add(Column.text("City"));
        cols.addAll(List.of(Column.count("Active"), Column.usd("Median asking"), Column.count("Prices (n)"), Column.text("Low sample"),
                Column.count("New listings"), Column.count("Demand"), Column.num("Demand per listing"), Column.count("Transactions"),
                Column.usd("Median transaction"), Column.count("Transactions (n)")));
        List<List<Object>> rows = new ArrayList<>();
        for (MarketDashboardService.Group g : groups) {
            List<Object> row = new ArrayList<>();
            row.add(g.label());
            if (withCity) row.add(g.city());
            row.addAll(Arrays.asList(g.active(), g.askingMedian(), g.askingN(), g.lowSample() ? "yes" : "", g.newListings(), g.demand(),
                    g.demandPerListing(), g.transactions(), g.transactionMedian(), g.transactionN()));
            rows.add(row);
        }
        return new Section(title, null, cols, rows, "No listings match.");
    }

    private static List<Object> yieldRow(MarketDashboardService.YieldRow y, String label) {
        return row(label != null ? label : y.suburb(), label != null ? "" : y.city(), y.rentMedian(), y.rentN(), y.saleMedian(), y.saleN(),
                y.grossYield(), y.netYield(), y.note());
    }

    // ---- Quarterly intelligence ----------------------------------------------------------------

    private ReportDocument intelligence(Ctx c) {
        Set<String> domains = InsightsService.visibleDomains(c.user());
        List<MarketObservation> obs = observations
                .findByPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAscIdAsc(c.start(), c.end()).stream()
                .filter(o -> domains.contains(o.getDomain()))
                .filter(o -> c.city() == null || c.city().equalsIgnoreCase(o.getCity()))
                .filter(o -> c.suburb() == null || c.suburb().equalsIgnoreCase(o.getSuburb()))
                .filter(o -> c.category() == null || c.category().equalsIgnoreCase(o.getCategory()))
                .toList();
        List<List<Object>> rows = new ArrayList<>();
        for (MarketObservation o : obs) {
            rows.add(row(o.getPeriodStart().atZone(c.zone()).toLocalDate(), o.getGrain().equals("WEEK") ? "Week" : "Month",
                    o.getStatement(), location(o), method(o.getMethod()), o.getSampleCurrent(),
                    o.getComparisonStart() == null ? null : o.getSamplePrevious(), o.getChangePct(), o.getQValue(), o.getConfidence(), o.getId()));
        }
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Observed changes", "Changes that passed their test after correcting for every comparison made in the run.",
                List.of(Column.text("Period starting"), Column.text("Grain"), Column.text("Observation"), Column.text("Location"),
                        Column.text("Test"), Column.count("n (period)"), Column.count("n (before)"), Column.pct("Change"),
                        Column.num("Adjusted q"), Column.text("Confidence"), Column.count("Ref")), rows,
                "No change passed the tests in this period, so no reliable conclusion can be drawn."));

        Set<Long> visibleIds = new HashSet<>();
        obs.forEach(o -> visibleIds.add(o.getId()));
        List<MarketInsight> written = insights.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc("OK", c.start(), c.end());
        List<List<Object>> ai = new ArrayList<>();
        String aiNote = null;
        if (!written.isEmpty()) {
            MarketInsight latest = written.get(0);
            List<Map<String, Object>> items = json.readValue(latest.getItemsJson() == null ? "[]" : latest.getItemsJson(),
                    new TypeReference<List<Map<String, Object>>>() { });
            int hidden = 0;
            for (Map<String, Object> m : items) {
                List<Long> ids = ((List<?>) m.getOrDefault("observationIds", List.of())).stream().map(x -> ((Number) x).longValue()).toList();
                if (ids.isEmpty() || !visibleIds.containsAll(ids)) {
                    hidden++;
                    continue;
                }
                ai.add(row(m.get("section"), m.get("title"), m.get("interpretation"), m.get("implication"),
                        String.join(", ", ids.stream().map(i -> "#" + i).toList())));
            }
            aiNote = "Written " + latest.getCreatedAt() + " by " + latest.getModel() + (latest.getHeadline() != null ? ". Headline: " + latest.getHeadline() : "")
                    + (hidden > 0 ? ". " + hidden + " item(s) rest on observations outside this report's scope or your role and are left out." : "");
        }
        sections.add(new Section("AI interpretation", aiNote == null ? null : "AI interpretation of the observations above, not a finding. " + aiNote,
                List.of(Column.text("Section"), Column.text("Title"), Column.text("Interpretation"), Column.text("Implication"),
                        Column.text("Based on")), ai, "No AI interpretation was written in this period."));

        Map<String, int[]> byRule = new LinkedHashMap<>();
        for (AnalyticsAlert a : alerts.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDomainInOrderByCreatedAtAsc(c.start(), c.end(), domains)) {
            int[] n = byRule.computeIfAbsent(a.getRuleKey(), k -> new int[2]);
            n[0]++;
            if (a.getAcknowledgedAt() == null) n[1]++;
        }
        List<List<Object>> alertRows = new ArrayList<>();
        byRule.forEach((k, n) -> alertRows.add(row(ruleLabel(k), n[0], n[1])));
        sections.add(new Section("Alerts raised", null, List.of(Column.text("Alert"), Column.count("Raised"), Column.count("Still open")),
                alertRows, "No alerts were raised in this period."));

        Instant first = jdbc.query("select min(first_seen_at) from market_observations",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
        List<String> coverage = new ArrayList<>();
        coverage.add(first == null ? "No observations have been produced yet." : "Observations have been produced since " + first
                + "; earlier periods were never analysed.");
        coverage.add("Only domains your role can see are included (" + String.join(", ", domains).toLowerCase(Locale.ROOT) + ").");
        return c.doc(null, c.period(), sections, coverage, List.of(
                "Counts (enquiries, views, new listings, failed payments, cancellations): Poisson rate test adjusted for period length; at least "
                        + ObservationService.MIN_EVENTS + " events across the two periods.",
                "Medians (asking price, agreed rent): approximate z-test on the median; at least " + ObservationService.MIN_MEDIAN_N
                        + " prices in each period.",
                "Category demand vs supply: two-proportion z-test of a category's share of enquiries against its share of listings.",
                "All tests in a run share one Benjamini-Hochberg correction; only adjusted q <= " + ObservationService.MAX_Q + " is reported.",
                "Supply and yield changes are thresholds on stock figures, not significance tests, and are labelled as such.",
                "AI interpretations only see the observations and must cite them; they suggest what changes may mean and never establish causes."),
                List.of());
    }

    // ---- Geographic ------------------------------------------------------------------------------

    private ReportDocument geographic(Ctx c) {
        List<Section> sections = new ArrayList<>();
        List<String> coverage = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        LocalDate snapshot = null;
        int days = 0;
        boolean includeDemand = c.user().hasPermission(Permission.ANALYTICS_DEMAND_VIEW);
        for (String type : List.of("RENT", "SALE")) {
            GeoService.GeoData g = geo.suburbs(new MarketFilter(type, null, c.city(), null, c.category(), null, null, null), includeDemand);
            String noun = type.equals("RENT") ? "Rentals" : "Sales";
            List<List<Object>> rows = new ArrayList<>();
            for (GeoService.SuburbFigures s : g.suburbs()) {
                if (c.suburb() != null && !c.suburb().equalsIgnoreCase(s.suburb())) continue;
                rows.add(row(s.suburb(), s.city(), s.active(), s.askingMedian(), s.askingMean(), s.askingN(), s.confidence(), s.growthPercent(),
                        s.newListings(), s.demand(), s.demandPerListing(), s.searches(), s.transactions(), s.grossYield(), s.placedBy()));
            }
            sections.add(new Section(noun + " by suburb", null, List.of(Column.text("Suburb"), Column.text("City"), Column.count("Active"),
                    Column.usd("Median asking"), Column.usd("Mean asking"), Column.count("Prices (n)"), Column.text("Confidence"),
                    Column.pct("Asking growth"), Column.count("New listings"), Column.count("Demand"), Column.num("Demand per listing"),
                    Column.count("Searches"), Column.count("Transactions"), Column.pct("Gross yield"), Column.text("Map position from")),
                    rows, "No live listings with a suburb."));
            GeoService.Coverage cv = g.coverage();
            coverage.add(noun + ": " + cv.liveListings() + " live listings, " + cv.withSuburb() + " with a suburb, " + cv.placedOnMap()
                    + " placed on the map (" + cv.withOwnPin() + " with their own pin); " + cv.withoutSuburb() + " without a suburb and "
                    + cv.inUnplacedSuburbs() + " in suburbs that couldn't be placed are not in the suburb table.");
            snapshot = g.snapshotDate();
            days = g.activityDays();
            for (String n : g.notes()) if (!notes.contains(n)) notes.add(n);
        }
        String period = snapshot == null ? "No snapshot yet" : "Listings as at " + snapshot + "; activity over the " + days + " days before";
        LocalDate to = snapshot == null ? c.to() : snapshot;
        LocalDate from = snapshot == null ? c.from() : snapshot.minusDays(Math.max(0, days - 1));
        Ctx fixed = new Ctx(c.type(), from, to, c.city(), c.suburb(), c.category(), c.user(), c.generatedBy(), c.zone(), c.now());
        return fixed.doc("A snapshot report: it always describes the latest published day, whatever period is chosen.", period, sections,
                coverage, List.of("Suburb positions come from the listings' own map pins where available (median of the pins), never invented.",
                        "Growth compares the median asking price with the snapshot " + days + " days earlier.",
                        "Gross yield needs both rent and sale prices in the same suburb."), notes);
    }

    // ---- Revenue -----------------------------------------------------------------------------------

    private ReportDocument revenue(Ctx c) {
        long days = ChronoUnit.DAYS.between(c.from(), c.to()) + 1;
        Grain g = days <= 45 ? Grain.DAY : days <= 190 ? Grain.WEEK : Grain.MONTH;
        RevenueService.Revenue r = revenue.revenue(c.from(), c.to(), g);
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Key figures", "Cash, recognised, outstanding and estimated figures are never added together.",
                List.of(Column.text("Group"), Column.text("Measure"), Column.text("Unit"), Column.num("Value"), Column.text("Note")),
                r.kpis().stream().map(k -> row(capitalise(k.group()), k.label(), unitLabel(k.unit()), k.value(), k.note())).toList(), null));
        sections.add(series("By " + g.name().toLowerCase(Locale.ROOT), r.series().stream()
                .map(p -> new SeriesPoint(p.label() + (p.complete() ? "" : " (so far)"), p.values())).toList()));
        for (Object[] s : new Object[][]{{"By product", r.byProduct()}, {"By customer category", r.byCategory()}, {"By location", r.byLocation()}}) {
            @SuppressWarnings("unchecked") List<RevenueService.Slice> slices = (List<RevenueService.Slice>) s[1];
            sections.add(new Section((String) s[0], null, List.of(Column.text("Group"), Column.usd("Cash"), Column.usd("Recognised"),
                    Column.count("Payments")), slices.stream().map(x -> row(x.label(), x.cash(), x.recognised(), x.payments())).toList(),
                    "No revenue in this period."));
        }
        sections.add(new Section("Marketing spend", null, List.of(Column.text("Month"), Column.text("Channel"), Column.usd("Amount"),
                Column.text("Note")), r.spend().stream().map(x -> row(x.month().toString(), x.channel(), x.amount(), x.note())).toList(),
                "No marketing spend recorded for these months."));
        List<String> methodology = new ArrayList<>(r.kpis().stream().filter(k -> k.help() != null).map(k -> k.label() + ": " + k.help()).toList());
        methodology.add("Rent, deposits, escrow and other client money passing through EstateX are never counted as revenue.");
        return c.doc(null, c.period(), sections, List.of(
                r.subscriptionHistoryStartsAt() == null ? "Subscription history: not recorded yet." :
                        "Subscription figures (MRR, churn) are replayed from history recorded since " + r.subscriptionHistoryStartsAt() + ".",
                "Commission share counted as estimated revenue: " + pct(r.commissionSharePercent()) + "."), methodology, r.notes());
    }

    // ---- Agent / developer performance ---------------------------------------------------------

    private ReportDocument performance(Ctx c, String role) {
        Map<Long, String> names = new HashMap<>();
        jdbc.query("select u.id, u.full_name from users u join app_user_roles r on r.app_user_id = u.id where r.roles = ?",
                rs -> { names.put(rs.getLong(1), rs.getString(2)); }, role);
        // Who a listing counts for: its agent; or, failing that, its owner if the owner has this role.
        Map<Long, Long> performerOf = new HashMap<>();
        Map<Long, long[]> stats = new LinkedHashMap<>();   // live, verified live, new, enquiries, viewings, leases, reported, verified
        Instant start = c.start();
        Instant end = c.end();
        jdbc.query("select id, landlord_id, agent_id, status, verification_status, created_at, city, suburb, category, listing_type "
                + "from properties", rs -> {
            long id = rs.getLong(1);
            Long agent = (Long) rs.getObject(3, Long.class);
            long owner = rs.getLong(2);
            Long who = "AGENT".equals(role) ? (agent != null && names.containsKey(agent) ? agent : names.containsKey(owner) ? owner : null)
                    : names.containsKey(owner) ? owner : null;
            if (who == null) return;
            if (c.city() != null && !c.city().equalsIgnoreCase(trim(rs.getString(7)))) return;
            if (c.suburb() != null && !c.suburb().equalsIgnoreCase(trim(rs.getString(8)))) return;
            if (c.category() != null && !c.category().equalsIgnoreCase(rs.getString(9))) return;
            performerOf.put(id, who);
            long[] s = stats.computeIfAbsent(who, k -> new long[8]);
            String status = rs.getString(4);
            boolean live = "AVAILABLE".equals(status) || "UNDER_OFFER".equals(status);
            if (live) s[0]++;
            if (live && "VERIFIED".equals(rs.getString(5))) s[1]++;
            Timestamp created = rs.getTimestamp(6);
            if (created != null && !created.toInstant().isBefore(start) && created.toInstant().isBefore(end)) s[2]++;
        });
        countPerProperty("select property_id from enquiries where created_at >= ? and created_at < ?", start, end, performerOf, stats, 3);
        countPerProperty("select property_id from property_inquiries where created_at >= ? and created_at < ?", start, end, performerOf, stats, 3);
        countPerProperty("select property_id from viewing_requests where created_at >= ? and created_at < ?", start, end, performerOf, stats, 4);
        countPerProperty("select property_id from leases where signed_at >= ? and signed_at < ?", start, end, performerOf, stats, 5);
        countPerProperty("select property_id from sale_transactions where reported_at >= ? and reported_at < ?", start, end, performerOf, stats, 6);
        countPerProperty("select property_id from sale_transactions where verification = 'VERIFIED' and verified_at >= ? and verified_at < ?",
                start, end, performerOf, stats, 7);

        List<Map.Entry<Long, long[]>> ranked = new ArrayList<>(stats.entrySet());
        ranked.sort((a, b) -> {
            int cmp = Long.compare(b.getValue()[3], a.getValue()[3]);
            return cmp != 0 ? cmp : Long.compare(b.getValue()[0], a.getValue()[0]);
        });
        List<List<Object>> rows = new ArrayList<>();
        long[] total = new long[8];
        for (Map.Entry<Long, long[]> e : ranked) {
            long[] s = e.getValue();
            for (int i = 0; i < 8; i++) total[i] += s[i];
            if (rows.size() < MAX_PERFORMERS) {
                rows.add(row(names.getOrDefault(e.getKey(), "User #" + e.getKey()), e.getKey(), s[0], s[1], s[2], s[3],
                        s[0] == 0 ? null : BigDecimal.valueOf(s[3]).divide(BigDecimal.valueOf(s[0]), 2, RoundingMode.HALF_UP), s[4], s[5], s[6], s[7]));
            }
        }
        String who = "AGENT".equals(role) ? "Agent" : "Developer";
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Totals", null, List.of(Column.text("Measure"), Column.count("Value")), List.of(
                row(who + "s with listings", (long) stats.size()), row("Live listings", total[0]), row("Verified live listings", total[1]),
                row("New listings in period", total[2]), row("Enquiries", total[3]), row("Viewing requests", total[4]),
                row("Leases signed", total[5]), row("Sales reported", total[6]), row("Sales verified", total[7])), null));
        sections.add(new Section(who + "s", "Ranked by enquiries received" + (ranked.size() > MAX_PERFORMERS ? "; top " + MAX_PERFORMERS + " shown" : "") + ".",
                List.of(Column.text(who), Column.count("User id"), Column.count("Live listings"), Column.count("Verified live"),
                        Column.count("New listings"), Column.count("Enquiries"), Column.num("Enquiries per live listing"),
                        Column.count("Viewing requests"), Column.count("Leases signed"), Column.count("Sales reported"), Column.count("Sales verified")),
                rows, "No " + who.toLowerCase(Locale.ROOT) + " had listings matching these filters."));
        List<String> methodology = new ArrayList<>(List.of(
                "AGENT".equals(role) ? "A listing counts for its assigned agent; if it has none, for its owner when the owner is an agent."
                        : "A listing counts for its owner when the owner has the developer role.",
                "Live and verified listings are as at the time the report was generated; everything else is counted within the period.",
                "Enquiries include both enquiry forms and property questions. Leases are counted when signed.",
                "Reported sales are as told by the agent or owner; only sales EstateX verified from conveyancer or Deeds Registry evidence count as verified."));
        if ("DEVELOPER".equals(role)) {
            List<List<Object>> projects = new ArrayList<>();
            jdbc.query("select owner_id, count(*), sum(case when status = 'COMPLETED' then 1 else 0 end), "
                    + "sum(case when status in ('DELAYED', 'PAUSED') then 1 else 0 end), avg(progress_percent), "
                    + "sum(case when currency = 'USD' then budget_total else 0 end), sum(case when currency = 'USD' then amount_paid else 0 end) "
                    + "from construction_projects group by owner_id", rs -> {
                long owner = rs.getLong(1);
                if (!names.containsKey(owner)) return;
                projects.add(row(names.get(owner), owner, rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getBigDecimal(5) == null ? null : rs.getBigDecimal(5).setScale(0, RoundingMode.HALF_UP),
                        rs.getBigDecimal(6), rs.getBigDecimal(7)));
            });
            sections.add(new Section("Construction projects", "All projects on EstateX, whatever the period.",
                    List.of(Column.text("Developer"), Column.count("User id"), Column.count("Projects"), Column.count("Completed"),
                            Column.count("Delayed or paused"), Column.pct("Average progress"), Column.usd("Budget"), Column.usd("Paid so far")),
                    projects, "No developer has a construction project on EstateX."));
            methodology.add("Construction budgets and payments are as entered by the developer, in USD only (other currencies are left out).");
        }
        return c.doc(null, c.period(), sections, List.of(
                "Built from listings, enquiries, viewing requests, leases and sale records on EstateX only; activity off the platform isn't visible.",
                "Names are shown as registered; contact details are left out of reports."), methodology, List.of());
    }

    private void countPerProperty(String sql, Instant start, Instant end, Map<Long, Long> performerOf, Map<Long, long[]> stats, int slot) {
        jdbc.query(sql, rs -> {
            Long who = performerOf.get(rs.getLong(1));
            if (who != null) stats.get(who)[slot]++;
        }, Timestamp.from(start), Timestamp.from(end));
    }

    // ---- Investment intelligence ---------------------------------------------------------------

    private ReportDocument investment(Ctx c) {
        Map<Long, long[]> counts = new HashMap<>();
        Map<Long, BigDecimal> amounts = new HashMap<>();
        Map<Long, Set<Long>> investors = new HashMap<>();
        jdbc.query("select reit_id, investor_id, amount, status, currency from investments where created_at >= ? and created_at < ?", rs -> {
            long reit = rs.getLong(1);
            String status = rs.getString(4);
            if ("CANCELLED".equals(status)) return;
            counts.computeIfAbsent(reit, k -> new long[1])[0]++;
            if ("USD".equalsIgnoreCase(rs.getString(5)) && rs.getBigDecimal(3) != null) amounts.merge(reit, rs.getBigDecimal(3), BigDecimal::add);
            investors.computeIfAbsent(reit, k -> new HashSet<>()).add(rs.getLong(2));
        }, Timestamp.from(c.start()), Timestamp.from(c.end()));
        List<List<Object>> reits = new ArrayList<>();
        jdbc.query("select id, name, property_type, risk_level, unit_price, projected_annual_yield, total_units, units_sold, active from reits "
                + "order by name", rs -> {
            long id = rs.getLong(1);
            BigDecimal total = rs.getBigDecimal(7);
            BigDecimal sold = rs.getBigDecimal(8);
            BigDecimal taken = total == null || total.signum() == 0 || sold == null ? null
                    : sold.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            reits.add(row(rs.getString(2), label(rs.getString(3)), label(rs.getString(4)), rs.getBigDecimal(5), rs.getBigDecimal(6), taken,
                    counts.containsKey(id) ? counts.get(id)[0] : 0L, amounts.getOrDefault(id, BigDecimal.ZERO),
                    investors.containsKey(id) ? investors.get(id).size() : 0, rs.getBoolean(9) ? "open" : "closed"));
        });
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("REIT offerings", null, List.of(Column.text("REIT"), Column.text("Property type"), Column.text("Risk"),
                Column.usd("Unit price"), Column.pct("Projected annual yield"), Column.pct("Units taken up"), Column.count("New investments"),
                Column.usd("Amount invested"), Column.count("Investors"), Column.text("Status")), reits, "No REITs are listed on EstateX."));
        MarketDashboardService.Dashboard rent = market.dashboard(new MarketDashboardService.Query("RENT", c.from(), c.to(), Grain.MONTH,
                new MarketFilter("RENT", null, c.city(), c.suburb(), c.category(), null, null, null), false));
        List<List<Object>> yields = new ArrayList<>();
        if (rent.yields() != null) {
            if (rent.yields().overall() != null) yields.add(yieldRow(rent.yields().overall(), "All areas"));
            rent.yields().bySuburb().forEach(y -> yields.add(yieldRow(y, null)));
        }
        sections.add(new Section("Indicative rental yields", "From asking prices on EstateX, as at " + (rent.yields() == null ? "-" : rent.yields().asOf()) + ".",
                List.of(Column.text("Suburb"), Column.text("City"), Column.usd("Median asking rent"), Column.count("Rents (n)"),
                        Column.usd("Median asking sale price"), Column.count("Sales (n)"), Column.pct("Gross yield"), Column.pct("Net yield"),
                        Column.text("Note")), yields, "Not enough rent and sale prices in the same suburbs to estimate yields."));
        return c.doc(null, c.period(), sections, List.of(
                "Investments are counted by the date they were made; cancelled ones are left out; amounts are USD only.",
                "Units taken up is the REIT's units sold as a share of its total units, now (not for the period)."), List.of(
                "Projected yields are the issuer's stated projections, not returns measured by EstateX.",
                "Rental yields are indicative: median asking rent x 12 / median asking sale price, before costs (gross) and after the "
                        + "configured cost ratio (net). Agreed prices may differ."), List.of());
    }

    // ---- Helpers -------------------------------------------------------------------------------

    static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    private static String note(String... parts) {
        String s = String.join("; ", Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).toList());
        return s.isEmpty() ? null : s;
    }

    private static String unitLabel(String unit) {
        if (unit == null) return "";
        return switch (unit) {
            case "USD" -> "USD";
            case "PERCENT" -> "%";
            case "COUNT" -> "count";
            case "DAYS" -> "days";
            case "MONTHS" -> "months";
            default -> unit.toLowerCase(Locale.ROOT);
        };
    }

    private static ReportDocument.Unit unitOfKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.contains("percent") || k.contains("yield") || k.contains("rate") || k.endsWith("pct")) return ReportDocument.Unit.PERCENT;
        if (k.contains("median") || k.contains("mean") || k.contains("mrr") || k.contains("cash") || k.contains("recognised")
                || k.contains("revenue") || k.contains("spend") || k.contains("price") || k.contains("rent") || k.contains("amount")) {
            return ReportDocument.Unit.USD;
        }
        return ReportDocument.Unit.NUMBER;
    }

    /** "askingMedian" → "Asking median". */
    static String humanise(String key) {
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
        spaced = spaced.replace("mrr", "MRR");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String location(MarketObservation o) {
        String place = o.getSuburb() != null ? o.getSuburb() + ", " + o.getCity() : o.getCity() != null ? o.getCity() : "All areas";
        List<String> extra = new ArrayList<>();
        if (o.getListingType() != null) extra.add(o.getListingType().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (o.getCategory() != null) extra.add(label(o.getCategory()));
        return extra.isEmpty() ? place : place + " (" + String.join(", ", extra) + ")";
    }

    private static String method(String m) {
        return switch (m) {
            case "POISSON_RATE" -> "Poisson rate test";
            case "MEDIAN_SHIFT" -> "Median z-test";
            case "SHARE_GAP" -> "Share-gap z-test";
            case "THRESHOLD" -> "Threshold (no test)";
            case "DESCRIPTIVE" -> "Count (no test)";
            default -> m;
        };
    }

    private static String ruleLabel(String key) {
        try {
            return AlertKind.valueOf(key).label();
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    /** A value already in percent (10 means 10%). */
    private static String pct(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString() + "%";
    }

    /** A fraction shown as a percent (0.25 means 25%). */
    private static String ratio(BigDecimal v) {
        return v == null ? "-" : pct(v.multiply(BigDecimal.valueOf(100)));
    }

    static String label(String enumName) {
        if (enumName == null) return null;
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String capitalise(String s) {
        return s == null || s.isEmpty() ? s : label(s);
    }

    private static String blank(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    static String category(String c) {
        String b = blank(c);
        if (b == null) return null;
        try {
            return PropertyCategory.valueOf(b.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown property category " + c);
        }
    }
}
