package com.example.primenestprop.property;

import static com.example.primenestprop.property.PropertyDtos.PropertyResponse;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.subscription.SubscriptionFeature;
import com.example.primenestprop.subscription.SubscriptionService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    /** Requests that pass page/size get a normal UI page. Requests that don't (older
     * "give me everything for a picker" call sites) get a generous but still-bounded slice, so
     * behaviour for those callers is unchanged in practice while the query can never run away. */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int LEGACY_UNPAGINATED_SIZE = 500;

    private final PropertyService service;
    private final PropertyPassportService passportService;
    private final SubscriptionService subscriptions;

    public PropertyController(PropertyService service, PropertyPassportService passportService, SubscriptionService subscriptions) {
        this.service = service;
        this.passportService = passportService;
        this.subscriptions = subscriptions;
    }

    @PostMapping
    PropertyResponse create(@Valid @RequestBody PropertyDtos.CreatePropertyRequest request, @AuthenticationPrincipal AppUser currentUser) {
        if (!request.landlordId().equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "landlordId must match the authenticated user");
        }
        if (!currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            subscriptions.requirePropertyCapacity(currentUser);
        }
        Property saved = service.create(request);
        if (saved.isEscrowRequired() && !subscriptions.hasFeature(saved.getLandlord(), SubscriptionFeature.ESCROW)) {
            saved = service.forceDisableEscrow(saved);
        }
        return PropertyResponse.from(service.require(saved.getId()));
    }

    @GetMapping
    ResponseEntity<List<PropertyResponse>> search(
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
            @RequestParam(required = false) Boolean escrowAvailable,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        boolean paginated = page != null || size != null;
        int resolvedSize = size != null ? size : (paginated ? DEFAULT_PAGE_SIZE : LEGACY_UNPAGINATED_SIZE);
        Page<Property> results = service.searchPage(listingType, city, suburb, minPrice, maxPrice, bedrooms, bathrooms,
                diasporaFriendly, solarInstalled, backupPower, waterSource, furnished, internetAvailable, securityFeatures,
                parkingAvailable, petsAllowed, verifiedOnly, escrowAvailable, page != null ? page : 0, resolvedSize);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(results.getTotalElements()))
                .body(results.getContent().stream().map(PropertyResponse::from).toList());
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

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/photos/{photoId}")
    PropertyResponse deletePhoto(@PathVariable Long id, @PathVariable Long photoId, @AuthenticationPrincipal AppUser currentUser) {
        return PropertyResponse.from(service.deletePhoto(id, photoId, currentUser));
    }

    @GetMapping("/{id}/billing")
    PropertyBillingDtos.PropertyBillingResponse billing(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return service.billingStatus(id, currentUser);
    }

    @PatchMapping("/{id}")
    PropertyResponse update(
            @PathVariable Long id,
            @RequestBody PropertyDtos.UpdatePropertyRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return PropertyResponse.from(service.update(id, request, currentUser));
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
