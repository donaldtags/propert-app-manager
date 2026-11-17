package com.example.primenestprop.property;

/**
 * How ownership of a property is held. CESSION means the seller holds rights ceded to them
 * (common on unregistered/developer stands), not a registered title: a materially higher risk for
 * a buyer, shown everywhere as a "Cession" risk badge.
 */
public enum TitleType {
    TITLE_DEED,
    SECTIONAL_TITLE,
    CESSION
}
