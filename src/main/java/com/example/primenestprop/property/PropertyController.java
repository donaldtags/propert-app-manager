package com.example.primenestprop.property;

import static com.example.primenestprop.property.PropertyDtos.PropertyResponse;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
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

    public PropertyController(PropertyService service) {
        this.service = service;
    }

    @PostMapping
    PropertyResponse create(@Valid @RequestBody PropertyDtos.CreatePropertyRequest request) {
        return PropertyResponse.from(service.create(request));
    }

    @GetMapping
    List<PropertyResponse> search(
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String suburb,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer bedrooms
    ) {
        return service.search(listingType, city, suburb, maxPrice, bedrooms).stream()
                .map(PropertyResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    PropertyResponse get(@PathVariable Long id) {
        return PropertyResponse.from(service.require(id));
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
