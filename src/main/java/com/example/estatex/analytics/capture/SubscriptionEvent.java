package com.example.estatex.analytics.capture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only history of each subscription's plan and status, with the plan's monthly price at
 * that moment. Subscriptions only keep their current state, so without this MRR, churn and
 * upgrades over time can't be measured. Written by {@link SubscriptionHistoryRecorder}; the
 * BASELINE rows come from migration V10.
 */
@Entity
@Table(name = "subscription_events", indexes = {
        @Index(name = "idx_subscription_events_sub", columnList = "subscription_id, occurred_at"),
        @Index(name = "idx_subscription_events_time", columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    /** BASELINE, CREATED or CHANGED. */
    @Column(name = "event_type", nullable = false, length = 12)
    private String eventType;
    @Column(nullable = false, length = 20)
    private String plan;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "monthly_price", precision = 19, scale = 2)
    private BigDecimal monthlyPrice;
    @Column(length = 3)
    private String currency;
    @Column(name = "previous_plan", length = 20)
    private String previousPlan;
    @Column(name = "previous_status", length = 20)
    private String previousStatus;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Column(nullable = false, length = 20)
    private String source;
}
