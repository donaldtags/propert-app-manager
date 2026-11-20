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

/** One of ten single-use recovery codes, for when the phone is lost. Stored hashed. */
@Entity
@Table(name = "two_factor_backup_codes", indexes = @Index(name = "idx_backup_codes_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class BackupCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 64)
    private String codeHash;
    private Instant usedAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
