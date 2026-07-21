package com.example.primenestprop.application;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class RentalApplicationDtos {
    private RentalApplicationDtos() {
    }

    public record CreateApplicationRequest(
            @NotNull Long propertyId,
            LocalDate desiredMoveInDate,
            @DecimalMin("0.0") BigDecimal monthlyIncome,
            String message,
            boolean saveAsDraft
    ) {
    }

    public record ReviewApplicationRequest(@NotNull ApplicationStatus status, String reviewNote) {
    }

    public record ApplicationResponse(
            Long id,
            Long propertyId,
            String propertyTitle,
            String propertyPhotoUrl,
            Long applicantId,
            String applicantName,
            ApplicationStatus status,
            LocalDate desiredMoveInDate,
            BigDecimal monthlyIncome,
            String message,
            String reviewNote,
            boolean identityVerified,
            Instant createdAt,
            Instant submittedAt,
            Instant reviewedAt
    ) {
        public static ApplicationResponse from(RentalApplication application) {
            String photoUrl = application.getProperty().getPhotos().isEmpty()
                    ? null
                    : application.getProperty().getPhotos().get(0).getPhotoUrl();
            return new ApplicationResponse(
                    application.getId(),
                    application.getProperty().getId(),
                    application.getProperty().getTitle(),
                    photoUrl,
                    application.getApplicant().getId(),
                    application.getApplicant().getFullName(),
                    application.getStatus(),
                    application.getDesiredMoveInDate(),
                    application.getMonthlyIncome(),
                    application.getMessage(),
                    application.getReviewNote(),
                    application.getApplicant().isIdentityVerified(),
                    application.getCreatedAt(),
                    application.getSubmittedAt(),
                    application.getReviewedAt()
            );
        }
    }
}
