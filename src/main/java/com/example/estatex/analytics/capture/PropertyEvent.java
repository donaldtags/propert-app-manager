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
 * Append-only history of a listing. Each row records what changed and the listing's state
 * straight after the change, so the state of any listing at any moment is the latest row at or
 * before that moment. Rows are never updated or deleted.
 *
 * <p>Written by {@link PropertyHistoryRecorder} inside the same transaction as the change, so a
 * change and its history row are committed together or not at all. The {@code BASELINE} rows
 * come from migration V6: they hold each listing's state on the day history began, because
 * nothing earlier was recorded.
 */
@Entity
@Table(name = "property_events", indexes = {
        @Index(name = "idx_property_events_property", columnList = "property_id, occurred_at"),
        @Index(name = "idx_property_events_time", columnList = "occurred_at"),
        @Index(name = "idx_property_events_type", columnList = "event_type, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class PropertyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    /** {@link PropertyEventType} name, stored as text so new types need no constraint change. */
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    // State straight after the change.
    @Column(name = "listing_type", length = 20)
    private String listingType;
    @Column(length = 20)
    private String status;
    @Column(name = "verification_status", length = 20)
    private String verificationStatus;
    private String country;
    private String city;
    private String suburb;
    @Column(length = 20)
    private String category;
    private Integer bedrooms;
    private Integer bathrooms;
    @Column(name = "floor_area_m2")
    private Integer floorAreaM2;
    @Column(precision = 19, scale = 2)
    private BigDecimal price;
    @Column(length = 3)
    private String currency;
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;
    @Column(name = "organisation_id")
    private Long organisationId;

    // What it was before (only for the fields this event is about).
    @Column(name = "previous_price", precision = 19, scale = 2)
    private BigDecimal previousPrice;
    @Column(name = "previous_status", length = 20)
    private String previousStatus;
    /** Short description of other fields that changed, e.g. "bedrooms 3→4; suburb Avondale→Belgravia". */
    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    /** When the change happened in the application. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    /** When this row was written. Differs from occurredAt only for imported or baseline rows. */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    /** APP (live capture), BASELINE (migration V6) or AUDIT_IMPORT. */
    @Column(nullable = false, length = 20)
    private String source;
}
