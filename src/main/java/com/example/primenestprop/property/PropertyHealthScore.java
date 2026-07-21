package com.example.primenestprop.property;

/**
 * A 0-100 health score per property, broken into sub-scores. Every sub-score is derived from
 * real platform data - a sub-score is left null (and excluded from the overall average) rather
 * than invented when Homestead simply doesn't have that data yet (e.g. no neighbourhood profile
 * curated, or the landlord hasn't declared a water source).
 */
public record PropertyHealthScore(
        int overall,
        int verification,
        int maintenance,
        int transactionSafety,
        Integer solar,
        Integer water,
        int occupancy,
        Integer neighbourhood
) {
}
