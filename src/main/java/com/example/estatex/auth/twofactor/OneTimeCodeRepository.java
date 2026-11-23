package com.example.estatex.auth.twofactor;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, Long> {
    Optional<OneTimeCode> findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(Long userId, String purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, String purpose, Instant after);
}
