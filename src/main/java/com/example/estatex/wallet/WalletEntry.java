package com.example.estatex.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One leg of a money movement. Entries are never updated or deleted: every transfer writes two
 * legs with the same transferId whose amounts sum to zero (double entry), and a correction is a
 * new, opposite transfer. The idempotency key is unique, so a replayed gateway webhook or a
 * double-clicked button can never move the same money twice.
 */
@Entity
@Table(name = "wallet_entries", indexes = {
        @Index(name = "idx_wallet_entries_wallet", columnList = "wallet_id, id"),
        @Index(name = "uk_wallet_entries_idempotency", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_wallet_entries_transfer", columnList = "transfer_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "transfer_id", nullable = false, length = 36)
    private String transferId;

    /** Positive = money in, negative = money out, in the currency's minor unit (cents). */
    @Column(nullable = false)
    private long amountCents;

    /** The wallet's balance right after this entry. Null on system accounts (no cached balance). */
    private Long balanceAfterCents;

    @Column(nullable = false, length = 3)
    private String currency;

    /** See {@link WalletEntryType}. */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 300)
    private String description;

    /** What the money was for, e.g. Payment 42, Withdrawal 3, Reit 2. */
    @Column(length = 40)
    private String relatedType;
    private Long relatedId;

    @Column(name = "idempotency_key", nullable = false, length = 140)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    WalletEntry(Long walletId, String transferId, long amountCents, Long balanceAfterCents, String currency, String type,
                String description, String relatedType, Long relatedId, String idempotencyKey) {
        this.walletId = walletId;
        this.transferId = transferId;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.currency = currency;
        this.type = type;
        this.description = description.length() > 300 ? description.substring(0, 300) : description;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.idempotencyKey = idempotencyKey;
    }
}
