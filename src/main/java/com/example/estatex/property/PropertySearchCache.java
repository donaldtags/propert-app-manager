package com.example.estatex.property;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Short-lived cache of public search result pages. Most traffic asks the same few questions
 * ("rentals in Harare", the home page's featured list), so one database round trip can answer
 * thousands of identical requests. Any change to a listing or its photos clears the whole cache
 * through the JPA listener below, so owners see their edits immediately; the TTL only bounds
 * staleness for changes made outside JPA (raw SQL, another instance).
 */
public final class PropertySearchCache {

    public record CachedPage(List<PropertyDtos.PropertyResponse> items, long total) {}

    private static final Cache<String, CachedPage> PAGES = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofSeconds(20))
            .build();

    private PropertySearchCache() {}

    public static CachedPage get(String key, Function<String, CachedPage> loader) {
        return PAGES.get(key, loader);
    }

    public static void invalidateAll() {
        PAGES.invalidateAll();
    }

    /** Registered on Property and PropertyPhoto with @EntityListeners. */
    public static class Invalidator {
        @PostPersist
        @PostUpdate
        @PostRemove
        void changed(Object entity) {
            invalidateAll();
        }
    }
}
