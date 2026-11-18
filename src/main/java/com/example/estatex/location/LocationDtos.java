package com.example.estatex.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public final class LocationDtos {
    private LocationDtos() {
    }

    public record LocationResponse(
            Long id,
            LocationType type,
            String name,
            Long parentId,
            String province,
            String city,
            DensityTier densityTier,
            List<String> aliases,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean verified
    ) {
        public static LocationResponse from(Location l) {
            List<String> aliases = l.getAliases() == null || l.getAliases().isBlank() ? null
                    : Arrays.stream(l.getAliases().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            return new LocationResponse(l.getId(), l.getType(), l.getName(), l.getParentId(), l.getProvince(),
                    l.getCity(), l.getDensityTier(), aliases, l.getLatitude(), l.getLongitude(), l.isVerified());
        }
    }

    /** A user proposing a suburb that isn't in the list yet. */
    public record SuggestSuburbRequest(@NotNull Long cityId, @NotBlank @Size(max = 120) String name) {
    }

    public record UpdateLocationRequest(
            @Size(max = 120) String name,
            DensityTier densityTier,
            List<String> aliases,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean verified
    ) {
    }
}
