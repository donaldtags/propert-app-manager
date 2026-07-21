package com.example.primenestprop.investment;

import com.example.primenestprop.property.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reits")
@Getter
@Setter
@NoArgsConstructor
public class Reit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    private String market = "Zimbabwe";
    private BigDecimal unitPrice;
    private BigDecimal projectedAnnualYield;
    private String riskLevel = "MEDIUM";
    private boolean vexEligible;
    private boolean active = true;
    private Instant createdAt = Instant.now();

    /** Sector this REIT invests in, e.g. RESIDENTIAL, COMMERCIAL, INDUSTRIAL, MIXED_USE, HOSPITALITY. */
    private String propertyType = "RESIDENTIAL";

    @Column(length = 1000)
    private String coverImageUrl;

    /** Null means unlimited inventory. */
    private BigDecimal totalUnits;
    private BigDecimal unitsSold = BigDecimal.ZERO;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "reit_properties",
            joinColumns = @JoinColumn(name = "reit_id"),
            inverseJoinColumns = @JoinColumn(name = "property_id")
    )
    private Set<Property> properties = new LinkedHashSet<>();

    public BigDecimal getAvailableUnits() {
        return totalUnits == null ? null : totalUnits.subtract(unitsSold);
    }
}
