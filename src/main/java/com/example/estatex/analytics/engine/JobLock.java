package com.example.estatex.analytics.engine;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Database-backed job lock. Plain auto-committed statements, so the lock row is visible to other
 * servers immediately and survives whatever happens to the run's own transactions.
 *
 * <ul>
 *   <li>A window that already SUCCEEDED is never processed again (idempotent).</li>
 *   <li>A fresh RUNNING row for the same job (any window) means someone else is working: skip.</li>
 *   <li>A RUNNING row whose heartbeat is older than {@code staleAfter} came from a crashed
 *       worker; it is marked ABANDONED by exactly one taker (conditional update).</li>
 *   <li>Two workers racing for the same window both insert attempt N; the unique key lets one win.</li>
 *   <li>The whole start-or-skip decision runs while holding the job's row in
 *       {@code analytics_job_locks} ({@code SELECT ... FOR UPDATE}), so workers decide one at a
 *       time and each sees the other's committed RUNNING row. (A run's id is allocated before its
 *       row is visible, so without this two simultaneous starts could each miss the other.)</li>
 * </ul>
 */
@Component
public class JobLock {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JobLock(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
    }

    /** @return the new run's id, or empty if the window is done or held by someone else. */
    public Optional<Long> acquire(String job, Instant windowStart, Instant windowEnd, Instant now, Duration staleAfter,
                                  String triggeredBy, String worker, boolean allowRerun) {
        // Databases keep microseconds; Linux clocks give nanoseconds. Round first, or the run row we
        // insert can't be found again by its window start (and duplicate checks miss it).
        Instant start = micros(windowStart), end = micros(windowEnd), at = micros(now);
        ensureLockRow(job);
        return tx.execute(status -> {
            // Blocks until any other worker deciding for this job has committed its decision.
            jdbc.queryForList("select job_name from analytics_job_locks where job_name = ? for update", job);
            return decide(job, start, end, at, staleAfter, triggeredBy, worker, allowRerun, status);
        });
    }

    static Instant micros(Instant i) {
        return i == null ? null : i.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    /** Created on first use, in its own statement: a duplicate-key error inside the locking transaction would abort it on Postgres. */
    private void ensureLockRow(String job) {
        Integer exists = jdbc.queryForObject("select count(*) from analytics_job_locks where job_name = ?", Integer.class, job);
        if (exists != null && exists > 0) return;
        try {
            jdbc.update("insert into analytics_job_locks (job_name) values (?)", job);
        } catch (DataIntegrityViolationException createdByAnotherWorker) {
            // fine: it exists now
        }
    }

    private Optional<Long> decide(String job, Instant windowStart, Instant windowEnd, Instant now, Duration staleAfter,
                                  String triggeredBy, String worker, boolean allowRerun,
                                  org.springframework.transaction.TransactionStatus status) {
        Timestamp staleCutoff = Timestamp.from(now.minus(staleAfter));
        List<Map<String, Object>> running = jdbc.queryForList(
                "select id, heartbeat_at from analytics_job_runs where job_name = ? and status = 'RUNNING'", job);
        for (Map<String, Object> r : running) {
            Instant heartbeat = instant(r.get("heartbeat_at"));
            if (!heartbeat.isBefore(staleCutoff.toInstant())) return Optional.empty();
            int taken = jdbc.update("update analytics_job_runs set status = 'ABANDONED', finished_at = ?, "
                            + "error = 'No heartbeat: the worker stopped (restart or crash). Taken over.' "
                            + "where id = ? and status = 'RUNNING' and heartbeat_at < ?",
                    Timestamp.from(now), ((Number) r.get("id")).longValue(), staleCutoff);
            if (taken == 0) return Optional.empty();
        }
        List<Map<String, Object>> previous = jdbc.queryForList(
                "select attempt, status from analytics_job_runs where job_name = ? and window_start = ? order by attempt desc",
                job, Timestamp.from(windowStart));
        int attempt = 1;
        if (!previous.isEmpty()) {
            Object latest = previous.get(0).get("status");
            // Someone started this window since the check above (stale runs were already released there).
            if ("RUNNING".equals(latest)) return Optional.empty();
            if ("SUCCEEDED".equals(latest) && !allowRerun) return Optional.empty();
            attempt = ((Number) previous.get(0).get("attempt")).intValue() + 1;
        }
        try {
            jdbc.update("insert into analytics_job_runs (job_name, window_start, window_end, attempt, status, triggered_by, "
                            + "worker, started_at, heartbeat_at, records_read, records_rejected, metrics_written, step_retries) "
                            + "values (?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, 0, 0, 0, 0)",
                    job, Timestamp.from(windowStart), Timestamp.from(windowEnd), attempt, triggeredBy, worker,
                    Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException lostRace) {
            status.setRollbackOnly(); // Postgres aborts the transaction on a failed insert
            return Optional.empty();
        }
        Long id = jdbc.queryForObject("select id from analytics_job_runs where job_name = ? and window_start = ? and attempt = ?",
                Long.class, job, Timestamp.from(windowStart), attempt);
        // Two workers can pass the first check at the same moment with different windows (e.g. a manual
        // run and the scheduled one). The younger row steps aside, so exactly one of them runs.
        Integer older = jdbc.queryForObject("select count(*) from analytics_job_runs where job_name = ? and status = 'RUNNING' "
                + "and id < ? and heartbeat_at >= ?", Integer.class, job, id, staleCutoff);
        if (older != null && older > 0) {
            jdbc.update("delete from analytics_job_runs where id = ? and status = 'RUNNING'", id);
            return Optional.empty();
        }
        return Optional.ofNullable(id);
    }

    /** Drivers differ: Postgres and MariaDB return Timestamp, H2 returns OffsetDateTime for zoned columns. */
    static Instant instant(Object v) {
        if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof java.time.OffsetDateTime o) return o.toInstant();
        if (v instanceof java.time.ZonedDateTime z) return z.toInstant();
        if (v instanceof Instant i) return i;
        if (v instanceof java.time.LocalDateTime l) return l.toInstant(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Unexpected timestamp type " + (v == null ? "null" : v.getClass()));
    }

    public void heartbeat(long runId, Instant now) {
        jdbc.update("update analytics_job_runs set heartbeat_at = ? where id = ?", Timestamp.from(now), runId);
    }
}
