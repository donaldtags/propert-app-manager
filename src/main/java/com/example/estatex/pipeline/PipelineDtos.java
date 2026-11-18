package com.example.estatex.pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class PipelineDtos {
    private PipelineDtos() {
    }

    public record CreateCardRequest(
            @Size(max = 40) String clientId,
            @NotBlank @Size(max = 200) String title,
            @NotNull Long ownerContactId,
            Long propertyId,
            Long negotiatorUserId,
            @Positive Long estimatedValueCents,
            LocalDate nextActionOn,
            @Size(max = 300) String nextAction,
            @Size(max = 2000) String notes
    ) {
    }

    public record UpdateCardRequest(
            @Size(max = 200) String title,
            Long propertyId,
            @Positive Long estimatedValueCents,
            LocalDate nextActionOn,
            @Size(max = 300) String nextAction,
            @Size(max = 2000) String notes
    ) {
    }

    public record MoveRequest(@NotNull PipelineStage stage, @Size(max = 300) String lostReason) {
    }

    public record CardResponse(Long id, String clientId, String title, PipelineStage stage, Long ownerContactId,
                               String ownerName, Long propertyId, String propertyTitle, Long negotiatorUserId,
                               String negotiatorName, Long estimatedValueCents, String currency, String lostReason,
                               LocalDate nextActionOn, String nextAction, String notes, Instant stageChangedAt,
                               long daysInStage, Instant createdAt, Long version) {
    }
}
