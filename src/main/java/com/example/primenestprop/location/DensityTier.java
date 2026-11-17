package com.example.primenestprop.location;

/**
 * Market classification of a suburb. In Harare, LOW_DENSITY is roughly "the northern suburbs".
 * Used by the Intelligence dashboards to group suburbs; not a legal zoning classification.
 */
public enum DensityTier {
    HIGH_DENSITY,
    MEDIUM_DENSITY,
    LOW_DENSITY,
    PERI_URBAN,
    INDUSTRIAL,
    CBD
}
