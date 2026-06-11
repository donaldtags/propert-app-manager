package com.example.primenestprop.investment;

import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "investments")
@Getter
@Setter
@NoArgsConstructor
public class Investment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser investor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Reit reit;

    @Enumerated(EnumType.STRING)
    private InvestmentStatus status = InvestmentStatus.PENDING;

    private BigDecimal units;
    private BigDecimal amount;
    private String currency = "USD";
    private Instant createdAt = Instant.now();
}
