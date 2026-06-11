package com.example.primenestprop.maintenance;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.UserService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {
    private final MaintenanceRepository requests;
    private final PropertyService properties;
    private final UserService users;

    public MaintenanceService(MaintenanceRepository requests, PropertyService properties, UserService users) {
        this.requests = requests;
        this.properties = properties;
        this.users = users;
    }

    @Transactional
    public MaintenanceRequest create(MaintenanceDtos.CreateMaintenanceRequest request) {
        MaintenanceRequest maintenance = new MaintenanceRequest();
        maintenance.setProperty(properties.require(request.propertyId()));
        maintenance.setRequester(users.require(request.requesterId()));
        maintenance.setCategory(request.category());
        maintenance.setPriority(request.priority() == null || request.priority().isBlank() ? "NORMAL" : request.priority());
        maintenance.setDescription(request.description());
        return requests.save(maintenance);
    }

    public MaintenanceRequest require(Long id) {
        return requests.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Maintenance request not found"));
    }

    public List<MaintenanceRequest> forProperty(Long propertyId) {
        Property property = properties.require(propertyId);
        return requests.findByProperty(property);
    }

    @Transactional
    public MaintenanceRequest updateStatus(Long id, MaintenanceStatus status) {
        MaintenanceRequest request = require(id);
        request.setStatus(status);
        if (status == MaintenanceStatus.RESOLVED) {
            request.setResolvedAt(Instant.now());
        }
        return request;
    }
}
