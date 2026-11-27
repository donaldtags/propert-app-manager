package com.example.estatex.analytics.market;

import com.example.estatex.analytics.metrics.ListingState;
import com.example.estatex.analytics.metrics.Stats;
import com.example.estatex.common.BusinessTime;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Suburb-level market figures for the map (docs/admin-analytics-plan.md Phase E).
 *
 * <p>Only listings with a suburb are aggregated. A suburb is placed on the map at its known
 * centre (the locations table) or, failing that, at the middle of its listings' own map pins.
 * Suburbs with neither can't be drawn; the response says how many listings that leaves off the
 * map, so coverage is always visible. No boundaries are drawn because none are on file.
 */
@Service
public class GeoService {
    static final int GROWTH_DAYS = 90;
    static final int ACTIVITY_DAYS = 90;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final Cache<String, GeoData> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(200).build();

    GeoService(JdbcTemplate jdbc, BusinessTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public record SuburbFigures(String suburb, String city, BigDecimal latitude, BigDecimal longitude, String placedBy,
                                int active, BigDecimal askingMedian, BigDecimal askingMean, int askingN, String confidence,
                                BigDecimal growthPercent, Integer newListings, Integer demand, BigDecimal demandPerListing,
                                Integer searches, int transactions, BigDecimal grossYield) {
    }

    public record Coverage(int liveListings, int withSuburb, int placedOnMap, int withoutSuburb, int inUnplacedSuburbs,
                           int withOwnPin) {
    }

    public record Point(long id, BigDecimal latitude, BigDecimal longitude, BigDecimal price, String suburb) {
    }

    public record GeoData(String type, LocalDate snapshotDate, LocalDate comparedWith, int activityDays,
                          boolean demandIncluded, Coverage coverage, List<SuburbFigures> suburbs, List<Point> points,
                          List<String> notes) {
    }

    public GeoData suburbs(MarketFilter filter, boolean demand) {
        Long run = jdbc.queryForObject("select max(id) from analytics_job_runs where status = 'SUCCEEDED'", Long.class);
        return cache.get(filter + "#" + demand + "#" + run, k -> build(filter, demand));
    }

    private GeoData build(MarketFilter f, boolean demand) {
        String type = f.listingType();
        boolean rent = "RENT".equals(type);
        List<String> notes = new ArrayList<>();
        LocalDate latest = latestSnapshot(null);
        if (latest == null) {
            return new GeoData(type, null, null, ACTIVITY_DAYS, demand, new Coverage(0, 0, 0, 0, 0, 0), List.of(), List.of(),
                    List.of("No daily snapshots yet. The map fills in after the first hourly update."));
        }
        LocalDate earlier = latestSnapshot(latest.minusDays(GROWTH_DAYS));
        List<ListingState> now = snapshot(latest);
        List<ListingState> then = earlier == null ? List.of() : snapshot(earlier);
        Instant since = time.clock().instant().minus(Duration.ofDays(ACTIVITY_DAYS));

        // Suburb centres from the location reference data.
        Map<String, BigDecimal[]> centres = new HashMap<>();
        jdbc.query("select s.name suburb, coalesce(s.city, c.name) city, s.latitude, s.longitude from locations s "
                + "left join locations c on c.id = s.parent_id where s.type = 'SUBURB' and s.latitude is not null "
                + "and s.longitude is not null", rs -> {
            String city = rs.getString("city");
            if (city == null) return;
            centres.putIfAbsent(key(rs.getString("suburb"), city),
                    new BigDecimal[]{rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude")});
        });

        Map<String, Acc> acc = new LinkedHashMap<>();
        int live = 0;
        int withoutSuburb = 0;
        int withOwnPin = 0;
        List<Point> points = new ArrayList<>();
        for (ListingState s : now) {
            if (!s.available() || !f.matches(s)) continue;
            live++;
            if (s.hasCoordinates()) {
                withOwnPin++;
                if (points.size() < 5000) points.add(new Point(s.propertyId(), s.latitude(), s.longitude(), s.price(), s.suburb()));
            }
            if (s.suburb() == null || s.suburb().isBlank() || s.city() == null) {
                withoutSuburb++;
                continue;
            }
            Acc a = acc.computeIfAbsent(key(s.suburb(), s.city()), k -> new Acc(s.suburb().trim(), s.city().trim()));
            a.active++;
            if (usd(s)) a.prices.add(s.price());
            if (s.hasCoordinates()) {
                a.lats.add(s.latitude());
                a.lngs.add(s.longitude());
            }
        }
        for (ListingState s : then) {
            if (!s.available() || !f.matches(s) || s.suburb() == null || s.city() == null || !usd(s)) continue;
            Acc a = acc.get(key(s.suburb(), s.city()));
            if (a != null) a.earlierPrices.add(s.price());
        }
        // Yield needs the other side of the market for the same place and kind of home.
        String otherType = rent ? "SALE" : "RENT";
        MarketFilter other = f.withoutPrice().withType(otherType);
        Map<String, List<BigDecimal>> otherPrices = new HashMap<>();
        for (ListingState s : now) {
            if (!s.available() || !other.place(s) || s.suburb() == null || s.city() == null || !usd(s)) continue;
            otherPrices.computeIfAbsent(key(s.suburb(), s.city()), k -> new ArrayList<>()).add(s.price());
        }

        // New listings, transactions and (with permission) demand in the last 90 days.
        Map<Long, ListingState> byId = new HashMap<>();
        now.forEach(s -> byId.put(s.propertyId(), s));
        jdbc.query("select e.property_id from property_events e where e.event_type = 'CREATED' and e.status = 'AVAILABLE' "
                + "and e.occurred_at >= ? union all select e.property_id from property_events e where e.event_type = 'STATUS_CHANGED' "
                + "and e.status = 'AVAILABLE' and e.previous_status = 'DRAFT' and e.occurred_at >= ?", rs -> {
            ListingState s = byId.get(rs.getLong(1));
            Acc a = s == null || !f.matches(s) ? null : acc.get(key(s.suburb(), s.city()));
            if (a != null) a.newListings++;
        }, Timestamp.from(since), Timestamp.from(since));
        String txSql = rent
                ? "select property_id from leases where signed_at >= ?"
                : "select property_id from sale_transactions where verification = 'VERIFIED' and completion_date >= ?";
        Object txArg = rent ? Timestamp.from(since) : Date.valueOf(time.today().minusDays(ACTIVITY_DAYS));
        jdbc.query(txSql, rs -> {
            ListingState s = byId.get(rs.getLong(1));
            Acc a = s == null || !f.withoutPrice().place(s) ? null : acc.get(key(s.suburb(), s.city()));
            if (a != null) a.transactions++;
        }, txArg);
        if (demand) {
            for (String sql : List.of("select property_id from enquiries where created_at >= ?",
                    "select property_id from property_inquiries where created_at >= ?",
                    "select property_id from viewing_requests where created_at >= ?")) {
                jdbc.query(sql, rs -> {
                    Long pid = rs.getObject(1, Long.class);
                    ListingState s = pid == null ? null : byId.get(pid);
                    Acc a = s == null || !f.matches(s) ? null : acc.get(key(s.suburb(), s.city()));
                    if (a != null) a.demand++;
                }, Timestamp.from(since));
            }
            jdbc.query("select city, suburb from marketplace_events where event_type = 'SEARCH' and occurred_at >= ? "
                    + "and suburb is not null and (listing_type is null or listing_type = ?)", rs -> {
                String city = rs.getString("city");
                String suburb = rs.getString("suburb");
                Acc a = city == null ? findBySuburb(acc, suburb) : acc.get(key(suburb, city));
                if (a != null) a.searches++;
            }, Timestamp.from(since), type);
        }

        List<SuburbFigures> out = new ArrayList<>();
        int placed = 0;
        int unplaced = 0;
        for (Map.Entry<String, Acc> e : acc.entrySet()) {
            Acc a = e.getValue();
            BigDecimal[] centre = centres.get(e.getKey());
            String placedBy = "suburb centre";
            if (centre == null && !a.lats.isEmpty()) {
                centre = new BigDecimal[]{median(a.lats), median(a.lngs)};
                placedBy = "middle of its listings";
            }
            if (centre == null) {
                unplaced += a.active;
                placedBy = null;
            } else {
                placed += a.active;
            }
            Stats.Summary p = Stats.summarise(a.prices);
            Stats.Summary before = Stats.summarise(a.earlierPrices);
            BigDecimal growth = p.n() >= MarketDashboardService.MIN_SAMPLE && before.n() >= MarketDashboardService.MIN_SAMPLE
                    && before.median().signum() > 0
                    ? p.median().subtract(before.median()).multiply(HUNDRED).divide(before.median(), 1, RoundingMode.HALF_UP) : null;
            List<BigDecimal> others = otherPrices.getOrDefault(e.getKey(), List.of());
            MarketDashboardService.YieldRow y = rent
                    ? MarketDashboardService.yieldRow(a.suburb, a.city, a.prices, others, new MarketDashboardService.CostRatio(BigDecimal.ZERO, false, null))
                    : MarketDashboardService.yieldRow(a.suburb, a.city, others, a.prices, new MarketDashboardService.CostRatio(BigDecimal.ZERO, false, null));
            out.add(new SuburbFigures(a.suburb, a.city, centre == null ? null : centre[0], centre == null ? null : centre[1], placedBy,
                    a.active, p.median(), p.mean(), p.n(), confidence(p.n()), growth, a.newListings, demand ? a.demand : null,
                    demand && a.active > 0 ? BigDecimal.valueOf(a.demand).divide(BigDecimal.valueOf(a.active), 2, RoundingMode.HALF_UP) : null,
                    demand ? a.searches : null, a.transactions, y.grossYield()));
        }
        out.sort(Comparator.comparingInt(SuburbFigures::active).reversed());
        if (earlier == null) notes.add("Price change needs a snapshot from about " + GROWTH_DAYS + " days ago; none exists yet.");
        notes.add("Suburb boundaries aren't on file, so suburbs are drawn as circles at their centre, not shaded areas.");
        if (unplaced > 0) {
            notes.add(unplaced + " live listing(s) are in suburbs with no known centre and no listing pins, so they're counted "
                    + "but not drawn. Adding suburb coordinates to the location data would place them.");
        }
        return new GeoData(type, latest, earlier, ACTIVITY_DAYS, demand,
                new Coverage(live, live - withoutSuburb, placed, withoutSuburb, unplaced, withOwnPin), out, points, notes);
    }

    /** Median at full precision. (Stats rounds to cents, which is about 1 km for a coordinate.) */
    static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 7, RoundingMode.HALF_UP);
    }

    static String confidence(int n) {
        return n >= 20 ? "HIGH" : n >= MarketDashboardService.MIN_SAMPLE ? "MEDIUM" : "LOW";
    }

    private static Acc findBySuburb(Map<String, Acc> acc, String suburb) {
        if (suburb == null) return null;
        String s = suburb.trim().toLowerCase(Locale.ROOT);
        Acc found = null;
        for (Acc a : acc.values()) {
            if (a.suburb.toLowerCase(Locale.ROOT).equals(s)) {
                if (found != null) return null; // same name in two cities: can't tell which
                found = a;
            }
        }
        return found;
    }

    private static boolean usd(ListingState s) {
        return s.price() != null && s.price().signum() > 0 && "USD".equalsIgnoreCase(s.currency());
    }

    static String key(String suburb, String city) {
        return (suburb.trim().replaceAll("\\s+", " ") + "|" + city.trim().replaceAll("\\s+", " ")).toLowerCase(Locale.ROOT);
    }

    private LocalDate latestSnapshot(LocalDate onOrBefore) {
        return onOrBefore == null
                ? jdbc.query("select max(snapshot_date) from listing_daily_snapshots",
                    rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null)
                : jdbc.query("select max(snapshot_date) from listing_daily_snapshots where snapshot_date <= ?",
                    rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null, Date.valueOf(onOrBefore));
    }

    private List<ListingState> snapshot(LocalDate d) {
        List<ListingState> out = new ArrayList<>();
        jdbc.query("select * from listing_daily_snapshots where snapshot_date = ?",
                rs -> { out.add(MarketDashboardService.state(rs)); }, Date.valueOf(d));
        return out;
    }

    static final class Acc {
        final String suburb;
        final String city;
        int active;
        int newListings;
        int transactions;
        int demand;
        int searches;
        final List<BigDecimal> prices = new ArrayList<>();
        final List<BigDecimal> earlierPrices = new ArrayList<>();
        final List<BigDecimal> lats = new ArrayList<>();
        final List<BigDecimal> lngs = new ArrayList<>();

        Acc(String suburb, String city) {
            this.suburb = suburb;
            this.city = city;
        }
    }
}
