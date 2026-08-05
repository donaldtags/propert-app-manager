package com.example.primenestprop.subscription;

import com.example.primenestprop.user.AppUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandlordSubscriptionRepository extends JpaRepository<LandlordSubscription, Long> {
    Optional<LandlordSubscription> findByUser(AppUser user);

    List<LandlordSubscription> findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus status, Instant instant);

    List<LandlordSubscription> findByStatusAndPastDueSinceBefore(SubscriptionStatus status, Instant instant);
}