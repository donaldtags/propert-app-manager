package com.example.estatex.analytics.insight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How much evidence the latest observation pass had, per grain: how many comparisons were tested,
 * how many were reported, and how many could not be tested (too few observations, or history that
 * started part-way through). Lets the dashboard say "no reliable conclusion" with a reason.
 */
@Entity
@Table(name = "observation_coverage")
@Getter
@Setter
@NoArgsConstructor
public class ObservationCoverage {
    @Id
    @Column(length = 10)
    private String grain;
    @Column(name = "period_start")
    private Instant periodStart;
    @Column(name = "period_end")
    private Instant periodEnd;
    @Column(name = "comparison_start")
    private Instant comparisonStart;
    @Column(name = "comparison_end")
    private Instant comparisonEnd;
    private int tested;
    private int reported;
    @Column(name = "below_minimum")
    private int belowMinimum;
    @Column(name = "history_incomplete")
    private int historyIncomplete;
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
