package com.example.primenestprop.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class MaintenanceDtos {
    private MaintenanceDtos() {
    }

    public record CreateMaintenanceRequest(
            @NotNull Long propertyId,
            @NotNull Long requesterId,
            @NotBlank String category,
            String priority,
            @NotBlank String description
    ) {
    }

    public record MaintenanceResponse(
            Long id,
            Long propertyId,
            Long requesterId,
            MaintenanceStatus status,
            String category,
            String priority,
            String description
    ) {
        public static MaintenanceResponse from(MaintenanceRequest request) {
            return new MaintenanceResponse(
                    request.getId(),
                    request.getProperty().getId(),
                    request.getRequester().getId(),
                    request.getStatus(),
                    request.getCategory(),
                    request.getPriority(),
                    request.getDescription()
            );
        }
    }
}
