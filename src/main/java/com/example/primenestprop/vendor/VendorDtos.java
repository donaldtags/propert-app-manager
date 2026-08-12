package com.example.primenestprop.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class VendorDtos {
    private VendorDtos() {
    }

    public record CreateVendorRequest(
            @NotBlank String businessName,
            @NotNull VendorCategory category,
            String description,
            String phone,
            String email,
            String city
    ) {
    }

    /** Self-registration by a SERVICE_PROVIDER user; starts unverified until an admin reviews it. */
    public record SelfRegisterVendorRequest(
            @NotBlank String businessName,
            @NotNull VendorCategory category,
            String description,
            String phone,
            String city
    ) {
    }

    /** All fields optional/nullable: only the ones present in the request body are changed. */
    public record UpdateVendorRequest(
            String businessName,
            VendorCategory category,
            String description,
            String phone,
            String email,
            String city
    ) {
    }

    public record VendorResponse(
            Long id,
            String businessName,
            VendorCategory category,
            String description,
            String phone,
            String email,
            String city,
            boolean verified,
            boolean active,
            Long ownerId,
            Double averageRating,
            long ratingCount,
            Instant createdAt
    ) {
        public static VendorResponse from(Vendor vendor, Double averageRating, long ratingCount) {
            return new VendorResponse(
                    vendor.getId(),
                    vendor.getBusinessName(),
                    vendor.getCategory(),
                    vendor.getDescription(),
                    vendor.getPhone(),
                    vendor.getEmail(),
                    vendor.getCity(),
                    vendor.isVerified(),
                    vendor.isActive(),
                    vendor.getOwner() == null ? null : vendor.getOwner().getId(),
                    averageRating,
                    ratingCount,
                    vendor.getCreatedAt()
            );
        }
    }
}
