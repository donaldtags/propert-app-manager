package com.example.primenestprop.payment;

import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
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
}
