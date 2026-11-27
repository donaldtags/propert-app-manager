package com.example.estatex.analytics.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.example.estatex.common.BusinessTime;

/** What the dashboard's freshness indicator and the engine page show. */
@Service
public class AnalyticsEngineStatusService {
    /** Older than this and the dashboard warns that figures are stale. */
    static final Duration STALE_AFTER = Duration.ofHours(2);

    private final AnalyticsJobRunRepository runs;
    private final JdbcTemplate jdbc;
    private final BusinessTime time;
    private final ObjectMapper json;

    AnalyticsEngineStatusService(AnalyticsJobRunRepository runs, JdbcTemplate jdbc, BusinessTime time, ObjectMapper json) {
        this.runs = runs;
        this.jdbc = jdbc;
        this.time = time;
        this.json = json;
    }

    public record RunView(Long id, String job, String status, String triggeredBy, Instant windowStart, Instant windowEnd,
                          int attempt, Instant startedAt, Instant finishedAt, Long durationMs, long recordsRead,
                          long recordsRejected, long metricsWritten, int stepRetries, JsonNode steps, JsonNode warnings,
                          String error) {
    }

    public record Status(
            /** UP_TO_DATE, RUNNING, STALE, FAILED or NEVER_RUN. */
            String state,
            boolean stale,
            Instant lastSuccessAt,
            Instant dataThrough,
            Instant nextScheduledAt,
            Instant historyStartsAt,
            long openDuplicatePairs,
            RunView lastRun,
            RunView lastSuccess) {
    }

    public Status status() {
        Instant now = time.clock().instant();
        AnalyticsJobRun success = runs.findFirstByStatusOrderByFinishedAtDesc("SUCCEEDED").orElse(null);
        AnalyticsJobRun last = runs.findByOrderByIdDesc(PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        Instant lastSuccessAt = success == null ? null : success.getFinishedAt();
        boolean stale = lastSuccessAt == null || Duration.between(lastSuccessAt, now).compareTo(STALE_AFTER) > 0;
        String state;
        if (last == null) state = "NEVER_RUN";
        else if ("RUNNING".equals(last.getStatus())) state = "RUNNING";
        else if ("FAILED".equals(last.getStatus())) state = "FAILED";
        else if (stale) state = "STALE";
        else state = "UP_TO_DATE";
        Instant historyStart = jdbc.query("select min(occurred_at) from property_events",
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
        Long duplicates = jdbc.queryForObject("select count(*) from listing_duplicate_candidates where status = 'OPEN'", Long.class);
        return new Status(state, stale, lastSuccessAt, success == null ? null : success.getWindowEnd(),
                nextRun(now), historyStart, duplicates == null ? 0 : duplicates,
                last == null ? null : view(last), success == null ? null : view(success));
    }

    public List<RunView> recent(int limit) {
        return runs.findByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 200)))).stream().map(this::view).toList();
    }

    Instant nextRun(Instant now) {
        ZoneId zone = ZoneId.of("Africa/Harare");
        ZonedDateTime next = CronExpression.parse(AnalyticsScheduler.HOURLY_CRON).next(now.atZone(zone));
        return next == null ? null : next.toInstant();
    }

    RunView view(AnalyticsJobRun r) {
        Long duration = r.getFinishedAt() == null ? null : Duration.between(r.getStartedAt(), r.getFinishedAt()).toMillis();
        return new RunView(r.getId(), r.getJobName(), r.getStatus(), r.getTriggeredBy(), r.getWindowStart(), r.getWindowEnd(),
                r.getAttempt(), r.getStartedAt(), r.getFinishedAt(), duration, r.getRecordsRead(), r.getRecordsRejected(),
                r.getMetricsWritten(), r.getStepRetries(), parse(r.getStepsJson()), parse(r.getWarningsJson()), r.getError());
    }

    private JsonNode parse(String s) {
        if (s == null || s.isBlank()) return json.createArrayNode();
        try {
            return json.readTree(s);
        } catch (RuntimeException e) {
            return json.createArrayNode();
        }
    }
}
