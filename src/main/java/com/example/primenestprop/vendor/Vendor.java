package com.example.primenestprop.vendor;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A service provider listed on PrimeNest - movers, cleaners, plumbers, insurers, lawyers, solar
 * installers, etc. Either admin-curated (owner null) or self-registered by a SERVICE_PROVIDER
 * user (owner set); either way, {@code verified} only ever flips to true via admin review, since
 * a fraudulent "vendor" listing is a direct route to scamming a tenant/landlord who trusts
 * PrimeNest's vetting.
 */
@Entity
@Table(name = "vendors", indexes = {
        @Index(name = "idx_vendors_category", columnList = "category"),
        @Index(name = "idx_vendors_city", columnList = "city")
})
@Getter
@Setter
@NoArgsConstructor
public class Vendor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorCategory category;

    @Column(length = 2000)
    private String description;

    private String phone;
    private String email;
    private String city;
    private boolean verified;
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser owner;

    private Instant createdAt = Instant.now();
}
