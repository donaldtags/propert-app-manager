package com.example.primenestprop.investment;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Computes a 0-100 Investment Score for a REIT from its own real fields: projected annual yield,
 * risk level, and demand as measured by how much of the offering has actually sold. This is a
 * deterministic, explainable formula - not a predictive ML model - since Homestead has no
 * historical performance dataset yet to train one on; presenting anything else as "AI-predicted"
 * would be fabricating confidence the platform doesn't have.
 */
@Service
public class InvestmentScoreService {
    private static final BigDecimal YIELD_CEILING = new BigDecimal("15.00");

    public InvestmentScore compute(Reit reit) {
        int yieldScore = yieldScore(reit.getProjectedAnnualYield());
        int riskScore = riskScore(reit.getRiskLevel());
        Integer sellThrough = sellThroughPercent(reit);
        int demandScore = sellThrough == null ? 60 : Math.min(100, sellThrough + 20);

        int overall = (int) Math.round(yieldScore * 0.5 + riskScore * 0.3 + demandScore * 0.2);

        return new InvestmentScore(
                Math.max(0, Math.min(100, overall)),
                yieldTier(reit.getProjectedAnnualYield()),
                reit.getRiskLevel(),
                demandTier(sellThrough),
                sellThrough
        );
    }

    private int yieldScore(BigDecimal projectedAnnualYield) {
        if (projectedAnnualYield == null) {
            return 50;
        }
        BigDecimal ratio = projectedAnnualYield.divide(YIELD_CEILING, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        return Math.max(0, Math.min(100, ratio.intValue()));
    }

    private String yieldTier(BigDecimal projectedAnnualYield) {
        if (projectedAnnualYield == null) return "Unknown";
        if (projectedAnnualYield.compareTo(new BigDecimal("11")) >= 0) return "High";
        if (projectedAnnualYield.compareTo(new BigDecimal("8")) >= 0) return "Moderate";
        return "Low";
    }

    private int riskScore(String riskLevel) {
        if (riskLevel == null) return 50;
        return switch (riskLevel.toUpperCase()) {
            case "LOW" -> 100;
            case "MEDIUM" -> 65;
            case "HIGH" -> 35;
            default -> 50;
        };
    }

    private Integer sellThroughPercent(Reit reit) {
        if (reit.getTotalUnits() == null || reit.getTotalUnits().signum() <= 0) {
            return null;
        }
        BigDecimal sold = reit.getUnitsSold() == null ? BigDecimal.ZERO : reit.getUnitsSold();
        return sold.divide(reit.getTotalUnits(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .intValue();
    }

    private String demandTier(Integer sellThroughPercent) {
        if (sellThroughPercent == null) return "Unknown";
        if (sellThroughPercent >= 60) return "Strong";
        if (sellThroughPercent >= 25) return "Moderate";
        return "Early";
    }
}
