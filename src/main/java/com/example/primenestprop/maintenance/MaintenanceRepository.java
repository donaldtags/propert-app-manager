package com.example.primenestprop.maintenance;

import com.example.primenestprop.property.Property;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRequest, Long> {
    List<MaintenanceRequest> findByProperty(Property property);
}
