package com.example.primenestprop.lease;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public final class LeaseActionDtos {
    private LeaseActionDtos() {
    }

    public record CreateLeaseActionRequest(
            @NotNull LeaseActionType type,
            LocalDate proposedEndDate,
            String note
    ) {
    }

    public record ReviewLeaseActionRequest(@NotNull LeaseActionStatus status, String reviewNote) {
    }

    public record LeaseActionResponse(
            Long id,
            Long leaseId,
            Long propertyId,
            Long requestedById,
            String requestedByName,
            LeaseActionType type,
            LeaseActionStatus status,
            LocalDate proposedEndDate,
            String note,
            String reviewNote,
            Instant createdAt,
            Instant resolvedAt
    ) {
        public static LeaseActionResponse from(LeaseActionRequest request) {
            return new LeaseActionResponse(
                    request.getId(),
                    request.getLease().getId(),
                    request.getLease().getProperty().getId(),
                    request.getRequestedBy().getId(),
                    request.getRequestedBy().getFullName(),
                    request.getType(),
                    request.getStatus(),
                    request.getProposedEndDate(),
                    request.getNote(),
                    request.getReviewNote(),
                    request.getCreatedAt(),
                    request.getResolvedAt()
            );
        }
    }
}
