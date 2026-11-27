package com.example.estatex.analytics.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Checks live listings before figures are calculated. Problems are reported, never silently
 * "fixed": a listing with no price is left out of price figures and counted, and an unusually
 * high price is flagged for a human but still counted (it may be a real luxury home).
 */
@Component
public class DataQualityChecker {
    static final BigDecimal SAME_LISTER_PRICE_TOLERANCE = new BigDecimal("0.03");
    static final BigDecimal OTHER_LISTER_PRICE_TOLERANCE = new BigDecimal("0.01");
    private static final int MAX_BUCKET = 200;

    private final JdbcTemplate jdbc;

    public DataQualityChecker(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Warning(String code, String message, long count, List<Long> sampleIds) {
    }

    public record Result(List<Warning> warnings, long rejected, int liveListings, int newDuplicatePairs, int openDuplicatePairs) {
    }

    record Live(long id, long landlordId, Long agentId, String type, String suburb, String city, int bedrooms, int bathrooms,
                BigDecimal price, String currency, String title, String category, boolean hasCoordinates) {
    }

    public Result check(Instant now) {
        List<Live> live = jdbc.query("select id, landlord_id, agent_id, listing_type, suburb, city, bedrooms, bathrooms, price, "
                        + "currency, title, category, latitude, longitude from properties where status = 'AVAILABLE'",
                (rs, i) -> new Live(rs.getLong("id"), rs.getLong("landlord_id"), rs.getObject("agent_id", Long.class),
                        rs.getString("listing_type"), rs.getString("suburb"), rs.getString("city"), rs.getInt("bedrooms"),
                        rs.getInt("bathrooms"), rs.getBigDecimal("price"), rs.getString("currency"), rs.getString("title"),
                        rs.getString("category"), rs.getObject("latitude") != null && rs.getObject("longitude") != null));

        Map<String, List<Long>> problems = new LinkedHashMap<>();
        long rejected = 0;
        for (Live l : live) {
            boolean excluded = false;
            if (l.price() == null || l.price().signum() <= 0) {
                add(problems, "NO_PRICE", l.id());
                excluded = true;
            } else if (l.currency() != null && !"USD".equalsIgnoreCase(l.currency())) {
                add(problems, "NON_USD", l.id());
                excluded = true;
            } else if (implausible(l)) {
                add(problems, "UNUSUAL_PRICE", l.id());
            }
            if (excluded) rejected++;
            if (l.suburb() == null || l.suburb().isBlank()) add(problems, "NO_SUBURB", l.id());
            if (!l.hasCoordinates()) add(problems, "NO_COORDINATES", l.id());
            if (l.category() == null) add(problems, "NO_CATEGORY", l.id());
        }
        List<Warning> warnings = new ArrayList<>();
        problems.forEach((code, ids) -> warnings.add(new Warning(code, message(code, ids.size()), ids.size(),
                ids.subList(0, Math.min(10, ids.size())))));

        int created = duplicates(live, now);
        Integer open = jdbc.queryForObject("select count(*) from listing_duplicate_candidates where status = 'OPEN'", Integer.class);
        if (open != null && open > 0) {
            warnings.add(new Warning("POSSIBLE_DUPLICATES", open + " pair(s) of live listings look like the same property.",
                    open, List.of()));
        }
        return new Result(warnings, rejected, live.size(), created, open == null ? 0 : open);
    }

    private static boolean implausible(Live l) {
        // Flag only; never excluded. Bounds are deliberately wide.
        if ("RENT".equals(l.type())) return l.price().compareTo(BigDecimal.valueOf(50_000)) > 0 || l.price().compareTo(BigDecimal.valueOf(20)) < 0;
        if ("SALE".equals(l.type())) return l.price().compareTo(BigDecimal.valueOf(1_000)) < 0 || l.price().compareTo(BigDecimal.valueOf(20_000_000)) > 0;
        return false;
    }

    private static String message(String code, int n) {
        return switch (code) {
            case "NO_PRICE" -> n + " live listing(s) have no price; left out of price figures.";
            case "NON_USD" -> n + " live listing(s) are priced in another currency; left out of USD price figures.";
            case "UNUSUAL_PRICE" -> n + " live listing(s) have an unusual price; still counted, please check.";
            case "NO_SUBURB" -> n + " live listing(s) have no suburb; left out of suburb figures.";
            case "NO_COORDINATES" -> n + " live listing(s) have no map location; left out of map layers.";
            case "NO_CATEGORY" -> n + " live listing(s) have no property type; shown as Unspecified.";
            default -> n + " record(s): " + code;
        };
    }

    private static void add(Map<String, List<Long>> problems, String code, long id) {
        problems.computeIfAbsent(code, k -> new ArrayList<>()).add(id);
    }

    /** @return number of newly found pairs */
    int duplicates(List<Live> live, Instant now) {
        Map<String, List<Live>> buckets = new HashMap<>();
        for (Live l : live) {
            if (l.price() == null || l.price().signum() <= 0 || l.suburb() == null) continue;
            String key = l.type() + "|" + norm(l.suburb()) + "|" + l.bedrooms();
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }
        int created = 0;
        for (List<Live> bucket : buckets.values()) {
            List<Live> b = bucket.size() > MAX_BUCKET ? bucket.subList(0, MAX_BUCKET) : bucket;
            for (int i = 0; i < b.size(); i++) {
                for (int j = i + 1; j < b.size(); j++) {
                    String reason = reason(b.get(i), b.get(j));
                    if (reason != null && upsert(b.get(i).id(), b.get(j).id(), reason, now)) created++;
                }
            }
        }
        return created;
    }

    static String reason(Live a, Live b) {
        boolean sameLister = a.landlordId() == b.landlordId()
                || (a.agentId() != null && Objects.equals(a.agentId(), b.agentId()));
        if (sameLister && within(a.price(), b.price(), SAME_LISTER_PRICE_TOLERANCE)) {
            return "Same lister, suburb and bedrooms; prices within 3%";
        }
        if (!sameLister && a.bathrooms() == b.bathrooms() && norm(a.title()).equals(norm(b.title()))
                && within(a.price(), b.price(), OTHER_LISTER_PRICE_TOLERANCE)) {
            return "Same title, suburb and rooms from different listers; prices within 1%";
        }
        return null;
    }

    static boolean within(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        BigDecimal max = a.max(b);
        return a.subtract(b).abs().divide(max, 6, RoundingMode.HALF_UP).compareTo(tolerance) <= 0;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private boolean upsert(long x, long y, String reason, Instant now) {
        long a = Math.min(x, y);
        long b = Math.max(x, y);
        int updated = jdbc.update("update listing_duplicate_candidates set last_seen_at = ? where property_a = ? and property_b = ?",
                Timestamp.from(now), a, b);
        if (updated > 0) return false;
        try {
            jdbc.update("insert into listing_duplicate_candidates (property_a, property_b, reason, status, first_detected_at, "
                    + "last_seen_at) values (?, ?, ?, 'OPEN', ?, ?)", a, b, reason, Timestamp.from(now), Timestamp.from(now));
            return true;
        } catch (DataIntegrityViolationException raced) {
            return false;
        }
    }
}
