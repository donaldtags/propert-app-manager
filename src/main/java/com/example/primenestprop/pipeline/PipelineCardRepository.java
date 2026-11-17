package com.example.primenestprop.pipeline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineCardRepository extends JpaRepository<PipelineCard, Long> {
    Optional<PipelineCard> findByClientId(String clientId);

    List<PipelineCard> findByOrganisationIdOrderByStageChangedAtDesc(Long organisationId);

    List<PipelineCard> findByOrganisationIdAndOwnerContactIdOrderByCreatedAtDesc(Long organisationId, Long contactId);
}
