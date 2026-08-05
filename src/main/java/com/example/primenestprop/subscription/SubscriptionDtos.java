package com.example.primenestprop.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SubscriptionDtos {
    private SubscriptionDtos() {
    }

    public record PlanSettingsResponse(
            SubscriptionPlan plan,
            BigDecimal monthlyPrice,
            String currency,
            Integer maxProperties,
            boolean escrowEnabled,
            boolean digitalLeasesEnabled,
            boolean maintenanceCoordinationEnabled,
            boolean rentRemindersEnabled,
            boolean aiPricingEnabled,
            boolean tenantPassportEnabled,
            boolean reportsEnabled,
            Instant updatedAt
    ) {
        public static PlanSettingsResponse from(SubscriptionPlanSettings s) {
            return new PlanSettingsResponse(
                    s.getPlan(), s.getMonthlyPrice(), s.getCurrency(), s.getMaxProperties(),
                    s.isEscrowEnabled(), s.isDigitalLeasesEnabled(), s.isMaintenanceCoordinationEnabled(),
                    s.isRentRemindersEnabled(), s.isAiPricingEnabled(), s.isTenantPassportEnabled(),
                    s.isReportsEnabled(), s.getUpdatedAt()
            );
        }
    }

    public record UpdatePlanSettingsRequest(
            @DecimalMin("0.00") BigDecimal monthlyPrice,
            @NotBlank String currency,
            Integer maxProperties,
            boolean escrowEnabled,
            boolean digitalLeasesEnabled,
            boolean maintenanceCoordinationEnabled,
            boolean rentRemindersEnabled,
            boolean aiPricingEnabled,
            boolean tenantPassportEnabled,
            boolean reportsEnabled
    ) {
    }

    public record SubscribeRequest(@NotNull SubscriptionPlan plan) {
    }

    public record SubscriptionResponse(
            SubscriptionPlan plan,
            SubscriptionStatus status,
            Instant currentPeriodEnd,
            Integer maxProperties,
            int activePropertyCount,
            List<SubscriptionFeature> enabledFeatures
    ) {
    }
}