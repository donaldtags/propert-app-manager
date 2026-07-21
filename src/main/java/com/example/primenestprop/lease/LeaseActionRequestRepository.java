package com.example.primenestprop.lease;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaseActionRequestRepository extends JpaRepository<LeaseActionRequest, Long> {
    @EntityGraph(attributePaths = {"lease", "lease.property", "lease.tenant", "lease.landlord", "requestedBy"})
    Optional<LeaseActionRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"lease", "lease.property", "requestedBy"})
    List<LeaseActionRequest> findByRequestedByOrderByCreatedAtDesc(AppUser requestedBy);

    @EntityGraph(attributePaths = {"lease", "lease.property", "requestedBy"})
    List<LeaseActionRequest> findByLeaseOrderByCreatedAtDesc(Lease lease);

    @Query("""
            select r from LeaseActionRequest r
            where r.lease.landlord.id = :userId or r.lease.property.agent.id = :userId
            order by r.createdAt desc
            """)
    @EntityGraph(attributePaths = {"lease", "lease.property", "requestedBy"})
    List<LeaseActionRequest> findForOwner(@Param("userId") Long userId);
}
