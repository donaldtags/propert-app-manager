package com.example.estatex.trust;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One append-only movement of client money. Never updated or deleted: a mistake is corrected by a
 * further entry. Balances are always computed from entries, never stored.
 */
@Entity
@Table(name = "trust_ledger_entries", indexes = {
        @Index(name = "idx_trust_org_currency", columnList = "organisationId,currency"),
        @Index(name = "idx_trust_property", columnList = "propertyId"),
        @Index(name = "uq_trust_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class TrustLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "entry_type", length = 40, nullable = false)
    private String typeCode;

    /** Always positive. */
    @Column(nullable = false)
    private long amountCents;

    /** amountCents with the entry type's direction applied; summed for balances. */
    @Column(nullable = false)
    private long signedAmountCents;

    @Column(length = 3, nullable = false)
    private String currency;

    /** The client money belongs to / is held for. Null bucket = not yet allocated to a property. */
    private Long propertyId;

    @Column(nullable = false, length = 160)
    private String clientName;

    @Column(length = 160)
    private String reference;

    @Column(length = 1000)
    private String notes;

    private Long escrowTransactionId;
    private Long paymentId;
    private Long commissionId;

    @Column(nullable = false)
    private LocalDate occurredOn;

    private Long recordedByUserId;
    private Instant recordedAt = Instant.now();

    public TrustEntryType getType() {
        return TrustEntryType.valueOf(typeCode);
    }

    public void setType(TrustEntryType type) {
        this.typeCode = type.name();
    }
}
