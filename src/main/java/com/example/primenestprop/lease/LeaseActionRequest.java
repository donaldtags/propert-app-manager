package com.example.primenestprop.lease;

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
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lease_action_requests", indexes = {
        @Index(name = "idx_lease_actions_lease", columnList = "lease_id")
})
@Getter
@Setter
@NoArgsConstructor
public class LeaseActionRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Lease lease;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseActionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseActionStatus status = LeaseActionStatus.PENDING;

    private LocalDate proposedEndDate;

    @Column(length = 1000)
    private String note;

    @Column(length = 1000)
    private String reviewNote;

    private Instant createdAt = Instant.now();
    private Instant resolvedAt;
}
