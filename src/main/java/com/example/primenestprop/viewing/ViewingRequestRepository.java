package com.example.primenestprop.viewing;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewingRequestRepository extends JpaRepository<ViewingRequest, Long> {
    @EntityGraph(attributePaths = {"property", "requester"})
    Optional<ViewingRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"property", "requester"})
    List<ViewingRequest> findByRequesterOrderByCreatedAtDesc(AppUser requester);

    @EntityGraph(attributePaths = {"property", "requester"})
    List<ViewingRequest> findByProperty_LandlordOrderByCreatedAtDesc(AppUser landlord);

    @EntityGraph(attributePaths = {"property", "requester"})
    List<ViewingRequest> findByProperty_AgentOrderByCreatedAtDesc(AppUser agent);

    @EntityGraph(attributePaths = {"property", "requester"})
    List<ViewingRequest> findByPropertyOrderByCreatedAtDesc(Property property);

    boolean existsByCheckInCode(String checkInCode);
}
