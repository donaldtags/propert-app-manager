package com.example.estatex.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A generated weekly seller report PDF, shareable through an unguessable, expiring link. */
@Entity
@Table(name = "seller_reports", indexes = {
        @Index(name = "idx_seller_reports_property", columnList = "propertyId"),
        @Index(name = "uq_seller_reports_token", columnList = "shareToken", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class SellerReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private LocalDate weekEnding;

    /** 256-bit random, URL-safe. Anyone holding it can read this one report until it expires. */
    @Column(nullable = false, length = 64)
    private String shareToken;

    @Column(nullable = false, length = 500)
    private String storageKey;

    @Column(length = 20) private String adviceKind;
    @Column(length = 600) private String adviceText;

    private Long generatedByUserId;
    private Instant generatedAt = Instant.now();
    private Instant expiresAt;
}
