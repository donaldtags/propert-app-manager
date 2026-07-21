package com.example.primenestprop.maintenance;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import com.example.primenestprop.vendor.Vendor;
import com.example.primenestprop.vendor.VendorService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaintenanceService {
    private static final long MAX_PHOTO_SIZE = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final MaintenanceRepository requests;
    private final MaintenancePhotoRepository photos;
    private final PropertyService properties;
    private final UserService users;
    private final MaintenanceTriageService triageService;
    private final VendorService vendorService;
    private final Path photoStorageRoot;
    private final String publicBaseUrl;

    public MaintenanceService(
            MaintenanceRepository requests,
            MaintenancePhotoRepository photos,
            PropertyService properties,
            UserService users,
            MaintenanceTriageService triageService,
            VendorService vendorService,
            @Value("${app.storage.maintenance-photos:storage/maintenance-photos}") String photoStorageRoot,
            @Value("${app.public-base-url:http://localhost:8081}") String publicBaseUrl
    ) {
        this.requests = requests;
        this.photos = photos;
        this.properties = properties;
        this.users = users;
        this.triageService = triageService;
        this.vendorService = vendorService;
        this.photoStorageRoot = Path.of(photoStorageRoot);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public MaintenanceRequest create(MaintenanceDtos.CreateMaintenanceRequest request, AppUser requester) {
        MaintenanceRequest maintenance = new MaintenanceRequest();
        maintenance.setProperty(properties.require(request.propertyId()));
        maintenance.setRequester(requester);
        maintenance.setCategory(request.category());
        maintenance.setPriority(request.priority() == null || request.priority().isBlank()
                ? triageService.classify(request.category(), request.description())
                : request.priority());
        maintenance.setDescription(request.description());
        return requests.save(maintenance);
    }

    @Transactional
    public MaintenanceRequest assignVendor(Long id, Long vendorId, AppUser currentUser) {
        MaintenanceRequest request = require(id);
        Property property = request.getProperty();
        boolean isLandlordOrAgent = property.getLandlord().getId().equals(currentUser.getId())
                || (property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId()));
        if (!isLandlordOrAgent && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the property landlord, agent, or an admin can assign a vendor");
        }
        Vendor vendor = vendorService.require(vendorId);
        request.setAssignedVendor(vendor);
        if (request.getStatus() == MaintenanceStatus.OPEN) {
            request.setStatus(MaintenanceStatus.ASSIGNED);
        }
        return request;
    }

    @Transactional(readOnly = true)
    public MaintenanceRequest require(Long id) {
        return requests.findWithDetailsById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Maintenance request not found"));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> forProperty(Long propertyId, AppUser currentUser) {
        Property property = properties.require(propertyId);
        List<MaintenanceRequest> all = requests.findByProperty(property);
        boolean isLandlordOrAgent = property.getLandlord().getId().equals(currentUser.getId())
                || (property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId()));
        if (isLandlordOrAgent || currentUser.getRoles().contains(UserRole.ADMIN)) {
            return all;
        }
        return all.stream().filter(r -> r.getRequester().getId().equals(currentUser.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> forProperties(List<Property> propertyList) {
        return propertyList.isEmpty() ? List.of() : requests.findByPropertyIn(propertyList);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> forRequester(AppUser requester) {
        return requests.findByRequesterOrderByCreatedAtDesc(requester);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return requests.countByStatusIn(List.of(MaintenanceStatus.OPEN, MaintenanceStatus.ASSIGNED, MaintenanceStatus.IN_PROGRESS));
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return requests.count();
    }

    @Transactional
    public MaintenanceRequest updateStatus(Long id, MaintenanceStatus status, AppUser currentUser) {
        MaintenanceRequest request = require(id);
        Property property = request.getProperty();
        boolean isLandlordOrAgent = property.getLandlord().getId().equals(currentUser.getId())
                || (property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId()));
        if (!isLandlordOrAgent && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the property landlord, agent, or an admin can update this request");
        }
        request.setStatus(status);
        if (status == MaintenanceStatus.RESOLVED) {
            request.setResolvedAt(Instant.now());
        }
        return request;
    }

    @Transactional
    public List<MaintenancePhoto> uploadPhotos(Long requestId, AppUser currentUser, List<MultipartFile> files) {
        MaintenanceRequest request = require(requestId);
        assertRequesterOrLandlordOrAdmin(request, currentUser);
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one photo is required");
        }
        try {
            Files.createDirectories(photoStorageRoot.resolve(String.valueOf(requestId)));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare photo storage");
        }
        return files.stream().map(file -> saveOne(request, file)).toList();
    }

    @Transactional(readOnly = true)
    public List<MaintenancePhoto> listPhotos(Long requestId, AppUser currentUser) {
        MaintenanceRequest request = require(requestId);
        assertRequesterOrLandlordOrAdmin(request, currentUser);
        return photos.findByMaintenanceRequestOrderByUploadedAtAsc(request);
    }

    @Transactional(readOnly = true)
    public List<MaintenancePhoto> photosFor(MaintenanceRequest request) {
        return photos.findByMaintenanceRequestOrderByUploadedAtAsc(request);
    }

    private MaintenancePhoto saveOne(MaintenanceRequest request, MultipartFile file) {
        validatePhoto(file);
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "photo"
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        String storageName = UUID.randomUUID() + "-" + originalName;
        Path target = photoStorageRoot.resolve(String.valueOf(request.getId())).resolve(storageName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded photo");
        }
        MaintenancePhoto photo = new MaintenancePhoto();
        photo.setMaintenanceRequest(request);
        photo.setStorageKey(target.toString());
        photo.setPhotoUrl(publicBaseUrl + "/uploads/maintenance-photos/" + request.getId() + "/" + storageName);
        return photos.save(photo);
    }

    private void validatePhoto(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded photo is empty");
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Photo size must be 8MB or less");
        }
        if (!ALLOWED_PHOTO_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PNG, JPEG, and WebP photos are allowed");
        }
    }

    private void assertRequesterOrLandlordOrAdmin(MaintenanceRequest request, AppUser currentUser) {
        Property property = request.getProperty();
        boolean isLandlordOrAgent = property.getLandlord().getId().equals(currentUser.getId())
                || (property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId()));
        boolean isRequester = request.getRequester().getId().equals(currentUser.getId());
        if (!isLandlordOrAgent && !isRequester && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the requester, property landlord, agent, or an admin can access these photos");
        }
    }
}
