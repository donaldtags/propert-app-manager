package com.example.primenestprop.vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A service provider Homestead's admins have vetted and listed - movers, cleaners, plumbers,
 * insurers, lawyers, solar installers, etc. Admin-curated (like the Neighbourhood profiles)
 * rather than open self-registration, since a fraudulent "vendor" listing is a direct route to
 * scamming a tenant/landlord who trusts Homestead's vetting.
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

    private Instant createdAt = Instant.now();
}
