package com.example.estatex.analytics.alert;

import com.example.estatex.analytics.metrics.MetricDefinition.Domain;
import java.math.BigDecimal;
import java.util.Set;

/**
 * The kinds of alert, with defaults an administrator can override ({@link AlertRule}).
 *
 * <p>Market rules fire only on observations that already passed a significance test (or, for
 * supply, a stock threshold), and then only when the change is at least {@code threshold}% with
 * at least {@code minSample} observations behind it. The cooldown stops the same rule firing again
 * for the same place within that many hours.
 */
public enum AlertKind {
    DEMAND_SURGE(Domain.DEMAND, "Rental or sale enquiries rose sharply", Set.of("ENQUIRIES_UP", "VIEWING_REQUESTS_UP"),
            "25", 30, 0.05, 72, "INFO", Unit.PERCENT),
    SUPPLY_DROP(Domain.MARKET, "Property supply fell", Set.of("ACTIVE_LISTINGS_DOWN", "NEW_LISTINGS_DOWN"),
            "20", 30, 0.05, 72, "WARNING", Unit.PERCENT),
    PRICE_JUMP(Domain.MARKET, "Asking prices jumped", Set.of("ASKING_PRICE_UP"),
            "15", 20, 0.05, 168, "WARNING", Unit.PERCENT),
    CATEGORY_LOW_SUPPLY(Domain.MARKET, "A property category is under-supplied for its demand", Set.of("CATEGORY_UNDERSUPPLIED"),
            "50", 30, 0.05, 168, "INFO", Unit.PERCENT),
    DUPLICATE_LISTINGS(Domain.PLATFORM, "Several listings look like the same property", Set.of(),
            "3", 1, 1, 24, "WARNING", Unit.COUNT),
    CANCELLATIONS_UP(Domain.FINANCE, "Subscription cancellations increased", Set.of("SUBSCRIPTION_CANCELLATIONS_UP"),
            "25", 10, 0.05, 168, "WARNING", Unit.PERCENT),
    PAYMENT_FAILURES_UP(Domain.FINANCE, "Payment failures increased", Set.of("PAYMENTS_FAILED_UP"),
            "25", 10, 0.05, 72, "WARNING", Unit.PERCENT),
    JOB_FAILED(Domain.PLATFORM, "An analytics run failed", Set.of(),
            "1", 1, 1, 6, "CRITICAL", Unit.COUNT),
    DATA_STALE(Domain.PLATFORM, "Analytics figures are stale", Set.of(),
            "2", 1, 1, 12, "CRITICAL", Unit.HOURS);

    /** What {@code threshold} is measured in. */
    public enum Unit { PERCENT, COUNT, HOURS }

    private final Domain domain;
    private final String label;
    private final Set<String> observationKinds;
    private final BigDecimal threshold;
    private final int minSample;
    private final double maxQValue;
    private final int cooldownHours;
    private final String severity;
    private final Unit unit;

    AlertKind(Domain domain, String label, Set<String> observationKinds, String threshold, int minSample, double maxQValue,
              int cooldownHours, String severity, Unit unit) {
        this.domain = domain;
        this.label = label;
        this.observationKinds = observationKinds;
        this.threshold = new BigDecimal(threshold);
        this.minSample = minSample;
        this.maxQValue = maxQValue;
        this.cooldownHours = cooldownHours;
        this.severity = severity;
        this.unit = unit;
    }

    public Domain domain() {
        return domain;
    }

    public String label() {
        return label;
    }

    public Set<String> observationKinds() {
        return observationKinds;
    }

    public Unit unit() {
        return unit;
    }

    /** A rule carrying this kind's defaults (not saved). */
    public AlertRule defaults() {
        AlertRule r = new AlertRule();
        r.setRuleKey(name());
        r.setEnabled(true);
        r.setThreshold(threshold);
        r.setMinSample(minSample);
        r.setMaxQValue(maxQValue);
        r.setCooldownHours(cooldownHours);
        r.setSeverity(severity);
        return r;
    }
}
