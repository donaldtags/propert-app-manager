package com.example.estatex.analytics.market;

import com.example.estatex.analytics.metrics.ListingState;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * The filters every chart on a market dashboard shares. One object, one {@link #matches} rule,
 * so a filter can never apply to one chart and not another.
 *
 * <p>Bedrooms: an exact count, except 5 means "5 or more". Price range: applies to the asking
 * price of listings; for transactions (leases, sales) it applies to the agreed amount instead.
 */
public record MarketFilter(String listingType, String country, String city, String suburb, String category,
                           Integer bedrooms, BigDecimal minPrice, BigDecimal maxPrice) {

    public MarketFilter withType(String type) {
        return new MarketFilter(type, country, city, suburb, category, bedrooms, minPrice, maxPrice);
    }

    /** The same place and kind of home, ignoring price (used where the price means something else). */
    public MarketFilter withoutPrice() {
        return new MarketFilter(listingType, country, city, suburb, category, bedrooms, null, null);
    }

    public boolean matches(ListingState s) {
        return place(s) && priceIn(s.price());
    }

    /** Everything except price. */
    public boolean place(ListingState s) {
        if (s == null) return false;
        if (listingType != null && !listingType.equals(s.listingType())) return false;
        if (!same(country, s.country()) || !same(city, s.city()) || !same(suburb, s.suburb())) return false;
        if (category != null && !category.equals(s.category())) return false;
        if (bedrooms != null) {
            if (s.bedrooms() == null) return false;
            if (bedrooms >= 5 ? s.bedrooms() < 5 : !bedrooms.equals(s.bedrooms())) return false;
        }
        return true;
    }

    public boolean priceIn(BigDecimal price) {
        if (minPrice == null && maxPrice == null) return true;
        if (price == null) return false;
        if (minPrice != null && price.compareTo(minPrice) < 0) return false;
        return maxPrice == null || price.compareTo(maxPrice) <= 0;
    }

    private static boolean same(String wanted, String actual) {
        if (wanted == null) return true;
        return actual != null && actual.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                .equals(wanted.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }
}
