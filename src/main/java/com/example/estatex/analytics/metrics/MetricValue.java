package com.example.estatex.analytics.metrics;

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
 * One published figure: a metric, for one period, for one slice of the market (dimensions;
 * "*" = all). Kept apart from the source tables. Each row says which definition version and
 * which job run produced it, how many observations it rests on and, for incomplete periods,
 * the moment it covers up to.
 */
@Entity
@Table(name = "metric_values", indexes = {
        @Index(name = "uq_metric_value", columnList = "metric_key, grain, period_start, variant, dims_key", unique = true),
        @Index(name = "idx_metric_value_lookup", columnList = "metric_key, grain, dim_listing_type, period_start")
})
@Getter
@Setter
@NoArgsConstructor
public class MetricValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_key", nullable = false, length = 40)
    private String metricKey;
    @Column(name = "definition_version", nullable = false)
    private int definitionVersion;
    @Column(nullable = false, length = 10)
    private String grain;
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;
    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;
    @Column(name = "as_of", nullable = false)
    private Instant asOf;
    @Column(nullable = false)
    private boolean complete;
    /**
     * FULL: the whole period (or up to now, if it's the current one). TO_DATE: an earlier period cut
     * to the same length as the current, unfinished one, e.g. 1–26 October for comparison with
     * 1–26 November.
     */
    @Column(nullable = false, length = 10)
    private String variant = "FULL";
    /** False when the period starts before its source began recording; such figures aren't comparable. */
    @Column(name = "history_complete", nullable = false)
    private boolean historyComplete = true;

    @Column(name = "dims_key", nullable = false, length = 400)
    private String dimsKey;
    @Column(name = "dim_listing_type", length = 20)
    private String dimListingType;
    @Column(name = "dim_country", length = 80)
    private String dimCountry;
    @Column(name = "dim_city", length = 120)
    private String dimCity;
    @Column(name = "dim_suburb", length = 120)
    private String dimSuburb;
    @Column(name = "dim_category", length = 20)
    private String dimCategory;
    @Column(name = "dim_bedrooms", length = 10)
    private String dimBedrooms;

    @Column(name = "metric_value", precision = 19, scale = 2)
    private BigDecimal value;
    @Column(name = "sample_size", nullable = false)
    private int sampleSize;
    @Column(precision = 19, scale = 2)
    private BigDecimal median;
    @Column(precision = 19, scale = 2)
    private BigDecimal p25;
    @Column(precision = 19, scale = 2)
    private BigDecimal p75;
    @Column(precision = 19, scale = 2)
    private BigDecimal mean;
    @Column(name = "min_value", precision = 19, scale = 2)
    private BigDecimal minValue;
    @Column(name = "max_value", precision = 19, scale = 2)
    private BigDecimal maxValue;
    @Column(name = "sum_value", precision = 21, scale = 2)
    private BigDecimal sumValue;
    /** Records left out (no price, other currency…), so coverage can be shown. */
    @Column(name = "excluded_count", nullable = false)
    private int excludedCount;
    /** Share of relevant records that had the data this metric needs (e.g. floor area), 0–1. */
    @Column(precision = 5, scale = 4)
    private BigDecimal coverage;
    @Column(name = "low_sample", nullable = false)
    private boolean lowSample;
    @Column(length = 3)
    private String currency;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
    @Column(name = "job_run_id")
    private Long jobRunId;
}
