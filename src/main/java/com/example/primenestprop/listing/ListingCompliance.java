package com.example.primenestprop.listing;

import com.example.primenestprop.organisation.Division;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Publication-gate state for one organisation listing (a {@code Property} with an
 * organisationId). Kept out of the legacy properties table so that table only gains two nullable
 * columns, and so this row can carry its own optimistic-lock version.
 */
@Entity
@Table(name = "listing_compliance", indexes = {
        @Index(name = "uq_listing_compliance_property", columnList = "propertyId", unique = true),
        @Index(name = "idx_listing_compliance_org", columnList = "organisationId")
})
@Getter
@Setter
@NoArgsConstructor
public class ListingCompliance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private Long organisationId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "division", length = 20)
    private String divisionCode;

    private Long negotiatorUserId;

    // Title / ownership verification
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "title_verification_status", length = 20, nullable = false)
    private String titleVerificationCode = TitleVerificationStatus.NOT_STARTED.name();

    /** Deed / sectional title / cession agreement number. */
    @Column(length = 120)
    private String titleReference;

    @Column(length = 2000)
    private String titleVerificationNotes;

    private Long titleVerifiedByUserId;
    private Instant titleVerifiedAt;

    // Owner consent to marketing
    private boolean ownerConsentPhotos;
    private boolean ownerConsentAddress;
    private Long consentRecordedByUserId;
    private Instant consentRecordedAt;

    // Principal Registered Estate Agent approval
    private Long preaApprovedByUserId;
    private Instant preaApprovedAt;

    /** Last change to price, address or title type: approval given before this no longer counts. */
    private Instant materialChangedAt;

    private Long activeMandateId;

    private Long publishedByUserId;
    private Instant publishedAt;

    private Instant createdAt = Instant.now();

    public Division getDivision() {
        return divisionCode == null ? null : Division.valueOf(divisionCode);
    }

    public void setDivision(Division division) {
        this.divisionCode = division == null ? null : division.name();
    }

    public TitleVerificationStatus getTitleVerificationStatus() {
        return TitleVerificationStatus.valueOf(titleVerificationCode);
    }

    public void setTitleVerificationStatus(TitleVerificationStatus status) {
        this.titleVerificationCode = status.name();
    }

    public void clearPreaApproval() {
        preaApprovedByUserId = null;
        preaApprovedAt = null;
    }
}
