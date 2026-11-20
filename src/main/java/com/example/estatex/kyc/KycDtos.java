package com.example.estatex.kyc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class KycDtos {
    private KycDtos() {
    }

    /**
     * confirmedChecks: what the reviewer confirmed by eye (see IdChecks.MANUAL_CHECKS). All of them
     * are required to approve.
     */
    public record ReviewKycRequest(KycStatus status, String reviewNote, java.util.List<String> confirmedChecks) {
    }

    public record KycDocumentResponse(
            Long id,
            KycDocumentType documentType,
            String fileName,
            String contentType,
            Long fileSize,
            Instant uploadedAt
    ) {
        public static KycDocumentResponse from(KycDocument document) {
            return new KycDocumentResponse(
                    document.getId(),
                    document.getDocumentType(),
                    document.getFileName(),
                    document.getContentType(),
                    document.getFileSize(),
                    document.getUploadedAt()
            );
        }
    }

    public record KycSubmissionResponse(
            Long id,
            Long userId,
            String userFullName,
            String userEmail,
            String legalFullName,
            LocalDate dateOfBirth,
            String nationalIdNumber,
            IdDocumentType idDocumentType,
            KycStatus status,
            Instant submittedAt,
            Instant reviewedAt,
            Long reviewedBy,
            String reviewNote,
            List<KycDocumentResponse> documents,
            /** Reviewer-only: automatic ID checks (format, age, name, ID used on another account). */
            List<IdChecks.Check> automatedChecks
    ) {
        public static KycSubmissionResponse from(KycSubmission submission) {
            return from(submission, null);
        }

        public static KycSubmissionResponse from(KycSubmission submission, List<IdChecks.Check> checks) {
            return new KycSubmissionResponse(
                    submission.getId(),
                    submission.getUser().getId(),
                    submission.getUser().getFullName(),
                    submission.getUser().getEmail(),
                    submission.getLegalFullName(),
                    submission.getDateOfBirth(),
                    submission.getNationalIdNumber(),
                    submission.getIdDocumentType(),
                    submission.getStatus(),
                    submission.getSubmittedAt(),
                    submission.getReviewedAt(),
                    submission.getReviewedBy(),
                    submission.getReviewNote(),
                    submission.getDocuments().stream().map(KycDocumentResponse::from).toList(),
                    checks
            );
        }
    }
}
