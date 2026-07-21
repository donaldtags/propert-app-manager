package com.example.primenestprop.neighbourhood;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class NeighbourhoodDtos {
    private NeighbourhoodDtos() {
    }

    public record UpsertNeighbourhoodRequest(
            @NotBlank String city,
            @NotBlank String suburb,
            String schoolsNote,
            String hospitalsNote,
            String transportNote,
            String shoppingNote,
            String generalNote
    ) {
    }

    public record NeighbourhoodResponse(
            Long id,
            String city,
            String suburb,
            String schoolsNote,
            String hospitalsNote,
            String transportNote,
            String shoppingNote,
            String generalNote,
            Instant updatedAt
    ) {
        public static NeighbourhoodResponse from(NeighbourhoodProfile profile) {
            return new NeighbourhoodResponse(
                    profile.getId(),
                    profile.getCity(),
                    profile.getSuburb(),
                    profile.getSchoolsNote(),
                    profile.getHospitalsNote(),
                    profile.getTransportNote(),
                    profile.getShoppingNote(),
                    profile.getGeneralNote(),
                    profile.getUpdatedAt()
            );
        }
    }
}
