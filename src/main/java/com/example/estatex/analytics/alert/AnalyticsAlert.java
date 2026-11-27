package com.example.estatex.analytics.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One raised alert. The dedupe key makes sure the same finding is never raised twice. */
@Entity
@Table(name = "analytics_alerts", indexes = {
        @Index(name = "uq_analytics_alert_dedupe", columnList = "dedupe_key", unique = true),
        @Index(name = "idx_analytics_alert_subject", columnList = "rule_key, subject_key, created_at"),
        @Index(name = "idx_analytics_alert_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AnalyticsAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "rule_key", nullable = false, length = 40)
    private String ruleKey;
    @Column(nullable = false, length = 10)
    private String severity;
    /** MetricDefinition.Domain: decides who receives and sees it. */
    @Column(nullable = false, length = 20)
    private String domain;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(name = "subject_key", nullable = false, length = 200)
    private String subjectKey;
    @Column(name = "dedupe_key", nullable = false, length = 250)
    private String dedupeKey;
    @Column(name = "observation_id")
    private Long observationId;
    @Column(name = "job_run_id")
    private Long jobRunId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "acknowledged_by_user_id")
    private Long acknowledgedByUserId;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "recipients", nullable = false)
    private int recipients;
}
