package com.example.estatex.auth;

import com.example.estatex.auth.twofactor.LoginChallengeRepository;
import com.example.estatex.common.ApiException;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops password and code guessing per ACCOUNT (the IP rate limit alone is easy to dodge with
 * many addresses): 5 wrong passwords or codes within 15 minutes lock sign-in for 15 minutes.
 * Unknown emails/phones are counted the same way in memory, so the response never reveals
 * whether an account exists.
 *
 * Failures are written in their own transaction: the sign-in that failed rolls back its own.
 */
@Service
public class LoginAttemptService {
    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCK = Duration.ofMinutes(15);
    static final String LOCKED_MESSAGE =
            "Too many sign-in attempts. Please wait 15 minutes and try again, or reset your password.";

    private final UserRepository users;
    private final LoginChallengeRepository challenges;
    private final Cache<String, Integer> unknownFailures = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(WINDOW)
            .build();

    public LoginAttemptService(UserRepository users, LoginChallengeRepository challenges) {
        this.users = users;
        this.challenges = challenges;
    }

    public void requireNotLocked(AppUser user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, LOCKED_MESSAGE);
        }
    }

    public void requireNotLocked(String unknownIdentifier) {
        Integer count = unknownFailures.getIfPresent(key(unknownIdentifier));
        if (count != null && count >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, LOCKED_MESSAGE);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        recordFailureInCurrentTransaction(userId);
    }

    /** A wrong code on a sign-in challenge: counts for the account, and burns the challenge after 5. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChallengeFailure(Long challengeId, Long userId) {
        challenges.findById(challengeId).ifPresent(c -> {
            c.setAttempts(c.getAttempts() + 1);
            if (c.getAttempts() >= MAX_FAILURES) {
                c.setUsedAt(Instant.now());
            }
        });
        recordFailureInCurrentTransaction(userId);
    }

    private void recordFailureInCurrentTransaction(Long userId) {
        users.findById(userId).ifPresent(user -> {
            Instant now = Instant.now();
            boolean windowExpired = user.getFailedLoginWindowStart() == null
                    || user.getFailedLoginWindowStart().plus(WINDOW).isBefore(now);
            int count = windowExpired || user.getFailedLoginAttempts() == null ? 1 : user.getFailedLoginAttempts() + 1;
            if (windowExpired) {
                user.setFailedLoginWindowStart(now);
            }
            if (count >= MAX_FAILURES) {
                user.setLockedUntil(now.plus(LOCK));
                user.setFailedLoginAttempts(0);
                user.setFailedLoginWindowStart(null);
            } else {
                user.setFailedLoginAttempts(count);
            }
        });
    }

    public void recordUnknownFailure(String identifier) {
        unknownFailures.asMap().merge(key(identifier), 1, Integer::sum);
    }

    /** Successful sign-in: start counting from zero again. Runs in the caller's transaction. */
    public void reset(AppUser user) {
        user.setFailedLoginAttempts(null);
        user.setFailedLoginWindowStart(null);
        user.setLockedUntil(null);
    }

    private static String key(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase();
    }
}
