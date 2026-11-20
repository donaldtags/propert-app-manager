package com.example.estatex.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A visitor's report that a listing looks fake, wrong or abusive. */
@Entity
@Table(name = "listing_reports", indexes = {
        @Index(name = "idx_listing_reports_property", columnList = "property_id"),
        @Index(name = "idx_listing_reports_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class ListingReport {
    public static final String OPEN = "OPEN";
    public static final String DISMISSED = "DISMISSED";
    public static final String TAKEN_DOWN = "TAKEN_DOWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    /** Signed-in reporter, if any. */
    private Long reporterUserId;
    /** SHA-256 of the reporter's IP: counts distinct anonymous reporters without storing the IP. */
    @Column(length = 64)
    private String reporterFingerprint;

    /** FAKE_OR_SCAM, NOT_AVAILABLE, WRONG_DETAILS, STOLEN_PHOTOS, OFFENSIVE or OTHER. */
    @Column(nullable = false, length = 30)
    private String reason;
    @Column(length = 1000)
    private String details;

    @Column(nullable = false, length = 20)
    private String status = OPEN;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;
    private Long resolvedByUserId;
    @Column(length = 1000)
    private String resolutionNote;
}
