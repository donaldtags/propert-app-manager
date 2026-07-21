package com.example.primenestprop.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class MaintenanceDtos {
    private MaintenanceDtos() {
    }

    public record CreateMaintenanceRequest(
            @NotNull Long propertyId,
            @NotBlank String category,
            String priority,
            @NotBlank String description
    ) {
    }

    public record MaintenancePhotoResponse(Long id, String photoUrl, Instant uploadedAt) {
        public static MaintenancePhotoResponse from(MaintenancePhoto photo) {
            return new MaintenancePhotoResponse(photo.getId(), photo.getPhotoUrl(), photo.getUploadedAt());
        }
    }

    public record MaintenanceResponse(
            Long id,
            Long propertyId,
            Long requesterId,
            MaintenanceStatus status,
            String category,
            String priority,
            String description,
            Long assignedVendorId,
            String assignedVendorName,
            List<MaintenancePhotoResponse> photos
    ) {
        public static MaintenanceResponse from(MaintenanceRequest request) {
            return from(request, List.of());
        }

        public static MaintenanceResponse from(MaintenanceRequest request, List<MaintenancePhoto> photos) {
            return new MaintenanceResponse(
                    request.getId(),
                    request.getProperty().getId(),
                    request.getRequester().getId(),
                    request.getStatus(),
                    request.getCategory(),
                    request.getPriority(),
                    request.getDescription(),
                    request.getAssignedVendor() == null ? null : request.getAssignedVendor().getId(),
                    request.getAssignedVendor() == null ? null : request.getAssignedVendor().getBusinessName(),
                    photos.stream().map(MaintenancePhotoResponse::from).toList()
            );
        }
    }
}
