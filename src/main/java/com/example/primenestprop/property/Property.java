package com.example.primenestprop.property;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "properties", indexes = {
        @Index(name = "idx_properties_search", columnList = "status, listingType, city, suburb, price, bedrooms"),
        @Index(name = "idx_properties_landlord", columnList = "landlord_id"),
        @Index(name = "idx_properties_agent", columnList = "agent_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Property {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingType listingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String suburb;

    private String address;
    private String country = "Zimbabwe";
    private int bedrooms;
    private int bathrooms;
    private BigDecimal price;
    private String currency = "USD";
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean diasporaFriendly;
    private boolean escrowRequired = true;
    private Instant createdAt = Instant.now();
    private Instant verifiedAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser landlord;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser agent;

    @OneToMany(mappedBy = "property")
    private List<PropertyMedia> media = new ArrayList<>();

    @OneToMany(mappedBy = "property")
    @OrderBy("primaryPhoto DESC, sortOrder ASC, id ASC")
    private List<PropertyPhoto> photos = new ArrayList<>();
}
