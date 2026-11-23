package com.example.estatex.kyc;

import com.example.estatex.user.AppUser;
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

    /** Other people's pending/approved submissions with the same ID number (ignoring spaces, dashes, slashes, case). */
    @org.springframework.data.jpa.repository.Query("select s from KycSubmission s join fetch s.user u "
            + "where u.id <> :userId and s.status in :statuses "
            + "and upper(replace(replace(replace(s.nationalIdNumber, '-', ''), ' ', ''), '/', '')) = :normalised")
    List<KycSubmission> findSameIdOnOtherAccounts(@org.springframework.data.repository.query.Param("normalised") String normalised,
                                                  @org.springframework.data.repository.query.Param("userId") Long userId,
                                                  @org.springframework.data.repository.query.Param("statuses") List<KycStatus> statuses);
}
