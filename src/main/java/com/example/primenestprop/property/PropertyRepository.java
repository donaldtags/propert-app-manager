package com.example.primenestprop.property;

import com.example.primenestprop.user.AppUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    @EntityGraph(attributePaths = {"landlord", "agent", "photos"})
    List<Property> findByLandlord(AppUser landlord);

    @EntityGraph(attributePaths = {"landlord", "agent", "photos"})
    List<Property> findByAgent(AppUser agent);

    @EntityGraph(attributePaths = {"landlord", "agent", "photos"})
    Optional<Property> findWithPhotosById(Long id);

    @EntityGraph(attributePaths = {"landlord", "agent", "photos"})
    @Query(value = """
            select p from Property p
            where (:listingType is null or p.listingType = :listingType)
              and (:city is null or lower(p.city) = lower(:city))
              and (:suburb is null or lower(p.suburb) like lower(concat('%', :suburb, '%')))
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:bedrooms is null or p.bedrooms >= :bedrooms)
              and (:bathrooms is null or p.bathrooms >= :bathrooms)
              and (:diasporaFriendly is null or p.diasporaFriendly = :diasporaFriendly)
              and (:solarInstalled is null or p.solarInstalled = :solarInstalled)
              and (:backupPower is null or p.backupPower = :backupPower)
              and (:waterSource is null or p.waterSource = :waterSource)
              and (:furnished is null or p.furnished = :furnished)
              and (:internetAvailable is null or p.internetAvailable = :internetAvailable)
              and (:securityFeatures is null or p.securityFeatures = :securityFeatures)
              and (:parkingAvailable is null or p.parkingAvailable = :parkingAvailable)
              and (:petsAllowed is null or p.petsAllowed = :petsAllowed)
              and (:verifiedOnly is null or :verifiedOnly = false
                   or p.verificationStatus = com.example.primenestprop.property.VerificationStatus.VERIFIED)
              and (:escrowAvailable is null or p.escrowRequired = :escrowAvailable)
              and p.status = com.example.primenestprop.property.PropertyStatus.AVAILABLE
            order by case when p.featured = true and (p.featuredUntil is null or p.featuredUntil > CURRENT_TIMESTAMP) then 0 else 1 end,
                     case when p.verificationStatus = com.example.primenestprop.property.VerificationStatus.VERIFIED then 0 else 1 end,
                     p.createdAt desc
            """,
            countQuery = """
            select count(p) from Property p
            where (:listingType is null or p.listingType = :listingType)
              and (:city is null or lower(p.city) = lower(:city))
              and (:suburb is null or lower(p.suburb) like lower(concat('%', :suburb, '%')))
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:bedrooms is null or p.bedrooms >= :bedrooms)
              and (:bathrooms is null or p.bathrooms >= :bathrooms)
              and (:diasporaFriendly is null or p.diasporaFriendly = :diasporaFriendly)
              and (:solarInstalled is null or p.solarInstalled = :solarInstalled)
              and (:backupPower is null or p.backupPower = :backupPower)
              and (:waterSource is null or p.waterSource = :waterSource)
              and (:furnished is null or p.furnished = :furnished)
              and (:internetAvailable is null or p.internetAvailable = :internetAvailable)
              and (:securityFeatures is null or p.securityFeatures = :securityFeatures)
              and (:parkingAvailable is null or p.parkingAvailable = :parkingAvailable)
              and (:petsAllowed is null or p.petsAllowed = :petsAllowed)
              and (:verifiedOnly is null or :verifiedOnly = false
                   or p.verificationStatus = com.example.primenestprop.property.VerificationStatus.VERIFIED)
              and (:escrowAvailable is null or p.escrowRequired = :escrowAvailable)
              and p.status = com.example.primenestprop.property.PropertyStatus.AVAILABLE
            """)
    Page<Property> search(
            @Param("listingType") ListingType listingType,
            @Param("city") String city,
            @Param("suburb") String suburb,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("bedrooms") Integer bedrooms,
            @Param("bathrooms") Integer bathrooms,
            @Param("diasporaFriendly") Boolean diasporaFriendly,
            @Param("solarInstalled") Boolean solarInstalled,
            @Param("backupPower") Boolean backupPower,
            @Param("waterSource") WaterSource waterSource,
            @Param("furnished") Boolean furnished,
            @Param("internetAvailable") Boolean internetAvailable,
            @Param("securityFeatures") Boolean securityFeatures,
            @Param("parkingAvailable") Boolean parkingAvailable,
            @Param("petsAllowed") Boolean petsAllowed,
            @Param("verifiedOnly") Boolean verifiedOnly,
            @Param("escrowAvailable") Boolean escrowAvailable,
            Pageable pageable
    );

    long countByStatus(PropertyStatus status);

    List<Property> findTop10ByOrderByCreatedAtDesc();
}
