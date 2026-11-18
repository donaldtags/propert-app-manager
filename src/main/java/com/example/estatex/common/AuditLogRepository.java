package com.example.estatex.common;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop10ByOrderByCreatedAtDesc();

    List<AuditLog> findByOrganisationIdOrderByCreatedAtDescIdDesc(Long organisationId, Pageable pageable);
}
