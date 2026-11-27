package com.example.estatex.analytics.engine;

import com.example.estatex.analytics.metrics.ListingState;
import com.example.estatex.analytics.metrics.StateReader;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes and checks {@code listing_daily_snapshots}. */
@Component
public class SnapshotWriter {
    private static final String INSERT = "insert into listing_daily_snapshots (snapshot_date, property_id, listing_type, status, "
            + "verification_status, country, city, suburb, category, bedrooms, bathrooms, floor_area_m2, price, currency, "
            + "latitude, longitude, organisation_id, as_of, job_run_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;
    private final StateReader reader;

    public SnapshotWriter(JdbcTemplate jdbc, StateReader reader) {
        this.jdbc = jdbc;
        this.reader = reader;
    }

    /**
     * Brings one day's snapshot up to date. If the day has no rows yet, every listing is written;
     * otherwise only the listings that changed. Runs inside the caller's transaction.
     * @return rows written
     */
    public int update(LocalDate day, ZoneId zone, Instant now, Collection<Long> changedPropertyIds, long runId) {
        Instant cutoff = cutoff(day, zone, now);
        Integer existing = jdbc.queryForObject("select count(*) from listing_daily_snapshots where snapshot_date = ?",
                Integer.class, Date.valueOf(day));
        if (existing == null || existing == 0) {
            return write(day, reader.stateAsOf(cutoff).values(), cutoff, runId);
        }
        if (changedPropertyIds.isEmpty()) return 0;
        Map<Long, ListingState> changed = reader.stateAsOf(cutoff, changedPropertyIds);
        delete(day, changed.keySet());
        return write(day, changed.values(), cutoff, runId);
    }

    public record Reconciliation(int expected, int stored, int missing, int extra, int different) {
        public int mismatches() {
            return missing + extra + different;
        }
    }

    /**
     * Rebuilds a finished day from scratch, compares it with what the hourly runs wrote, then
     * replaces it. A mismatch means an hourly window was missed or a change bypassed history.
     */
    public Reconciliation reconcile(LocalDate day, ZoneId zone, Instant now, long runId) {
        Instant cutoff = cutoff(day, zone, now);
        Map<Long, ListingState> truth = reader.stateAsOf(cutoff);
        Map<Long, String> stored = new HashMap<>();
        jdbc.query("select property_id, status, price, listing_type, suburb, verification_status from listing_daily_snapshots "
                + "where snapshot_date = ?", rs -> {
            stored.put(rs.getLong("property_id"), fingerprint(rs.getString("status"), rs.getBigDecimal("price"),
                    rs.getString("listing_type"), rs.getString("suburb"), rs.getString("verification_status")));
        }, Date.valueOf(day));
        int missing = 0;
        int different = 0;
        for (ListingState s : truth.values()) {
            String f = stored.remove(s.propertyId());
            if (f == null) missing++;
            else if (!f.equals(fingerprint(s.status(), s.price(), s.listingType(), s.suburb(), s.verificationStatus()))) different++;
        }
        int extra = stored.size();
        Integer storedCount = jdbc.queryForObject("select count(*) from listing_daily_snapshots where snapshot_date = ?",
                Integer.class, Date.valueOf(day));
        jdbc.update("delete from listing_daily_snapshots where snapshot_date = ?", Date.valueOf(day));
        write(day, truth.values(), cutoff, runId);
        return new Reconciliation(truth.size(), storedCount == null ? 0 : storedCount, missing, extra, different);
    }

    private static String fingerprint(String status, java.math.BigDecimal price, String type, String suburb, String verification) {
        return String.join("|", Objects.toString(status), price == null ? "null" : price.stripTrailingZeros().toPlainString(),
                Objects.toString(type), Objects.toString(suburb), Objects.toString(verification));
    }

    private static Instant cutoff(LocalDate day, ZoneId zone, Instant now) {
        Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
        return end.isAfter(now) ? now : end;
    }

    private void delete(LocalDate day, Collection<Long> ids) {
        List<Long> list = new ArrayList<>(ids);
        for (int i = 0; i < list.size(); i += BATCH) {
            List<Long> chunk = list.subList(i, Math.min(list.size(), i + BATCH));
            List<Object> args = new ArrayList<>();
            args.add(Date.valueOf(day));
            args.addAll(chunk);
            jdbc.update("delete from listing_daily_snapshots where snapshot_date = ? and property_id in ("
                    + String.join(",", java.util.Collections.nCopies(chunk.size(), "?")) + ")", args.toArray());
        }
    }

    private int write(LocalDate day, Collection<ListingState> states, Instant asOf, long runId) {
        List<Object[]> rows = new ArrayList<>();
        for (ListingState s : states) {
            rows.add(new Object[]{Date.valueOf(day), s.propertyId(), s.listingType(), s.status(), s.verificationStatus(),
                    s.country(), s.city(), s.suburb(), s.category(), s.bedrooms(), s.bathrooms(), s.floorAreaM2(),
                    s.price(), s.currency(), s.latitude(), s.longitude(), s.organisationId(), Timestamp.from(asOf), runId});
        }
        for (int i = 0; i < rows.size(); i += BATCH) {
            jdbc.batchUpdate(INSERT, rows.subList(i, Math.min(rows.size(), i + BATCH)));
        }
        return rows.size();
    }
}
