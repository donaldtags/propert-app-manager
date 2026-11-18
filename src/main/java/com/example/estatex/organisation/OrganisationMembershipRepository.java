package com.example.estatex.organisation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationMembershipRepository extends JpaRepository<OrganisationMembership, Long> {
    Optional<OrganisationMembership> findByOrganisationIdAndUserId(Long organisationId, Long userId);

    @EntityGraph(attributePaths = "organisation")
    List<OrganisationMembership> findByUserIdAndActiveTrue(Long userId);

    @EntityGraph(attributePaths = "user")
    List<OrganisationMembership> findByOrganisationIdOrderByCreatedAtAsc(Long organisationId);

    @EntityGraph(attributePaths = "user")
    List<OrganisationMembership> findByOrganisationIdAndActiveTrue(Long organisationId);
}
