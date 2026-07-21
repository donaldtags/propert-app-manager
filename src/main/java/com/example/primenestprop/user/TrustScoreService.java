package com.example.primenestprop.user;

import com.example.primenestprop.chat.ResponseRateService;
import com.example.primenestprop.escrow.EscrowRepository;
import com.example.primenestprop.escrow.EscrowStatus;
import com.example.primenestprop.kyc.KycStatus;
import com.example.primenestprop.kyc.KycSubmissionRepository;
import com.example.primenestprop.lease.LeaseRepository;
import com.example.primenestprop.lease.LeaseStatus;
import com.example.primenestprop.review.LandlordRatingRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes a 0-100 Trust Score from real platform activity - identity verification, escrow
 * history, lease history, landlord ratings, message responsiveness, and business verification -
 * rather than the static placeholder every user used to carry. Recomputed (and persisted)
 * whenever a user's profile is fetched by id.
 */
@Service
public class TrustScoreService {
    private static final List<LeaseStatus> COMPLETED_LEASE_STATUSES =
            List.of(LeaseStatus.SIGNED, LeaseStatus.ACTIVE, LeaseStatus.ENDED);

    private final EscrowRepository escrows;
    private final LeaseRepository leases;
    private final LandlordRatingRepository ratings;
    private final KycSubmissionRepository kycSubmissions;
    private final UserRepository users;
    private final ResponseRateService responseRateService;

    public TrustScoreService(
            EscrowRepository escrows,
            LeaseRepository leases,
            LandlordRatingRepository ratings,
            KycSubmissionRepository kycSubmissions,
            UserRepository users,
            ResponseRateService responseRateService
    ) {
        this.escrows = escrows;
        this.leases = leases;
        this.ratings = ratings;
        this.kycSubmissions = kycSubmissions;
        this.users = users;
        this.responseRateService = responseRateService;
    }

    @Transactional
    public int recompute(AppUser user) {
        int score = compute(user);
        if (score != user.getTrustScore()) {
            user.setTrustScore(score);
            users.save(user);
        }
        return score;
    }

    private int compute(AppUser user) {
        int score = 20;

        if (user.isIdentityVerified()) score += 20;
        if (user.isFaceVerified()) score += 15;
        if (user.getPhone() != null && !user.getPhone().isBlank()) score += 5;
        if (kycSubmissions.findByUserAndStatus(user, KycStatus.APPROVED).isPresent()) score += 10;

        long accountAgeDays = user.getCreatedAt() == null
                ? 0
                : Duration.between(user.getCreatedAt(), Instant.now()).toDays();
        if (accountAgeDays >= 90) score += 5;
        if (accountAgeDays >= 365) score += 5;

        boolean hasReleasedEscrow = escrows.existsByPayerAndStatus(user, EscrowStatus.RELEASED)
                || escrows.existsByBeneficiaryAndStatus(user, EscrowStatus.RELEASED);
        if (hasReleasedEscrow) score += 10;
        boolean hasDisputedEscrow = escrows.existsByPayerAndStatus(user, EscrowStatus.DISPUTED)
                || escrows.existsByBeneficiaryAndStatus(user, EscrowStatus.DISPUTED);
        if (hasDisputedEscrow) score -= 20;

        long completedLeaseCount = leases.countByTenantAndStatusIn(user, COMPLETED_LEASE_STATUSES)
                + leases.countByLandlordAndStatusIn(user, COMPLETED_LEASE_STATUSES)
                + leases.countByProperty_AgentAndStatusIn(user, COMPLETED_LEASE_STATUSES);
        if (completedLeaseCount >= 1) score += 10;
        if (completedLeaseCount >= 3) score += 5;

        var landlordRatings = ratings.findByLandlordOrderByCreatedAtDesc(user);
        if (!landlordRatings.isEmpty()) {
            double average = landlordRatings.stream().mapToInt(r -> r.getRating()).average().orElse(0);
            score += (int) Math.round((average / 5.0) * 15);
        }

        if (user.isBusinessVerified()) score += 5;

        var response = responseRateService.compute(user);
        if (response.responseRatePercent() != null && response.responseRatePercent() >= 80) {
            score += 5;
        }

        return Math.max(0, Math.min(100, score));
    }
}
