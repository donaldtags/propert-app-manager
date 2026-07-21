package com.example.primenestprop.lease;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseRepository extends JpaRepository<Lease, Long> {

    @EntityGraph(attributePaths = {"property", "tenant", "landlord"})
    Optional<Lease> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"property", "tenant", "landlord"})
    List<Lease> findByPropertyOrderByCreatedAtDesc(Property property);

    @EntityGraph(attributePaths = {"property", "tenant", "landlord"})
    List<Lease> findByTenant(AppUser tenant);

    @EntityGraph(attributePaths = {"property", "tenant", "landlord"})
    List<Lease> findByLandlord(AppUser landlord);

    @EntityGraph(attributePaths = {"property", "tenant", "landlord"})
    List<Lease> findByProperty_Agent(AppUser agent);

    long countByTenantAndStatusIn(AppUser tenant, List<LeaseStatus> statuses);

    long countByLandlordAndStatusIn(AppUser landlord, List<LeaseStatus> statuses);

    long countByProperty_AgentAndStatusIn(AppUser agent, List<LeaseStatus> statuses);

    long countByStatus(LeaseStatus status);

    List<Lease> findTop10ByStatusOrderBySignedAtDesc(LeaseStatus status);
}
