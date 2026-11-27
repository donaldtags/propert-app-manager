package com.example.estatex.analytics.engine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per analytics job, used only as a mutex: {@link JobLock#acquire} locks it with
 * {@code SELECT ... FOR UPDATE} so that, per job, one worker at a time decides whether to start a
 * run. Without it, two workers inserting at the same moment could each miss the other's
 * not-yet-visible row and both run (QA finding F-4).
 */
@Entity
@Table(name = "analytics_job_locks")
@Getter
@NoArgsConstructor
public class AnalyticsJobLockRow {
    @Id
    @Column(name = "job_name", length = 80)
    private String jobName;
}
