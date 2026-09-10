package com.example.primenestprop.construction;

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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "construction_milestones", indexes = {
        @Index(name = "idx_construction_milestones_project", columnList = "project_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ConstructionMilestone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ConstructionProject project;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    private BigDecimal amountDue;
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    private Instant submittedAt;
    private Instant approvedAt;

    /** Set once the diaspora owner (or an admin) approves — the payment-release gate. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser approvedBy;

    private Instant createdAt = Instant.now();
}
