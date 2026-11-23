package com.example.estatex.auth.twofactor;

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

/** A 6-digit code sent by SMS. Only its SHA-256 hash is stored. */
@Entity
@Table(name = "one_time_codes", indexes = @Index(name = "idx_one_time_codes_user_purpose", columnList = "user_id, purpose"))
@Getter
@Setter
@NoArgsConstructor
public class OneTimeCode {
    public static final String LOGIN = "LOGIN";
    public static final String ENABLE_SMS = "ENABLE_SMS";
    public static final String DISABLE = "DISABLE";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 30)
    private String purpose;
    @Column(nullable = false, length = 64)
    private String codeHash;
    @Column(nullable = false)
    private Instant expiresAt;
    private int attempts;
    private Instant consumedAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
