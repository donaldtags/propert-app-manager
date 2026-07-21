package com.example.primenestprop.maintenance;

import static com.example.primenestprop.maintenance.MaintenanceDtos.MaintenancePhotoResponse;
import static com.example.primenestprop.maintenance.MaintenanceDtos.MaintenanceResponse;

import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {
    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    MaintenanceResponse create(@Valid @RequestBody MaintenanceDtos.CreateMaintenanceRequest request, @AuthenticationPrincipal AppUser currentUser) {
        return MaintenanceResponse.from(service.create(request, currentUser));
    }

    @GetMapping
    List<MaintenanceResponse> list(@RequestParam Long propertyId, @AuthenticationPrincipal AppUser currentUser) {
        return service.forProperty(propertyId, currentUser).stream()
                .map(r -> MaintenanceResponse.from(r, service.photosFor(r)))
                .toList();
    }

    @PatchMapping("/{id}/status")
    MaintenanceResponse updateStatus(
            @PathVariable Long id,
            @RequestParam MaintenanceStatus status,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return MaintenanceResponse.from(service.updateStatus(id, status, currentUser));
    }

    @PostMapping(value = "/{id}/photos", consumes = "multipart/form-data")
    List<MaintenancePhotoResponse> uploadPhotos(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return service.uploadPhotos(id, currentUser, files).stream().map(MaintenancePhotoResponse::from).toList();
    }

    @GetMapping("/{id}/photos")
    List<MaintenancePhotoResponse> listPhotos(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return service.listPhotos(id, currentUser).stream().map(MaintenancePhotoResponse::from).toList();
    }

    @PatchMapping("/{id}/assign-vendor")
    MaintenanceResponse assignVendor(
            @PathVariable Long id,
            @RequestParam Long vendorId,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        MaintenanceRequest request = service.assignVendor(id, vendorId, currentUser);
        return MaintenanceResponse.from(request, service.photosFor(request));
    }
}
