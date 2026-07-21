package com.example.primenestprop.vendor;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long> {
    @EntityGraph(attributePaths = {"vendor", "property", "requester"})
    Optional<ServiceBooking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"vendor", "property", "requester"})
    List<ServiceBooking> findByRequesterOrderByCreatedAtDesc(AppUser requester);

    @EntityGraph(attributePaths = {"vendor", "property", "requester"})
    List<ServiceBooking> findByVendorOrderByCreatedAtDesc(Vendor vendor);

    List<ServiceBooking> findByVendorAndFeedbackRatingIsNotNull(Vendor vendor);
}
