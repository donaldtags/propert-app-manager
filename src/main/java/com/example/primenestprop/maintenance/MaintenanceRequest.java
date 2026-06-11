package com.example.primenestprop.maintenance;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_requests")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser requester;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    private String category;
    private String priority = "NORMAL";

    @Column(length = 2000)
    private String description;

    private Instant createdAt = Instant.now();
    private Instant updatedAt;
    private Instant resolvedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }
}
