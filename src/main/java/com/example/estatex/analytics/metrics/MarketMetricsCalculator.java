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
 */
@Component
public class MarketMetricsCalculator {
    private static final Set<String> MONEY = Set.of("ASKING_PRICE", "ASKING_PRICE_PER_M2", "CONTRACTED_RENT", "VERIFIED_SALES");
    /** Distributions whose headline value is the count, not the median. */
    private static final Set<String> COUNT_HEADLINE = Set.of("PRICE_REDUCTIONS", "VERIFIED_SALES");
    private static final Map<String, MetricDefinition> STATUS_METRICS = Map.of(
            "WITHDRAWN", MetricDefinition.WITHDRAWN_LISTINGS,
            "EXPIRED", MetricDefinition.EXPIRED_LISTINGS,
            "UNDER_OFFER", MetricDefinition.UNDER_OFFER,
            "REPORTED_SOLD", MetricDefinition.REPORTED_SOLD);

    private final JdbcTemplate jdbc;
    private final StateReader reader;

    public MarketMetricsCalculator(JdbcTemplate jdbc, StateReader reader) {
        this.jdbc = jdbc;
        this.reader = reader;
    }

    /** Facts shared by every period in one run, loaded once. */
    public record RunContext(Map<Long, Event> firstAvailable) {
    }

    public RunContext context() {
        return new RunContext(reader.firstAvailable());
    }

    public List<MetricValue> compute(Period p, Instant now, ZoneId zone, RunContext ctx, Long runId) {
        Instant asOf = p.asOf(now);
        boolean hourly = p.grain() == Grain.HOUR;
        Acc acc = new Acc(hourly);

        Map<Long, ListingState> end = reader.stateAsOf(asOf);
        List<Event> events = reader.events(p.start(), asOf);

        for (ListingState s : end.values()) {
            if (s.available()) acc.count(MetricDefinition.ACTIVE_LISTINGS, Dims.of(s), true);
        }
        for (Event e : ctx.firstAvailable().values()) {
            if (!"BASELINE".equals(e.type()) && !e.occurredAt().isBefore(p.start()) && e.occurredAt().isBefore(asOf)) {
                acc.count(MetricDefinition.NEW_LISTINGS, Dims.of(e.state()), true);
            }
        }
        Set<String> seenStatus = new HashSet<>();
        for (Event e : events) {
            if ("STATUS_CHANGED".equals(e.type())) {
                MetricDefinition m = STATUS_METRICS.get(e.state().status());
                if (m != null && seenStatus.add(m.name() + ":" + e.propertyId())) acc.count(m, Dims.of(e.state()), true);
            } else if ("PRICE_CHANGED".equals(e.type())) {
                BigDecimal before = e.previousPrice();
                BigDecimal after = e.state().price();
                if (before != null && after != null && before.signum() > 0 && after.signum() > 0) {
                    if (after.compareTo(before) < 0) {
                        BigDecimal pct = before.subtract(after).multiply(BigDecimal.valueOf(100))
                                .divide(before, 2, RoundingMode.HALF_UP);
                        acc.value(MetricDefinition.PRICE_REDUCTIONS, Dims.of(e.state()), pct, true);
                    } else if (after.compareTo(before) > 0) {
                        acc.count(MetricDefinition.PRICE_INCREASES, Dims.of(e.state()), true);
                    }
                }
            }
        }

        if (!hourly) askingPrices(p, end, events, acc);
        leases(p, asOf, end, acc);
        sales(p, asOf, zone, hourly, acc);
        activity(p, asOf, end, acc);
        return acc.rows(p, now, runId);
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
            if (s.price() == null || s.price().signum() <= 0 || !"USD".equalsIgnoreCase(s.currency())) {
                acc.exclude(MetricDefinition.ASKING_PRICE, d);
                continue;
            }
            acc.value(MetricDefinition.ASKING_PRICE, d, s.price(), false);
            boolean hasArea = s.floorAreaM2() != null && s.floorAreaM2() > 0;
            acc.coverage(MetricDefinition.ASKING_PRICE_PER_M2, d, hasArea);
            if (hasArea) {
                acc.value(MetricDefinition.ASKING_PRICE_PER_M2, d,
                        s.price().divide(BigDecimal.valueOf(s.floorAreaM2()), 2, RoundingMode.HALF_UP), false);
            }
        }
    }

    private void leases(Period p, Instant asOf, Map<Long, ListingState> end, Acc acc) {
        jdbc.query("select property_id, monthly_rent, currency from leases where signed_at >= ? and signed_at < ?", rs -> {
            long propertyId = rs.getLong("property_id");
            ListingState s = end.get(propertyId);
            Dims d = s == null ? new Dims("RENT", null, null, null, null, null)
                    : new Dims("RENT", Dims.of(s).country(), Dims.of(s).city(), Dims.of(s).suburb(), s.category(),
                    Dims.bedroomBand(s.bedrooms()));
            acc.count(MetricDefinition.RENTAL_AGREEMENTS_SIGNED, d, true);
            BigDecimal rent = rs.getBigDecimal("monthly_rent");
            String currency = rs.getString("currency");
            if (rent == null || rent.signum() <= 0 || (currency != null && !"USD".equalsIgnoreCase(currency))) {
                acc.exclude(MetricDefinition.CONTRACTED_RENT, d);
            } else {
                acc.value(MetricDefinition.CONTRACTED_RENT, d, rent, false);
            }
        }, Timestamp.from(p.start()), Timestamp.from(asOf));
    }

    private void sales(Period p, Instant asOf, ZoneId zone, boolean hourly, Acc acc) {
        jdbc.query("select country, city, suburb, category, bedrooms from sale_transactions "
                + "where reported_at >= ? and reported_at < ? and verification <> 'REJECTED'", rs -> {
            acc.count(MetricDefinition.SALES_REPORTED, Dims.of("SALE", rs.getString("country"), rs.getString("city"),
                    rs.getString("suburb"), rs.getString("category"), rs.getObject("bedrooms", Integer.class)), true);
        }, Timestamp.from(p.start()), Timestamp.from(asOf));
        if (hourly) return;
        LocalDate first = p.start().atZone(zone).toLocalDate();
        LocalDate afterLast = asOf.atZone(zone).toLocalDate().plusDays(p.isComplete(asOf) && asOf.equals(p.end()) ? 0 : 1);
        jdbc.query("select price, currency, country, city, suburb, category, bedrooms from sale_transactions "
                + "where verification = 'VERIFIED' and completion_date >= ? and completion_date < ?", rs -> {
            Dims d = Dims.of("SALE", rs.getString("country"), rs.getString("city"), rs.getString("suburb"),
                    rs.getString("category"), rs.getObject("bedrooms", Integer.class));
            if ("USD".equalsIgnoreCase(rs.getString("currency"))) {
                acc.value(MetricDefinition.VERIFIED_SALES, d, rs.getBigDecimal("price"), true);
            } else {
                acc.exclude(MetricDefinition.VERIFIED_SALES, d);
            }
        }, Date.valueOf(first), Date.valueOf(afterLast));
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
                case "VIEW" -> acc.count(MetricDefinition.LISTING_VIEWS, d, true);
                case "SAVE" -> acc.count(MetricDefinition.SAVES, d, true);
                case "SEARCH" -> {
                    acc.count(MetricDefinition.SEARCHES, d, true);
                    Integer results = rs.getObject("result_count", Integer.class);
                    if (results != null && results == 0) acc.count(MetricDefinition.SEARCHES_NO_RESULTS, d, true);
                }
                default -> { }
            }
        }, from, to);
        jdbc.query("select property_id, intent from enquiries where created_at >= ? and created_at < ?", rs -> {
            Long id = rs.getObject("property_id", Long.class);
            String intent = rs.getString("intent");
            String type = "BUY".equals(intent) ? "SALE" : "RENT".equals(intent) ? "RENT" : null;
            acc.count(MetricDefinition.ENQUIRIES, dimsFor(id, end, type), true);
        }, from, to);
        perProperty("select property_id from property_inquiries where created_at >= ? and created_at < ?",
                MetricDefinition.ENQUIRIES, from, to, end, acc);
        perProperty("select property_id from viewing_requests where created_at >= ? and created_at < ?",
                MetricDefinition.VIEWING_REQUESTS, from, to, end, acc);
        perProperty("select property_id from rental_applications where submitted_at >= ? and submitted_at < ?",
                MetricDefinition.RENTAL_APPLICATIONS, from, to, end, acc);
        perProperty("select property_id from offers where created_at >= ? and created_at < ?",
                MetricDefinition.OFFERS, from, to, end, acc);
    }

    private void perProperty(String sql, MetricDefinition m, Timestamp from, Timestamp to,
                             Map<Long, ListingState> end, Acc acc) {
        jdbc.query(sql, rs -> {
            acc.count(m, dimsFor(rs.getObject("property_id", Long.class), end, null), true);
        }, from, to);
    }

    private static Dims dimsFor(Long propertyId, Map<Long, ListingState> end, String fallbackType) {
        ListingState s = propertyId == null ? null : end.get(propertyId);
        if (s != null) return Dims.of(s);
        return new Dims(fallbackType, null, null, null, null, null);
    }

    /** Accumulates observations per (metric, slice) and turns them into rows. */
    static final class Acc {
        private final boolean hourly;
        private final Map<String, Cell> cells = new LinkedHashMap<>();

        Acc(boolean hourly) {
            this.hourly = hourly;
            // Headline zero rows, so "0 this period" is published instead of a gap.
            for (MetricDefinition m : MetricDefinition.values()) {
                if (m.kind() != Kind.COUNT) continue;
                cell(m, Dims.ALL);
                for (String type : List.of("RENT", "SALE")) cell(m, new Dims(type, null, null, null, null, null));
            }
        }

        void count(MetricDefinition m, Dims d, boolean includeAllTypes) {
            for (Dims r : d.rollups(includeAllTypes, hourly)) cell(m, r).count++;
        }

        void value(MetricDefinition m, Dims d, BigDecimal v, boolean includeAllTypes) {
            for (Dims r : d.rollups(includeAllTypes, hourly)) cell(m, r).values.add(v);
        }

        void exclude(MetricDefinition m, Dims d) {
            for (Dims r : d.rollups(false, hourly)) cell(m, r).excluded++;
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
                    if (c.values.isEmpty() && c.excluded == 0) continue;
                    Stats.Summary s = Stats.summarise(c.values);
                    v.setSampleSize(s.n());
                    v.setMedian(s.median());
                    v.setP25(s.p25());
                    v.setP75(s.p75());
                    v.setMean(s.mean());
                    v.setMinValue(s.min());
                    v.setMaxValue(s.max());
                    v.setSumValue(s.sum());
                    v.setValue(COUNT_HEADLINE.contains(c.metric.name()) ? BigDecimal.valueOf(s.n()) : s.median());
                    v.setLowSample(s.n() < MetricDefinition.MIN_RELIABLE_SAMPLE);
                    if (MONEY.contains(c.metric.name())) v.setCurrency("USD");
                }
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

        Cell(MetricDefinition metric, Dims dims) {
            this.metric = metric;
            this.dims = dims;
        }
    }
}
