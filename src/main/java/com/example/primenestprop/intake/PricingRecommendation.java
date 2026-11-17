package com.example.primenestprop.intake;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A saved price recommendation with the comparables it was based on (snapshot, never edited). */
@Entity
@Table(name = "pricing_recommendations", indexes = @Index(name = "idx_pricing_property", columnList = "propertyId"))
@Getter
@Setter
@NoArgsConstructor
public class PricingRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private long recommendedCents;

    private Long lowCents;
    private Long medianCents;
    private Long highCents;
    @Column(length = 3, nullable = false) private String currency = "USD";
    private int comparableCount;

    // Portable long text: TEXT on Postgres/H2, LONGTEXT on MariaDB (no OID large objects, no row-size limit).
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String comparablesJson;

    @Column(length = 2000) private String rationale;
    private Long createdByUserId;
    private Instant createdAt = Instant.now();
}
