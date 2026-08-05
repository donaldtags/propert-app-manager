package com.example.primenestprop.subscription;

import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Seeds the PRD's suggested default pricing/limits for each plan tier, once per plan. Admin can
 * change all of this afterwards via the pricing settings screen - these are just starting points. */
@Component
@Order(11)
class SubscriptionPlanSeeder implements CommandLineRunner {
    private final SubscriptionPlanSettingsRepository settings;

    SubscriptionPlanSeeder(SubscriptionPlanSettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public void run(String... args) {
        seedIfMissing(SubscriptionPlan.STARTER, BigDecimal.ZERO, 1, false, false, false, false, false, false, false);
        seedIfMissing(SubscriptionPlan.GROWTH, new BigDecimal("15.00"), 10, true, true, true, true, true, true, true);
        seedIfMissing(SubscriptionPlan.PROFESSIONAL, new BigDecimal("49.00"), null, true, true, true, true, true, true, true);
    }

    private void seedIfMissing(
            SubscriptionPlan plan, BigDecimal monthlyPrice, Integer maxProperties,
            boolean escrow, boolean digitalLeases, boolean maintenance, boolean rentReminders,
            boolean aiPricing, boolean tenantPassport, boolean reports
    ) {
        if (settings.existsById(plan)) {
            return;
        }
        SubscriptionPlanSettings row = new SubscriptionPlanSettings(plan);
        row.setMonthlyPrice(monthlyPrice);
        row.setMaxProperties(maxProperties);
        row.setEscrowEnabled(escrow);
        row.setDigitalLeasesEnabled(digitalLeases);
        row.setMaintenanceCoordinationEnabled(maintenance);
        row.setRentRemindersEnabled(rentReminders);
        row.setAiPricingEnabled(aiPricing);
        row.setTenantPassportEnabled(tenantPassport);
        row.setReportsEnabled(reports);
        settings.save(row);
    }
}