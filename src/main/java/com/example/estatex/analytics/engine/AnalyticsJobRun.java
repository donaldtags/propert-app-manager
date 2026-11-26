package com.example.estatex.analytics.engine;

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

/**
 * One execution of an analytics job. Doubles as the lock: the unique key on
 * (job, window start, attempt) means only one worker can start a given window, on any number of
 * servers. A run that stops sending heartbeats is taken over after {@code staleAfter}.
 */
@Entity
@Table(name = "analytics_job_runs", indexes = {
        @Index(name = "uq_analytics_job_window", columnList = "job_name, window_start, attempt", unique = true),
        @Index(name = "idx_analytics_job_status", columnList = "job_name, status, window_end")
})
@Getter
@Setter
@NoArgsConstructor
public class AnalyticsJobRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 30)
    private String jobName;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    @Column(nullable = false)
    private int attempt;
    /** RUNNING, SUCCEEDED, FAILED or ABANDONED (a crashed run someone else took over). */
    @Column(nullable = false, length = 12)
    private String status;
    /** SCHEDULE, STARTUP, MANUAL or NIGHTLY. */
    @Column(name = "triggered_by", length = 12)
    private String triggeredBy;
    @Column(length = 80)
    private String worker;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "records_read", nullable = false)
    private long recordsRead;
    @Column(name = "records_rejected", nullable = false)
    private long recordsRejected;
    @Column(name = "metrics_written", nullable = false)
    private long metricsWritten;
    @Column(name = "step_retries", nullable = false)
    private int stepRetries;

    /** JSON array of {name, status, ms, detail}. Long text: two 8,000-character VARCHARs overflow MariaDB's row limit. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
    @Column(name = "steps_json")
    private String stepsJson;
    /** JSON array of data-quality warnings. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
    @Column(name = "warnings_json")
    private String warningsJson;
    @Column(length = 2000)
    private String error;
}
