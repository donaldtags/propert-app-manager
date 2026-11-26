package com.example.estatex.analytics.alert;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsAlertRepository extends JpaRepository<AnalyticsAlert, Long> {
    boolean existsByDedupeKey(String dedupeKey);

    boolean existsByRuleKeyAndSubjectKeyAndCreatedAtAfter(String ruleKey, String subjectKey, Instant after);

    List<AnalyticsAlert> findByDomainInOrderByCreatedAtDescIdDesc(Collection<String> domains, Pageable page);

    List<AnalyticsAlert> findByDomainInAndAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc(Collection<String> domains, Pageable page);

    long countByDomainInAndAcknowledgedAtIsNull(Collection<String> domains);

    List<AnalyticsAlert> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDomainInOrderByCreatedAtAsc(Instant from, Instant to,
                                                                                                          Collection<String> domains);
}
