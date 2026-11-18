package com.example.estatex.location;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {
    private static final Set<LocationType> SETTLEMENTS = EnumSet.of(LocationType.CITY, LocationType.TOWN, LocationType.GROWTH_POINT);

    private final LocationRepository locations;
    private final AuditService audit;

    public LocationService(LocationRepository locations, AuditService audit) {
        this.locations = locations;
        this.audit = audit;
    }

    /** Every verified location. Small (low thousands of rows) and cached client-side for offline use. */
    @Transactional(readOnly = true)
    public List<Location> allVerified() {
        return locations.findByVerifiedTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Location> pending(AppUser currentUser) {
        requireManager(currentUser);
        return locations.findByVerifiedFalseOrderByCreatedAtDesc();
    }

    /**
     * Records a suburb a user typed that isn't in the list. Returns the existing row instead if the
     * name (or one of its aliases) already exists under that city, so the same suggestion is never
     * stored twice.
     */
    @Transactional
    public Location suggestSuburb(LocationDtos.SuggestSuburbRequest request, AppUser currentUser) {
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to add a suburb");
        }
        Location city = locations.findById(request.cityId())
                .filter(l -> SETTLEMENTS.contains(l.getType()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "cityId must be a city or town"));
        String name = normaliseName(request.name());
        Location existing = locations.findByParentIdAndNameIgnoreCase(city.getId(), name)
                .orElseGet(() -> findByAlias(city.getId(), name));
        if (existing != null) {
            return existing;
        }
        Location suburb = new Location();
        suburb.setType(LocationType.SUBURB);
        suburb.setName(name);
        suburb.setParentId(city.getId());
        suburb.setCity(city.getName());
        suburb.setProvince(city.getProvince());
        suburb.setSource("user suggestion");
        suburb.setVerified(false);
        suburb.setSuggestedByUserId(currentUser.getId());
        locations.save(suburb);
        return suburb;
    }

    @Transactional
    public Location update(Long id, LocationDtos.UpdateLocationRequest request, AppUser currentUser) {
        requireManager(currentUser);
        Location location = locations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Location not found"));
        String before = location.getName() + (location.isVerified() ? "" : " (unverified)");
        if (request.name() != null && !request.name().isBlank()) {
            String name = normaliseName(request.name());
            locations.findByParentIdAndNameIgnoreCase(location.getParentId(), name)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ApiException(HttpStatus.CONFLICT, name + " already exists here");
                    });
            location.setName(name);
        }
        if (request.densityTier() != null) location.setDensityTier(request.densityTier());
        if (request.aliases() != null) {
            location.setAliases(request.aliases().stream().map(String::trim).filter(s -> !s.isEmpty())
                    .distinct().collect(Collectors.joining(",")));
        }
        if (request.latitude() != null) location.setLatitude(request.latitude());
        if (request.longitude() != null) location.setLongitude(request.longitude());
        if (request.verified() != null) location.setVerified(request.verified());
        String after = location.getName() + (location.isVerified() ? "" : " (unverified)");
        audit.record(currentUser, entry("LOCATION_UPDATED", "Location", location.getId()).transition(before, after));
        return location;
    }

    @Transactional
    public void rejectSuggestion(Long id, AppUser currentUser) {
        requireManager(currentUser);
        Location location = locations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Location not found"));
        if (location.isVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "Only unverified suggestions can be removed. Edit verified locations instead.");
        }
        audit.record(currentUser, entry("LOCATION_SUGGESTION_REJECTED", "Location", id)
                .metadata(Map.of("name", location.getName(), "city", String.valueOf(location.getCity()))));
        locations.delete(location);
    }

    private Location findByAlias(Long parentId, String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return locations.findByVerifiedTrueOrderByNameAsc().stream()
                .filter(l -> parentId.equals(l.getParentId()) && l.getAliases() != null)
                .filter(l -> Set.of(l.getAliases().toLowerCase(Locale.ROOT).split("\\s*,\\s*")).contains(needle))
                .findFirst()
                .orElse(null);
    }

    private void requireManager(AppUser user) {
        if (user == null || !user.hasPermission(Permission.LOCATION_MANAGE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only platform admins can curate locations");
        }
    }

    static String normaliseName(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }
}
