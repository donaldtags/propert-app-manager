package com.example.primenestprop.property;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Turns a street / suburb / city into coordinates using the ArcGIS World Geocoding Service.
 * Without an API key it uses the anonymous endpoint, which ArcGIS only allows for display
 * (not stored) results, so production should set ARCGIS_API_KEY. In Zimbabwe the service
 * typically resolves to street or suburb level, not to individual house numbers, which is why
 * the listing form also lets the owner drag the pin onto the exact house.
 */
@Service
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final int MIN_SCORE = 75;

    private final RestClient client;
    private final String apiKey;
    private final boolean enabled;

    public GeocodingService(
            @Value("${app.maps.arcgis-api-key:}") String apiKey,
            @Value("${app.maps.geocoding-enabled:true}") boolean enabled
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder()
                .baseUrl(this.apiKey.isEmpty()
                        ? "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer"
                        : "https://geocode-api.arcgis.com/arcgis/rest/services/World/GeocodeServer")
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * @param precision ArcGIS Addr_type, e.g. PointAddress (exact house), StreetAddress,
     *                  StreetName, Locality (suburb) or POI.
     */
    public record GeocodeResult(BigDecimal latitude, BigDecimal longitude, String matchedAddress, String precision, double score) {
    }

    public Optional<GeocodeResult> geocode(String address, String suburb, String city, String country) {
        return candidates(address, suburb, city, country, 1).stream().findFirst();
    }

    /**
     * Tries the full address first, then widens to suburb + city, then city alone, so a listing
     * always lands somewhere sensible even when its street isn't in ArcGIS's data.
     */
    public List<GeocodeResult> candidates(String address, String suburb, String city, String country, int max) {
        if (!enabled || isBlank(city) && isBlank(suburb) && isBlank(address)) {
            return List.of();
        }
        String countryCode = countryCode(country);
        for (String query : List.of(
                singleLine(address, suburb, city, country),
                singleLine(null, suburb, city, country),
                singleLine(null, null, city, country))) {
            if (query.isEmpty()) continue;
            List<GeocodeResult> results = search(query, countryCode, max);
            if (!results.isEmpty()) return results;
        }
        return List.of();
    }

    private List<GeocodeResult> search(String singleLine, String countryCode, int max) {
        try {
            JsonNode body = client.get()
                    .uri(builder -> {
                        builder.path("/findAddressCandidates")
                                .queryParam("f", "json")
                                .queryParam("SingleLine", singleLine)
                                .queryParam("maxLocations", max)
                                .queryParam("outFields", "Addr_type,Match_addr,Score");
                        if (countryCode != null) builder.queryParam("countryCode", countryCode);
                        if (!apiKey.isEmpty()) builder.queryParam("token", apiKey);
                        return builder.build();
                    })
                    .retrieve()
                    .body(JsonNode.class);
            List<GeocodeResult> results = new ArrayList<>();
            if (body == null || !body.path("candidates").isArray()) {
                return results;
            }
            for (JsonNode candidate : body.path("candidates")) {
                double score = candidate.path("score").asDouble(0);
                if (score < MIN_SCORE) continue;
                JsonNode location = candidate.path("location");
                results.add(new GeocodeResult(
                        BigDecimal.valueOf(location.path("y").asDouble()).setScale(7, RoundingMode.HALF_UP),
                        BigDecimal.valueOf(location.path("x").asDouble()).setScale(7, RoundingMode.HALF_UP),
                        candidate.path("address").asString(""),
                        candidate.path("attributes").path("Addr_type").asString(""),
                        score
                ));
            }
            return results;
        } catch (RuntimeException ex) {
            log.warn("Geocoding failed for '{}': {}", singleLine, ex.getMessage());
            return List.of();
        }
    }

    /** "25 Ferguson Rd, Athlone, Gweru, Zimbabwe", skipping parts the owner already typed into the address. */
    private static String singleLine(String address, String suburb, String city, String country) {
        List<String> parts = new ArrayList<>();
        String typed = isBlank(address) ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (!typed.isEmpty()) parts.add(address.trim());
        for (String part : new String[] {suburb, city}) {
            if (!isBlank(part) && !typed.contains(part.trim().toLowerCase(Locale.ROOT))) parts.add(part.trim());
        }
        if (parts.isEmpty()) return "";
        parts.add(isBlank(country) ? "Zimbabwe" : country.trim());
        return String.join(", ", parts);
    }

    private static final Map<String, String> COUNTRY_CODES = Map.of(
            "zimbabwe", "ZWE",
            "south africa", "ZAF",
            "zambia", "ZMB",
            "botswana", "BWA",
            "mozambique", "MOZ",
            "namibia", "NAM",
            "malawi", "MWI",
            "united kingdom", "GBR"
    );

    private static String countryCode(String country) {
        if (isBlank(country)) return "ZWE";
        String code = COUNTRY_CODES.get(country.trim().toLowerCase(Locale.ROOT));
        if (code != null) return code;
        // ArcGIS only accepts ISO codes here; for any other country name, search worldwide.
        return country.trim().matches("[A-Za-z]{2,3}") ? country.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
