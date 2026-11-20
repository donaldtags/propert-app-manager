package com.example.estatex.auth.twofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The half-finished sign-in between "password correct" and "code correct". The browser holds the
 * raw token; we keep its hash. Short-lived, single use, limited attempts.
 */
@Entity
@Table(name = "login_challenges")
@Getter
@Setter
@NoArgsConstructor
public class LoginChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
    private int attempts;
    @Column(length = 100)
    private String deviceId;
    @Column(length = 300)
    private String userAgent;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
