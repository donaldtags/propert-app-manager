package com.example.primenestprop.intake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The intake inspection of a listing: condition per item, sizes, notes. One per listing, updated in place. */
@Entity
@Table(name = "listing_inspections", indexes = @Index(name = "uq_listing_inspection_property", columnList = "propertyId", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class ListingInspection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    /** JSON array of {key, label, condition, note}. */
    // Portable long text: TEXT on Postgres/H2, LONGTEXT on MariaDB (no OID large objects, no row-size limit).
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String itemsJson;

    private Integer standSizeM2;
    private Integer floorAreaM2;
    @Column(length = 4000) private String notes;

    private LocalDate inspectedOn;
    private Long inspectedByUserId;
    private Instant updatedAt = Instant.now();
}
