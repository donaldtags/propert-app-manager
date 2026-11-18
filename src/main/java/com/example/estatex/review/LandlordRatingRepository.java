package com.example.estatex.review;

import com.example.estatex.lease.Lease;
import com.example.estatex.property.Property;
import com.example.estatex.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandlordRatingRepository extends JpaRepository<LandlordRating, Long> {
    @EntityGraph(attributePaths = {"landlord", "tenant", "property", "lease"})
    List<LandlordRating> findByLandlordOrderByCreatedAtDesc(AppUser landlord);

    @EntityGraph(attributePaths = {"landlord", "tenant", "property", "lease"})
    List<LandlordRating> findByPropertyOrderByCreatedAtDesc(Property property);

    @EntityGraph(attributePaths = {"landlord", "tenant", "property", "lease"})
    List<LandlordRating> findByTenant(AppUser tenant);

    @EntityGraph(attributePaths = {"landlord", "tenant", "property", "lease"})
    Optional<LandlordRating> findByLeaseAndTenant(Lease lease, AppUser tenant);
}
