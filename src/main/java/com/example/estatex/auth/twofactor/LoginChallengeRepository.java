package com.example.estatex.auth.twofactor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {
    Optional<LoginChallenge> findByTokenHash(String tokenHash);
}
