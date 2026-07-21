package com.example.primenestprop.kyc;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    List<KycDocument> findBySubmissionOrderByUploadedAtAsc(KycSubmission submission);

    Optional<KycDocument> findByIdAndSubmission(Long id, KycSubmission submission);
}
