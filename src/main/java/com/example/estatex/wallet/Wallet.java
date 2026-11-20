package com.example.estatex.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A ledger account. A user wallet belongs to one user in one currency and caches its balance
 * (always equal to the sum of its entries, changed only under a row lock). A system account
 * (systemCode set, userId null) is the other side of every movement - money arriving from a
 * gateway, collected by EstateX, or on its way out to a bank - and keeps no cached balance, so
 * it never becomes a hot row that every wallet operation has to lock.
 */
@Entity
@Table(name = "wallets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallets_user_currency", columnNames = {"user_id", "currency"}),
        @UniqueConstraint(name = "uk_wallets_system_currency", columnNames = {"system_code", "currency"})
})
@Getter
@Setter
@NoArgsConstructor
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "system_code", length = 40)
    private String systemCode;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Cached for user wallets only; system accounts are summed from their entries when needed. */
    @Column(nullable = false)
    private long balanceCents;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt;

    public boolean isSystem() {
        return systemCode != null;
    }
}
