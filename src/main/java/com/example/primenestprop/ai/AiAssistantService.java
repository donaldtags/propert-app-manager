package com.example.primenestprop.ai;

import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantService {
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:under|below|less than)\\s*\\$?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BED_PATTERN = Pattern.compile("(\\d+)\\s*(?:bed|bedroom)", Pattern.CASE_INSENSITIVE);
    private final PropertyService properties;

    public AiAssistantService(PropertyService properties) {
        this.properties = properties;
    }

    public List<Property> search(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        ListingType type = normalized.contains("buy") || normalized.contains("sale") ? ListingType.SALE : ListingType.RENT;
        BigDecimal maxPrice = extractDecimal(PRICE_PATTERN.matcher(query));
        Integer bedrooms = extractInteger(BED_PATTERN.matcher(query));
        String suburb = extractKnownSuburb(normalized);
        return properties.search(type, null, suburb, maxPrice, bedrooms);
    }

    public String answer(String query, int matchCount) {
        if (matchCount == 0) {
            return "I could not find matching verified listings yet. Try widening the suburb, price, or bedroom filters.";
        }
        return "I found " + matchCount + " matching listings. Verified properties are ranked first, with escrow-friendly options included where available.";
    }

    private BigDecimal extractDecimal(Matcher matcher) {
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private Integer extractInteger(Matcher matcher) {
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String extractKnownSuburb(String query) {
        List<String> suburbs = List.of("borrowdale", "avondale", "mount pleasant", "greendale", "newlands", "belvedere", "mabelreign", "uz");
        return suburbs.stream().filter(query::contains).findFirst().orElse(null);
    }
}
