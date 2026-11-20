package com.example.estatex.property;

import org.hibernate.Hibernate;
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
            boolean solarInstalled,
            boolean backupPower,
            WaterSource waterSource,
            boolean furnished,
            boolean internetAvailable,
            boolean securityFeatures,
            boolean parkingAvailable,
            boolean petsAllowed,
            @NotNull Long landlordId,
            Long agentId,
            List<String> photoUrls,
            List<String> imageUrls,
            List<String> photos,
            String virtualTourUrl
    ) {
    }

    /**
     * approved: true (default) marks the listing VERIFIED, false REJECTED (a note is then required).
     * verifierId is ignored - the verifier is always the signed-in user - and kept only so older
     * clients that still send it keep working.
     */
    public record VerifyPropertyRequest(Long verifierId, String note, Boolean approved) {
    }

    /** All fields optional/nullable: only the ones present in the request body are changed. */
    public record UpdatePropertyRequest(
            String title,
            String description,
            ListingType listingType,
            String city,
            String suburb,
            String address,
            String country,
            @Min(0) Integer bedrooms,
            @Min(0) Integer bathrooms,
            @DecimalMin("0.0") BigDecimal price,
            String currency,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean diasporaFriendly,
            Boolean escrowRequired,
            Boolean solarInstalled,
            Boolean backupPower,
            WaterSource waterSource,
            Boolean furnished,
            Boolean internetAvailable,
            Boolean securityFeatures,
            Boolean parkingAvailable,
            Boolean petsAllowed,
            String virtualTourUrl,
            PropertyStatus status
    ) {
    }

    public record InquiryRequest(
            @NotBlank String name,
            @NotBlank String email,
            String phone,
            @NotBlank String message
    ) {
    }

    public record InquiryResponse(
            Long id,
            Long propertyId,
            String propertyTitle,
            String name,
            String email,
            String phone,
            String message,
            Instant createdAt
    ) {
        public static InquiryResponse from(PropertyInquiry inquiry) {
            return new InquiryResponse(
                    inquiry.getId(),
                    inquiry.getProperty().getId(),
                    inquiry.getProperty().getTitle(),
                    inquiry.getName(),
                    inquiry.getEmail(),
                    inquiry.getPhone(),
                    inquiry.getMessage(),
                    inquiry.getCreatedAt()
            );
        }
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
            boolean solarInstalled,
            boolean backupPower,
            WaterSource waterSource,
            boolean furnished,
            boolean internetAvailable,
            boolean securityFeatures,
            boolean parkingAvailable,
            boolean petsAllowed,
            String virtualTourUrl,
            Long landlordId,
            Long agentId,
            String landlordName,
            String landlordCompanyName,
            Integer landlordTrustScore,
            String agentName,
            String agentPhone,
            String agentCompanyName,
            Integer agentTrustScore,
            Instant createdAt,
            Instant verifiedAt,
            boolean featured,
            Instant featuredUntil,
            List<String> photoUrls,
            List<String> imageUrls,
            List<String> photos,
            List<PhotoResponse> photoDetails,
            Long organisationId,
            TitleType titleType,
            /** Buyer-facing risk markers, e.g. "CESSION". Empty when none apply. */
            List<String> riskFlags,
            /** Who is marketing it: AGENCY (a company on EstateX), AGENT (independent agent) or OWNER. */
            String listedBy,
            /** For AGENT/AGENCY listings: is the agent's Estate Agents Council licence approved by EstateX? */
            Boolean listerLicensed,
            /** For AGENCY listings (approved companies only): the company. */
            String organisationName,
            String organisationSlug,
            String organisationLogoUrl,
            String organisationPhone,
            /** The person marketing it (agent, or owner) has had their ID document checked by EstateX. */
            Boolean listerIdVerified
    ) {
        public static PropertyResponse from(Property property) {
            com.example.estatex.organisation.Organisation org = property.getOrganisationId() == null ? null
                    : property.getOrganisation();
            // Not loaded on this path (no open session): leave the company out rather than fail.
            if (org != null && !Hibernate.isInitialized(org)) org = null;
            if (org != null && !org.isApproved()) org = null;
            Lister lister = Lister.of(property);
            List<String> urls = property.getPhotos().stream()
                    .map(PropertyPhoto::getPhotoUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();
            List<PhotoResponse> photoDetails = property.getPhotos().stream()
                    .filter(p -> p.getPhotoUrl() != null && !p.getPhotoUrl().isBlank())
                    .map(p -> new PhotoResponse(p.getId(), p.getPhotoUrl()))
                    .toList();
            String landlordName = property.getLandlord() != null ? property.getLandlord().getFullName() : null;
            String landlordCompanyName = property.getLandlord() != null ? property.getLandlord().getCompanyName() : null;
            Integer landlordTrustScore = property.getLandlord() != null ? property.getLandlord().getTrustScore() : null;
            String agentName = property.getAgent() != null ? property.getAgent().getFullName() : null;
            String agentPhone = property.getAgent() != null ? property.getAgent().getPhone() : null;
            String agentCompanyName = property.getAgent() != null ? property.getAgent().getCompanyName() : null;
            Integer agentTrustScore = property.getAgent() != null ? property.getAgent().getTrustScore() : null;
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
                    property.isSolarInstalled(),
                    property.isBackupPower(),
                    property.getWaterSource(),
                    property.isFurnished(),
                    property.isInternetAvailable(),
                    property.isSecurityFeatures(),
                    property.isParkingAvailable(),
                    property.isPetsAllowed(),
                    property.getVirtualTourUrl(),
                    property.getLandlord().getId(),
                    property.getAgent() == null ? null : property.getAgent().getId(),
                    landlordName,
                    landlordCompanyName,
                    landlordTrustScore,
                    agentName,
                    agentPhone,
                    agentCompanyName,
                    agentTrustScore,
                    property.getCreatedAt(),
                    property.getVerifiedAt(),
                    property.isFeatured(),
                    property.getFeaturedUntil(),
                    urls,
                    urls,
                    urls,
                    photoDetails,
                    property.getOrganisationId(),
                    property.getTitleType(),
                    property.getTitleType() == TitleType.CESSION ? List.of("CESSION") : List.of(),
                    lister.listedBy(),
                    lister.licensed(),
                    org == null ? null : org.getName(),
                    org == null ? null : org.getSlug(),
                    org == null ? null : org.getLogoOnLightUrl(),
                    org == null ? null : (org.getPhone() != null ? org.getPhone() : org.getWhatsappNumber()),
                    lister.idVerified()
            );
        }
    }

    public record PhotoResponse(Long id, String url) {
    }

    /** How a listing's marketer is described to the public. */
    record Lister(String listedBy, Boolean licensed, Boolean idVerified) {
        static Lister of(Property p) {
            if (p.getOrganisationId() != null) {
                return p.getLandlord() == null ? new Lister("AGENCY", null, null)
                        : new Lister("AGENCY", p.getLandlord().isLicensedAgent(), p.getLandlord().isIdentityVerified());
            }
            com.example.estatex.user.AppUser agent = p.getAgent() != null ? p.getAgent()
                    : (p.getLandlord() != null && p.getLandlord().getRoles().contains(com.example.estatex.user.UserRole.AGENT)
                    ? p.getLandlord() : null);
            if (agent != null) return new Lister("AGENT", agent.isLicensedAgent(), agent.isIdentityVerified());
            return new Lister("OWNER", null, p.getLandlord() == null ? null : p.getLandlord().isIdentityVerified());
        }
    }
}
