package com.example.estatex.commission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionRepository extends JpaRepository<Commission, Long> {
    List<Commission> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
}
