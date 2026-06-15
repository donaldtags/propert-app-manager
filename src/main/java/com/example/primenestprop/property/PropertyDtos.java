package com.example.primenestprop.property;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PropertyDtos {
    private PropertyDtos() {
    }

    public record CreatePropertyRequest(
            @NotBlank String title,
            String description,
            @NotNull ListingType listingType,
            @NotBlank String city,
            @NotBlank String suburb,
            String address,
            String country,
            @Min(0) int bedrooms,
            @Min(0) int bathrooms,
            @DecimalMin("0.0") BigDecimal price,
            String currency,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean diasporaFriendly,
            boolean escrowRequired,
            @NotNull Long landlordId,
            Long agentId,
            List<String> photoUrls,
            List<String> imageUrls,
            List<String> photos
    ) {
    }

    public record VerifyPropertyRequest(@NotNull Long verifierId, String note) {
    }

    public record InquiryRequest(
            @NotBlank String name,
            @NotBlank String email,
            String phone,
            @NotBlank String message
    ) {
    }

    public record PropertyResponse(
            Long id,
            String title,
            String description,
            ListingType listingType,
            PropertyStatus status,
            VerificationStatus verificationStatus,
            String city,
            String suburb,
            String address,
            String country,
            int bedrooms,
            int bathrooms,
            BigDecimal price,
            String currency,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean diasporaFriendly,
            boolean escrowRequired,
            Long landlordId,
            Long agentId,
            String landlordName,
            String agentName,
            String agentPhone,
            Instant createdAt,
            Instant verifiedAt,
            List<String> photoUrls,
            List<String> imageUrls,
            List<String> photos
    ) {
        public static PropertyResponse from(Property property) {
            List<String> urls = property.getPhotos().stream()
                    .map(PropertyPhoto::getPhotoUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();
            String landlordName = property.getLandlord() != null ? property.getLandlord().getFullName() : null;
            String agentName = property.getAgent() != null ? property.getAgent().getFullName() : null;
            String agentPhone = property.getAgent() != null ? property.getAgent().getPhone() : null;
            return new PropertyResponse(
                    property.getId(),
                    property.getTitle(),
                    property.getDescription(),
                    property.getListingType(),
                    property.getStatus(),
                    property.getVerificationStatus(),
                    property.getCity(),
                    property.getSuburb(),
                    property.getAddress(),
                    property.getCountry(),
                    property.getBedrooms(),
                    property.getBathrooms(),
                    property.getPrice(),
                    property.getCurrency(),
                    property.getLatitude(),
                    property.getLongitude(),
                    property.isDiasporaFriendly(),
                    property.isEscrowRequired(),
                    property.getLandlord().getId(),
                    property.getAgent() == null ? null : property.getAgent().getId(),
                    landlordName,
                    agentName,
                    agentPhone,
                    property.getCreatedAt(),
                    property.getVerifiedAt(),
                    urls,
                    urls,
                    urls
            );
        }
    }
}
