package com.example.primenestprop.kyc;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, Long> {

    @EntityGraph(attributePaths = {"user", "documents"})
    List<KycSubmission> findByUserOrderBySubmittedAtDesc(AppUser user);

    Optional<KycSubmission> findByUserAndStatus(AppUser user, KycStatus status);

    @EntityGraph(attributePaths = {"user", "documents"})
    List<KycSubmission> findByStatusOrderBySubmittedAtDesc(KycStatus status);

    @EntityGraph(attributePaths = {"user", "documents"})
    List<KycSubmission> findAllByOrderBySubmittedAtDesc();

    long countByStatus(KycStatus status);

    @EntityGraph(attributePaths = {"user", "documents"})
    Optional<KycSubmission> findWithDetailsById(Long id);

    List<KycSubmission> findTop10ByStatusInOrderByReviewedAtDesc(List<KycStatus> statuses);
}
