package com.example.primenestprop.ai;

import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * "How much should I charge?" - a market-comparables pricing suggestion built entirely from
 * PrimeNest's own live, verified-or-not listings (no external data source, no fabricated market
 * report). It widens the comparison pool step by step until it has a workable sample:
 *   1. Same city + suburb + exact bedroom count
 *   2. Same city + suburb, bedrooms +/- 1
 *   3. Same city (any suburb), exact bedroom count
 * and reports which basis it used and how many comparables it found, so a landlord can judge how
 * much to trust the suggestion.
 */
@Service
public class RentPricingService {
    private static final int MIN_COMPARABLES = 3;

    private final PropertyService properties;

    public RentPricingService(PropertyService properties) {
        this.properties = properties;
    }

    public AiDtos.RentSuggestionResponse suggest(ListingType listingType, String city, String suburb, int bedrooms) {
        List<Property> citywide = properties.search(listingType, city, null, null, null, null, null, null);

        List<Property> sameSuburbExactBeds = filter(citywide, suburb, bedrooms, bedrooms);
        if (sameSuburbExactBeds.size() >= MIN_COMPARABLES) {
            return build(sameSuburbExactBeds, "Same suburb, " + bedrooms + " bedroom(s)");
        }

        List<Property> sameSuburbNearBeds = filter(citywide, suburb, bedrooms - 1, bedrooms + 1);
        if (sameSuburbNearBeds.size() >= MIN_COMPARABLES) {
            return build(sameSuburbNearBeds, "Same suburb, " + (bedrooms - 1) + "-" + (bedrooms + 1) + " bedrooms");
        }

        List<Property> cityExactBeds = filter(citywide, null, bedrooms, bedrooms);
        if (cityExactBeds.size() >= MIN_COMPARABLES) {
            return build(cityExactBeds, "Same city (any suburb), " + bedrooms + " bedroom(s)");
        }

        // Not enough data anywhere - report whatever we found, even if below the confidence threshold.
        List<Property> best = !sameSuburbNearBeds.isEmpty() ? sameSuburbNearBeds
                : !cityExactBeds.isEmpty() ? cityExactBeds
                : citywide;
        if (best.isEmpty()) {
            return new AiDtos.RentSuggestionResponse(null, null, null, 0, "No comparable listings found in this city yet.");
        }
        return build(best, "Limited data (" + best.size() + " comparable listing(s) citywide) - suggestion is low-confidence");
    }

    private List<Property> filter(List<Property> properties, String suburb, int minBeds, int maxBeds) {
        return properties.stream()
                .filter(p -> suburb == null || suburb.equalsIgnoreCase(p.getSuburb()))
                .filter(p -> p.getBedrooms() >= minBeds && p.getBedrooms() <= maxBeds)
                .toList();
    }

    private AiDtos.RentSuggestionResponse build(List<Property> comparables, String basis) {
        List<BigDecimal> prices = comparables.stream()
                .map(Property::getPrice)
                .sorted()
                .toList();
        BigDecimal median = median(prices);
        BigDecimal low = prices.get(0);
        BigDecimal high = prices.get(prices.size() - 1);
        return new AiDtos.RentSuggestionResponse(median, low, high, comparables.size(), basis);
    }

    private BigDecimal median(List<BigDecimal> sortedPrices) {
        int size = sortedPrices.size();
        if (size % 2 == 1) {
            return sortedPrices.get(size / 2);
        }
        return sortedPrices.get(size / 2 - 1)
                .add(sortedPrices.get(size / 2))
                .divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
    }
}
