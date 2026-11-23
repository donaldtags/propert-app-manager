package com.example.estatex.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A request to send wallet money to EcoCash or a bank account. The amount leaves the wallet
 * (held) the moment it's requested, so it can't be spent twice; EstateX staff then pay it out by
 * hand and record the reference, or reject it, which puts the money back.
 */
@Entity
@Table(name = "wallet_withdrawals", indexes = {
        @Index(name = "idx_wallet_withdrawals_user", columnList = "user_id"),
        @Index(name = "idx_wallet_withdrawals_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Withdrawal {
    public static final String REQUESTED = "REQUESTED";
    public static final String PAID = "PAID";
    public static final String REJECTED = "REJECTED";

    public static final String METHOD_ECOCASH = "ECOCASH";
    public static final String METHOD_BANK = "BANK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(length = 32)
    private String ecocashPhone;
    @Column(length = 120)
    private String bankAccountName;
    @Column(length = 40)
    private String bankAccountNumber;
    @Column(length = 120)
    private String bankName;
    @Column(length = 120)
    private String bankBranch;

    @Column(nullable = false, length = 20)
    private String status = REQUESTED;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();
    private Instant decidedAt;
    private Long decidedByUserId;
    @Column(length = 120)
    private String payoutReference;
    @Column(length = 500)
    private String rejectReason;
}
