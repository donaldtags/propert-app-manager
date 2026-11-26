package com.example.estatex.analytics.api;

import com.example.estatex.analytics.metrics.Dims;
import com.example.estatex.analytics.metrics.Grain;
import com.example.estatex.analytics.metrics.MarketMetricsCalculator;
import com.example.estatex.analytics.metrics.MetricDefinition;
import com.example.estatex.analytics.metrics.Period;
import com.example.estatex.common.BusinessTime;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The records behind a figure. Recalculates the period with tracing on, so the list reflects
 * the data as it is now; the screen says when the published figure was calculated, in case the
 * two differ by a few late changes.
 */
@Service
public class DrillDownService {
    static final int LIMIT = 500;

    private final MarketMetricsCalculator calculator;
    private final JdbcTemplate jdbc;
    private final BusinessTime time;

    DrillDownService(MarketMetricsCalculator calculator, JdbcTemplate jdbc, BusinessTime time) {
        this.calculator = calculator;
        this.jdbc = jdbc;
        this.time = time;
    }

    public record RecordView(String kind, long id, String title, String detail, BigDecimal amount, String currency,
                             Instant at, String status, String href) {
    }

    public record Records(String metric, Instant periodStart, Instant periodEnd, Instant asOf, int total, boolean truncated,
                          String note, List<RecordView> records) {
    }

    public Records records(MetricDefinition m, Grain grain, Instant periodStart, String listingType, String city, String suburb) {
        Instant now = time.clock().instant();
        Period p = Period.containing(grain, periodStart, time.zone());
        if ("marketplace_events".equals(m.captureSource())) {
            return new Records(m.name(), p.start(), p.end(), p.asOf(now), 0, false,
                    "Views, searches and saves are anonymous, so there are no individual records to show.", List.of());
        }
        if (m == MetricDefinition.TOTAL_USERS) {
            return new Records(m.name(), p.start(), p.end(), p.asOf(now), 0, false,
                    "This is a running total of every account; open Users in the admin portal to browse them.", List.of());
        }
        MarketMetricsCalculator.Result r = calculator.computeTraced(p, now, time.zone(), calculator.context(true), null);
        Set<String> refs = new LinkedHashSet<>();
        r.refs().forEach((key, list) -> {
            if (matches(key, m, listingType, city, suburb)) refs.addAll(list);
        });
        List<String> all = new ArrayList<>(refs);
        boolean truncated = all.size() > LIMIT;
        List<RecordView> views = resolve(truncated ? all.subList(0, LIMIT) : all);
        return new Records(m.name(), p.start(), p.end(), p.asOf(now), all.size(), truncated, null, views);
    }

    /** The same slice selection as the overview: exact key without a location, or every type's city/suburb row with one. */
    static boolean matches(String cellKey, MetricDefinition m, String listingType, String city, String suburb) {
        int hash = cellKey.indexOf('#');
        if (!cellKey.substring(0, hash).equals(m.name())) return false;
        String[] d = cellKey.substring(hash + 1).split("\\|", -1);
        if (city == null) {
            String want = (listingType == null ? Dims.ALL.key()
                    : listingType.toLowerCase(Locale.ROOT) + "|*|*|*|*|*");
            return cellKey.substring(hash + 1).equals(want);
        }
        boolean type = listingType == null ? !d[0].equals("*") : d[0].equals(listingType.toLowerCase(Locale.ROOT));
        return type && d[2].equals(city.trim().toLowerCase(Locale.ROOT))
                && d[3].equals(suburb == null ? "*" : suburb.trim().toLowerCase(Locale.ROOT))
                && d[4].equals("*") && d[5].equals("*");
    }

    private List<RecordView> resolve(List<String> refs) {
        Map<String, List<Long>> byKind = new LinkedHashMap<>();
        for (String ref : refs) {
            int colon = ref.indexOf(':');
            byKind.computeIfAbsent(ref.substring(0, colon), k -> new ArrayList<>()).add(Long.parseLong(ref.substring(colon + 1)));
        }
        Map<String, RecordView> found = new LinkedHashMap<>();
        byKind.forEach((kind, ids) -> {
            String sql = switch (kind) {
                case "property" -> "select id, title, concat(concat(suburb, ', '), city) detail, price amount, currency, created_at at, status from properties where id in ";
                case "lease" -> "select l.id, p.title, concat('Lease, ', l.status) detail, l.monthly_rent amount, l.currency, l.signed_at at, l.status from leases l left join properties p on p.id = l.property_id where l.id in ";
                case "sale" -> "select s.id, p.title, concat('Source: ', s.source) detail, s.price amount, s.currency, s.reported_at at, s.verification status from sale_transactions s left join properties p on p.id = s.property_id where s.id in ";
                case "enquiry" -> "select e.id, p.title, concat('Enquiry via ', e.channel) detail, cast(null as decimal(19,2)) amount, cast(null as char(3)) currency, e.created_at at, e.status from enquiries e left join properties p on p.id = e.property_id where e.id in ";
                case "inquiry" -> "select i.id, p.title, 'Website enquiry' detail, cast(null as decimal(19,2)) amount, cast(null as char(3)) currency, i.created_at at, cast(null as char(20)) status from property_inquiries i left join properties p on p.id = i.property_id where i.id in ";
                case "viewing" -> "select v.id, p.title, 'Viewing request' detail, cast(null as decimal(19,2)) amount, cast(null as char(3)) currency, v.created_at at, v.status from viewing_requests v left join properties p on p.id = v.property_id where v.id in ";
                case "application" -> "select a.id, p.title, 'Rental application' detail, cast(null as decimal(19,2)) amount, cast(null as char(3)) currency, a.submitted_at at, a.status from rental_applications a left join properties p on p.id = a.property_id where a.id in ";
                case "offer" -> "select o.id, p.title, 'Offer' detail, o.amount_cents / 100.0 amount, o.currency, o.created_at at, o.status from offers o left join properties p on p.id = o.property_id where o.id in ";
                case "user" -> "select id, full_name title, cast(null as char(20)) detail, cast(null as decimal(19,2)) amount, cast(null as char(3)) currency, created_at at, cast(null as char(20)) status from users where id in ";
                case "payment" -> "select id, purpose_type title, provider detail, amount, currency, coalesce(paid_at, created_at) at, status from payments where id in ";
                default -> null;
            };
            if (sql == null) return;
            for (int i = 0; i < ids.size(); i += 500) {
                List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + 500));
                jdbc.query(sql + "(" + String.join(",", Collections.nCopies(chunk.size(), "?")) + ")",
                        rs -> { found.put(kind + ":" + rs.getLong("id"), view(kind, rs)); }, chunk.toArray());
            }
        });
        return refs.stream().map(found::get).filter(java.util.Objects::nonNull).toList();
    }

    private static RecordView view(String kind, ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        java.sql.Timestamp at = rs.getTimestamp("at");
        String title = rs.getString("title");
        return new RecordView(kind, id, title == null ? "(deleted listing)" : title, rs.getString("detail"),
                rs.getBigDecimal("amount"), rs.getString("currency"), at == null ? null : at.toInstant(),
                rs.getString("status"), "property".equals(kind) ? "/properties/" + id : null);
    }
}
