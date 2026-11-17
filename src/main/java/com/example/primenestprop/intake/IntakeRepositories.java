package com.example.primenestprop.intake;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ListingInspectionRepository extends JpaRepository<ListingInspection, Long> {
    Optional<ListingInspection> findByPropertyId(Long propertyId);
}

interface InspectionPhotoRepository extends JpaRepository<InspectionPhoto, Long> {
    Optional<InspectionPhoto> findByClientId(String clientId);

    List<InspectionPhoto> findByPropertyIdOrderByCreatedAtAsc(Long propertyId);
}

interface PricingRecommendationRepository extends JpaRepository<PricingRecommendation, Long> {
    Optional<PricingRecommendation> findFirstByPropertyIdOrderByCreatedAtDesc(Long propertyId);
}
