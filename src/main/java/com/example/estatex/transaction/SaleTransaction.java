package com.example.estatex.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A sale of a listed property. It starts as {@link SaleVerification#REPORTED} when the owner or
 * agent says the property sold, and only becomes {@link SaleVerification#VERIFIED} once EstateX
 * staff (not the person who reported it) check conveyancer or Deeds Registry evidence.
 *
 * <p>The listing's location and characteristics are copied in when the sale is reported, so a
 * later edit to the listing can't move a past sale to another suburb in the figures.
 */
@Entity
@Table(name = "sale_transactions", indexes = {
        @Index(name = "idx_sale_tx_property", columnList = "property_id"),
        @Index(name = "idx_sale_tx_verification", columnList = "verification, completion_date")
})
@Getter
@Setter
@NoArgsConstructor
public class SaleTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    @Column(nullable = false, length = 3)
    private String currency = "USD";
    /** The day the sale completed (transfer registered) or, if not yet known, the agreed date. */
    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SaleSource source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleVerification verification = SaleVerification.REPORTED;

    @Column(name = "reported_by_user_id")
    private Long reportedByUserId;
    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt = Instant.now();

    /** Conveyancer letter reference or deed of transfer number. */
    @Column(name = "evidence_reference", length = 200)
    private String evidenceReference;
    @Column(name = "verified_by_user_id")
    private Long verifiedByUserId;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "verification_note", length = 1000)
    private String verificationNote;

    // Copied from the listing when reported.
    private String country;
    private String city;
    private String suburb;
    @Column(length = 20)
    private String category;
    private Integer bedrooms;
    @Column(name = "floor_area_m2")
    private Integer floorAreaM2;
    @Column(name = "asking_price", precision = 19, scale = 2)
    private BigDecimal askingPrice;
    @Column(name = "organisation_id")
    private Long organisationId;
}
