package com.example.estatex.analytics.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which slice of the market a figure describes. A null field means "all". Text is trimmed and
 * spaces collapsed; grouping ignores case, so "Avondale" and "avondale " are one suburb.
 */
public record Dims(String listingType, String country, String city, String suburb, String category, String bedrooms) {
    public static final Dims ALL = new Dims(null, null, null, null, null, null);

    public static Dims of(ListingState s) {
        return new Dims(s.listingType(), clean(s.country()), clean(s.city()), clean(s.suburb()), s.category(),
                bedroomBand(s.bedrooms()));
    }

    public static Dims of(String listingType, String country, String city, String suburb, String category, Integer bedrooms) {
        return new Dims(listingType, clean(country), clean(city), clean(suburb), category, bedroomBand(bedrooms));
    }

    public String key() {
        return String.join("|", part(listingType), part(country), part(city), part(suburb), part(category), part(bedrooms));
    }

    /**
     * The slices a single observation is counted in. Hourly figures only get the coarse slices:
     * an hour is too short for suburb-level numbers to mean anything.
     */
    public List<Dims> rollups(boolean includeAllTypes, boolean coarseOnly) {
        List<Dims> out = new ArrayList<>();
        if (includeAllTypes) out.add(ALL);
        if (listingType == null) return out;
        out.add(new Dims(listingType, null, null, null, null, null));
        if (city != null) out.add(new Dims(listingType, country, city, null, null, null));
        if (coarseOnly) return out;
        if (city != null && suburb != null) out.add(new Dims(listingType, country, city, suburb, null, null));
        if (category != null) out.add(new Dims(listingType, null, null, null, category, null));
        if (bedrooms != null) out.add(new Dims(listingType, null, null, null, null, bedrooms));
        if (category != null && bedrooms != null) out.add(new Dims(listingType, null, null, null, category, bedrooms));
        if (city != null && category != null) out.add(new Dims(listingType, country, city, null, category, null));
        return out;
    }

    static String bedroomBand(Integer bedrooms) {
        if (bedrooms == null) return null;
        if (bedrooms <= 0) return "0";
        return bedrooms >= 5 ? "5+" : String.valueOf(bedrooms);
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String part(String s) {
        return s == null ? "*" : s.toLowerCase(Locale.ROOT);
    }
}
