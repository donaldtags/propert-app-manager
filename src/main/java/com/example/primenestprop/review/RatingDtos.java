package com.example.primenestprop.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class RatingDtos {
    private RatingDtos() {
    }

    public record CreateLandlordRatingRequest(
            @NotNull Long landlordId,
            @NotNull Long tenantId,
            Long propertyId,
            Long leaseId,
            @Min(1) @Max(5) int rating,
            String comment
    ) {
    }

    public record LandlordRatingResponse(
            Long id,
            Long landlordId,
            Long tenantId,
            String tenantName,
            Long propertyId,
            Long leaseId,
            int rating,
            String comment,
            Instant createdAt
    ) {
        public static LandlordRatingResponse from(LandlordRating rating) {
            return new LandlordRatingResponse(
                    rating.getId(),
                    rating.getLandlord().getId(),
                    rating.getTenant().getId(),
                    rating.getTenant().getFullName(),
                    rating.getProperty() == null ? null : rating.getProperty().getId(),
                    rating.getLease() == null ? null : rating.getLease().getId(),
                    rating.getRating(),
                    rating.getComment(),
                    rating.getCreatedAt()
            );
        }
    }
}
