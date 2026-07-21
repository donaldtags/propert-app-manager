package com.example.primenestprop.vendor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public final class ServiceBookingDtos {
    private ServiceBookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull Long vendorId,
            Long propertyId,
            LocalDate preferredDate,
            String notes
    ) {
    }

    public record FeedbackRequest(@Min(1) @Max(5) @NotNull Integer rating, String comment) {
    }

    public record ServiceBookingResponse(
            Long id,
            Long vendorId,
            String vendorBusinessName,
            VendorCategory vendorCategory,
            Long propertyId,
            String propertyTitle,
            Long requesterId,
            String requesterName,
            BookingStatus status,
            LocalDate preferredDate,
            String notes,
            Instant createdAt,
            Instant completedAt,
            Integer feedbackRating,
            String feedbackComment
    ) {
        public static ServiceBookingResponse from(ServiceBooking booking) {
            return new ServiceBookingResponse(
                    booking.getId(),
                    booking.getVendor().getId(),
                    booking.getVendor().getBusinessName(),
                    booking.getVendor().getCategory(),
                    booking.getProperty() == null ? null : booking.getProperty().getId(),
                    booking.getProperty() == null ? null : booking.getProperty().getTitle(),
                    booking.getRequester().getId(),
                    booking.getRequester().getFullName(),
                    booking.getStatus(),
                    booking.getPreferredDate(),
                    booking.getNotes(),
                    booking.getCreatedAt(),
                    booking.getCompletedAt(),
                    booking.getFeedbackRating(),
                    booking.getFeedbackComment()
            );
        }
    }
}
