package com.example.primenestprop.featured;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Singleton row (id always 1) holding the admin-controlled price to feature a listing. */
@Entity
@Table(name = "featured_listing_settings")
@Getter
@Setter
@NoArgsConstructor
public class FeaturedListingSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** Defaults match the PRD's "Featured Listing" line item ($10-$20 per 14 days); admin can change both. */
    private BigDecimal price = new BigDecimal("15.00");
    private String currency = "USD";
    private int durationDays = 14;
    private Instant updatedAt = Instant.now();
}