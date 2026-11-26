package com.example.estatex.analytics.report;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {
    List<ReportSchedule> findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(Instant now);

    List<ReportSchedule> findAllByOrderByNameAsc();
}
