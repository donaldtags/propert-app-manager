package com.example.primenestprop.property;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyBillingRepository extends JpaRepository<PropertyBilling, Long> {
    Optional<PropertyBilling> findByProperty(Property property);

    List<PropertyBilling> findByStatusAndCurrentPeriodEndBefore(PropertyBillingStatus status, Instant before);

    List<PropertyBilling> findByStatusAndPastDueSinceBefore(PropertyBillingStatus status, Instant before);
}