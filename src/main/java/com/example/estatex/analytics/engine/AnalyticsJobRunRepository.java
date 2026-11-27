package com.example.estatex.analytics.engine;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsJobRunRepository extends JpaRepository<AnalyticsJobRun, Long> {
    Optional<AnalyticsJobRun> findFirstByJobNameAndStatusOrderByWindowEndDesc(String jobName, String status);

    Optional<AnalyticsJobRun> findFirstByJobNameOrderByIdDesc(String jobName);

    Optional<AnalyticsJobRun> findFirstByStatusOrderByFinishedAtDesc(String status);

    List<AnalyticsJobRun> findByOrderByIdDesc(Pageable page);

    List<AnalyticsJobRun> findByJobNameAndWindowStartOrderByAttemptDesc(String jobName, java.time.Instant windowStart);
}
