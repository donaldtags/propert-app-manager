package com.example.primenestprop.kyc;

import com.example.primenestprop.escrow.EscrowService;
import com.example.primenestprop.user.AppUser;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class VerificationLevelService {
    private static final int TRUSTED_MIN_TRUST_SCORE = 80;
    private static final long TRUSTED_MIN_ACCOUNT_AGE_DAYS = 90;

    private final EscrowService escrows;

    public VerificationLevelService(EscrowService escrows) {
        this.escrows = escrows;
    }

    public Result levelFor(AppUser user) {
        boolean emailVerified = hasText(user.getEmail());
        boolean phoneVerified = hasText(user.getPhone());
        boolean identityVerified = user.isIdentityVerified();
        boolean faceVerified = user.isFaceVerified();
        long accountAgeDays = user.getCreatedAt() == null
                ? 0
                : Duration.between(user.getCreatedAt(), Instant.now()).toDays();
        boolean trustedUser = faceVerified
                && user.getTrustScore() >= TRUSTED_MIN_TRUST_SCORE
                && accountAgeDays >= TRUSTED_MIN_ACCOUNT_AGE_DAYS
                && !escrows.hasDisputedEscrow(user);

        int level = 0;
        if (emailVerified) {
            level = 1;
        }
        if (phoneVerified) {
            level = 2;
        }
        if (identityVerified) {
            level = 3;
        }
        if (faceVerified) {
            level = 4;
        }
        if (trustedUser) {
            level = 5;
        }

        return new Result(level, labelFor(level), emailVerified, phoneVerified, identityVerified, faceVerified, trustedUser);
    }

    private String labelFor(int level) {
        return switch (level) {
            case 1 -> "Email on File";
            case 2 -> "Phone on File";
            case 3 -> "Identity Verified";
            case 4 -> "Face Verified";
            case 5 -> "Trusted User";
            default -> "Guest";
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Result(
            int level,
            String label,
            boolean emailVerified,
            boolean phoneVerified,
            boolean identityVerified,
            boolean faceVerified,
            boolean trustedUser
    ) {
    }
}
