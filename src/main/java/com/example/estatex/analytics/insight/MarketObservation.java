package com.example.estatex.analytics.insight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A deterministic, statistically tested finding ("Observed"), e.g. "enquiries for rentals in
 * Avondale rose from 12 to 31". Produced by {@link ObservationService} from published figures
 * only; never written by AI. The fingerprint keeps the row (and its id) stable across hourly
 * re-runs, so alerts and AI interpretations can point at it.
 */
@Entity
@Table(name = "market_observations", indexes = {
        @Index(name = "uq_market_observation", columnList = "fingerprint", unique = true),
        @Index(name = "idx_market_observation_period", columnList = "grain, period_start")
})
@Getter
@Setter
@NoArgsConstructor
public class MarketObservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 250)
    private String fingerprint;
    /** e.g. ENQUIRIES_UP, ASKING_PRICE_DOWN, CATEGORY_UNDERSUPPLIED, TOP_SUPPLY_CATEGORY. */
    @Column(nullable = false, length = 60)
    private String kind;
    @Column(name = "metric_key", nullable = false, length = 60)
    private String metricKey;
    /** MetricDefinition.Domain: decides who may see it. */
    @Column(nullable = false, length = 20)
    private String domain;
    /** POISSON_RATE, MEDIAN_SHIFT, SHARE_GAP, THRESHOLD or DESCRIPTIVE. */
    @Column(nullable = false, length = 20)
    private String method;
    @Column(nullable = false, length = 10)
    private String grain;
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;
    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;
    @Column(name = "comparison_start")
    private Instant comparisonStart;
    @Column(name = "comparison_end")
    private Instant comparisonEnd;
    @Column(name = "dims_key", nullable = false, length = 200)
    private String dimsKey;
    @Column(name = "listing_type", length = 20)
    private String listingType;
    @Column(length = 80)
    private String city;
    @Column(length = 80)
    private String suburb;
    @Column(length = 40)
    private String category;
    @Column(name = "current_value", precision = 19, scale = 4)
    private BigDecimal currentValue;
    @Column(name = "previous_value", precision = 19, scale = 4)
    private BigDecimal previousValue;
    @Column(name = "change_pct", precision = 12, scale = 2)
    private BigDecimal changePct;
    @Column(name = "sample_current", nullable = false)
    private long sampleCurrent;
    @Column(name = "sample_previous", nullable = false)
    private long samplePrevious;
    private Double statistic;
    @Column(name = "p_value")
    private Double pValue;
    /** Benjamini–Hochberg adjusted p-value across every test in the run. */
    @Column(name = "q_value")
    private Double qValue;
    /** HIGH, MEDIUM or LOW. */
    @Column(nullable = false, length = 10)
    private String confidence;
    @Column(nullable = false, length = 600)
    private String statement;
    @Column(length = 3)
    private String currency;
    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "job_run_id")
    private Long jobRunId;
}
