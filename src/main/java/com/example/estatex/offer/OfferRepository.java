package com.example.estatex.offer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    Optional<Offer> findByClientId(String clientId);

    List<Offer> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);

    List<Offer> findByOrganisationIdAndBuyerContactIdOrderByCreatedAtDesc(Long organisationId, Long contactId);

    boolean existsByPropertyIdAndStatusCode(Long propertyId, String statusCode);

    List<Offer> findByOrganisationIdAndStatusCodeIn(Long organisationId, Collection<String> statuses);
}
