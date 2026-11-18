package com.example.estatex.payment;

import com.example.estatex.lease.Lease;
import com.example.estatex.property.Property;
import com.example.estatex.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser payer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser payee;

    @ManyToOne(fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    private Lease lease;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.INITIATED;

    private BigDecimal amount;
    private String currency = "USD";
    private String provider;
    private String reference;
    private String purpose;
    private Instant createdAt = Instant.now();
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    private PaymentPurpose purposeType = PaymentPurpose.OTHER;

    /** Small "key=value,key=value" blob carrying whatever the initiating service needs to
     * finish its job once {@link PaymentSucceededEvent} fires (e.g. "plan=GROWTH", "reitId=3,units=10"). */
    private String metadata;

    /** Set only for {@link PaymentPurpose#ESCROW_FUND} payments; not a JPA relation to keep
     * payment and escrow from depending on each other's entities. */
    private Long escrowTransactionId;

    private String providerReference;
    private String checkoutUrl;
    private String pollUrl;
}
