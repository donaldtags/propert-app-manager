package com.example.estatex.analytics.capture;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedPropertyRepository extends JpaRepository<SavedProperty, Long> {
    Optional<SavedProperty> findByUserIdAndPropertyId(Long userId, Long propertyId);

    List<SavedProperty> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByPropertyId(Long propertyId);
}
