package com.example.estatex.analytics.metrics;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricValueRepository extends JpaRepository<MetricValue, Long> {
    List<MetricValue> findByMetricKeyAndGrainAndDimsKeyAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStart(
            String metricKey, String grain, String dimsKey, Instant from, Instant to);

    List<MetricValue> findByGrainAndPeriodStart(String grain, Instant periodStart);

    List<MetricValue> findByMetricKeyAndGrainAndPeriodStartAndDimListingType(String metricKey, String grain,
                                                                           Instant periodStart, String listingType);
}
