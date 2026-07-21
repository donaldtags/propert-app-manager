package com.example.primenestprop.investment;

import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyPhoto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

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
            boolean vexEligible,
            @DecimalMin("0.01") BigDecimal totalUnits,
            String propertyType,
            String coverImageUrl,
            List<Long> propertyIds
    ) {
    }

    public record UpdateReitPropertiesRequest(@NotNull List<Long> propertyIds) {
    }

    public record CreateInvestmentRequest(
            @NotNull Long reitId,
            @DecimalMin("0.01") BigDecimal units,
            String currency
    ) {
    }

    public record SellInvestmentRequest(
            @NotNull Long reitId,
            @DecimalMin("0.01") BigDecimal units
    ) {
    }

    public record ReitPropertySummary(
            Long id,
            String title,
            String city,
            String suburb,
            String country,
            BigDecimal price,
            String currency,
            ListingType listingType,
            String coverPhotoUrl
    ) {
        public static ReitPropertySummary from(Property property) {
            String cover = property.getPhotos().stream()
                    .map(PropertyPhoto::getPhotoUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse(null);
            return new ReitPropertySummary(
                    property.getId(),
                    property.getTitle(),
                    property.getCity(),
                    property.getSuburb(),
                    property.getCountry(),
                    property.getPrice(),
                    property.getCurrency(),
                    property.getListingType(),
                    cover
            );
        }
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
            boolean active,
            BigDecimal totalUnits,
            BigDecimal availableUnits,
            String propertyType,
            String coverImageUrl,
            InvestmentScore investmentScore,
            List<ReitPropertySummary> properties
    ) {
        public static ReitResponse from(Reit reit, InvestmentScoreService scoreService) {
            return new ReitResponse(
                    reit.getId(),
                    reit.getName(),
                    reit.getDescription(),
                    reit.getMarket(),
                    reit.getUnitPrice(),
                    reit.getProjectedAnnualYield(),
                    reit.getRiskLevel(),
                    reit.isVexEligible(),
                    reit.isActive(),
                    reit.getTotalUnits(),
                    reit.getAvailableUnits(),
                    reit.getPropertyType(),
                    reit.getCoverImageUrl(),
                    scoreService.compute(reit),
                    reit.getProperties().stream().map(ReitPropertySummary::from).toList()
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
