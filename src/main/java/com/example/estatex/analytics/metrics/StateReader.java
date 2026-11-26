package com.example.estatex.analytics.metrics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rebuilds listing state at any moment from {@code property_events}, and reads the raw events
 * of a period. Read-only: analytics never writes to source tables.
 *
 * <p>"Latest" means the highest id. Ids are assigned in commit order and every live row's
 * occurred_at is its insert time, so the highest id before a cutoff is the latest state. (The
 * V6 BASELINE rows came first, so they never win over a real change.)
 */
@Component
public class StateReader {
    static final String COLUMNS = "id, property_id, event_type, listing_type, status, verification_status, country, city, "
            + "suburb, category, bedrooms, bathrooms, floor_area_m2, price, currency, latitude, longitude, organisation_id, "
            + "previous_price, previous_status, occurred_at";
    private static final int BATCH = 1000;

    private final JdbcTemplate jdbc;

    public StateReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every listing's state just before {@code cutoff}, keyed by property id. */
    public Map<Long, ListingState> stateAsOf(Instant cutoff) {
        List<Long> ids = jdbc.queryForList(
                "select max(id) from property_events where occurred_at < ? group by property_id",
                Long.class, Timestamp.from(cutoff));
        return statesForEventIds(ids);
    }

    /** The state just before {@code cutoff} for only these listings. */
    public Map<Long, ListingState> stateAsOf(Instant cutoff, java.util.Collection<Long> propertyIds) {
        List<Long> ids = new ArrayList<>();
        List<Long> props = new ArrayList<>(propertyIds);
        for (int i = 0; i < props.size(); i += BATCH) {
            List<Long> chunk = props.subList(i, Math.min(props.size(), i + BATCH));
            String in = placeholders(chunk.size());
            List<Object> args = new ArrayList<>();
            args.add(Timestamp.from(cutoff));
            args.addAll(chunk);
            ids.addAll(jdbc.queryForList("select max(id) from property_events where occurred_at < ? and property_id in ("
                    + in + ") group by property_id", Long.class, args.toArray()));
        }
        return statesForEventIds(ids);
    }

    public record Event(long id, long propertyId, String type, ListingState state, java.math.BigDecimal previousPrice,
                        String previousStatus, Instant occurredAt) {
    }

    /** Events in [from, to), oldest first. */
    public List<Event> events(Instant from, Instant to) {
        return jdbc.query("select " + COLUMNS + " from property_events where occurred_at >= ? and occurred_at < ? order by id",
                (rs, i) -> event(rs), Timestamp.from(from), Timestamp.from(to));
    }

    /** For each listing, the first event that put it on the market: id → (event type, when). */
    public Map<Long, Event> firstAvailable() {
        Map<Long, Event> out = new HashMap<>();
        List<Long> ids = jdbc.queryForList(
                "select min(id) from property_events where status = 'AVAILABLE' group by property_id", Long.class);
        for (int i = 0; i < ids.size(); i += BATCH) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + BATCH));
            jdbc.query("select " + COLUMNS + " from property_events where id in (" + placeholders(chunk.size()) + ")",
                    rs -> {
                        Event e = event(rs);
                        out.put(e.propertyId(), e);
                    }, chunk.toArray());
        }
        return out;
    }

    private Map<Long, ListingState> statesForEventIds(List<Long> ids) {
        Map<Long, ListingState> out = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i += BATCH) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + BATCH));
            jdbc.query("select " + COLUMNS + " from property_events where id in (" + placeholders(chunk.size()) + ")",
                    rs -> {
                        ListingState s = state(rs);
                        out.put(s.propertyId(), s);
                    }, chunk.toArray());
        }
        return out;
    }

    static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static Event event(ResultSet rs) throws SQLException {
        return new Event(rs.getLong("id"), rs.getLong("property_id"), rs.getString("event_type"), state(rs),
                rs.getBigDecimal("previous_price"), rs.getString("previous_status"),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private static ListingState state(ResultSet rs) throws SQLException {
        return new ListingState(rs.getLong("property_id"), rs.getString("listing_type"), rs.getString("status"),
                rs.getString("verification_status"), rs.getString("country"), rs.getString("city"), rs.getString("suburb"),
                rs.getString("category"), (Integer) rs.getObject("bedrooms", Integer.class),
                rs.getObject("bathrooms", Integer.class), rs.getObject("floor_area_m2", Integer.class),
                rs.getBigDecimal("price"), rs.getString("currency"), rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"), rs.getObject("organisation_id", Long.class));
    }
}
