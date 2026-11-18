package com.example.estatex.offer;

import com.example.estatex.contact.Financing;
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

/** A buyer's (or tenant's) offer on an agency listing, recorded and worked within the mandate. */
@Entity
@Table(name = "offers", indexes = {
        @Index(name = "idx_offers_property", columnList = "propertyId"),
        @Index(name = "idx_offers_contact", columnList = "buyerContactId"),
        @Index(name = "uq_offers_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Offer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private Long mandateId;

    @Column(nullable = false)
    private Long buyerContactId;

    @Column(nullable = false)
    private long amountCents;

    @Column(length = 3, nullable = false)
    private String currency = "USD";

    private Long depositCents;

    /** The latest counter-offer the owner made, if any. */
    private Long counterAmountCents;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "financing", length = 20)
    private String financingCode;

    @Column(length = 2000)
    private String conditions;

    private LocalDate expiresOn;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "status", length = 20, nullable = false)
    private String statusCode = OfferStatus.RECEIVED.name();

    @Column(length = 20)
    private String presentedVia;
    private Instant presentedAt;
    private Instant decidedAt;

    /** How the owner instructed a decision outside the mandate's authority (e.g. "WhatsApp 17 Nov 14:02"). */
    @Column(length = 300)
    private String ownerInstructionReference;

    private Long negotiatorUserId;
    private Instant createdAt = Instant.now();

    public OfferStatus getStatus() {
        return OfferStatus.valueOf(statusCode);
    }

    void setStatus(OfferStatus status) {
        this.statusCode = status.name();
    }

    public Financing getFinancing() {
        return financingCode == null ? null : Financing.valueOf(financingCode);
    }

    public void setFinancing(Financing financing) {
        this.financingCode = financing == null ? null : financing.name();
    }
}
