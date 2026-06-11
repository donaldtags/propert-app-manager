package com.example.primenestprop.payment;

import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rent_invoices", indexes = {
        @Index(name = "idx_rent_invoices_lease", columnList = "lease_id"),
        @Index(name = "idx_rent_invoices_tenant", columnList = "tenant_id"),
        @Index(name = "idx_rent_invoices_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class RentInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Lease lease;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser tenant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser landlord;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    private RentInvoiceStatus status = RentInvoiceStatus.PENDING;

    private LocalDate dueDate;
    private Instant paidAt;

    @OneToOne(fetch = FetchType.LAZY)
    private Payment payment;
}
