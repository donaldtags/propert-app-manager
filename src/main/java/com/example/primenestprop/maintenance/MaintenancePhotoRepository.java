package com.example.primenestprop.maintenance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenancePhotoRepository extends JpaRepository<MaintenancePhoto, Long> {
    List<MaintenancePhoto> findByMaintenanceRequestOrderByUploadedAtAsc(MaintenanceRequest maintenanceRequest);
}
