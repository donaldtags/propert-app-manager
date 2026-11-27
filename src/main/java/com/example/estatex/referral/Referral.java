package com.example.estatex.referral;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One person who signed up with someone's code, and what came of it: the discount they got on
 * their first subscription payment, and the wallet credit their referrer got for it.
 */
@Entity
@Table(name = "referrals", indexes = {
        @Index(name = "uq_referral_referee", columnList = "referee_user_id", unique = true),
        @Index(name = "idx_referral_referrer", columnList = "referrer_user_id, rewarded_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Referral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 12)
    private String code;
    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;
    @Column(name = "referee_user_id", nullable = false)
    private Long refereeUserId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferralStatus status = ReferralStatus.REGISTERED;
    @Column(name = "block_reason", length = 200)
    private String blockReason;

    @Column(name = "discount_percent")
    private Integer discountPercent;
    @Column(name = "discount_cents")
    private Long discountCents;
    @Column(name = "reward_cents")
    private Long rewardCents;
    @Column(length = 3)
    private String currency;
    @Column(name = "qualifying_payment_id")
    private Long qualifyingPaymentId;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt = Instant.now();
    @Column(name = "qualified_at")
    private Instant qualifiedAt;
    @Column(name = "rewarded_at")
    private Instant rewardedAt;
    @Column(name = "reversed_at")
    private Instant reversedAt;
    @Column(name = "reversal_reason", length = 300)
    private String reversalReason;
}
