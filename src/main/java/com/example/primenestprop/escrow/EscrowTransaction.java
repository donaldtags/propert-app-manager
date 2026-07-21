package com.example.primenestprop.escrow;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "escrows")
@Getter
@Setter
@NoArgsConstructor
public class EscrowTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    private Lease lease;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser payer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser beneficiary;

    @Enumerated(EnumType.STRING)
    private EscrowStatus status = EscrowStatus.CREATED;

    private BigDecimal amount;
    private String currency = "USD";
    private String purpose;
    private Instant createdAt = Instant.now();
    private Instant fundedAt;
    private Instant releasedAt;

    @Enumerated(EnumType.STRING)
    private FundingMethod fundingMethod;
    private String fundingProvider;

    @OneToMany(mappedBy = "escrow", fetch = FetchType.LAZY)
    private List<EscrowReleaseApproval> releaseApprovals = new ArrayList<>();
}
