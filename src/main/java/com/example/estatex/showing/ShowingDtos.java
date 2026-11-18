package com.example.estatex.showing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ShowingDtos {
    private ShowingDtos() {
    }

    public record ScheduleRequest(
            @Size(max = 40) String clientId,
            @NotNull Long contactId,
            @NotNull Instant scheduledAt,
            @Min(10) @Max(240) Integer durationMinutes
    ) {
    }

    public record OutcomeRequest(
            @NotNull ShowingStatus status,
            BuyerInterest interest,
            PriceOpinion priceOpinion,
            @Size(max = 2000) String feedback
    ) {
    }

    public record ShowingResponse(Long id, String clientId, Long propertyId, String propertyTitle, String propertySuburb,
                                  Long contactId, String contactName, String contactPhone, Long negotiatorUserId,
                                  Instant scheduledAt, int durationMinutes, ShowingStatus status, BuyerInterest interest,
                                  PriceOpinion priceOpinion, String feedback, Instant completedAt, Long version) {
    }
}
