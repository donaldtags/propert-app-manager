package com.example.primenestprop.property;

import static com.example.primenestprop.property.PropertyDtos.PropertyResponse;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/properties")
public class PropertyController {
    private final PropertyService service;
    private final PropertyPassportService passportService;

    public PropertyController(PropertyService service, PropertyPassportService passportService) {
        this.service = service;
        this.passportService = passportService;
    }

    @PostMapping
    PropertyResponse create(@Valid @RequestBody PropertyDtos.CreatePropertyRequest request, @AuthenticationPrincipal AppUser currentUser) {
        if (!request.landlordId().equals(currentUser.getId()) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "landlordId must match the authenticated user");
        }
        return PropertyResponse.from(service.create(request));
    }

    @GetMapping
    List<PropertyResponse> search(
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String suburb,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) Integer bathrooms,
            @RequestParam(required = false) Boolean diasporaFriendly,
            @RequestParam(required = false) Boolean solarInstalled,
            @RequestParam(required = false) Boolean backupPower,
            @RequestParam(required = false) WaterSource waterSource,
            @RequestParam(required = false) Boolean furnished,
            @RequestParam(required = false) Boolean internetAvailable,
            @RequestParam(required = false) Boolean securityFeatures,
            @RequestParam(required = false) Boolean parkingAvailable,
            @RequestParam(required = false) Boolean petsAllowed,
            @RequestParam(required = false) Boolean verifiedOnly,
            @RequestParam(required = false) Boolean escrowAvailable
    ) {
        return service.search(listingType, city, suburb, minPrice, maxPrice, bedrooms, bathrooms, diasporaFriendly,
                        solarInstalled, backupPower, waterSource, furnished, internetAvailable, securityFeatures,
                        parkingAvailable, petsAllowed, verifiedOnly, escrowAvailable).stream()
                .map(PropertyResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    PropertyResponse get(@PathVariable Long id) {
        return PropertyResponse.from(service.require(id));
    }

    @GetMapping("/{id}/passport")
    PropertyPassportDtos.PropertyPassportResponse passport(@PathVariable Long id) {
        return passportService.build(id);
    }

    @PostMapping("/{id}/photos")
    PropertyResponse uploadPhotos(@PathVariable Long id, @ModelAttribute UploadPhotosRequest request) {
        return PropertyResponse.from(service.uploadPhotos(id, request.files()));
    }

    @PatchMapping("/{id}/verify")
    PropertyResponse verify(@PathVariable Long id, @Valid @RequestBody PropertyDtos.VerifyPropertyRequest request) {
        return PropertyResponse.from(service.verify(id, request));
    }

    @PostMapping("/{id}/inquiries")
    void submitInquiry(@PathVariable Long id, @Valid @RequestBody PropertyDtos.InquiryRequest request) {
        service.submitInquiry(id, request);
    }

    public record UploadPhotosRequest(List<MultipartFile> files) {
    }
}
