package com.example.primenestprop.subscription;

import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "landlord_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class LandlordSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan = SubscriptionPlan.STARTER;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /** Null for STARTER (free, never expires). Set for paid plans; renewed by the billing sweep. */
    private Instant currentPeriodEnd;

    private Instant pastDueSince;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}