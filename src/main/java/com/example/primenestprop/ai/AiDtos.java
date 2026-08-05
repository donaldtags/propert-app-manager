package com.example.primenestprop.ai;

import com.example.primenestprop.property.PropertyDtos.PropertyResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class AiDtos {
    private AiDtos() {
    }

    public record ConversationTurn(@NotBlank String role, @NotBlank String content) {
    }

    public record AiPropertyQuery(@NotBlank String query, List<ConversationTurn> history) {
    }

    public record AiPropertyAnswer(String answer, List<PropertyResponse> matches, boolean aiPowered) {
    }

    public record AffordabilityRequest(
            @NotNull @DecimalMin("0.01") BigDecimal grossMonthlyIncome,
            BigDecimal existingMonthlyDebt,
            Long propertyId
    ) {
    }

    public record AffordabilityResponse(
            BigDecimal maxByRentToIncomeRule,
            BigDecimal maxByDebtToIncomeRule,
            BigDecimal recommendedMaxRent,
            BigDecimal propertyRent,
            Boolean fitsRecommendedBudget,
            String note
    ) {
    }

    public record RentSuggestionResponse(
            BigDecimal suggestedPrice,
            BigDecimal priceRangeLow,
            BigDecimal priceRangeHigh,
            int comparableCount,
            String basis
    ) {
    }

    public record HomeAssistantRequest(@NotBlank String message, List<ConversationTurn> history) {
    }

    public record HomeAssistantResponse(String answer, boolean aiPowered) {
    }
}
