package com.example.primenestprop.review;

import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandlordRatingRepository extends JpaRepository<LandlordRating, Long> {
    @EntityGraph(attributePaths = {"landlord", "tenant", "property", "lease"})
    List<LandlordRating> findByLandlordOrderByCreatedAtDesc(AppUser landlord);

    @EntityGraph(attributePaths = {"landlord", "tenant", "property", "lease"})
    Optional<LandlordRating> findByLeaseAndTenant(Lease lease, AppUser tenant);
}
