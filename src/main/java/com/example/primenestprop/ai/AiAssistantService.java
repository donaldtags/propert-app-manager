package com.example.primenestprop.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.property.WaterSource;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantService {
    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:under|below|less than)\\s*\\$?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BED_PATTERN = Pattern.compile("(\\d+)\\s*(?:bed|bedroom)", Pattern.CASE_INSENSITIVE);
    private static final List<String> KNOWN_SUBURBS = List.of(
            "borrowdale", "avondale", "mount pleasant", "greendale", "newlands", "belvedere", "mabelreign", "uz");

    private final PropertyService properties;
    private final AnthropicClient client;
    private final Model model;

    public AiAssistantService(PropertyService properties, AiAnthropicConfig anthropicConfig) {
        this.properties = properties;
        this.client = anthropicConfig.client();
        this.model = anthropicConfig.model();
    }

    /** Whether this instance is backed by a real Claude call or the keyword-matching fallback. */
    public boolean aiPowered() {
        return client != null;
    }

    public record SearchFilters(
            @JsonPropertyDescription("RENT to rent, SALE to buy, SHORT_STAY for a short/holiday stay. Null if unclear.")
            ListingType listingType,
            @JsonPropertyDescription("City name if the query names one, otherwise null.") String city,
            @JsonPropertyDescription("Suburb or neighbourhood if the query names one, otherwise null.") String suburb,
            @JsonPropertyDescription("Minimum price in USD if the query implies a floor, otherwise null.") BigDecimal minPrice,
            @JsonPropertyDescription("Maximum price in USD if the query implies a ceiling, otherwise null.") BigDecimal maxPrice,
            @JsonPropertyDescription("Minimum bedroom count if mentioned, otherwise null.") Integer bedrooms,
            @JsonPropertyDescription("Minimum bathroom count if mentioned, otherwise null.") Integer bathrooms,
            @JsonPropertyDescription("True only if the query explicitly asks for diaspora/remote-friendly properties, otherwise null.")
            Boolean diasporaFriendly,
            @JsonPropertyDescription("True only if the query explicitly asks for solar power, otherwise null.") Boolean solarInstalled,
            @JsonPropertyDescription("True only if the query explicitly asks for backup power/generator, otherwise null.") Boolean backupPower,
            @JsonPropertyDescription("MUNICIPAL, BOREHOLE, WELL, TANKER, or OTHER if the query names a specific water source, otherwise null.")
            WaterSource waterSource,
            @JsonPropertyDescription("True only if the query explicitly asks for a furnished property, otherwise null.") Boolean furnished,
            @JsonPropertyDescription("True only if the query explicitly asks for internet/fibre/wifi, otherwise null.") Boolean internetAvailable,
            @JsonPropertyDescription("True only if the query explicitly asks for security/a guarded/gated property, otherwise null.") Boolean securityFeatures,
            @JsonPropertyDescription("True only if the query explicitly asks for parking, otherwise null.") Boolean parkingAvailable,
            @JsonPropertyDescription("True only if the query explicitly asks for a pet-friendly property, otherwise null.") Boolean petsAllowed,
            @JsonPropertyDescription("True only if the query explicitly asks for verified-only listings, otherwise null.") Boolean verifiedOnly,
            @JsonPropertyDescription("True only if the query explicitly asks for escrow-protected listings, otherwise null.") Boolean escrowAvailable
    ) {
    }

    public List<Property> search(String query) {
        if (client != null) {
            try {
                return searchWithClaude(query);
            } catch (Exception ex) {
                log.warn("Claude search-filter extraction failed, falling back to keyword search", ex);
            }
        }
        return searchWithKeywords(query);
    }

    public String answer(String query, List<Property> matches) {
        if (client != null) {
            try {
                return answerWithClaude(query, matches);
            } catch (Exception ex) {
                log.warn("Claude answer generation failed, falling back to canned response", ex);
            }
        }
        return cannedAnswer(matches.size());
    }

    private List<Property> searchWithClaude(String query) {
        StructuredMessageCreateParams<SearchFilters> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system("Extract structured real-estate search filters from the user's natural-language query about "
                        + "properties in Zimbabwe. Only set a field when the query genuinely implies it; leave every "
                        + "other field null rather than guessing.")
                .outputConfig(SearchFilters.class)
                .addUserMessage(query)
                .build();

        SearchFilters filters = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(structured -> structured.text())
                .orElseThrow(() -> new IllegalStateException("Claude returned no structured search filters"));

        return properties.search(filters.listingType(), filters.city(), filters.suburb(), filters.minPrice(),
                filters.maxPrice(), filters.bedrooms(), filters.bathrooms(), filters.diasporaFriendly(),
                filters.solarInstalled(), filters.backupPower(), filters.waterSource(), filters.furnished(),
                filters.internetAvailable(), filters.securityFeatures(), filters.parkingAvailable(),
                filters.petsAllowed(), filters.verifiedOnly(), filters.escrowAvailable());
    }

    private String answerWithClaude(String query, List<Property> matches) {
        String system;
        String userMessage;
        if (matches.isEmpty()) {
            system = "You are PrimeNest's property search assistant for Zimbabwe. The search below returned zero "
                    + "verified listings. In one short, friendly sentence, suggest the user widen their suburb, "
                    + "price, or bedroom criteria. Do not invent any properties.";
            userMessage = "User query: " + query;
        } else {
            system = "You are PrimeNest's property search assistant for Zimbabwe. You are given the ACTUAL verified "
                    + "listings that matched the user's query below. Write a short, friendly 2-3 sentence summary for "
                    + "the user. Reference only the listings given here - never invent a property, price, or detail "
                    + "that isn't in this list.";
            String listingsSummary = matches.stream()
                    .limit(10)
                    .map(p -> "- %s in %s, %s: %d bed / %d bath, %s %s (%s)".formatted(
                            p.getTitle(), p.getSuburb(), p.getCity(), p.getBedrooms(), p.getBathrooms(),
                            p.getPrice(), p.getCurrency(), p.getVerificationStatus()))
                    .collect(Collectors.joining("\n"));
            userMessage = "User query: " + query + "\n\nMatching listings:\n" + listingsSummary;
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(512L)
                .system(system)
                .addUserMessage(userMessage)
                .build();

        return textOf(client.messages().create(params));
    }

    private static String textOf(Message message) {
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining("\n"))
                .trim();
    }

    private List<Property> searchWithKeywords(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        ListingType type = normalized.contains("buy") || normalized.contains("sale") ? ListingType.SALE : ListingType.RENT;
        BigDecimal maxPrice = extractDecimal(PRICE_PATTERN.matcher(query));
        Integer bedrooms = extractInteger(BED_PATTERN.matcher(query));
        String suburb = extractKnownSuburb(normalized);
        return properties.search(type, null, suburb, null, maxPrice, bedrooms, null, null);
    }

    private String cannedAnswer(int matchCount) {
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
        return KNOWN_SUBURBS.stream().filter(query::contains).findFirst().orElse(null);
    }
}
