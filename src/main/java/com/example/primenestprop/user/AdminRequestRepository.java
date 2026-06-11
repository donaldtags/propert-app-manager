package com.example.primenestprop.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRequestRepository extends JpaRepository<AdminRequest, Long> {
    Optional<AdminRequest> findByUserIdAndStatus(Long userId, AdminRequestStatus status);
}
