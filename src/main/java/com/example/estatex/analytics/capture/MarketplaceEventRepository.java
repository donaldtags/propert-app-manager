package com.example.estatex.analytics.capture;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceEventRepository extends JpaRepository<MarketplaceEvent, Long> {
    long countByEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(String eventType, Instant from, Instant to);

    boolean existsByDedupeKey(String dedupeKey);
}
