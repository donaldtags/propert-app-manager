package com.example.primenestprop.investment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class InvestmentDtos {
    private InvestmentDtos() {
    }

    public record CreateReitRequest(
            @NotBlank String name,
            String description,
            String market,
            @DecimalMin("0.01") BigDecimal unitPrice,
            BigDecimal projectedAnnualYield,
            String riskLevel,
            boolean vexEligible
    ) {
    }

    public record CreateInvestmentRequest(
            @NotNull Long investorId,
            @NotNull Long reitId,
            @DecimalMin("0.01") BigDecimal units,
            String currency
    ) {
    }

    public record ReitResponse(
            Long id,
            String name,
            String description,
            String market,
            BigDecimal unitPrice,
            BigDecimal projectedAnnualYield,
            String riskLevel,
            boolean vexEligible,
            boolean active
    ) {
        public static ReitResponse from(Reit reit) {
            return new ReitResponse(
                    reit.getId(),
                    reit.getName(),
                    reit.getDescription(),
                    reit.getMarket(),
                    reit.getUnitPrice(),
                    reit.getProjectedAnnualYield(),
                    reit.getRiskLevel(),
                    reit.isVexEligible(),
                    reit.isActive()
            );
        }
    }

    public record InvestmentResponse(
            Long id,
            Long investorId,
            Long reitId,
            InvestmentStatus status,
            BigDecimal units,
            BigDecimal amount,
            String currency
    ) {
        public static InvestmentResponse from(Investment investment) {
            return new InvestmentResponse(
                    investment.getId(),
                    investment.getInvestor().getId(),
                    investment.getReit().getId(),
                    investment.getStatus(),
                    investment.getUnits(),
                    investment.getAmount(),
                    investment.getCurrency()
            );
        }
    }
}
