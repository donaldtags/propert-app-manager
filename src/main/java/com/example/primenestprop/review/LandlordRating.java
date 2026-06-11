package com.example.primenestprop.review;

import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
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
@Table(name = "landlord_ratings", indexes = {
        @Index(name = "idx_landlord_ratings_landlord", columnList = "landlord_id"),
        @Index(name = "idx_landlord_ratings_tenant", columnList = "tenant_id"),
        @Index(name = "idx_landlord_ratings_lease", columnList = "lease_id")
})
@Getter
@Setter
@NoArgsConstructor
public class LandlordRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser landlord;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    private Lease lease;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
