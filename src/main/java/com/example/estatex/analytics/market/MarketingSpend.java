package com.example.estatex.analytics.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Money spent on marketing in a month, entered by finance staff (EstateX has no other record of
 * it). Needed for customer acquisition cost.
 */
@Entity
@Table(name = "marketing_spend", indexes = @Index(name = "idx_marketing_spend_month", columnList = "spend_month"))
@Getter
@Setter
@NoArgsConstructor
public class MarketingSpend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** First day of the month the money was spent in. ("month" alone is a reserved word in some databases.) */
    @Column(name = "spend_month", nullable = false)
    private LocalDate month;
    @Column(nullable = false, length = 60)
    private String channel;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency = "USD";
    @Column(length = 300)
    private String note;
    @Column(name = "entered_by_user_id")
    private Long enteredByUserId;
    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt = Instant.now();
}
