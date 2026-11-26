package com.example.estatex.analytics.capture;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyEventRepository extends JpaRepository<PropertyEvent, Long> {
    List<PropertyEvent> findByPropertyIdOrderByOccurredAtAscIdAsc(Long propertyId);

    List<PropertyEvent> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByIdAsc(Instant from, Instant to);
}
