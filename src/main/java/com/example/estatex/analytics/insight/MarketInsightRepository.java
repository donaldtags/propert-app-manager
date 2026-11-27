package com.example.estatex.analytics.insight;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketInsightRepository extends JpaRepository<MarketInsight, Long> {
    Optional<MarketInsight> findFirstByOrderByCreatedAtDescIdDesc();

    Optional<MarketInsight> findFirstByStatusOrderByCreatedAtDescIdDesc(String status);

    List<MarketInsight> findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(String status, Instant from,
                                                                                                         Instant to);
}
