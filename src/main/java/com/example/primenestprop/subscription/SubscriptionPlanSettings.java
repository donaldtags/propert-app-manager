package com.example.primenestprop.subscription;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin-editable price, property cap, and feature flags for one {@link SubscriptionPlan} tier.
 * One row per plan, seeded with the PRD's suggested defaults on first use. */
@Entity
@Table(name = "subscription_plan_settings")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlanSettings {
    @Id
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan;

    private BigDecimal monthlyPrice = BigDecimal.ZERO;
    private String currency = "USD";

    /** Null means unlimited active listings. */
    private Integer maxProperties;

    private boolean escrowEnabled;
    private boolean digitalLeasesEnabled;
    private boolean maintenanceCoordinationEnabled;
    private boolean rentRemindersEnabled;
    private boolean aiPricingEnabled;
    private boolean tenantPassportEnabled;
    private boolean reportsEnabled;

    private Instant updatedAt = Instant.now();

    public SubscriptionPlanSettings(SubscriptionPlan plan) {
        this.plan = plan;
    }

    public boolean supports(SubscriptionFeature feature) {
        return switch (feature) {
            case ESCROW -> escrowEnabled;
            case DIGITAL_LEASES -> digitalLeasesEnabled;
            case MAINTENANCE_REQUESTS -> maintenanceCoordinationEnabled;
            case RENT_REMINDERS -> rentRemindersEnabled;
            case AI_PRICING -> aiPricingEnabled;
            case TENANT_PASSPORT -> tenantPassportEnabled;
            case REPORTS -> reportsEnabled;
        };
    }
}