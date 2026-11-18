package com.example.estatex.listing;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MandateRepository extends JpaRepository<Mandate, Long> {
    List<Mandate> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);

    Optional<Mandate> findByClientId(String clientId);

    List<Mandate> findByStatusCodeInAndExpiryDateBefore(Collection<String> statuses, LocalDate date);
}
