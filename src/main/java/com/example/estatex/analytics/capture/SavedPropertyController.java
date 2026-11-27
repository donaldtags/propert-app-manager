package com.example.estatex.analytics.capture;

import com.example.estatex.common.ApiException;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyDtos.PropertyResponse;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.user.AppUser;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** A signed-in user's saved listings. Saving the same listing twice is harmless. */
@RestController
class SavedPropertyController {
    private final SavedPropertyRepository saved;
    private final PropertyRepository properties;
    private final MarketplaceEventService events;

    SavedPropertyController(SavedPropertyRepository saved, PropertyRepository properties, MarketplaceEventService events) {
        this.saved = saved;
        this.properties = properties;
        this.events = events;
    }

    @GetMapping("/api/v1/saved-properties")
    @Transactional(readOnly = true)
    List<PropertyResponse> list(@AuthenticationPrincipal AppUser user) {
        List<Long> ids = saved.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(SavedProperty::getPropertyId).toList();
        Map<Long, Property> byId = properties.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Property::getId, p -> p));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .filter(p -> !p.getStatus().isHiddenFromPublic())
                .map(PropertyResponse::publicView).toList();
    }

    @GetMapping("/api/v1/saved-properties/ids")
    List<Long> ids(@AuthenticationPrincipal AppUser user) {
        return saved.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(SavedProperty::getPropertyId).toList();
    }

    @PutMapping("/api/v1/saved-properties/{propertyId}")
    @Transactional
    Map<String, Boolean> save(@PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        Property p = properties.findById(propertyId)
                .filter(x -> !x.getStatus().isHiddenFromPublic())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (saved.findByUserIdAndPropertyId(user.getId(), propertyId).isEmpty()) {
            SavedProperty s = new SavedProperty();
            s.setUserId(user.getId());
            s.setPropertyId(propertyId);
            saved.save(s);
            events.recordSave(p, user.getId(), true);
        }
        return Map.of("saved", true);
    }

    @DeleteMapping("/api/v1/saved-properties/{propertyId}")
    @Transactional
    Map<String, Boolean> unsave(@PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        saved.findByUserIdAndPropertyId(user.getId(), propertyId).ifPresent(s -> {
            saved.delete(s);
            properties.findById(propertyId).ifPresent(p -> events.recordSave(p, user.getId(), false));
        });
        return Map.of("saved", false);
    }
}
