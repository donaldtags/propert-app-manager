package com.example.estatex.analytics.capture;

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

/** A listing a signed-in user saved to come back to. */
@Entity
@Table(name = "saved_properties", indexes = {
        @Index(name = "uq_saved_property", columnList = "user_id, property_id", unique = true),
        @Index(name = "idx_saved_property_property", columnList = "property_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SavedProperty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
