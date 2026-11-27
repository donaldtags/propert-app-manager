package com.example.estatex.analytics.capture;

/** What a {@link PropertyEvent} records. One change to a listing can produce several events. */
public enum PropertyEventType {
    /** The listing's state on the day history began (written by migration V6). */
    BASELINE,
    CREATED,
    PRICE_CHANGED,
    STATUS_CHANGED,
    VERIFICATION_CHANGED,
    /** Rent ↔ sale ↔ short stay. */
    LISTING_TYPE_CHANGED,
    /** City, suburb, country or map pin. */
    LOCATION_CHANGED,
    /** Bedrooms, bathrooms, category or floor area. */
    CHARACTERISTICS_CHANGED
}
