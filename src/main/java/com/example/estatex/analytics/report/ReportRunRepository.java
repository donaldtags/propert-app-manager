package com.example.estatex.analytics.report;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {
    List<ReportRun> findAllByOrderByGeneratedAtDescIdDesc(Pageable page);
}
