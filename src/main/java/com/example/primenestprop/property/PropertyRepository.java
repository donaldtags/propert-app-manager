package com.example.primenestprop.property;

import com.example.primenestprop.user.AppUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
    @Query("""
            select p from Property p
            where (:listingType is null or p.listingType = :listingType)
              and (:city is null or lower(p.city) = lower(:city))
              and (:suburb is null or lower(p.suburb) like lower(concat('%', :suburb, '%')))
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:bedrooms is null or p.bedrooms >= :bedrooms)
              and p.status = com.example.primenestprop.property.PropertyStatus.AVAILABLE
            order by case when p.verificationStatus = com.example.primenestprop.property.VerificationStatus.VERIFIED then 0 else 1 end,
                     p.createdAt desc
            """)
    List<Property> search(
            @Param("listingType") ListingType listingType,
            @Param("city") String city,
            @Param("suburb") String suburb,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("bedrooms") Integer bedrooms
    );
}
