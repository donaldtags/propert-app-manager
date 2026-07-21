package com.example.primenestprop.viewing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public final class ViewingDtos {
    private ViewingDtos() {
    }

    public record CreateViewingRequest(
            @NotNull Long propertyId,
            @NotNull ViewingMode mode,
            LocalDate preferredDate,
            String preferredTime,
            String notes
    ) {
    }

    public record ConfirmViewingRequest(String videoCallLink) {
    }

    public record CheckInRequest(@NotNull String code) {
    }

    public record FeedbackRequest(@Min(1) @Max(5) @NotNull Integer rating, String comment) {
    }

    public record ViewingResponse(
            Long id,
            Long propertyId,
            String propertyTitle,
            Long requesterId,
            String requesterName,
            ViewingMode mode,
            ViewingStatus status,
            LocalDate preferredDate,
            String preferredTime,
            String notes,
            String videoCallLink,
            String checkInCode,
            String qrCodeDataUri,
            Instant createdAt,
            Instant confirmedAt,
            Instant completedAt,
            Integer feedbackRating,
            String feedbackComment
    ) {
        public static ViewingResponse from(ViewingRequest viewing, boolean includeCheckInMaterials) {
            return new ViewingResponse(
                    viewing.getId(),
                    viewing.getProperty().getId(),
                    viewing.getProperty().getTitle(),
                    viewing.getRequester().getId(),
                    viewing.getRequester().getFullName(),
                    viewing.getMode(),
                    viewing.getStatus(),
                    viewing.getPreferredDate(),
                    viewing.getPreferredTime(),
                    viewing.getNotes(),
                    viewing.getVideoCallLink(),
                    includeCheckInMaterials ? viewing.getCheckInCode() : null,
                    includeCheckInMaterials && viewing.getStatus() == ViewingStatus.CONFIRMED
                            ? QrCodeGenerator.toDataUri("HOMESTEAD-VIEWING:" + viewing.getId() + ":" + viewing.getCheckInCode(), 220)
                            : null,
                    viewing.getCreatedAt(),
                    viewing.getConfirmedAt(),
                    viewing.getCompletedAt(),
                    viewing.getFeedbackRating(),
                    viewing.getFeedbackComment()
            );
        }
    }
}
