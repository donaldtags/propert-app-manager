package com.example.primenestprop.maintenance;

import static com.example.primenestprop.maintenance.MaintenanceDtos.MaintenanceResponse;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {
    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    MaintenanceResponse create(@Valid @RequestBody MaintenanceDtos.CreateMaintenanceRequest request) {
        return MaintenanceResponse.from(service.create(request));
    }

    @GetMapping
    List<MaintenanceResponse> list(@RequestParam Long propertyId) {
        return service.forProperty(propertyId).stream().map(MaintenanceResponse::from).toList();
    }

    @PatchMapping("/{id}/status")
    MaintenanceResponse updateStatus(@PathVariable Long id, @RequestParam MaintenanceStatus status) {
        return MaintenanceResponse.from(service.updateStatus(id, status));
    }
}
