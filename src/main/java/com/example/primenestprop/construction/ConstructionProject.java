package com.example.primenestprop.construction;

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
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "construction_projects", indexes = {
        @Index(name = "idx_construction_projects_owner", columnList = "owner_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ConstructionProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser owner;

    /** Optional link to a marketplace listing — a self-build on private land may have none. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Property property;

    @Column(nullable = false)
    private String title;

    private String country = "Zimbabwe";
    private String city;
    private String address;

    private String contractorName;
    private String contractorPhone;
    private String contractorCompany;

    @Enumerated(EnumType.STRING)
    private ConstructionStage stage = ConstructionStage.PLANNING;

    private int progressPercent;

    @Enumerated(EnumType.STRING)
    private ConstructionStatus status = ConstructionStatus.ACTIVE;

    private BigDecimal budgetTotal;
    private BigDecimal amountPaid = BigDecimal.ZERO;
    private String currency = "USD";

    private LocalDate startDate;
    private LocalDate expectedCompletionDate;

    private Instant createdAt = Instant.now();
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }
}
