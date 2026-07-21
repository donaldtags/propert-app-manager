package com.example.primenestprop.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccessRequestRepository extends JpaRepository<AdminAccessRequest, Long> {
    Optional<AdminAccessRequest> findByUserAndStatus(AppUser user, AdminRequestStatus status);

    List<AdminAccessRequest> findByStatusOrderByRequestedAtDesc(AdminRequestStatus status);

    long countByStatus(AdminRequestStatus status);
}
