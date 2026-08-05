package com.example.primenestprop.featured;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;

public final class FeaturedListingDtos {
    private FeaturedListingDtos() {
    }

    public record SettingsResponse(BigDecimal price, String currency, int durationDays, Instant updatedAt) {
        public static SettingsResponse from(FeaturedListingSettings settings) {
            return new SettingsResponse(settings.getPrice(), settings.getCurrency(), settings.getDurationDays(), settings.getUpdatedAt());
        }
    }

    public record UpdateSettingsRequest(
            @DecimalMin("0.01") BigDecimal price,
            @NotBlank String currency,
            @Min(1) int durationDays
    ) {
    }

    public record FeatureListingResponse(
            Long propertyId,
            boolean featured,
            Instant featuredUntil,
            Long paymentId,
            BigDecimal amountCharged,
            String currency
    ) {
    }
}