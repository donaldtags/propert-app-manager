package com.example.estatex.listing;

import com.example.estatex.organisation.Division;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.PropertyDtos.PropertyResponse;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.property.TitleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ListingDtos {
    private ListingDtos() {
    }

    public record CreateListingRequest(
            @Size(max = 40) String clientId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull ListingType listingType,
            @NotBlank String city,
            @NotBlank String suburb,
            @Size(max = 300) String address,
            @Min(0) @Max(50) int bedrooms,
            @Min(0) @Max(50) int bathrooms,
            @NotNull @Positive Long priceCents,
            @Pattern(regexp = "^(USD|ZWG)$", message = "currency must be USD or ZWG") String currency,
            TitleType titleType,
            Division division,
            Long negotiatorUserId
    ) {
    }

    public record CheckResponse(String key, String label, boolean met, String detail) {
        static CheckResponse from(PublicationGuard.Check c) {
            return new CheckResponse(c.key(), c.label(), c.met(), c.detail());
        }
    }

    public record ComplianceResponse(
            TitleVerificationStatus titleVerificationStatus,
            String titleReference,
            String titleVerificationNotes,
            Instant titleVerifiedAt,
            String titleVerifiedByName,
            boolean ownerConsentPhotos,
            boolean ownerConsentAddress,
            Instant consentRecordedAt,
            Instant preaApprovedAt,
            String preaApprovedByName,
            Instant materialChangedAt,
            Long activeMandateId,
            Instant publishedAt,
            Division division,
            Long negotiatorUserId,
            String negotiatorName,
            List<CheckResponse> checks,
            List<ChecklistItemResponse> verificationChecklist,
            boolean publishable,
            boolean readyForApproval,
            Long version
    ) {
    }

    public record ChecklistItemResponse(String key, String label, boolean checked) {
    }

    public record ChecklistRequest(@NotNull java.util.Set<String> checked) {
    }

    public record ListingSummary(
            Long propertyId,
            String title,
            ListingType listingType,
            PropertyStatus status,
            String city,
            String suburb,
            Long priceCents,
            String currency,
            TitleType titleType,
            List<String> riskFlags,
            String negotiatorName,
            boolean publishable,
            int openChecks,
            Instant createdAt
    ) {
    }

    public record ListingDetail(PropertyResponse property, ComplianceResponse compliance, List<MandateResponse> mandates) {
    }

    public record SetTitleRequest(@NotNull TitleType titleType, @Size(max = 120) String titleReference) {
    }

    public record TitleVerificationRequest(@NotNull TitleVerificationStatus status, @Size(max = 2000) String notes) {
    }

    public record ConsentRequest(@NotNull Boolean photos, @NotNull Boolean address) {
    }

    public record UnpublishRequest(@Size(max = 500) String reason) {
    }

    public record CreateMandateRequest(
            @Size(max = 40) String clientId,
            @NotNull MandateType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate expiryDate,
            @NotBlank @Size(max = 160) String ownerName,
            @Size(max = 40) String ownerPhone,
            @Size(max = 160) String ownerEmail,
            @Positive Long askingPriceCents,
            @Positive Long minimumPriceCents,
            @Pattern(regexp = "^(USD|ZWG)$", message = "currency must be USD or ZWG") String currency,
            @Min(0) @Max(2000) Integer commissionBps,
            @Size(max = 2000) String offerAuthority
    ) {
    }

    public record MandateResponse(
            Long id,
            Long propertyId,
            MandateType type,
            MandateStatus status,
            LocalDate startDate,
            LocalDate expiryDate,
            String ownerName,
            String ownerPhone,
            String ownerEmail,
            Long askingPriceCents,
            Long minimumPriceCents,
            String currency,
            Integer commissionBps,
            String offerAuthority,
            boolean hasSignedDocument,
            String documentFileName,
            LocalDate ownerSignedOn,
            Instant preaSignedOffAt,
            String preaSignedOffByName,
            Instant createdAt,
            Long version
    ) {
        public static MandateResponse from(Mandate m, String preaName) {
            return new MandateResponse(m.getId(), m.getPropertyId(), m.getType(), m.getStatus(), m.getStartDate(),
                    m.getExpiryDate(), m.getOwnerName(), m.getOwnerPhone(), m.getOwnerEmail(), m.getAskingPriceCents(),
                    m.getMinimumPriceCents(), m.getCurrency(), m.getCommissionBps(), m.getOfferAuthority(),
                    m.getDocumentStorageKey() != null, m.getDocumentFileName(), m.getOwnerSignedOn(),
                    m.getPreaSignedOffAt(), preaName, m.getCreatedAt(), m.getVersion());
        }
    }
}
