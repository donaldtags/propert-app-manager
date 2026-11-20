package com.example.estatex.auth.twofactor;

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

/** A browser/phone this user has signed in from before, so sign-ins from anywhere else raise an alert. */
@Entity
@Table(name = "known_devices", uniqueConstraints = @UniqueConstraint(name = "uk_known_devices_user_device",
        columnNames = {"user_id", "device_hash"}))
@Getter
@Setter
@NoArgsConstructor
public class KnownDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;
    @Column(length = 200)
    private String label;
    @Column(nullable = false)
    private Instant firstSeenAt = Instant.now();
    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();
}
