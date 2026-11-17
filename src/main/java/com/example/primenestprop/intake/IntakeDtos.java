package com.example.primenestprop.intake;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class IntakeDtos {
    private IntakeDtos() {
    }

    public record InspectionItem(@NotBlank @Size(max = 40) String key, @Size(max = 120) String label,
                                 InspectionCondition condition, @Size(max = 500) String note) {
    }

    public record SaveInspectionRequest(
            @NotNull @Size(max = 60) List<@Valid InspectionItem> items,
            @Min(0) @Max(10_000_000) Integer standSizeM2,
            @Min(0) @Max(100_000) Integer floorAreaM2,
            @Size(max = 4000) String notes,
            LocalDate inspectedOn,
            /** Version the device started from; a mismatch is refused (server wins). */
            Long baseVersion
    ) {
    }

    public record PhotoResponse(Long id, String clientId, String caption, String itemKey, String contentType, Instant createdAt) {
    }

    public record InspectionResponse(List<InspectionItem> items, Integer standSizeM2, Integer floorAreaM2, String notes,
                                     LocalDate inspectedOn, Long inspectedByUserId, Long version,
                                     List<PhotoResponse> photos, int ratedItems, int poorItems) {
    }

    public record Comparable(Long propertyId, String title, String suburb, String city, int bedrooms, int bathrooms,
                             long priceCents, String currency, String status, String titleType, boolean sameSuburb,
                             String source) {
    }

    public record PricingSuggestion(
            Long propertyId, long askingCents, String currency, int comparableCount, Long lowCents, Long medianCents,
            Long highCents, Long suggestedCents, boolean enoughData, String basis, List<Comparable> comparables,
            SavedRecommendation latest
    ) {
    }

    public record SavedRecommendation(Long id, long recommendedCents, Long lowCents, Long medianCents, Long highCents,
                                      int comparableCount, String rationale, Long createdByUserId, Instant createdAt) {
    }

    public record SaveRecommendationRequest(@NotNull @Positive Long recommendedCents, @Size(max = 2000) String rationale) {
    }
}
