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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * How people use the marketplace (docs/admin-analytics-plan.md Phase E).
 *
 * <p>Everything here measures <em>interest</em> (searches, views, saves, enquiries), never
 * confirmed buying or renting. Searches and views are anonymous; funnels therefore compare the
 * volume at each step in the period, not the same people moving through it. Wording on screen
 * says so.
 */
@Service
public class DemandService {
    /** A search counts as "led to a view" if the same browser opened a listing within this time. */
    static final Duration FOLLOW_UP = Duration.ofMinutes(30);
    /** Fewer searches than this for a segment is too little to flag a supply gap. */
    static final int MIN_SEARCHES = 10;
    /** A segment is flagged when its searches per live listing are at least this many times the market's. */
    static final BigDecimal GAP_LIFT = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final long[] RENT_BANDS = {0, 300, 500, 800, 1200, 2000};
    static final long[] SALE_BANDS = {0, 50_000, 100_000, 200_000, 500_000, 1_000_000};

    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final Cache<String, Demand> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(200).build();

    DemandService(JdbcTemplate jdbc, BusinessTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public record Count(String key, String label, String city, int searches, int noResults, Integer liveListings,
                        BigDecimal searchesPerListing) {
    }

    public record Step(String key, String label, int count, BigDecimal fromPrevious) {
    }

    public record Funnel(String type, List<Step> steps) {
    }

    public record ListingInterest(long id, String title, String type, String suburb, String city, BigDecimal price,
                                  int views, int saves, int enquiries) {
    }

    public record ResponseTime(String key, String label, BigDecimal medianHours, int n, String note) {
    }

    public record Gap(String type, String city, String category, Integer bedrooms, int searches, int noResults,
                      int liveListings, BigDecimal searchesPerListing, BigDecimal lift, String explanation) {
    }

    public record Demand(LocalDate from, LocalDate to, Instant figuresCalculatedAt, int searches, int searchesAnyType,
                         BigDecimal noResultsPercent, BigDecimal ledToViewPercent, int views, int saves,
                         List<Funnel> funnels, List<Count> topSuburbs, List<Count> topCategories, List<Count> bedrooms,
                         List<Count> rentPriceBands, List<Count> salePriceBands, List<ListingInterest> mostViewed,
                         List<ListingInterest> mostSaved, List<ResponseTime> responseTimes, List<Gap> gaps,
                         BigDecimal rentSearchesPerListing, BigDecimal saleSearchesPerListing, List<String> notes) {
    }

    public Demand demand(LocalDate from, LocalDate to, String city) {
        Long run = jdbc.queryForObject("select max(id) from analytics_job_runs where status = 'SUCCEEDED'", Long.class);
        String key = from + "|" + to + "|" + city + "#" + run;
        return cache.get(key, k -> build(from, to, city));
    }

    record Search(String type, String city, String suburb, String category, Integer bedrooms, BigDecimal minPrice,
                  BigDecimal maxPrice, Integer results, String visitor, Instant at) {
    }

    private Demand build(LocalDate from, LocalDate to, String cityFilter) {
        Instant start = from.atStartOfDay(time.zone()).toInstant();
        Instant endRaw = to.plusDays(1).atStartOfDay(time.zone()).toInstant();
        Instant now = time.clock().instant();
        Instant end = endRaw.isAfter(now) ? now : endRaw;
        Timestamp ts = Timestamp.from(start);
        Timestamp te = Timestamp.from(end);
        List<String> notes = new ArrayList<>();
        notes.add("Searches, views and saves show interest, not confirmed demand. They are anonymous, so funnels compare "
                + "volumes at each step in the period, not the same people.");

        // Live supply from the latest snapshot.
        LocalDate latest = jdbc.query("select max(snapshot_date) from listing_daily_snapshots",
                rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null);
        List<ListingState> live = new ArrayList<>();
        if (latest != null) {
            jdbc.query("select * from listing_daily_snapshots where snapshot_date = ? and status = 'AVAILABLE'",
                    rs -> { ListingState s = MarketDashboardService.state(rs); if (inCity(s.city(), cityFilter)) live.add(s); },
                    Date.valueOf(latest));
        } else {
            notes.add("No listing snapshots yet, so supply figures are missing.");
        }

        List<Search> searches = new ArrayList<>();
        Map<String, List<Instant>> viewsByVisitor = new HashMap<>();
        Map<Long, Integer> viewsByListing = new HashMap<>();
        Map<Long, Integer> savesByListing = new HashMap<>();
        int[] views = {0};
        int[] saves = {0};
        Map<String, int[]> viewsByType = new HashMap<>();
        jdbc.query("select event_type, property_id, listing_type, city, suburb, category, bedrooms, min_price, max_price, "
                + "result_count, visitor_hash, occurred_at from marketplace_events where occurred_at >= ? and occurred_at < ?", rs -> {
            String t = rs.getString("event_type");
            String city = rs.getString("city");
            if (cityFilter != null && city != null && !inCity(city, cityFilter)) return;
            Instant at = rs.getTimestamp("occurred_at").toInstant();
            switch (t) {
                case "SEARCH" -> {
                    if (cityFilter != null && city == null) return;
                    searches.add(new Search(rs.getString("listing_type"), city, rs.getString("suburb"), rs.getString("category"),
                            rs.getObject("bedrooms", Integer.class), rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"),
                            rs.getObject("result_count", Integer.class), rs.getString("visitor_hash"), at));
                }
                case "VIEW" -> {
                    if (cityFilter != null && city == null) return;
                    views[0]++;
                    viewsByVisitor.computeIfAbsent(rs.getString("visitor_hash"), k -> new ArrayList<>()).add(at);
                    viewsByListing.merge(rs.getLong("property_id"), 1, Integer::sum);
                    viewsByType.computeIfAbsent(String.valueOf(rs.getString("listing_type")), k -> new int[1])[0]++;
                }
                case "SAVE" -> {
                    if (cityFilter != null && city == null) return;
                    saves[0]++;
                    savesByListing.merge(rs.getLong("property_id"), 1, Integer::sum);
                }
                default -> { }
            }
        }, ts, te);
        viewsByVisitor.values().forEach(Collections::sort);

        // Abandonment: searches with no listing opened by the same browser soon after.
        int followed = 0;
        int noResults = 0;
        int anyType = 0;
        for (Search s : searches) {
            if (s.results() != null && s.results() == 0) noResults++;
            if (s.type() == null) anyType++;
            List<Instant> v = viewsByVisitor.getOrDefault(s.visitor(), List.of());
            for (Instant at : v) {
                if (!at.isBefore(s.at()) && at.isBefore(s.at().plus(FOLLOW_UP))) {
                    followed++;
                    break;
                }
            }
        }
        BigDecimal noResultsPct = pct(noResults, searches.size());
        BigDecimal ledToView = pct(followed, searches.size());

        // Enquiries and the later steps, per listing type (from the listing's current state).
        Map<Long, String[]> listingInfo = new HashMap<>();
        jdbc.query("select id, listing_type, city, suburb, title, price from properties", rs -> {
            listingInfo.put(rs.getLong("id"), new String[]{rs.getString("listing_type"), rs.getString("city"), rs.getString("suburb"),
                    rs.getString("title"), rs.getBigDecimal("price") == null ? null : rs.getBigDecimal("price").toPlainString()});
        });
        Map<String, int[]> enquiriesByType = new HashMap<>();
        Map<Long, Integer> enquiriesByListing = new HashMap<>();
        for (String sql : List.of("select property_id, intent from enquiries where created_at >= ? and created_at < ?",
                "select property_id, cast(null as char(10)) intent from property_inquiries where created_at >= ? and created_at < ?")) {
            jdbc.query(sql, rs -> {
                Long pid = rs.getObject("property_id", Long.class);
                String[] info = pid == null ? null : listingInfo.get(pid);
                String type = info != null ? info[0] : "BUY".equals(rs.getString("intent")) ? "SALE"
                        : "RENT".equals(rs.getString("intent")) ? "RENT" : null;
                if (cityFilter != null && (info == null || !inCity(info[1], cityFilter))) return;
                enquiriesByType.computeIfAbsent(String.valueOf(type), k -> new int[1])[0]++;
                if (pid != null) enquiriesByListing.merge(pid, 1, Integer::sum);
            }, ts, te);
        }
        int[] viewingsRent = {0};
        int[] viewingsSale = {0};
        jdbc.query("select property_id from viewing_requests where created_at >= ? and created_at < ?", rs -> {
            String[] info = listingInfo.get(rs.getLong(1));
            if (info == null || (cityFilter != null && !inCity(info[1], cityFilter))) return;
            if ("RENT".equals(info[0])) viewingsRent[0]++;
            if ("SALE".equals(info[0])) viewingsSale[0]++;
        }, ts, te);
        int[] apps = {0, 0};
        jdbc.query("select property_id, status from rental_applications where submitted_at >= ? and submitted_at < ?", rs -> {
            String[] info = listingInfo.get(rs.getLong(1));
            if (info == null || (cityFilter != null && !inCity(info[1], cityFilter))) return;
            apps[0]++;
            String st = rs.getString("status");
            if ("APPROVED".equals(st) || "LEASE_PREPARATION".equals(st)) apps[1]++;
        }, ts, te);
        int[] offers = {0, 0};
        jdbc.query("select property_id, status from offers where created_at >= ? and created_at < ?", rs -> {
            String[] info = listingInfo.get(rs.getLong(1));
            if (info == null || (cityFilter != null && !inCity(info[1], cityFilter))) return;
            offers[0]++;
            if ("ACCEPTED".equals(rs.getString("status"))) offers[1]++;
        }, ts, te);

        int rentSearches = (int) searches.stream().filter(s -> "RENT".equals(s.type())).count();
        int saleSearches = (int) searches.stream().filter(s -> "SALE".equals(s.type())).count();
        List<Funnel> funnels = List.of(
                funnel("RENT", List.of(
                        new int[]{rentSearches}, new int[]{viewsByType.getOrDefault("RENT", new int[1])[0]},
                        new int[]{enquiriesByType.getOrDefault("RENT", new int[1])[0]}, new int[]{viewingsRent[0]},
                        new int[]{apps[0]}, new int[]{apps[1]}),
                        List.of("Searches", "Listing views", "Enquiries", "Viewing requests", "Applications", "Approved applications")),
                funnel("SALE", List.of(
                        new int[]{saleSearches}, new int[]{viewsByType.getOrDefault("SALE", new int[1])[0]},
                        new int[]{enquiriesByType.getOrDefault("SALE", new int[1])[0]}, new int[]{viewingsSale[0]},
                        new int[]{offers[0]}, new int[]{offers[1]}),
                        List.of("Searches", "Listing views", "Enquiries", "Viewing requests", "Offers", "Accepted offers")));

        // What people searched for, next to what's live.
        Map<String, Count> bySuburb = new LinkedHashMap<>();
        Map<String, int[]> tallies = new HashMap<>();
        for (Search s : searches) {
            String place = s.suburb() != null ? s.suburb().trim() : s.city() != null ? s.city().trim() + " (whole city)" : null;
            if (place == null) continue;
            String k = (place + "|" + (s.suburb() != null ? String.valueOf(s.city()) : "")).toLowerCase(Locale.ROOT);
            int[] t = tallies.computeIfAbsent("S" + k, x -> new int[2]);
            t[0]++;
            if (s.results() != null && s.results() == 0) t[1]++;
            if (!bySuburb.containsKey(k)) bySuburb.put(k, new Count(k, place, s.suburb() != null ? s.city() : s.city(), 0, 0, null, null));
        }
        List<Count> topSuburbs = new ArrayList<>();
        bySuburb.forEach((k, c) -> {
            int[] t = tallies.get("S" + k);
            boolean wholeCity = c.label().endsWith("(whole city)");
            int supply = (int) live.stream().filter(s -> wholeCity ? inCity(s.city(), c.city())
                    : s.suburb() != null && s.suburb().trim().equalsIgnoreCase(c.label())
                    && (c.city() == null || inCity(s.city(), c.city()))).count();
            topSuburbs.add(new Count(k, c.label(), wholeCity ? null : c.city(), t[0], t[1], latest == null ? null : supply,
                    latest == null || supply == 0 ? null : ratio(t[0], supply)));
        });
        topSuburbs.sort(Comparator.comparingInt(Count::searches).reversed());

        List<Count> topCategories = tally(searches, s -> s.category(), s -> MarketDashboardService.categoryLabel(s.category()),
                live, (l, s) -> s.category() != null && s.category().equals(l.category()), latest != null);
        List<Count> beds = tally(searches, s -> s.bedrooms() == null ? null : s.bedrooms() >= 5 ? "5" : String.valueOf(s.bedrooms()),
                s -> s.bedrooms() == null ? null : s.bedrooms() >= 5 ? "5+ bedrooms" : s.bedrooms() == 0 ? "Any / studio" : s.bedrooms() + "+ bedrooms",
                live, (l, s) -> l.bedrooms() != null && s.bedrooms() != null && l.bedrooms() >= s.bedrooms(), latest != null);
        beds = beds.stream().sorted(Comparator.comparing(Count::key)).toList();
        List<Count> rentBands = bands(searches, "RENT", RENT_BANDS);
        List<Count> saleBands = bands(searches, "SALE", SALE_BANDS);

        // Listings attracting the most interest.
        List<ListingInterest> mostViewed = viewsByListing.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()).limit(10)
                .map(e -> interest(e.getKey(), listingInfo, viewsByListing, savesByListing, enquiriesByListing)).toList();
        Map<Long, Integer> currentlySaved = new HashMap<>();
        jdbc.query("select property_id, count(*) n from saved_properties group by property_id",
                rs -> { currentlySaved.put(rs.getLong(1), rs.getInt(2)); });
        List<ListingInterest> mostSaved = currentlySaved.entrySet().stream()
                .filter(e -> cityFilter == null || (listingInfo.get(e.getKey()) != null && inCity(listingInfo.get(e.getKey())[1], cityFilter)))
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()).limit(10)
                .map(e -> interest(e.getKey(), listingInfo, viewsByListing, currentlySaved, enquiriesByListing)).toList();

        List<ResponseTime> responses = List.of(
                // First response = the agency moved the enquiry on from NEW (recorded from 2026-11-27).
                responseTime("enquiries", "Enquiries first answered", "select captured_at a, first_responded_at b, property_id from enquiries "
                        + "where captured_at >= ? and captured_at < ? and first_responded_at is not null", ts, te, listingInfo, cityFilter),
                responseTime("viewings", "Viewing requests confirmed", "select created_at a, confirmed_at b, property_id from viewing_requests "
                        + "where created_at >= ? and created_at < ? and confirmed_at is not null", ts, te, listingInfo, cityFilter),
                responseTime("applications", "Rental applications reviewed", "select submitted_at a, reviewed_at b, property_id from "
                        + "rental_applications where submitted_at >= ? and submitted_at < ? and reviewed_at is not null", ts, te, listingInfo, cityFilter),
                responseTime("offers", "Offers decided", "select created_at a, decided_at b, property_id from offers "
                        + "where created_at >= ? and created_at < ? and decided_at is not null", ts, te, listingInfo, cityFilter));

        // Supply gaps: segments searched far more than the market overall, relative to what's live.
        int rentLive = (int) live.stream().filter(l -> "RENT".equals(l.listingType())).count();
        int saleLive = (int) live.stream().filter(l -> "SALE".equals(l.listingType())).count();
        BigDecimal rentRatio = rentLive == 0 ? null : ratio(rentSearches, rentLive);
        BigDecimal saleRatio = saleLive == 0 ? null : ratio(saleSearches, saleLive);
        List<Gap> gaps = latest == null ? List.of() : gaps(searches, live, rentRatio, saleRatio);

        Instant calculated = jdbc.query("select max(finished_at) from analytics_job_runs where status = 'SUCCEEDED'",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
        Instant capture = jdbc.query("select started_at from analytics_capture_starts where source = 'marketplace_events'",
                rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null);
        if (capture != null && capture.isAfter(start)) {
            notes.add("Search, view and save tracking started on " + capture.atZone(time.zone()).toLocalDate()
                    + "; earlier dates have none.");
        }
        return new Demand(from, to, calculated, searches.size(), anyType, noResultsPct, ledToView, views[0], saves[0], funnels,
                topSuburbs.subList(0, Math.min(25, topSuburbs.size())), topCategories, beds, rentBands, saleBands, mostViewed,
                mostSaved, responses, gaps, rentRatio, saleRatio, notes);
    }

    private static Funnel funnel(String type, List<int[]> counts, List<String> labels) {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            int c = counts.get(i)[0];
            BigDecimal conv = i == 0 ? null : pct(c, counts.get(i - 1)[0]);
            steps.add(new Step(labels.get(i).toLowerCase(Locale.ROOT).replace(' ', '_'), labels.get(i), c, conv));
        }
        return new Funnel(type, steps);
    }

    private ResponseTime responseTime(String key, String label, String sql, Timestamp from, Timestamp to,
                                      Map<Long, String[]> info, String city) {
        List<BigDecimal> hours = new ArrayList<>();
        jdbc.query(sql, rs -> {
            String[] i = info.get(rs.getLong("property_id"));
            if (city != null && (i == null || !inCity(i[1], city))) return;
            long minutes = Duration.between(rs.getTimestamp("a").toInstant(), rs.getTimestamp("b").toInstant()).toMinutes();
            if (minutes >= 0) hours.add(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP));
        }, from, to);
        Stats.Summary s = Stats.summarise(hours);
        return new ResponseTime(key, label, s.median(), s.n(),
                s.n() == 0 ? "None answered in this period." : s.n() < MarketDashboardService.MIN_SAMPLE ? "Small sample." : null);
    }

    interface Key {
        String of(Search s);
    }

    interface Match {
        boolean test(ListingState l, Search s);
    }

    private static List<Count> tally(List<Search> searches, Key key, Key label, List<ListingState> live, Match match, boolean haveSupply) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Search> sample = new HashMap<>();
        for (Search s : searches) {
            String k = key.of(s);
            if (k == null) continue;
            int[] c = counts.computeIfAbsent(k, x -> new int[2]);
            c[0]++;
            if (s.results() != null && s.results() == 0) c[1]++;
            sample.putIfAbsent(k, s);
        }
        List<Count> out = new ArrayList<>();
        counts.forEach((k, c) -> {
            Search s = sample.get(k);
            int supply = (int) live.stream().filter(l -> (s.type() == null || s.type().equals(l.listingType())) && match.test(l, s)).count();
            out.add(new Count(k, label.of(s), null, c[0], c[1], haveSupply ? supply : null,
                    !haveSupply || supply == 0 ? null : ratio(c[0], supply)));
        });
        out.sort(Comparator.comparingInt(Count::searches).reversed());
        return out;
    }

    /** Price brackets people searched in: by the top of their range (or the bottom, if no top was given). */
    private static List<Count> bands(List<Search> searches, String type, long[] edges) {
        Map<Integer, int[]> counts = new TreeMap<>();
        for (Search s : searches) {
            if (!type.equals(s.type())) continue;
            BigDecimal p = s.maxPrice() != null ? s.maxPrice() : s.minPrice();
            if (p == null) continue;
            int band = 0;
            for (int i = edges.length - 1; i >= 0; i--) {
                if (p.compareTo(BigDecimal.valueOf(edges[i])) >= 0) { band = i; break; }
            }
            int[] c = counts.computeIfAbsent(band, x -> new int[2]);
            c[0]++;
            if (s.results() != null && s.results() == 0) c[1]++;
        }
        List<Count> out = new ArrayList<>();
        counts.forEach((i, c) -> out.add(new Count(String.format("%02d", i),
                i == edges.length - 1 ? "$" + money(edges[i]) + "+" : "$" + money(edges[i]) + "–$" + money(edges[i + 1]),
                null, c[0], c[1], null, null)));
        return out;
    }

    private List<Gap> gaps(List<Search> searches, List<ListingState> live, BigDecimal rentRatio, BigDecimal saleRatio) {
        Map<String, List<Search>> segments = new LinkedHashMap<>();
        for (Search s : searches) {
            if (s.type() == null || "SHORT_STAY".equals(s.type())) continue;
            if (s.category() == null && s.bedrooms() == null && s.suburb() == null) continue; // too broad to be a segment
            String k = String.join("|", s.type(), norm(s.city()), norm(s.suburb()), String.valueOf(s.category()), String.valueOf(s.bedrooms()));
            segments.computeIfAbsent(k, x -> new ArrayList<>()).add(s);
        }
        List<Gap> out = new ArrayList<>();
        segments.forEach((k, list) -> {
            if (list.size() < MIN_SEARCHES) return;
            Search s = list.get(0);
            BigDecimal market = "RENT".equals(s.type()) ? rentRatio : saleRatio;
            int supply = (int) live.stream().filter(l -> s.type().equals(l.listingType())
                    && (s.city() == null || inCity(l.city(), s.city()))
                    && (s.suburb() == null || (l.suburb() != null && l.suburb().trim().equalsIgnoreCase(s.suburb().trim())))
                    && (s.category() == null || s.category().equals(l.category()))
                    && (s.bedrooms() == null || (l.bedrooms() != null && l.bedrooms() >= s.bedrooms()))).count();
            int none = (int) list.stream().filter(x -> x.results() != null && x.results() == 0).count();
            BigDecimal perListing = supply == 0 ? null : ratio(list.size(), supply);
            BigDecimal lift = perListing == null || market == null || market.signum() == 0 ? null
                    : perListing.divide(market, 1, RoundingMode.HALF_UP);
            if (supply > 0 && (lift == null || lift.compareTo(GAP_LIFT) < 0)) return;
            String what = describe(s);
            String explanation = supply == 0
                    ? list.size() + " searches for " + what + " in the period, and no matching listing is live."
                    : list.size() + " searches for " + what + " against " + supply + " live listing(s): " + lift
                    + "× the market's searches per listing. Interest, not confirmed demand.";
            out.add(new Gap(s.type(), s.city(), s.category(), s.bedrooms(), list.size(), none, supply, perListing, lift, explanation));
        });
        out.sort(Comparator.comparingInt(Gap::liveListings).thenComparing(Gap::searches, Comparator.reverseOrder()));
        return out;
    }

    static String describe(Search s) {
        StringBuilder b = new StringBuilder();
        if (s.bedrooms() != null && s.bedrooms() > 0) b.append(s.bedrooms()).append("+ bedroom ");
        b.append(s.category() == null ? "homes" : MarketDashboardService.categoryLabel(s.category()).toLowerCase(Locale.ROOT) + "s");
        b.append("RENT".equals(s.type()) ? " to rent" : " for sale");
        if (s.suburb() != null) b.append(" in ").append(s.suburb().trim());
        if (s.city() != null) b.append(s.suburb() != null ? ", " : " in ").append(s.city().trim());
        return b.toString();
    }

    private static ListingInterest interest(long id, Map<Long, String[]> info, Map<Long, Integer> views, Map<Long, Integer> saves,
                                            Map<Long, Integer> enquiries) {
        String[] i = info.get(id);
        return new ListingInterest(id, i == null ? "(deleted listing)" : i[3], i == null ? null : i[0], i == null ? null : i[2],
                i == null ? null : i[1], i == null || i[4] == null ? null : new BigDecimal(i[4]), views.getOrDefault(id, 0),
                saves.getOrDefault(id, 0), enquiries.getOrDefault(id, 0));
    }

    private static boolean inCity(String actual, String wanted) {
        return wanted == null || (actual != null && norm(actual).equals(norm(wanted)));
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static BigDecimal pct(int part, int whole) {
        return whole == 0 ? null : BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(int a, int b) {
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 2, RoundingMode.HALF_UP);
    }

    private static String money(long v) {
        return v >= 1_000_000 ? (v / 1_000_000) + "m" : v >= 1000 ? (v / 1000) + "k" : String.valueOf(v);
    }
}
