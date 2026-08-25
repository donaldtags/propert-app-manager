package com.example.primenestprop.neighbourhood;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-curated facts about a suburb - schools, hospitals, transport, shopping. Deliberately NOT
 * auto-populated from any external data source: PrimeNest has no live feed for crime statistics,
 * ISP coverage, or power-outage patterns, and presenting invented numbers for those would be
 * worse than presenting nothing. What's here is only what an admin has actually verified and
 * entered by hand.
 */
@Entity
@Table(name = "neighbourhood_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_neighbourhood_city_suburb", columnNames = {"city", "suburb"})
})
@Getter
@Setter
@NoArgsConstructor
public class NeighbourhoodProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String suburb;

    @Column(length = 2000)
    private String schoolsNote;

    @Column(length = 2000)
    private String hospitalsNote;

    @Column(length = 2000)
    private String transportNote;

    @Column(length = 2000)
    private String shoppingNote;

    @Column(length = 2000)
    private String generalNote;

    private Long updatedByUserId;
    private Instant updatedAt = Instant.now();
}
