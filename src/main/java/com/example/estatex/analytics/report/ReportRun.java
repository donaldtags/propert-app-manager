package com.example.estatex.analytics.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Every report produced: downloaded, sent now, or delivered on schedule. */
@Entity
@Table(name = "report_runs", indexes = @Index(name = "idx_report_run_generated", columnList = "generated_at"))
@Getter
@Setter
@NoArgsConstructor
public class ReportRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "schedule_id")
    private Long scheduleId;
    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType;
    @Column(nullable = false, length = 10)
    private String format;
    /** DOWNLOAD, SEND_NOW or SCHEDULE. */
    @Column(name = "run_trigger", nullable = false, length = 10)
    private String trigger;
    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
    @Column(name = "period_from")
    private LocalDate periodFrom;
    @Column(name = "period_to")
    private LocalDate periodTo;
    /** OK, FAILED or SKIPPED. */
    @Column(nullable = false, length = 10)
    private String status;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(nullable = false)
    private int recipients;
    @Column(length = 500)
    private String error;
}
