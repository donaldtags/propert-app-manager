package com.example.primenestprop.lease;

import java.time.Instant;

public final class LeaseDocumentDtos {
    private LeaseDocumentDtos() {
    }

    public record LeaseDocumentResponse(
            Long id,
            Long leaseId,
            Long userId,
            LeaseDocumentType documentType,
            String fileName,
            String contentType,
            Long fileSize,
            LeaseDocumentStatus status,
            Instant uploadedAt,
            Instant reviewedAt,
            Long reviewedBy,
            String reviewNote
    ) {
        public static LeaseDocumentResponse from(LeaseDocument document) {
            return new LeaseDocumentResponse(
                    document.getId(),
                    document.getLease().getId(),
                    document.getUser().getId(),
                    document.getDocumentType(),
                    document.getFileName(),
                    document.getContentType(),
                    document.getFileSize(),
                    document.getStatus(),
                    document.getUploadedAt(),
                    document.getReviewedAt(),
                    document.getReviewedBy(),
                    document.getReviewNote()
            );
        }
    }
}
