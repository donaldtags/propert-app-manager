package com.example.primenestprop.showing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowingRepository extends JpaRepository<Showing, Long> {
    Optional<Showing> findByClientId(String clientId);

    List<Showing> findByPropertyIdOrderByScheduledAtDesc(Long propertyId);

    List<Showing> findByOrganisationIdAndContactIdOrderByScheduledAtDesc(Long organisationId, Long contactId);

    List<Showing> findByOrganisationIdAndScheduledAtBetweenOrderByScheduledAtAsc(Long organisationId, Instant from, Instant to);
}
