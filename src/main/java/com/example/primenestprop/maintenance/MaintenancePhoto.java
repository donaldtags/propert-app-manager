package com.example.primenestprop.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_photos", indexes = {
        @Index(name = "idx_maintenance_photos_request", columnList = "maintenance_request_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MaintenancePhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MaintenanceRequest maintenanceRequest;

    @Column(length = 1000)
    private String photoUrl;

    @Column(length = 1000)
    private String storageKey;

    private Instant uploadedAt = Instant.now();
}
