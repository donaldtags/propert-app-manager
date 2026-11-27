package com.example.estatex.referral;

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

/** A user's personal referral code (one each, created the first time they open "Invite friends"). */
@Entity
@Table(name = "referral_codes", indexes = {
        @Index(name = "uq_referral_code", columnList = "code", unique = true),
        @Index(name = "uq_referral_code_user", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class ReferralCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 12)
    private String code;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
