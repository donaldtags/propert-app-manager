package com.example.estatex.analytics.metrics;

import com.example.estatex.analytics.metrics.MetricDefinition.Kind;
import com.example.estatex.analytics.metrics.StateReader.Event;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calculates every {@link MetricDefinition} for one period, for every market slice, from the
 * source tables. Pure read: the result is handed to the engine, which publishes it.
 *
 * <p>With tracing on, each figure also lists the records it was built from ("property:12",
 * "lease:4"…), for drill-down. Anonymous views and searches have no individual records to show.
 */
@Component
public class MarketMetricsCalculator {
    private static final Set<MetricDefinition> MONEY = Set.of(MetricDefinition.ASKING_PRICE, MetricDefinition.ASKING_PRICE_PER_M2,
            MetricDefinition.CONTRACTED_RENT, MetricDefinition.VERIFIED_SALES, MetricDefinition.PLATFORM_REVENUE,
            MetricDefinition.REVENUE_SUBSCRIPTIONS, MetricDefinition.REVENUE_FEATURED_LISTINGS, MetricDefinition.PAYMENT_VOLUME);
    /** Distributions whose headline value is the count, not the median. */
    private static final Set<MetricDefinition> COUNT_HEADLINE = Set.of(MetricDefinition.PRICE_REDUCTIONS, MetricDefinition.VERIFIED_SALES);
    /** Distributions whose headline value is the total. */
    private static final Set<MetricDefinition> SUM_HEADLINE = Set.of(MetricDefinition.PLATFORM_REVENUE,
            MetricDefinition.REVENUE_SUBSCRIPTIONS, MetricDefinition.REVENUE_FEATURED_LISTINGS, MetricDefinition.PAYMENT_VOLUME);
    private static final Map<String, MetricDefinition> STATUS_METRICS = Map.of(
            "WITHDRAWN", MetricDefinition.WITHDRAWN_LISTINGS,
            "EXPIRED", MetricDefinition.EXPIRED_LISTINGS,
            "UNDER_OFFER", MetricDefinition.UNDER_OFFER,
            "REPORTED_SOLD", MetricDefinition.REPORTED_SOLD);
    private static final Set<String> UPDATE_EVENTS = Set.of("PRICE_CHANGED", "CHARACTERISTICS_CHANGED", "LOCATION_CHANGED",
            "LISTING_TYPE_CHANGED");
    /** EstateX's own products. Everything else that moves through payments is client money. */
    private static final Map<String, MetricDefinition> REVENUE_PRODUCTS = Map.of(
            "SUBSCRIPTION", MetricDefinition.REVENUE_SUBSCRIPTIONS,
            "FEATURED_LISTING", MetricDefinition.REVENUE_FEATURED_LISTINGS);
    private static final Map<String, MetricDefinition> NEW_BY_ROLE = Map.of(
            "AGENT", MetricDefinition.NEW_AGENTS, "LANDLORD", MetricDefinition.NEW_LANDLORDS,
            "DEVELOPER", MetricDefinition.NEW_DEVELOPERS);

    private final JdbcTemplate jdbc;
    private final StateReader reader;

    public MarketMetricsCalculator(JdbcTemplate jdbc, StateReader reader) {
        this.jdbc = jdbc;
        this.reader = reader;
    }

    /** Facts shared by every period in one run, loaded once. */
    public record RunContext(Map<Long, Event> firstAvailable, Map<String, Instant> captureStarts, boolean trace) {
    }

    public RunContext context() {
        return context(false);
    }

    public RunContext context(boolean trace) {
        Map<String, Instant> starts = new HashMap<>();
        jdbc.query("select source, started_at from analytics_capture_starts",
                rs -> { starts.put(rs.getString("source"), rs.getTimestamp("started_at").toInstant()); });
        return new RunContext(reader.firstAvailable(), starts, trace);
    }

    /** The figures, and with tracing on, the records behind each one (keyed metric#dimsKey). */
    public record Result(List<MetricValue> values, Map<String, List<String>> refs) {
    }

    public List<MetricValue> compute(Period p, Instant now, ZoneId zone, RunContext ctx, Long runId) {
        return computeTraced(p, now, zone, ctx, runId).values();
    }

    public Result computeTraced(Period p, Instant now, ZoneId zone, RunContext ctx, Long runId) {
        Instant asOf = p.asOf(now);
        boolean hourly = p.grain() == Grain.HOUR;
        Acc acc = new Acc(hourly, ctx.trace());

        Map<Long, ListingState> end = reader.stateAsOf(asOf);
        List<Event> events = reader.events(p.start(), asOf);

        for (ListingState s : end.values()) {
            if (!s.available()) continue;
            acc.count(MetricDefinition.ACTIVE_LISTINGS, Dims.of(s), true, "property:" + s.propertyId());
            if ("VERIFIED".equals(s.verificationStatus())) {
                acc.count(MetricDefinition.VERIFIED_LISTINGS, Dims.of(s), true, "property:" + s.propertyId());
            }
        }
        for (Event e : ctx.firstAvailable().values()) {
            if (!"BASELINE".equals(e.type()) && !e.occurredAt().isBefore(p.start()) && e.occurredAt().isBefore(asOf)) {
                acc.count(MetricDefinition.NEW_LISTINGS, Dims.of(e.state()), true, "property:" + e.propertyId());
            }
        }
        Set<String> seen = new HashSet<>();
        for (Event e : events) {
            String ref = "property:" + e.propertyId();
            if (UPDATE_EVENTS.contains(e.type()) && seen.add("U:" + e.propertyId())) {
                acc.count(MetricDefinition.UPDATED_LISTINGS, Dims.of(e.state()), true, ref);
            }
            if ("STATUS_CHANGED".equals(e.type())) {
                MetricDefinition m = STATUS_METRICS.get(e.state().status());
                if (m != null && seen.add(m.name() + ":" + e.propertyId())) acc.count(m, Dims.of(e.state()), true, ref);
            } else if ("PRICE_CHANGED".equals(e.type())) {
                BigDecimal before = e.previousPrice();
                BigDecimal after = e.state().price();
                if (before != null && after != null && before.signum() > 0 && after.signum() > 0) {
                    if (after.compareTo(before) < 0) {
                        BigDecimal pct = before.subtract(after).multiply(BigDecimal.valueOf(100))
                                .divide(before, 2, RoundingMode.HALF_UP);
                        acc.value(MetricDefinition.PRICE_REDUCTIONS, Dims.of(e.state()), pct, true, ref);
                    } else if (after.compareTo(before) > 0) {
                        acc.count(MetricDefinition.PRICE_INCREASES, Dims.of(e.state()), true, ref);
                    }
                }
            }
        }

        if (!hourly) askingPrices(p, end, events, acc);
        leases(p, asOf, end, acc);
        sales(p, asOf, zone, hourly, acc);
        activity(p, asOf, end, acc);
        platform(p, asOf, zone, hourly, acc);
        payments(p, asOf, acc);
        List<MetricValue> rows = acc.rows(p, now, runId);
        for (MetricValue v : rows) {
            String source = MetricDefinition.valueOf(v.getMetricKey()).captureSource();
            Instant started = source == null ? null : ctx.captureStarts().get(source);
            // Started recording after this period began: the figure misses part of the period.
            v.setHistoryComplete(source == null || (started != null && !started.isAfter(p.start())));
        }
        return new Result(rows, acc.refs());
    }

    private void askingPrices(Period p, Map<Long, ListingState> end, List<Event> events, Acc acc) {
        Set<Long> liveDuring = new HashSet<>();
        reader.stateAsOf(p.start()).values().forEach(s -> {
            if (s.available()) liveDuring.add(s.propertyId());
        });
        for (Event e : events) {
            if (e.state().available()) liveDuring.add(e.propertyId());
        }
        for (Long id : liveDuring) {
            ListingState s = end.get(id);
            if (s == null) continue;
            Dims d = Dims.of(s);
            String ref = "property:" + id;
            if (s.price() == null || s.price().signum() <= 0 || !"USD".equalsIgnoreCase(s.currency())) {
                acc.exclude(MetricDefinition.ASKING_PRICE, d);
                continue;
            }
            acc.value(MetricDefinition.ASKING_PRICE, d, s.price(), false, ref);
            boolean hasArea = s.floorAreaM2() != null && s.floorAreaM2() > 0;
            acc.coverage(MetricDefinition.ASKING_PRICE_PER_M2, d, hasArea);
            if (hasArea) {
                acc.value(MetricDefinition.ASKING_PRICE_PER_M2, d,
                        s.price().divide(BigDecimal.valueOf(s.floorAreaM2()), 2, RoundingMode.HALF_UP), false, ref);
            }
        }
    }

    private void leases(Period p, Instant asOf, Map<Long, ListingState> end, Acc acc) {
        jdbc.query("select id, property_id, monthly_rent, currency from leases where signed_at >= ? and signed_at < ?", rs -> {
            ListingState s = end.get(rs.getLong("property_id"));
            Dims d = s == null ? new Dims("RENT", null, null, null, null, null)
                    : new Dims("RENT", Dims.of(s).country(), Dims.of(s).city(), Dims.of(s).suburb(), s.category(),
                    Dims.bedroomBand(s.bedrooms()));
            String ref = "lease:" + rs.getLong("id");
            acc.count(MetricDefinition.RENTAL_AGREEMENTS_SIGNED, d, true, ref);
            BigDecimal rent = rs.getBigDecimal("monthly_rent");
            String currency = rs.getString("currency");
            if (rent == null || rent.signum() <= 0 || (currency != null && !"USD".equalsIgnoreCase(currency))) {
                acc.exclude(MetricDefinition.CONTRACTED_RENT, d);
            } else {
                acc.value(MetricDefinition.CONTRACTED_RENT, d, rent, false, ref);
            }
        }, Timestamp.from(p.start()), Timestamp.from(asOf));
    }

    private void sales(Period p, Instant asOf, ZoneId zone, boolean hourly, Acc acc) {
        jdbc.query("select id, country, city, suburb, category, bedrooms from sale_transactions "
                + "where reported_at >= ? and reported_at < ? and verification <> 'REJECTED'", rs -> {
            acc.count(MetricDefinition.SALES_REPORTED, Dims.of("SALE", rs.getString("country"), rs.getString("city"),
                    rs.getString("suburb"), rs.getString("category"), rs.getObject("bedrooms", Integer.class)), true,
                    "sale:" + rs.getLong("id"));
        }, Timestamp.from(p.start()), Timestamp.from(asOf));
        if (hourly) return;
        LocalDate[] days = dates(p, asOf, zone);
        jdbc.query("select id, price, currency, country, city, suburb, category, bedrooms from sale_transactions "
                + "where verification = 'VERIFIED' and completion_date >= ? and completion_date < ?", rs -> {
            Dims d = Dims.of("SALE", rs.getString("country"), rs.getString("city"), rs.getString("suburb"),
                    rs.getString("category"), rs.getObject("bedrooms", Integer.class));
            if ("USD".equalsIgnoreCase(rs.getString("currency"))) {
                acc.value(MetricDefinition.VERIFIED_SALES, d, rs.getBigDecimal("price"), true, "sale:" + rs.getLong("id"));
            } else {
                acc.exclude(MetricDefinition.VERIFIED_SALES, d);
            }
        }, Date.valueOf(days[0]), Date.valueOf(days[1]));
    }

    /** The Harare dates a period covers, as [first, afterLast). Today is included while the period is running. */
    static LocalDate[] dates(Period p, Instant asOf, ZoneId zone) {
        LocalDate first = p.start().atZone(zone).toLocalDate();
        boolean endsAtMidnight = asOf.atZone(zone).toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
        LocalDate afterLast = asOf.atZone(zone).toLocalDate().plusDays(endsAtMidnight ? 0 : 1);
        return new LocalDate[]{first, afterLast};
    }

    private void activity(Period p, Instant asOf, Map<Long, ListingState> end, Acc acc) {
        Timestamp from = Timestamp.from(p.start());
        Timestamp to = Timestamp.from(asOf);
        jdbc.query("select event_type, listing_type, city, suburb, category, bedrooms, result_count "
                + "from marketplace_events where occurred_at >= ? and occurred_at < ?", rs -> {
            // Visitors don't pick a country; every listing so far is in Zimbabwe (country slices come with expansion).
            String city = rs.getString("city");
            Dims d = Dims.of(rs.getString("listing_type"), city == null ? null : "Zimbabwe", city,
                    rs.getString("suburb"), rs.getString("category"), rs.getObject("bedrooms", Integer.class));
            switch (rs.getString("event_type")) {
                case "VIEW" -> acc.count(MetricDefinition.LISTING_VIEWS, d, true, null);
                case "SAVE" -> acc.count(MetricDefinition.SAVES, d, true, null);
                case "SEARCH" -> {
                    acc.count(MetricDefinition.SEARCHES, d, true, null);
                    Integer results = rs.getObject("result_count", Integer.class);
                    if (results != null && results == 0) acc.count(MetricDefinition.SEARCHES_NO_RESULTS, d, true, null);
                }
                default -> { }
            }
        }, from, to);
        jdbc.query("select id, property_id, intent from enquiries where created_at >= ? and created_at < ?", rs -> {
            Long id = rs.getObject("property_id", Long.class);
            String intent = rs.getString("intent");
            String type = "BUY".equals(intent) ? "SALE" : "RENT".equals(intent) ? "RENT" : null;
            acc.count(MetricDefinition.ENQUIRIES, dimsFor(id, end, type), true, "enquiry:" + rs.getLong("id"));
        }, from, to);
        perProperty("select id, property_id from property_inquiries where created_at >= ? and created_at < ?",
                MetricDefinition.ENQUIRIES, "inquiry", from, to, end, acc);
        perProperty("select id, property_id from viewing_requests where created_at >= ? and created_at < ?",
                MetricDefinition.VIEWING_REQUESTS, "viewing", from, to, end, acc);
        perProperty("select id, property_id from rental_applications where submitted_at >= ? and submitted_at < ?",
                MetricDefinition.RENTAL_APPLICATIONS, "application", from, to, end, acc);
        perProperty("select id, property_id from offers where created_at >= ? and created_at < ?",
                MetricDefinition.OFFERS, "offer", from, to, end, acc);
    }

    private void platform(Period p, Instant asOf, ZoneId zone, boolean hourly, Acc acc) {
        Long total = jdbc.queryForObject("select count(*) from users where created_at < ?", Long.class, Timestamp.from(asOf));
        acc.add(MetricDefinition.TOTAL_USERS, total == null ? 0 : total);
        jdbc.query("select id from users where created_at >= ? and created_at < ?", rs -> {
            acc.count(MetricDefinition.NEW_REGISTRATIONS, Dims.ALL, true, "user:" + rs.getLong("id"));
        }, Timestamp.from(p.start()), Timestamp.from(asOf));
        jdbc.query("select u.id, r.roles from users u join app_user_roles r on r.app_user_id = u.id "
                + "where u.created_at >= ? and u.created_at < ? and r.roles in ('AGENT', 'LANDLORD', 'DEVELOPER')", rs -> {
            acc.count(NEW_BY_ROLE.get(rs.getString("roles")), Dims.ALL, true, "user:" + rs.getLong("id"));
        }, Timestamp.from(p.start()), Timestamp.from(asOf));
        if (hourly) return; // activity is recorded per day
        LocalDate[] days = dates(p, asOf, zone);
        jdbc.query("select distinct user_id from user_activity_days where activity_date >= ? and activity_date < ?", rs -> {
            acc.count(MetricDefinition.ACTIVE_USERS, Dims.ALL, true, "user:" + rs.getLong("user_id"));
        }, Date.valueOf(days[0]), Date.valueOf(days[1]));
    }

    private void payments(Period p, Instant asOf, Acc acc) {
        Timestamp from = Timestamp.from(p.start());
        Timestamp to = Timestamp.from(asOf);
        jdbc.query("select id, amount, currency, purpose_type, status, paid_at, created_at from payments "
                + "where (paid_at >= ? and paid_at < ?) or (created_at >= ? and created_at < ?)", rs -> {
            String ref = "payment:" + rs.getLong("id");
            String status = rs.getString("status");
            Timestamp paid = rs.getTimestamp("paid_at");
            Instant paidAt = (paid != null ? paid : rs.getTimestamp("created_at")).toInstant();
            Instant created = rs.getTimestamp("created_at").toInstant();
            if ("FAILED".equals(status) && !created.isBefore(p.start()) && created.isBefore(asOf)) {
                acc.count(MetricDefinition.PAYMENTS_FAILED, Dims.ALL, true, ref);
            }
            if (!"SUCCESSFUL".equals(status) || paidAt.isBefore(p.start()) || !paidAt.isBefore(asOf)) return;
            acc.count(MetricDefinition.PAYMENTS_SUCCESSFUL, Dims.ALL, true, ref);
            BigDecimal amount = rs.getBigDecimal("amount");
            boolean usd = "USD".equalsIgnoreCase(rs.getString("currency"));
            MetricDefinition product = REVENUE_PRODUCTS.get(rs.getString("purpose_type"));
            if (product != null) {
                if (!usd || amount == null) {
                    acc.exclude(MetricDefinition.PLATFORM_REVENUE, Dims.ALL);
                    return;
                }
                acc.value(MetricDefinition.PLATFORM_REVENUE, Dims.ALL, amount, true, ref);
                acc.value(product, Dims.ALL, amount, true, ref);
            } else if (usd && amount != null) {
                acc.value(MetricDefinition.PAYMENT_VOLUME, Dims.ALL, amount, true, ref);
            }
        }, from, to, from, to);
    }

    private void perProperty(String sql, MetricDefinition m, String kind, Timestamp from, Timestamp to,
                             Map<Long, ListingState> end, Acc acc) {
        jdbc.query(sql, rs -> {
            acc.count(m, dimsFor(rs.getObject("property_id", Long.class), end, null), true, kind + ":" + rs.getLong("id"));
        }, from, to);
    }

    private static Dims dimsFor(Long propertyId, Map<Long, ListingState> end, String fallbackType) {
        ListingState s = propertyId == null ? null : end.get(propertyId);
        if (s != null) return Dims.of(s);
        return new Dims(fallbackType, null, null, null, null, null);
    }

    /** Accumulates observations per (metric, slice) and turns them into rows. */
    static final class Acc {
        private static final int MAX_REFS = 2000;
        private final boolean hourly;
        private final boolean trace;
        private final Map<String, Cell> cells = new LinkedHashMap<>();

        Acc(boolean hourly, boolean trace) {
            this.hourly = hourly;
            this.trace = trace;
            // Headline zero rows, so "0 this period" is published instead of a gap.
            for (MetricDefinition m : MetricDefinition.values()) {
                // An all-types median would mix rents with sale prices, so only counts and totals get one.
                if (m.kind() == Kind.COUNT || SUM_HEADLINE.contains(m)) cell(m, Dims.ALL);
                if (m.domain() == MetricDefinition.Domain.MARKET || m.domain() == MetricDefinition.Domain.DEMAND) {
                    if (m.kind() == Kind.COUNT) {
                        for (String type : List.of("RENT", "SALE")) cell(m, new Dims(type, null, null, null, null, null));
                    }
                }
            }
        }

        void count(MetricDefinition m, Dims d, boolean includeAllTypes, String ref) {
            for (Dims r : d.rollups(includeAllTypes, hourly)) {
                Cell c = cell(m, r);
                c.count++;
                c.ref(ref, trace);
            }
        }

        /** Adds a pre-counted total (no individual records), e.g. registered accounts. */
        void add(MetricDefinition m, long n) {
            cell(m, Dims.ALL).count += (int) n;
        }

        void value(MetricDefinition m, Dims d, BigDecimal v, boolean includeAllTypes, String ref) {
            for (Dims r : d.rollups(includeAllTypes, hourly)) {
                Cell c = cell(m, r);
                c.values.add(v);
                c.ref(ref, trace);
            }
        }

        void exclude(MetricDefinition m, Dims d) {
            for (Dims r : d.rollups(d.listingType() == null, hourly)) cell(m, r).excluded++;
        }

        void coverage(MetricDefinition m, Dims d, boolean has) {
            for (Dims r : d.rollups(false, hourly)) {
                Cell c = cell(m, r);
                c.coverageDen++;
                if (has) c.coverageNum++;
            }
        }

        private Cell cell(MetricDefinition m, Dims d) {
            return cells.computeIfAbsent(m.name() + "#" + d.key(), k -> new Cell(m, d));
        }

        Map<String, List<String>> refs() {
            Map<String, List<String>> out = new HashMap<>();
            if (trace) cells.forEach((k, c) -> out.put(k, c.refs));
            return out;
        }

        List<MetricValue> rows(Period p, Instant now, Long runId) {
            List<MetricValue> out = new ArrayList<>();
            Instant asOf = p.asOf(now);
            for (Cell c : cells.values()) {
                MetricValue v = new MetricValue();
                v.setMetricKey(c.metric.name());
                v.setDefinitionVersion(c.metric.version());
                v.setGrain(p.grain().name());
                v.setPeriodStart(p.start());
                v.setPeriodEnd(p.end());
                v.setAsOf(asOf);
                v.setComplete(p.isComplete(now));
                v.setDimsKey(c.dims.key());
                v.setDimListingType(c.dims.listingType());
                v.setDimCountry(c.dims.country());
                v.setDimCity(c.dims.city());
                v.setDimSuburb(c.dims.suburb());
                v.setDimCategory(c.dims.category());
                v.setDimBedrooms(c.dims.bedrooms());
                v.setExcludedCount(c.excluded);
                if (c.coverageDen > 0) {
                    v.setCoverage(BigDecimal.valueOf(c.coverageNum).divide(BigDecimal.valueOf(c.coverageDen), 4, RoundingMode.HALF_UP));
                }
                if (c.metric.kind() == Kind.COUNT) {
                    v.setValue(BigDecimal.valueOf(c.count));
                    v.setSampleSize(c.count);
                } else {
                    boolean headline = c.dims.equals(Dims.ALL) || SUM_HEADLINE.contains(c.metric);
                    if (c.values.isEmpty() && c.excluded == 0 && !headline) continue;
                    Stats.Summary s = Stats.summarise(c.values);
                    v.setSampleSize(s.n());
                    v.setMedian(s.median());
                    v.setP25(s.p25());
                    v.setP75(s.p75());
                    v.setMean(s.mean());
                    v.setMinValue(s.min());
                    v.setMaxValue(s.max());
                    v.setSumValue(s.sum());
                    v.setValue(COUNT_HEADLINE.contains(c.metric) ? BigDecimal.valueOf(s.n())
                            : SUM_HEADLINE.contains(c.metric) ? s.sum() : s.median());
                    v.setLowSample(s.n() < MetricDefinition.MIN_RELIABLE_SAMPLE && !SUM_HEADLINE.contains(c.metric));
                }
                if (MONEY.contains(c.metric)) v.setCurrency("USD");
                v.setComputedAt(now);
                v.setJobRunId(runId);
                out.add(v);
            }
            return out;
        }
    }

    static final class Cell {
        final MetricDefinition metric;
        final Dims dims;
        int count;
        int excluded;
        int coverageNum;
        int coverageDen;
        final List<BigDecimal> values = new ArrayList<>();
        final List<String> refs = new ArrayList<>();

        Cell(MetricDefinition metric, Dims dims) {
            this.metric = metric;
            this.dims = dims;
        }

        void ref(String ref, boolean trace) {
            if (trace && ref != null && refs.size() < Acc.MAX_REFS) refs.add(ref);
        }
    }
}
