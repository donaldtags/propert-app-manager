package com.example.estatex.analytics.metrics;

import java.math.BigDecimal;

/** A listing's state at one moment, rebuilt from its latest {@code property_events} row. */
public record ListingState(long propertyId, String listingType, String status, String verificationStatus,
                           String country, String city, String suburb, String category, Integer bedrooms,
                           Integer bathrooms, Integer floorAreaM2, BigDecimal price, String currency,
                           BigDecimal latitude, BigDecimal longitude, Long organisationId) {

    public boolean available() {
        return "AVAILABLE".equals(status);
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
