package com.example.estatex.analytics.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An administrator's settings for one {@link AlertKind}. A kind with no row uses its defaults. */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
public class AlertRule {
    @Id
    @Column(name = "rule_key", length = 40)
    private String ruleKey;
    @Column(nullable = false)
    private boolean enabled;
    /** Smallest change, in percent (or the count, for count-based rules), that raises an alert. */
    @Column(name = "threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal threshold;
    @Column(name = "min_sample", nullable = false)
    private int minSample;
    @Column(name = "max_q_value", nullable = false)
    private double maxQValue;
    @Column(name = "cooldown_hours", nullable = false)
    private int cooldownHours;
    /** INFO, WARNING or CRITICAL. */
    @Column(nullable = false, length = 10)
    private String severity;
    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
    @Column(name = "updated_at")
    private Instant updatedAt;
}
