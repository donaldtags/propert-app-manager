package com.example.estatex.referral;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {
    Optional<ReferralCode> findByUserId(Long userId);

    Optional<ReferralCode> findByCode(String code);
}

interface ReferralRepository extends JpaRepository<Referral, Long> {
    Optional<Referral> findByRefereeUserId(Long refereeUserId);

    List<Referral> findByReferrerUserIdOrderByRegisteredAtDesc(Long referrerUserId);

    long countByReferrerUserIdAndStatusInAndRewardedAtGreaterThanEqual(Long referrerUserId, Collection<ReferralStatus> statuses,
                                                                       Instant since);

    List<Referral> findByOrderByRegisteredAtDesc(Pageable page);

    Optional<Referral> findByQualifyingPaymentId(Long paymentId);
}

interface ReferralDeviceMarkRepository extends JpaRepository<ReferralDeviceMark, Long> {
    boolean existsByUserIdAndDeviceHash(Long userId, String deviceHash);

    List<ReferralDeviceMark> findByUserId(Long userId);
}
