package com.example.estatex.analytics.report;

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
 * A report emailed on a schedule. Each delivery covers the period that has just finished (the
 * last day, week, month, quarter or year) and goes only to staff accounts allowed to see it.
 */
@Entity
@Table(name = "report_schedules", indexes = @Index(name = "idx_report_schedule_due", columnList = "enabled, next_run_at"))
@Getter
@Setter
@NoArgsConstructor
public class ReportSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType;
    @Column(nullable = false, length = 10)
    private String format;
    /** DAY, WEEK, MONTH, QUARTER or YEAR. */
    @Column(nullable = false, length = 10)
    private String frequency;
    @Column(length = 80)
    private String city;
    @Column(length = 80)
    private String suburb;
    @Column(length = 40)
    private String category;
    /** Comma-separated user ids of the staff who receive it. */
    @Column(name = "recipient_ids", nullable = false, length = 500)
    private String recipientIds;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;
    @Column(name = "last_run_at")
    private Instant lastRunAt;
    @Column(name = "last_status", length = 10)
    private String lastStatus;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
