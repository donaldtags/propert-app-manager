package com.example.primenestprop.lease;

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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leases")
@Getter
@Setter
@NoArgsConstructor
public class Lease {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser landlord;

    @Enumerated(EnumType.STRING)
    private LeaseStatus status = LeaseStatus.DRAFT;

    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyRent;
    private BigDecimal depositAmount;
    private String currency = "USD";
    private Instant tenantSignedAt;
    private Instant landlordSignedAt;
    private Instant signedAt;
    private Instant createdAt = Instant.now();

    @Column(length = 4000)
    private String terms;
}
