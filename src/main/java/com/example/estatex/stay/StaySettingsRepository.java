package com.example.estatex.stay;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaySettingsRepository extends JpaRepository<StaySettings, Long> {
    List<StaySettings> findByPropertyIdIn(Collection<Long> propertyIds);
}
