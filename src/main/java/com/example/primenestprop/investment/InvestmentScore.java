package com.example.primenestprop.investment;

/**
 * A 0-100 algorithmic investment score for a REIT, computed from its own real, admin-entered
 * fields (projected yield, risk level, unit sell-through rate) - never a fabricated number.
 */
public record InvestmentScore(
        int overall,
        String yieldTier,
        String riskTier,
        String demandTier,
        Integer sellThroughPercent
) {
}
