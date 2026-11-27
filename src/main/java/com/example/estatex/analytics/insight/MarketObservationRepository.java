package com.example.estatex.analytics.insight;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketObservationRepository extends JpaRepository<MarketObservation, Long> {
    Optional<MarketObservation> findByFingerprint(String fingerprint);

    List<MarketObservation> findByGrainAndPeriodStart(String grain, Instant periodStart);

    List<MarketObservation> findByPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAscIdAsc(Instant from, Instant to);
}
