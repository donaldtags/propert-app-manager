package com.example.primenestprop.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
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
}
