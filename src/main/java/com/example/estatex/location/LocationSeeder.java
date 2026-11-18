package com.example.estatex.location;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads reference/zimbabwe-locations.json: provinces, then cities/towns, then suburbs grouped by
 * density tier. Insert-only and idempotent: a row that already exists (same parent + name, case
 * insensitive) is left exactly as an admin may have edited it, and new entries added to the JSON
 * are picked up on the next boot.
 *
 * <p>Suburb entries are plain names, or {@code "Name|Alias one;Alias two"}.
 */
@Component
@Order(3)
class LocationSeeder implements CommandLineRunner {
    static final String RESOURCE = "reference/zimbabwe-locations.json";
    private static final Logger log = LoggerFactory.getLogger(LocationSeeder.class);

    private final LocationRepository locations;
    private final ObjectMapper objectMapper;

    LocationSeeder(LocationRepository locations, ObjectMapper objectMapper) {
        this.locations = locations;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        JsonNode root;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            root = objectMapper.readTree(in);
        }
        String source = root.path("source").asString("compiled reference list");

        Map<String, Location> existing = new HashMap<>();
        locations.findAll().forEach(l -> existing.put(key(l.getParentId(), l.getName()), l));
        int before = existing.size();

        for (JsonNode provinceNode : root.path("provinces")) {
            String provinceName = provinceNode.path("name").asString();
            Location province = upsert(existing, null, LocationType.PROVINCE, provinceName, provinceName, null, null, source);
            for (JsonNode placeNode : provinceNode.path("places")) {
                LocationType type = LocationType.valueOf(placeNode.path("type").asString("TOWN"));
                String placeName = placeNode.path("name").asString();
                Location place = upsert(existing, province.getId(), type, placeName, provinceName, null, null, source);
                if (place.getLatitude() == null && placeNode.has("lat")) {
                    place.setLatitude(BigDecimal.valueOf(placeNode.path("lat").asDouble()));
                    place.setLongitude(BigDecimal.valueOf(placeNode.path("lng").asDouble()));
                }
                JsonNode suburbs = placeNode.path("suburbs");
                Iterator<Map.Entry<String, JsonNode>> tiers = suburbs.properties().iterator();
                while (tiers.hasNext()) {
                    Map.Entry<String, JsonNode> tier = tiers.next();
                    DensityTier densityTier = "UNCLASSIFIED".equals(tier.getKey()) ? null : DensityTier.valueOf(tier.getKey());
                    for (JsonNode suburbNode : tier.getValue()) {
                        String[] parts = suburbNode.asString().split("\\|", 2);
                        Location suburb = upsert(existing, place.getId(), LocationType.SUBURB, parts[0].trim(),
                                provinceName, placeName, densityTier, source);
                        if (parts.length > 1 && suburb.getAliases() == null) {
                            suburb.setAliases(String.join(",", parts[1].split("\\s*;\\s*")));
                        }
                    }
                }
            }
        }
        int added = existing.size() - before;
        if (added > 0) {
            log.info("Location reference data: added {} rows ({} total)", added, existing.size());
        }
    }

    private Location upsert(Map<String, Location> existing, Long parentId, LocationType type, String name,
                            String province, String city, DensityTier tier, String source) {
        String k = key(parentId, name);
        Location found = existing.get(k);
        if (found != null) {
            return found;
        }
        Location l = new Location();
        l.setParentId(parentId);
        l.setType(type);
        l.setName(name);
        l.setProvince(province);
        l.setCity(city);
        l.setDensityTier(tier);
        l.setSource(source);
        l.setVerified(true);
        locations.save(l);
        existing.put(k, l);
        return l;
    }

    private static String key(Long parentId, String name) {
        return parentId + "|" + name.toLowerCase(Locale.ROOT);
    }
}
