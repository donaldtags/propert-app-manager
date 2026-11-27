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

/**
 * "This account has used this browser": a salted hash of the browser's random id, comparable across
 * accounts. Used only to spot someone referring themselves from a second account.
 */
@Entity
@Table(name = "referral_device_marks", indexes = {
        @Index(name = "uq_referral_device_mark", columnList = "user_id, device_hash", unique = true),
        @Index(name = "idx_referral_device_hash", columnList = "device_hash")
})
@Getter
@Setter
@NoArgsConstructor
public class ReferralDeviceMark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;
    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();
}
