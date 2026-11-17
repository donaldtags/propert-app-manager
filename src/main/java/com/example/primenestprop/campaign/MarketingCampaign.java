package com.example.primenestprop.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marketing_campaigns", indexes = @Index(name = "idx_campaigns_property", columnList = "propertyId"))
@Getter
@Setter
@NoArgsConstructor
public class MarketingCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    /** Day 1. Business date (Africa/Harare). */
    @Column(nullable = false)
    private LocalDate startDate;

    private boolean stopped;
    private Long createdByUserId;
    private Instant createdAt = Instant.now();
}
