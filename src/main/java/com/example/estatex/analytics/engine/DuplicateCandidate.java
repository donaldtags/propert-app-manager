package com.example.estatex.analytics.engine;

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

/** Two live listings that look like the same property. For staff to review; nothing is merged automatically. */
@Entity
@Table(name = "listing_duplicate_candidates", indexes = {
        @Index(name = "uq_duplicate_pair", columnList = "property_a, property_b", unique = true),
        @Index(name = "idx_duplicate_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class DuplicateCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Always the smaller id of the pair. */
    @Column(name = "property_a", nullable = false)
    private Long propertyA;
    @Column(name = "property_b", nullable = false)
    private Long propertyB;
    @Column(nullable = false, length = 200)
    private String reason;
    /** OPEN, DISMISSED (not a duplicate) or CONFIRMED. */
    @Column(nullable = false, length = 12)
    private String status = "OPEN";
    @Column(name = "first_detected_at", nullable = false)
    private Instant firstDetectedAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
