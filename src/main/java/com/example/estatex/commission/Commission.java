package com.example.estatex.commission;

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

@Entity
@Table(name = "commissions", indexes = {
        @Index(name = "idx_commissions_org_status", columnList = "organisationId,status"),
        @Index(name = "idx_commissions_negotiator", columnList = "negotiatorUserId")
})
@Getter
@Setter
@NoArgsConstructor
public class Commission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    private Long mandateId;
    private Long negotiatorUserId;

    private Long salePriceCents;

    @Column(nullable = false)
    private long amountCents;

    @Column(length = 3, nullable = false)
    private String currency = "USD";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "status", length = 20, nullable = false)
    private String statusCode = CommissionStatus.EXPECTED.name();

    // Conveyancer confirmation (the only route to EARNED)
    @Column(length = 160) private String conveyancerName;
    @Column(length = 160) private String conveyancerFirm;
    @Column(length = 120) private String deedsReference;
    private LocalDate transferRegisteredOn;
    @Column(length = 1000) private String confirmationNotes;
    private Long confirmedByUserId;
    private Instant confirmedAt;

    private LocalDate receivedOn;
    @Column(length = 160) private String receivedReference;
    private Long receivedRecordedByUserId;

    private Long createdByUserId;
    private Instant createdAt = Instant.now();

    public CommissionStatus getStatus() {
        return CommissionStatus.valueOf(statusCode);
    }

    void setStatus(CommissionStatus status) {
        this.statusCode = status.name();
    }
}
