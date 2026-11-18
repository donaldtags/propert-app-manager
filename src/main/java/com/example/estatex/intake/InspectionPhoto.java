package com.example.estatex.intake;

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

/** Internal inspection photo (condition evidence). Never shown publicly; listing photos are separate. */
@Entity
@Table(name = "inspection_photos", indexes = {
        @Index(name = "idx_inspection_photos_property", columnList = "propertyId"),
        @Index(name = "uq_inspection_photos_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class InspectionPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false, length = 500)
    private String storageKey;

    @Column(length = 120) private String contentType;
    @Column(length = 300) private String caption;
    /** Inspection item this photo shows, e.g. ROOF. */
    @Column(length = 40) private String itemKey;

    private Long uploadedByUserId;
    private Instant createdAt = Instant.now();
}
