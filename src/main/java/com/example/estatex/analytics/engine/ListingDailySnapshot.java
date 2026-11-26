package com.example.estatex.analytics.engine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Every listing's state at the end of each day (for today: as of the last run). Lets filtered
 * figures (any price range, bedroom count, suburb) be calculated exactly, including medians,
 * without replaying the whole event history.
 */
@Entity
@Table(name = "listing_daily_snapshots", indexes = {
        @Index(name = "uq_listing_snapshot", columnList = "snapshot_date, property_id", unique = true),
        @Index(name = "idx_listing_snapshot_market", columnList = "snapshot_date, listing_type, status, city, suburb")
})
@Getter
@Setter
@NoArgsConstructor
public class ListingDailySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;
    @Column(name = "property_id", nullable = false)
    private Long propertyId;
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
    @Column(name = "as_of", nullable = false)
    private Instant asOf;
    @Column(name = "job_run_id")
    private Long jobRunId;
}
