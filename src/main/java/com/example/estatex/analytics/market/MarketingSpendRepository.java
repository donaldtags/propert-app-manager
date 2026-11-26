package com.example.estatex.analytics.market;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingSpendRepository extends JpaRepository<MarketingSpend, Long> {
    List<MarketingSpend> findByMonthGreaterThanEqualAndMonthLessThanEqualOrderByMonthAscChannelAsc(LocalDate from, LocalDate to);
}
