package com.example.estatex.analytics.capture;

import com.example.estatex.common.ApiException;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyCategory;
import com.example.estatex.property.PropertyRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records anonymous views, searches and saves for demand analytics.
 *
 * <p>The figures are only as good as this filter. So: views of a listing by its own owner or
 * agent are not counted; crawlers are ignored; each visitor counts once per listing per hour and
 * once per identical search per 10 minutes; and one IP address can record at most
 * {@value #MAX_EVENTS_PER_IP_PER_10_MIN} events per 10 minutes. The IP is used only for that
 * limit, in memory, and is never stored.
 */
@Service
public class MarketplaceEventService {
    static final int MAX_EVENTS_PER_IP_PER_10_MIN = 120;
    private static final Pattern BOT = Pattern.compile("(?i)bot|crawl|spider|slurp|headless|lighthouse|preview");
    private static final Pattern VISITOR_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final MarketplaceEventRepository events;
    private final PropertyRepository properties;
    private final String salt;
    private final Clock clock;
    private final Cache<String, AtomicInteger> perIp = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(50_000).build();
    private final Cache<String, Boolean> recent = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(65)).maximumSize(200_000).build();

    @org.springframework.beans.factory.annotation.Autowired
    public MarketplaceEventService(MarketplaceEventRepository events, PropertyRepository properties,
                                   @Value("${app.analytics.visitor-salt:estatex-analytics}") String salt) {
        this(events, properties, salt, Clock.systemUTC());
    }

    MarketplaceEventService(MarketplaceEventRepository events, PropertyRepository properties, String salt, Clock clock) {
        this.events = events;
        this.properties = properties;
        this.salt = salt;
        this.clock = clock;
    }

    public record Track(MarketplaceEventType type, Long propertyId, String visitorId, ListingType listingType,
                        String city, String suburb, PropertyCategory category, Integer bedrooms,
                        BigDecimal minPrice, BigDecimal maxPrice, Integer resultCount) {
    }

    /**
     * @return true if counted, false if ignored as a duplicate, crawler or own listing. Not
     *         transactional on purpose: the insert runs in the repository's own transaction, so
     *         losing a duplicate-key race just returns false.
     */
    public boolean track(Track t, Long signedInUserId, String ip, String userAgent) {
        if (t.type() == null || t.type() == MarketplaceEventType.SAVE || t.type() == MarketplaceEventType.UNSAVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "type must be VIEW or SEARCH");
        }
        if (t.visitorId() == null || !VISITOR_ID.matcher(t.visitorId()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "visitorId is missing or malformed");
        }
        if (userAgent != null && BOT.matcher(userAgent).find()) return false;
        if (ip != null && perIp.get(ip, k -> new AtomicInteger()).incrementAndGet() > MAX_EVENTS_PER_IP_PER_10_MIN) {
            return false;
        }
        Instant now = clock.instant();
        MarketplaceEvent e = new MarketplaceEvent();
        e.setEventType(t.type().name());
        e.setVisitorHash(hash(t.visitorId()));
        e.setSignedIn(signedInUserId != null);
        e.setOccurredAt(now);
        if (t.type() == MarketplaceEventType.VIEW) {
            if (t.propertyId() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "propertyId is required for a view");
            Property p = properties.findById(t.propertyId()).orElse(null);
            if (p == null || p.getStatus().isHiddenFromPublic()) return false;
            if (signedInUserId != null && (Objects.equals(p.getLandlord().getId(), signedInUserId)
                    || (p.getAgent() != null && Objects.equals(p.getAgent().getId(), signedInUserId)))) {
                return false;
            }
            describe(e, p);
            e.setDedupeKey(key("V", e.getVisitorHash(), p.getId().toString(), now.truncatedTo(ChronoUnit.HOURS).toString()));
        } else {
            e.setListingType(t.listingType() == null ? null : t.listingType().name());
            e.setCity(clean(t.city()));
            e.setSuburb(clean(t.suburb()));
            e.setCategory(t.category() == null ? null : t.category().name());
            e.setBedrooms(t.bedrooms());
            e.setMinPrice(t.minPrice());
            e.setMaxPrice(t.maxPrice());
            e.setResultCount(t.resultCount() == null ? null : Math.max(0, t.resultCount()));
            long bucket = now.getEpochSecond() / 600;
            e.setDedupeKey(key("S", e.getVisitorHash(), e.getListingType(), e.getCity(), e.getSuburb(), e.getCategory(),
                    String.valueOf(e.getBedrooms()), String.valueOf(e.getMinPrice()), String.valueOf(e.getMaxPrice()),
                    String.valueOf(bucket)));
        }
        return save(e);
    }

    /** A signed-in user saved or unsaved a listing. Called by the saved-properties service. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSave(Property p, Long userId, boolean saved) {
        Instant now = clock.instant();
        MarketplaceEvent e = new MarketplaceEvent();
        e.setEventType((saved ? MarketplaceEventType.SAVE : MarketplaceEventType.UNSAVE).name());
        e.setVisitorHash(hash("user:" + userId));
        e.setSignedIn(true);
        e.setOccurredAt(now);
        describe(e, p);
        e.setDedupeKey(key(saved ? "SV" : "US", e.getVisitorHash(), p.getId().toString(), now.toString()));
        events.save(e);
    }

    private boolean save(MarketplaceEvent e) {
        if (recent.asMap().putIfAbsent(e.getDedupeKey(), Boolean.TRUE) != null) return false;
        if (events.existsByDedupeKey(e.getDedupeKey())) return false;
        try {
            events.saveAndFlush(e);
            return true;
        } catch (DataIntegrityViolationException raced) {
            return false;
        }
    }

    private static void describe(MarketplaceEvent e, Property p) {
        e.setPropertyId(p.getId());
        e.setListingType(p.getListingType() == null ? null : p.getListingType().name());
        e.setCity(p.getCity());
        e.setSuburb(p.getSuburb());
        e.setCategory(p.getCategory() == null ? null : p.getCategory().name());
        e.setBedrooms(p.getBedrooms());
        e.setMinPrice(p.getPrice());
        e.setMaxPrice(p.getPrice());
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    String hash(String visitorId) {
        return sha256(salt + ":" + visitorId).substring(0, 32);
    }

    private static String key(String... parts) {
        return sha256(String.join("|", parts).toLowerCase(Locale.ROOT));
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
