package com.example.estatex.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The owner's written authority for the agency to market (and, within limits, negotiate on) a
 * property. A listing can only be published while it has an ACTIVE, in-date mandate that the
 * PREA has signed off. Money is integer cents.
 */
@Entity
@Table(name = "mandates", indexes = {
        @Index(name = "idx_mandates_property", columnList = "propertyId"),
        @Index(name = "idx_mandates_org_status", columnList = "organisationId,status"),
        @Index(name = "uq_mandates_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Mandate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    /** Client-generated id for idempotent offline creates. */
    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "mandate_type", length = 20, nullable = false)
    private String typeCode = MandateType.SOLE.name();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "status", length = 20, nullable = false)
    private String statusCode = MandateStatus.DRAFT.name();

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, length = 160)
    private String ownerName;

    @Column(length = 40) private String ownerPhone;
    @Column(length = 160) private String ownerEmail;
    private Long ownerUserId;

    private Long askingPriceCents;
    /** Lowest offer the agency may accept/present as acceptable without referring back to the owner. */
    private Long minimumPriceCents;

    @Column(length = 3, nullable = false)
    private String currency = "USD";

    private Integer commissionBps;

    @Column(length = 2000)
    private String offerAuthority;

    // Signed mandate document
    @Column(length = 500) private String documentStorageKey;
    @Column(length = 255) private String documentFileName;
    @Column(length = 120) private String documentContentType;
    private LocalDate ownerSignedOn;

    private Long preaSignedOffByUserId;
    private Instant preaSignedOffAt;

    private Long createdByUserId;
    private Instant createdAt = Instant.now();
    private Instant closedAt;

    public MandateType getType() {
        return MandateType.valueOf(typeCode);
    }

    public void setType(MandateType type) {
        this.typeCode = type.name();
    }

    public MandateStatus getStatus() {
        return MandateStatus.valueOf(statusCode);
    }

    public void setStatus(MandateStatus status) {
        this.statusCode = status.name();
    }

    /** Signed off and within its dates on {@code today} (Africa/Harare). */
    public boolean isInForceOn(LocalDate today) {
        return getStatus() == MandateStatus.ACTIVE && preaSignedOffAt != null
                && !today.isBefore(startDate) && !today.isAfter(expiryDate);
    }
}
