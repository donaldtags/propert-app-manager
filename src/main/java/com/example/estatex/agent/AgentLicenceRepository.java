package com.example.estatex.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentLicenceRepository extends JpaRepository<AgentLicence, Long> {
    Optional<AgentLicence> findByUserId(Long userId);

    List<AgentLicence> findByStatusOrderBySubmittedAtAsc(String status);
}
