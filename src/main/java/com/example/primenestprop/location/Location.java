package com.example.primenestprop.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One node of the Zimbabwe place hierarchy: province, city/town/growth point, then suburb.
 * Seeded from reference/zimbabwe-locations.json and curated by admins after that. Rows a user
 * suggests start {@code verified=false} and stay out of public lists until an admin approves them.
 */
@Entity
@Table(name = "locations",
        uniqueConstraints = @UniqueConstraint(name = "uq_location_parent_name", columnNames = {"parentId", "name"}),
        indexes = {
                @Index(name = "idx_locations_parent", columnList = "parentId"),
                @Index(name = "idx_locations_type", columnList = "type")
        })
@Getter
@Setter
@NoArgsConstructor
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    // Enum columns are plain strings with typed accessors: Hibernate 7 bakes the enum's current
    // values into a CHECK constraint (even behind an AttributeConverter) that ddl-auto=update never
    // widens, so adding a value later would break inserts. Guarded by OrganisationWorkspaceTests.
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "type", nullable = false, length = 20)
    private String typeCode;

    @Column(nullable = false, length = 120)
    private String name;

    /** Province for cities/towns, city/town for suburbs, null for provinces. */
    private Long parentId;

    /** Denormalised for fast filtering and display ("Glen View, Harare, Harare Metropolitan"). */
    @Column(length = 80)
    private String province;

    @Column(length = 120)
    private String city;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "density_tier", length = 20)
    private String densityTierCode;

    /** Comma-separated alternative spellings/names ("Malbereign", "Warren Park 1"). */
    @Column(length = 500)
    private String aliases;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 120)
    private String source;

    private boolean verified;
    private Long suggestedByUserId;
    private Instant createdAt = Instant.now();

    public LocationType getType() {
        return typeCode == null ? null : LocationType.valueOf(typeCode);
    }

    public void setType(LocationType type) {
        this.typeCode = type == null ? null : type.name();
    }

    public DensityTier getDensityTier() {
        return densityTierCode == null ? null : DensityTier.valueOf(densityTierCode);
    }

    public void setDensityTier(DensityTier tier) {
        this.densityTierCode = tier == null ? null : tier.name();
    }
}
