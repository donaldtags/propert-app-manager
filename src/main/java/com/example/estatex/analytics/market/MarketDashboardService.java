package com.example.estatex.analytics.market;

import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.ListingState;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.analytics.metrics.StateReader;
import com.example.estatex.analytics.metrics.StateReader.Event;
import com.example.estatex.analytics.metrics.Stats;
import com.example.estatex.common.BusinessTime;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The rental and sales market dashboards (docs/admin-analytics-plan.md Phase D).
 *
 * <p>Any combination of filters (place, property type, exact bedrooms, price range, dates) is
 * allowed, so figures are calculated on request rather than read from pre-calculated slices:
 * <ul>
 *   <li>stock (how many are listed, asking prices, vacancy) from the engine's daily listing
 *       snapshots, so they match the engine's last successful run;</li>
 *   <li>flows (new listings, withdrawals, price cuts, leases, sales, demand) from listing history
 *       and the transaction tables.</li>
 * </ul>
 * Every chart, table and figure on a page goes through the same {@link MarketFilter}, so they
 * always describe the same homes. Results are cached per filter set until the next engine run.
 *
 * <p>Asking prices and agreed prices are always separate figures, and a sale counts as verified
 * only when EstateX verified it from conveyancer or Deeds Registry evidence.
 */
@Service
public class MarketDashboardService {
    static final String COST_RATIO_KEY = "yield_cost_ratio";
    static final BigDecimal DEFAULT_COST_RATIO = new BigDecimal("0.25");
    public static final int MIN_SAMPLE = 5;
    private static final int MAX_GROUPS = 25;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Sale price bands, in USD; the last is open-ended. */
    static final long[] PRICE_BANDS = {0, 50_000, 100_000, 200_000, 500_000, 1_000_000};

    private final JdbcTemplate jdbc;
    private final StateReader reader;
    private final BusinessTime time;
    private final AnalyticsSettingRepository settings;
    private final Cache<String, Dashboard> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(500).build();

    MarketDashboardService(JdbcTemplate jdbc, StateReader reader, BusinessTime time, AnalyticsSettingRepository settings) {
        this.jdbc = jdbc;
        this.reader = reader;
        this.time = time;
        this.settings = settings;
    }

    public record Query(String type, LocalDate from, LocalDate to, Grain grain, MarketFilter filter, boolean demand) {
    }

    public record Kpi(String key, String label, String help, String unit, BigDecimal value, BigDecimal previous,
                      BigDecimal changePercent, Integer sampleSize, boolean lowSample, String note) {
    }

    /** One period on the charts. Missing keys mean "no data for this period", never zero. */
    public record Point(Instant start, Instant end, String label, boolean complete, LocalDate snapshotDate,
                        Map<String, BigDecimal> values) {
    }

    public record Group(String key, String label, String city, int active, BigDecimal askingMedian, int askingN,
                        boolean lowSample, int newListings, Integer demand, BigDecimal demandPerListing,
                        int transactions, BigDecimal transactionMedian, int transactionN) {
    }

    public record YieldRow(String suburb, String city, BigDecimal rentMedian, int rentN, BigDecimal saleMedian, int saleN,
                           BigDecimal grossYield, BigDecimal netYield, String note) {
    }

    public record Yields(YieldRow overall, List<YieldRow> bySuburb, BigDecimal costRatio, boolean costRatioConfirmed,
                         LocalDate asOf) {
    }

    public record Dashboard(String type, LocalDate from, LocalDate to, String grain, MarketFilter filter,
                            Instant historyStartsAt, LocalDate snapshotDate, Instant figuresCalculatedAt,
                            boolean demandIncluded, List<Kpi> kpis, List<Point> series, List<Group> bySuburb,
                            List<Group> byCategory, List<Group> byBedrooms, List<Group> byPriceBand, Yields yields,
                            List<String> notes) {
    }

    public Dashboard dashboard(Query q) {
        Long run = jdbc.queryForObject("select max(id) from analytics_job_runs where status = 'SUCCEEDED'", Long.class);
        String key = q + "#" + run;
        return cache.get(key, k -> build(q));
    }

    public void invalidate() {
        cache.invalidateAll();
    }

    // ---------------------------------------------------------------------------------------

    private Dashboard build(Query q) {
        ZoneId zone = time.zone();
        Instant now = time.clock().instant();
        boolean rent = "RENT".equals(q.type());
        MarketFilter f = q.filter().withType(q.type());
        Instant rangeStart = q.from().atStartOfDay(zone).toInstant();
        Instant rangeEndRaw = q.to().plusDays(1).atStartOfDay(zone).toInstant();
        Instant rangeEnd = rangeEndRaw.isAfter(now) ? now : rangeEndRaw;
        List<Bucket> buckets = buckets(q.grain(), rangeStart, rangeEnd, zone, now);
        List<String> notes = new ArrayList<>();

        // Stock: the snapshot closest to the end of each period.
        List<LocalDate> available = jdbc.query("select distinct snapshot_date from listing_daily_snapshots "
                        + "where snapshot_date >= ? and snapshot_date <= ? order by snapshot_date",
                (rs, i) -> rs.getDate(1).toLocalDate(), Date.valueOf(q.from()), Date.valueOf(q.to()));
        for (Bucket b : buckets) {
            LocalDate first = b.start.atZone(zone).toLocalDate();
            LocalDate last = b.end.minusMillis(1).atZone(zone).toLocalDate();
            for (LocalDate d : available) {
                if (!d.isBefore(first) && !d.isAfter(last)) b.snapshotDate = d;
            }
        }
        Set<LocalDate> wanted = new HashSet<>();
        buckets.forEach(b -> { if (b.snapshotDate != null) wanted.add(b.snapshotDate); });
        LocalDate latestSnapshot = available.isEmpty() ? null : available.get(available.size() - 1);
        if (latestSnapshot != null) wanted.add(latestSnapshot);
        Map<LocalDate, List<ListingState>> snaps = snapshots(wanted);

        for (Bucket b : buckets) {
            if (b.snapshotDate == null) continue;
            Stock s = stock(snaps.getOrDefault(b.snapshotDate, List.of()), f, rent);
            b.put("active", BigDecimal.valueOf(s.active));
            if (s.asking.n() > 0) {
                b.put("askingMedian", s.asking.median());
                b.put("askingP25", s.asking.p25());
                b.put("askingP75", s.asking.p75());
                b.put("askingMean", s.asking.mean());
                b.put("askingN", BigDecimal.valueOf(s.asking.n()));
            }
            if (s.perM2.n() > 0) b.put("perM2Median", s.perM2.median());
            if (rent && s.vacancy != null) b.put("vacancyPercent", s.vacancy);
        }

        // Flows across the range.
        Map<Long, ListingState> endState = reader.stateAsOf(rangeEnd);
        Map<Long, Event> firstAvailable = reader.firstAvailable();
        List<Event> events = reader.events(rangeStart, rangeEnd);
        Flows flows = new Flows();
        for (Event e : firstAvailable.values()) {
            if ("BASELINE".equals(e.type()) || e.occurredAt().isBefore(rangeStart) || !e.occurredAt().isBefore(rangeEnd)) continue;
            if (!f.matches(e.state())) continue;
            bucket(buckets, e.occurredAt()).add("newListings", BigDecimal.ONE);
            flows.newListings++;
            flows.newStates.add(e.state());
        }
        Set<String> seen = new HashSet<>();
        List<BigDecimal> cuts = new ArrayList<>();
        Set<Long> movedOn = new HashSet<>();
        Map<Long, Instant> endOfListing = new HashMap<>();
        Set<Long> liveDuring = new HashSet<>();
        reader.stateAsOf(rangeStart).values().forEach(s -> { if (s.available() && f.matches(s)) liveDuring.add(s.propertyId()); });
        for (Event e : events) {
            ListingState s = e.state();
            if (s.available() && f.matches(s)) liveDuring.add(e.propertyId());
            if (!f.place(s)) continue;
            Bucket b = bucket(buckets, e.occurredAt());
            if ("STATUS_CHANGED".equals(e.type())) {
                String st = s.status();
                if ("WITHDRAWN".equals(st) && seen.add("W" + e.propertyId())) { b.add("withdrawn", BigDecimal.ONE); flows.withdrawn++; }
                if ("EXPIRED".equals(st) && seen.add("E" + e.propertyId())) { b.add("expired", BigDecimal.ONE); flows.expired++; }
                boolean ended = rent ? ("OCCUPIED".equals(st) || "UNDER_OFFER".equals(st))
                        : ("UNDER_OFFER".equals(st) || "REPORTED_SOLD".equals(st));
                if (ended && f.priceIn(s.price())) {
                    endOfListing.merge(e.propertyId(), e.occurredAt(), (a, c) -> a.isBefore(c) ? a : c);
                    movedOn.add(e.propertyId());
                }
                if (!rent && "UNDER_OFFER".equals(st) && seen.add("U" + e.propertyId())) b.add("underOffer", BigDecimal.ONE);
                if (!rent && "REPORTED_SOLD".equals(st) && seen.add("R" + e.propertyId())) b.add("reportedSoldListings", BigDecimal.ONE);
            } else if ("PRICE_CHANGED".equals(e.type()) && f.matches(s)) {
                BigDecimal before = e.previousPrice();
                BigDecimal after = s.price();
                if (before != null && after != null && before.signum() > 0 && after.signum() > 0 && after.compareTo(before) < 0) {
                    BigDecimal pct = before.subtract(after).multiply(HUNDRED).divide(before, 2, RoundingMode.HALF_UP);
                    b.add("priceCuts", BigDecimal.ONE);
                    cuts.add(pct);
                }
            }
        }

        List<Tx> txs = new ArrayList<>();
        if (rent) {
            jdbc.query("select id, property_id, monthly_rent, currency, signed_at from leases where signed_at >= ? and signed_at < ?", rs -> {
                ListingState s = endState.get(rs.getLong("property_id"));
                BigDecimal amount = rs.getBigDecimal("monthly_rent");
                if (s == null || !f.withoutPrice().place(s) || !f.priceIn(amount)) return;
                Instant at = rs.getTimestamp("signed_at").toInstant();
                Bucket b = bucket(buckets, at);
                b.add("leasesSigned", BigDecimal.ONE);
                boolean usd = rs.getString("currency") == null || "USD".equalsIgnoreCase(rs.getString("currency"));
                if (usd && amount != null && amount.signum() > 0) {
                    b.contracted.add(amount);
                    txs.add(new Tx(s, amount));
                }
                endOfListing.merge(s.propertyId(), at, (a, c) -> a.isBefore(c) ? a : c);
                movedOn.add(s.propertyId());
            }, Timestamp.from(rangeStart), Timestamp.from(rangeEnd));
            for (Bucket b : buckets) {
                if (!b.contracted.isEmpty()) {
                    Stats.Summary s = Stats.summarise(b.contracted);
                    b.put("contractedMedian", s.median());
                    b.put("contractedN", BigDecimal.valueOf(s.n()));
                }
            }
        } else {
            jdbc.query("select id, property_id, price, currency, reported_at, completion_date, verification, country, city, suburb, "
                            + "category, bedrooms from sale_transactions where (reported_at >= ? and reported_at < ?) "
                            + "or (completion_date >= ? and completion_date <= ?)", rs -> {
                ListingState s = new ListingState(rs.getLong("property_id"), "SALE", null, null, rs.getString("country"),
                        rs.getString("city"), rs.getString("suburb"), rs.getString("category"), rs.getObject("bedrooms", Integer.class),
                        null, null, rs.getBigDecimal("price"), rs.getString("currency"), null, null, null);
                if (!f.withoutPrice().place(s) || !f.priceIn(s.price())) return;
                String verification = rs.getString("verification");
                Instant reported = rs.getTimestamp("reported_at").toInstant();
                if (!"REJECTED".equals(verification) && !reported.isBefore(rangeStart) && reported.isBefore(rangeEnd)) {
                    bucket(buckets, reported).add("salesReported", BigDecimal.ONE);
                    flows.reported++;
                }
                LocalDate done = rs.getDate("completion_date").toLocalDate();
                if ("VERIFIED".equals(verification) && !done.isBefore(q.from()) && !done.isAfter(q.to())
                        && "USD".equalsIgnoreCase(rs.getString("currency"))) {
                    Bucket b = bucket(buckets, done.atStartOfDay(zone).toInstant());
                    b.add("verifiedSales", BigDecimal.ONE);
                    b.contracted.add(s.price());
                    txs.add(new Tx(s, s.price()));
                }
            }, Timestamp.from(rangeStart), Timestamp.from(rangeEnd), Date.valueOf(q.from()), Date.valueOf(q.to()));
            for (Bucket b : buckets) {
                if (!b.contracted.isEmpty()) {
                    Stats.Summary s = Stats.summarise(b.contracted);
                    b.put("verifiedMedian", s.median());
                    b.put("verifiedValue", s.sum());
                }
            }
        }

        // Demand (only for people who may see demand analytics).
        Map<Long, Integer> demandByProperty = new HashMap<>();
        if (q.demand()) {
            demand(buckets, "select property_id, created_at at from enquiries where created_at >= ? and created_at < ?", "enquiries",
                    rangeStart, rangeEnd, endState, f, flows, demandByProperty);
            demand(buckets, "select property_id, created_at at from property_inquiries where created_at >= ? and created_at < ?", "enquiries",
                    rangeStart, rangeEnd, endState, f, flows, demandByProperty);
            demand(buckets, "select property_id, created_at at from viewing_requests where created_at >= ? and created_at < ?", "viewings",
                    rangeStart, rangeEnd, endState, f, flows, demandByProperty);
            if (rent) {
                jdbc.query("select property_id, submitted_at, status from rental_applications where submitted_at >= ? and submitted_at < ?", rs -> {
                    ListingState s = endState.get(rs.getLong("property_id"));
                    if (s == null || !f.matches(s)) return;
                    Bucket b = bucket(buckets, rs.getTimestamp("submitted_at").toInstant());
                    b.add("applications", BigDecimal.ONE);
                    flows.applications++;
                    String st = rs.getString("status");
                    if ("APPROVED".equals(st) || "LEASE_PREPARATION".equals(st)) {
                        b.add("applicationsApproved", BigDecimal.ONE);
                        flows.approved++;
                    }
                }, Timestamp.from(rangeStart), Timestamp.from(rangeEnd));
            } else {
                demand(buckets, "select property_id, created_at at from offers where created_at >= ? and created_at < ?", "offers",
                        rangeStart, rangeEnd, endState, f, flows, null);
            }
            for (Bucket b : buckets) {
                BigDecimal active = b.values.get("active");
                BigDecimal enq = b.values.getOrDefault("enquiries", BigDecimal.ZERO);
                if (active != null && active.signum() > 0) {
                    b.put("enquiriesPerListing", enq.divide(active, 2, RoundingMode.HALF_UP));
                }
            }
        }

        // Time to let / days on market: from first going live (when known) to the end of the listing.
        List<BigDecimal> daysToEnd = new ArrayList<>();
        endOfListing.forEach((pid, endAt) -> {
            Event start = firstAvailable.get(pid);
            if (start == null || "BASELINE".equals(start.type()) || !start.occurredAt().isBefore(endAt)) return;
            daysToEnd.add(BigDecimal.valueOf(Duration.between(start.occurredAt(), endAt).toMinutes())
                    .divide(BigDecimal.valueOf(1440), 1, RoundingMode.HALF_UP));
        });
        List<BigDecimal> stillListedAges = new ArrayList<>();
        for (ListingState s : endState.values()) {
            if (!s.available() || !f.matches(s)) continue;
            Event start = firstAvailable.get(s.propertyId());
            if (start == null || "BASELINE".equals(start.type())) continue;
            stillListedAges.add(BigDecimal.valueOf(Duration.between(start.occurredAt(), rangeEnd).toMinutes())
                    .divide(BigDecimal.valueOf(1440), 1, RoundingMode.HALF_UP));
        }

        // KPIs.
        Bucket firstStock = buckets.stream().filter(b -> b.snapshotDate != null).findFirst().orElse(null);
        Bucket lastStock = null;
        for (Bucket b : buckets) if (b.snapshotDate != null) lastStock = b;
        List<Kpi> kpis = new ArrayList<>();
        String unit = "USD";
        kpis.add(change("active", rent ? "Rental listings available" : "Properties for sale", "Listings live at the end of the range, "
                + "matching the filters.", "COUNT", lastStock, firstStock, "active", null));
        kpis.add(sum("new_listings", "New listings", "Listings that went live for the first time in the range.", flows.newListings));
        kpis.add(sum("withdrawn", "Withdrawn", "Taken off the market by the owner or agent.", flows.withdrawn));
        kpis.add(change("asking_median", rent ? "Median asking rent" : "Median asking price",
                "Middle asking price of live listings at the end of the range (USD). Half are above, half below.",
                unit, lastStock, firstStock, "askingMedian", "askingN"));
        kpis.add(change("asking_mean", rent ? "Average asking rent" : "Average asking price",
                "Mean asking price. A few very expensive listings pull it up, so read it next to the median.",
                unit, lastStock, firstStock, "askingMean", "askingN"));
        Stats.Summary tx = Stats.summarise(txs.stream().map(Tx::amount).toList());
        if (rent) {
            kpis.add(new Kpi("contracted_median", "Median agreed rent", "Middle monthly rent in leases signed during the range. "
                    + "Agreed rent, not asking rent.", unit, tx.median(), null, null, tx.n(), tx.n() < MIN_SAMPLE,
                    tx.n() == 0 ? "No leases were signed in the range." : null));
            kpis.add(sum("leases_signed", "Rental agreements signed", "Leases signed during the range.", countAll(buckets, "leasesSigned")));
            BigDecimal vacancy = lastStock == null ? null : lastStock.values.get("vacancyPercent");
            kpis.add(new Kpi("vacancy", "Listed as available", "Share of tracked rental homes (available or let) that are "
                    + "listed as available. An indicator only: homes let outside EstateX aren't counted.", "PERCENT",
                    vacancy, null, null, null, false, vacancy == null ? "Not enough tracked rental homes." : null));
        } else {
            kpis.add(new Kpi("verified_median", "Median verified sale price", "Middle price of sales verified by EstateX from "
                    + "conveyancer or Deeds Registry evidence. Reported sales aren't included.", unit, tx.median(), null, null,
                    tx.n(), tx.n() < MIN_SAMPLE, tx.n() == 0 ? "No verified sales in the range yet." : null));
            kpis.add(sum("verified_sales", "Verified sales", "Sales verified by EstateX, by completion date.", tx.n()));
            kpis.add(new Kpi("verified_value", "Verified transaction value", "Total value of verified sales (USD).", unit,
                    tx.sum(), null, null, tx.n(), false, null));
            kpis.add(sum("reported_sales", "Reported sales", "Sales reported by owners or agents. Not verified.", flows.reported));
            kpis.add(change("per_m2", "Median price per m²", "Asking price ÷ floor area, for live listings with a floor area.",
                    unit, lastStock, firstStock, "perM2Median", null));
            kpis.add(new Kpi("price_cuts", "Price reductions", "Asking-price cuts in the range; the note gives the typical cut.",
                    "COUNT", BigDecimal.valueOf(cuts.size()), null, null, cuts.size(), false,
                    cuts.isEmpty() ? null : "Typical cut " + Stats.summarise(cuts).median() + "%"));
            int live = liveDuring.size();
            BigDecimal conversion = live == 0 ? null : BigDecimal.valueOf(movedOn.size()).multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(live), 1, RoundingMode.HALF_UP);
            kpis.add(new Kpi("sale_conversion", "Went under offer or sold", "Share of listings live during the range that went "
                    + "under offer or were reported sold.", "PERCENT", conversion, null, null, live, live < MIN_SAMPLE,
                    live == 0 ? "No listings were live." : movedOn.size() + " of " + live + " listings"));
        }
        Stats.Summary dom = Stats.summarise(daysToEnd);
        Stats.Summary ages = Stats.summarise(stillListedAges);
        kpis.add(new Kpi("days_to_close", rent ? "Median time to let" : "Median days on market",
                rent ? "Days from going live to being let or going under offer, for homes let in the range."
                        : "Days from going live to going under offer or being reported sold, for listings that did so in the range.",
                "DAYS", dom.median(), null, null, dom.n(), dom.n() < MIN_SAMPLE,
                (dom.n() == 0 ? "None yet in the range." : "") + (ages.n() > 0 ? (dom.n() == 0 ? " " : "") + ages.n()
                        + " still listed, typically for " + ages.median() + " days." : "")));
        if (q.demand()) {
            kpis.add(sum("enquiries", rent ? "Rental enquiries" : "Buyer enquiries", "Enquiries about matching listings. "
                    + "Shows interest, not confirmed demand.", flows.enquiries));
            kpis.add(sum("viewings", "Viewing requests", "Requests to view matching listings.", flows.viewings));
            if (rent) {
                BigDecimal conv = flows.applications == 0 ? null : BigDecimal.valueOf(flows.approved).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(flows.applications), 1, RoundingMode.HALF_UP);
                kpis.add(new Kpi("applications", "Rental applications", "Applications submitted in the range; the note "
                        + "gives how many were approved.", "COUNT", BigDecimal.valueOf(flows.applications), null, null,
                        flows.applications, false, flows.applications == 0 ? null
                        : flows.approved + " approved (" + conv + "%)"));
            } else {
                kpis.add(sum("offers", "Offers made", "Purchase offers on matching listings.", flows.offers));
            }
        }

        // Breakdowns at the end of the range.
        List<ListingState> latest = latestSnapshot == null ? List.of() : snaps.getOrDefault(latestSnapshot, List.of());
        Function<ListingState, String[]> bySuburb = s -> s.suburb() == null ? null
                : new String[]{(s.suburb() + "|" + s.city()).toLowerCase(Locale.ROOT), s.suburb(), s.city()};
        Function<ListingState, String[]> byCategory = s -> new String[]{String.valueOf(s.category()),
                s.category() == null ? "Unspecified" : categoryLabel(s.category()), null};
        Function<ListingState, String[]> byBeds = s -> {
            String band = s.bedrooms() == null ? "?" : s.bedrooms() >= 5 ? "5+" : String.valueOf(s.bedrooms());
            return new String[]{band, band.equals("0") ? "Studio / no bedrooms" : band.equals("?") ? "Unknown" : band + " bedroom" + (band.equals("1") ? "" : "s"), null};
        };
        List<Group> suburbs = groups(latest, f, flows.newStates, txs, demandByProperty, endState, bySuburb, q.demand(), true);
        List<Group> categories = groups(latest, f, flows.newStates, txs, demandByProperty, endState, byCategory, q.demand(), false);
        List<Group> bedrooms = groups(latest, f, flows.newStates, txs, demandByProperty, endState, byBeds, q.demand(), false);
        List<Group> priceBands = rent ? List.of() : groups(latest, f, flows.newStates, txs, demandByProperty, endState,
                s -> priceBand(s.price()), q.demand(), false);
        priceBands = priceBands.stream().sorted(Comparator.comparing(Group::key)).toList();
        bedrooms = bedrooms.stream().sorted(Comparator.comparing(Group::key)).toList();

        Yields yields = yields(latest, q.filter(), latestSnapshot);

        Instant historyStart = jdbc.query("select started_at from analytics_capture_starts where source = 'property_events'",
                rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null);
        // Counts: a period with nothing in it is a real zero. Transactions and demand come from tables
        // with full history; listing events only exist from when history began, so earlier periods stay blank.
        List<String> fullHistory = new ArrayList<>(rent ? List.of("leasesSigned") : List.of("salesReported", "verifiedSales"));
        if (q.demand()) {
            fullHistory.addAll(List.of("enquiries", "viewings"));
            fullHistory.addAll(rent ? List.of("applications", "applicationsApproved") : List.of("offers"));
        }
        List<String> listingFlows = rent ? List.of("newListings", "withdrawn", "expired", "priceCuts")
                : List.of("newListings", "withdrawn", "expired", "priceCuts", "underOffer", "reportedSoldListings");
        for (Bucket b : buckets) {
            fullHistory.forEach(k -> b.values.putIfAbsent(k, BigDecimal.ZERO));
            if (historyStart == null || !b.end.isAfter(historyStart)) continue;
            listingFlows.forEach(k -> b.values.putIfAbsent(k, BigDecimal.ZERO));
        }
        if (historyStart != null && historyStart.isAfter(rangeStart)) {
            notes.add("Listing history starts on " + historyStart.atZone(zone).toLocalDate()
                    + ". Periods before that have no listing figures.");
        }
        if (latestSnapshot == null) notes.add("No daily snapshots in this range yet. They appear after the first hourly update.");
        Instant calculated = jdbc.query("select max(finished_at) from analytics_job_runs where status = 'SUCCEEDED'",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
        List<Point> series = buckets.stream().map(b -> new Point(b.start, b.end, b.label, b.complete, b.snapshotDate,
                Collections.unmodifiableMap(b.values))).toList();
        return new Dashboard(q.type(), q.from(), q.to(), q.grain().name(), q.filter(), historyStart, latestSnapshot, calculated,
                q.demand(), kpis, series, suburbs, categories, bedrooms, priceBands, yields, notes);
    }

    // ---- Stock ----------------------------------------------------------------------------

    record Stock(int active, Stats.Summary asking, Stats.Summary perM2, BigDecimal vacancy) {
    }

    static Stock stock(List<ListingState> rows, MarketFilter f, boolean rent) {
        List<BigDecimal> prices = new ArrayList<>();
        List<BigDecimal> perM2 = new ArrayList<>();
        int active = 0;
        int let = 0;
        for (ListingState s : rows) {
            if (!f.matches(s)) continue;
            if ("OCCUPIED".equals(s.status())) let++;
            if (!s.available()) continue;
            active++;
            if (s.price() != null && s.price().signum() > 0 && "USD".equalsIgnoreCase(s.currency())) {
                prices.add(s.price());
                if (s.floorAreaM2() != null && s.floorAreaM2() > 0) {
                    perM2.add(s.price().divide(BigDecimal.valueOf(s.floorAreaM2()), 2, RoundingMode.HALF_UP));
                }
            }
        }
        BigDecimal vacancy = rent && active + let > 0
                ? BigDecimal.valueOf(active).multiply(HUNDRED).divide(BigDecimal.valueOf(active + let), 1, RoundingMode.HALF_UP)
                : null;
        return new Stock(active, Stats.summarise(prices), Stats.summarise(perM2), vacancy);
    }

    private Map<LocalDate, List<ListingState>> snapshots(Collection<LocalDate> dates) {
        Map<LocalDate, List<ListingState>> out = new HashMap<>();
        if (dates.isEmpty()) return out;
        List<Object> args = new ArrayList<>();
        dates.forEach(d -> args.add(Date.valueOf(d)));
        jdbc.query("select snapshot_date, property_id, listing_type, status, verification_status, country, city, suburb, category, "
                        + "bedrooms, bathrooms, floor_area_m2, price, currency, latitude, longitude, organisation_id "
                        + "from listing_daily_snapshots where snapshot_date in (" + String.join(",", Collections.nCopies(dates.size(), "?")) + ")",
                rs -> { out.computeIfAbsent(rs.getDate("snapshot_date").toLocalDate(), k -> new ArrayList<>()).add(state(rs)); },
                args.toArray());
        return out;
    }

    static ListingState state(ResultSet rs) throws SQLException {
        return new ListingState(rs.getLong("property_id"), rs.getString("listing_type"), rs.getString("status"),
                rs.getString("verification_status"), rs.getString("country"), rs.getString("city"), rs.getString("suburb"),
                rs.getString("category"), rs.getObject("bedrooms", Integer.class), rs.getObject("bathrooms", Integer.class),
                rs.getObject("floor_area_m2", Integer.class), rs.getBigDecimal("price"), rs.getString("currency"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getObject("organisation_id", Long.class));
    }

    // ---- Buckets & flows ------------------------------------------------------------------

    static final class Bucket {
        final Instant start;
        final Instant end;
        final String label;
        final boolean complete;
        LocalDate snapshotDate;
        final Map<String, BigDecimal> values = new LinkedHashMap<>();
        final List<BigDecimal> contracted = new ArrayList<>();

        Bucket(Instant start, Instant end, String label, boolean complete) {
            this.start = start;
            this.end = end;
            this.label = label;
            this.complete = complete;
        }

        void put(String k, BigDecimal v) {
            values.put(k, v);
        }

        void add(String k, BigDecimal v) {
            values.merge(k, v, BigDecimal::add);
        }
    }

    static final class Flows {
        int newListings;
        int withdrawn;
        int expired;
        int reported;
        int enquiries;
        int viewings;
        int applications;
        int approved;
        int offers;
        final List<ListingState> newStates = new ArrayList<>();
    }

    record Tx(ListingState listing, BigDecimal amount) {
    }

    static List<Bucket> buckets(Grain grain, Instant from, Instant to, ZoneId zone, Instant now) {
        List<Bucket> out = new ArrayList<>();
        for (Period p : Period.overlapping(grain, from, to, zone)) {
            Instant s = p.start().isBefore(from) ? from : p.start();
            Instant e = p.end().isAfter(to) ? to : p.end();
            if (!s.isBefore(e)) continue;
            boolean complete = !p.end().isAfter(now) && e.equals(p.end());
            out.add(new Bucket(s, e, label(p, zone, !e.equals(p.end())), complete));
        }
        return out;
    }

    private static String label(Period p, ZoneId zone, boolean partial) {
        var s = p.start().atZone(zone);
        String base = switch (p.grain()) {
            case DAY, HOUR -> s.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
            case WEEK -> "w/c " + s.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
            case MONTH -> s.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
            case QUARTER -> "Q" + ((s.getMonthValue() - 1) / 3 + 1) + " " + s.getYear();
            case YEAR -> String.valueOf(s.getYear());
        };
        return partial ? base + " (part)" : base;
    }

    private static Bucket bucket(List<Bucket> buckets, Instant t) {
        Bucket found = buckets.get(0);
        for (Bucket b : buckets) {
            if (!t.isBefore(b.start)) found = b;
        }
        return found;
    }

    private void demand(List<Bucket> buckets, String sql, String key, Instant from, Instant to, Map<Long, ListingState> endState,
                        MarketFilter f, Flows flows, Map<Long, Integer> byProperty) {
        jdbc.query(sql, rs -> {
            Long pid = rs.getObject("property_id", Long.class);
            ListingState s = pid == null ? null : endState.get(pid);
            if (s == null || !f.matches(s)) return;
            bucket(buckets, rs.getTimestamp("at").toInstant()).add(key, BigDecimal.ONE);
            switch (key) {
                case "enquiries" -> flows.enquiries++;
                case "viewings" -> flows.viewings++;
                case "offers" -> flows.offers++;
                default -> { }
            }
            if (byProperty != null) byProperty.merge(pid, 1, Integer::sum);
        }, Timestamp.from(from), Timestamp.from(to));
    }

    private static int countAll(List<Bucket> buckets, String key) {
        return buckets.stream().map(b -> b.values.getOrDefault(key, BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add).intValue();
    }

    // ---- KPIs -----------------------------------------------------------------------------

    private static Kpi sum(String key, String label, String help, int value) {
        return new Kpi(key, label, help, "COUNT", BigDecimal.valueOf(value), null, null, value, false, null);
    }

    /** Value at the end of the range, compared with the start of the range. */
    private static Kpi change(String key, String label, String help, String unit, Bucket last, Bucket first, String valueKey,
                              String nKey) {
        BigDecimal now = last == null ? null : last.values.get(valueKey);
        BigDecimal then = first == null || first == last ? null : first.values.get(valueKey);
        Integer n = nKey == null || last == null || last.values.get(nKey) == null ? null : last.values.get(nKey).intValue();
        BigDecimal pct = now != null && then != null && then.signum() != 0
                ? now.subtract(then).multiply(HUNDRED).divide(then.abs(), 1, RoundingMode.HALF_UP) : null;
        String note = now == null ? "No listings match these filters at the end of the range."
                : then == null ? "No earlier figure in this range to compare with." : "vs " + first.label;
        return new Kpi(key, label, help, unit, now, then, pct, n, n != null && n < MIN_SAMPLE, note);
    }

    // ---- Breakdowns -----------------------------------------------------------------------

    private List<Group> groups(List<ListingState> latest, MarketFilter f, List<ListingState> newStates, List<Tx> txs,
                               Map<Long, Integer> demandByProperty, Map<Long, ListingState> endState,
                               Function<ListingState, String[]> keyOf, boolean demand, boolean cap) {
        Map<String, GroupAcc> acc = new LinkedHashMap<>();
        for (ListingState s : latest) {
            if (!s.available() || !f.matches(s)) continue;
            String[] k = keyOf.apply(s);
            if (k == null) continue;
            GroupAcc g = acc.computeIfAbsent(k[0], x -> new GroupAcc(k));
            g.active++;
            if (s.price() != null && s.price().signum() > 0 && "USD".equalsIgnoreCase(s.currency())) g.prices.add(s.price());
        }
        for (ListingState s : newStates) {
            String[] k = keyOf.apply(s);
            if (k != null) acc.computeIfAbsent(k[0], x -> new GroupAcc(k)).newListings++;
        }
        for (Tx t : txs) {
            String[] k = keyOf.apply(t.listing().price() == null ? t.listing() : withPrice(t.listing(), t.amount()));
            if (k != null) acc.computeIfAbsent(k[0], x -> new GroupAcc(k)).tx.add(t.amount());
        }
        if (demand) {
            demandByProperty.forEach((pid, n) -> {
                ListingState s = endState.get(pid);
                String[] k = s == null ? null : keyOf.apply(s);
                if (k != null) acc.computeIfAbsent(k[0], x -> new GroupAcc(k)).demand += n;
            });
        }
        List<Group> out = new ArrayList<>();
        for (GroupAcc g : acc.values()) {
            Stats.Summary p = Stats.summarise(g.prices);
            Stats.Summary t = Stats.summarise(g.tx);
            out.add(new Group(g.key[0], g.key[1], g.key[2], g.active, p.median(), p.n(), p.n() < MIN_SAMPLE, g.newListings,
                    demand ? g.demand : null,
                    demand && g.active > 0 ? BigDecimal.valueOf(g.demand).divide(BigDecimal.valueOf(g.active), 2, RoundingMode.HALF_UP) : null,
                    t.n(), t.median(), t.n()));
        }
        out.sort(Comparator.comparingInt(Group::active).reversed().thenComparing(Group::label));
        return cap && out.size() > MAX_GROUPS ? out.subList(0, MAX_GROUPS) : out;
    }

    private static ListingState withPrice(ListingState s, BigDecimal price) {
        return new ListingState(s.propertyId(), s.listingType(), s.status(), s.verificationStatus(), s.country(), s.city(),
                s.suburb(), s.category(), s.bedrooms(), s.bathrooms(), s.floorAreaM2(), price, s.currency(), s.latitude(),
                s.longitude(), s.organisationId());
    }

    static final class GroupAcc {
        final String[] key;
        int active;
        int newListings;
        int demand;
        final List<BigDecimal> prices = new ArrayList<>();
        final List<BigDecimal> tx = new ArrayList<>();

        GroupAcc(String[] key) {
            this.key = key;
        }
    }

    static String[] priceBand(BigDecimal price) {
        if (price == null) return null;
        for (int i = PRICE_BANDS.length - 1; i >= 0; i--) {
            if (price.compareTo(BigDecimal.valueOf(PRICE_BANDS[i])) >= 0) {
                String label = i == PRICE_BANDS.length - 1 ? "$" + money(PRICE_BANDS[i]) + "+"
                        : "$" + money(PRICE_BANDS[i]) + "–$" + money(PRICE_BANDS[i + 1]);
                return new String[]{String.format("%02d", i), label, null};
            }
        }
        return null;
    }

    private static String money(long v) {
        return v >= 1_000_000 ? (v / 1_000_000) + "m" : v >= 1000 ? (v / 1000) + "k" : String.valueOf(v);
    }

    static String categoryLabel(String code) {
        return switch (code) {
            case "HOUSE" -> "House";
            case "FLAT" -> "Flat / apartment";
            case "GARDEN_FLAT" -> "Garden flat";
            case "TOWNHOUSE" -> "Townhouse / cluster";
            case "COTTAGE" -> "Cottage";
            case "ROOM" -> "Room";
            case "STAND" -> "Land / stand";
            case "DEVELOPMENT_LAND" -> "Land for development";
            case "COMMERCIAL" -> "Commercial";
            case "INDUSTRIAL" -> "Industrial";
            case "FARM" -> "Farm / plot";
            default -> "Other";
        };
    }

    // ---- Yields ---------------------------------------------------------------------------

    /**
     * Estimated gross yield = median asking rent × 12 ÷ median asking sale price, for the same
     * place and kind of home, only where both sides have at least {@value #MIN_SAMPLE} listings.
     * Based on asking prices, so it's an estimate; net yield also needs the admin's cost ratio.
     */
    Yields yields(List<ListingState> latest, MarketFilter filter, LocalDate asOf) {
        MarketFilter place = filter.withoutPrice();
        CostRatio ratio = costRatio();
        Map<String, List<BigDecimal>[]> bySuburb = new LinkedHashMap<>();
        Map<String, String[]> names = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<BigDecimal>[] overall = new List[]{new ArrayList<>(), new ArrayList<>()};
        for (ListingState s : latest) {
            if (!s.available() || s.price() == null || s.price().signum() <= 0 || !"USD".equalsIgnoreCase(s.currency())) continue;
            int side = "RENT".equals(s.listingType()) ? 0 : "SALE".equals(s.listingType()) ? 1 : -1;
            if (side < 0 || !place.withType(s.listingType()).place(s)) continue;
            overall[side].add(s.price());
            if (s.suburb() == null) continue;
            String key = (s.suburb() + "|" + s.city()).toLowerCase(Locale.ROOT);
            names.putIfAbsent(key, new String[]{s.suburb(), s.city()});
            @SuppressWarnings("unchecked")
            List<BigDecimal>[] lists = bySuburb.computeIfAbsent(key, k -> new List[]{new ArrayList<>(), new ArrayList<>()});
            lists[side].add(s.price());
        }
        List<YieldRow> rows = new ArrayList<>();
        bySuburb.forEach((k, lists) -> {
            if (lists[0].isEmpty() || lists[1].isEmpty()) return;
            rows.add(yieldRow(names.get(k)[0], names.get(k)[1], lists[0], lists[1], ratio));
        });
        rows.sort(Comparator.comparing((YieldRow r) -> r.grossYield() == null ? BigDecimal.valueOf(-1) : r.grossYield()).reversed());
        return new Yields(yieldRow(null, null, overall[0], overall[1], ratio), rows, ratio.value, ratio.confirmed, asOf);
    }

    static YieldRow yieldRow(String suburb, String city, List<BigDecimal> rents, List<BigDecimal> sales, CostRatio ratio) {
        Stats.Summary r = Stats.summarise(rents);
        Stats.Summary s = Stats.summarise(sales);
        if (r.n() < MIN_SAMPLE || s.n() < MIN_SAMPLE) {
            return new YieldRow(suburb, city, r.median(), r.n(), s.median(), s.n(), null, null,
                    "Needs at least " + MIN_SAMPLE + " rentals and " + MIN_SAMPLE + " sales listed.");
        }
        BigDecimal gross = r.median().multiply(BigDecimal.valueOf(12)).multiply(HUNDRED).divide(s.median(), 2, RoundingMode.HALF_UP);
        BigDecimal net = ratio.confirmed ? gross.multiply(BigDecimal.ONE.subtract(ratio.value)).setScale(2, RoundingMode.HALF_UP) : null;
        return new YieldRow(suburb, city, r.median(), r.n(), s.median(), s.n(), gross, net, null);
    }

    public record CostRatio(BigDecimal value, boolean confirmed, Instant updatedAt) {
    }

    public CostRatio costRatio() {
        return settings.findById(COST_RATIO_KEY)
                .map(s -> new CostRatio(new BigDecimal(s.getValue()), true, s.getUpdatedAt()))
                .orElse(new CostRatio(DEFAULT_COST_RATIO, false, null));
    }

    public CostRatio setCostRatio(BigDecimal ratio, Long userId) {
        AnalyticsSetting s = settings.findById(COST_RATIO_KEY).orElseGet(AnalyticsSetting::new);
        s.setKey(COST_RATIO_KEY);
        s.setValue(ratio.setScale(4, RoundingMode.HALF_UP).toPlainString());
        s.setUpdatedByUserId(userId);
        s.setUpdatedAt(time.clock().instant());
        settings.save(s);
        cache.invalidateAll();
        return costRatio();
    }

    // ---- Drill-down -----------------------------------------------------------------------

    public record ListingRow(long id, String title, String suburb, String city, String category, Integer bedrooms,
                             BigDecimal price, String currency, String status, String verification) {
    }

    /** The live listings behind a figure, from the latest snapshot, with the same filters. */
    public List<ListingRow> listings(MarketFilter f, int limit) {
        LocalDate latest = jdbc.query("select max(snapshot_date) from listing_daily_snapshots",
                rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null);
        if (latest == null) return List.of();
        List<ListingRow> out = new ArrayList<>();
        jdbc.query("select s.*, p.title from listing_daily_snapshots s left join properties p on p.id = s.property_id "
                + "where s.snapshot_date = ? and s.listing_type = ? and s.status = 'AVAILABLE' order by s.price", rs -> {
            ListingState st = state(rs);
            if (out.size() >= limit || !f.matches(st)) return;
            out.add(new ListingRow(st.propertyId(), rs.getString("title"), st.suburb(), st.city(), st.category(), st.bedrooms(),
                    st.price(), st.currency(), st.status(), st.verificationStatus()));
        }, Date.valueOf(latest), f.listingType());
        return out;
    }
}
