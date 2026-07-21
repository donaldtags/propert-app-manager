package com.example.primenestprop.maintenance;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRequest, Long> {

    @EntityGraph(attributePaths = {"property", "requester", "assignedVendor"})
    Optional<MaintenanceRequest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"property", "requester", "assignedVendor"})
    List<MaintenanceRequest> findByProperty(Property property);

    @EntityGraph(attributePaths = {"property", "requester", "assignedVendor"})
    List<MaintenanceRequest> findByPropertyIn(List<Property> properties);

    @EntityGraph(attributePaths = {"property", "requester", "assignedVendor"})
    List<MaintenanceRequest> findByRequesterOrderByCreatedAtDesc(AppUser requester);

    long countByStatusIn(List<MaintenanceStatus> statuses);
}
