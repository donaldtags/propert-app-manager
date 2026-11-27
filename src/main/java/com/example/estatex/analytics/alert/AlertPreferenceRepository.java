package com.example.estatex.analytics.alert;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertPreferenceRepository extends JpaRepository<AlertPreference, Long> {
    List<AlertPreference> findByUserId(Long userId);

    Optional<AlertPreference> findByUserIdAndRuleKey(Long userId, String ruleKey);
}
