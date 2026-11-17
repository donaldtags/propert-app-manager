package com.example.primenestprop.listing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingComplianceRepository extends JpaRepository<ListingCompliance, Long> {
    Optional<ListingCompliance> findByPropertyId(Long propertyId);

    Optional<ListingCompliance> findByClientId(String clientId);

    List<ListingCompliance> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    List<ListingCompliance> findByActiveMandateId(Long mandateId);
}
