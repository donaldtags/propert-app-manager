package com.example.estatex.analytics.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One anonymous marketplace interaction: a listing view, a search, or a save. These rows only
 * measure interest. They are never reported as confirmed demand from buyers or tenants.
 *
 * <p>No personal data is stored: the visitor is a salted hash of a random browser id, and no IP
 * address, name or account id is kept. {@code dedupeKey} is unique, so a visitor refreshing a
 * listing counts once per hour and repeating a search counts once per 10 minutes.
 */
@Entity
@Table(name = "marketplace_events", indexes = {
        @Index(name = "uq_marketplace_event_dedupe", columnList = "dedupe_key", unique = true),
        @Index(name = "idx_marketplace_event_time", columnList = "occurred_at, event_type"),
        @Index(name = "idx_marketplace_event_property", columnList = "property_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@link MarketplaceEventType} name. */
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "visitor_hash", nullable = false, length = 32)
    private String visitorHash;
    @Column(name = "signed_in", nullable = false)
    private boolean signedIn;

    // For views and saves: the listing's state at that moment. For searches: the filters used.
    @Column(name = "listing_type", length = 20)
    private String listingType;
    private String city;
    private String suburb;
    @Column(length = 20)
    private String category;
    private Integer bedrooms;
    @Column(name = "min_price", precision = 19, scale = 2)
    private BigDecimal minPrice;
    @Column(name = "max_price", precision = 19, scale = 2)
    private BigDecimal maxPrice;
    /** Searches only: how many listings matched. 0 means the visitor found nothing. */
    @Column(name = "result_count")
    private Integer resultCount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "dedupe_key", nullable = false, length = 64)
    private String dedupeKey;
}
