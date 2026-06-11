package com.example.primenestprop.user;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admin_access_requests", indexes = {
        @Index(name = "idx_admin_access_user_status", columnList = "user_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class AdminAccessRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminRequestStatus status = AdminRequestStatus.PENDING;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    private Instant reviewedAt;
    private Long reviewedBy;
}
