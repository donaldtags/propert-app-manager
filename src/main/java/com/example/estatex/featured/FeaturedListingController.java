package com.example.estatex.featured;

import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FeaturedListingController {
    private final FeaturedListingService service;

    public FeaturedListingController(FeaturedListingService service) {
        this.service = service;
    }

    @GetMapping("/featured-listings/settings")
    FeaturedListingDtos.SettingsResponse settings() {
        return FeaturedListingDtos.SettingsResponse.from(service.settings());
    }

    @PutMapping("/featured-listings/settings")
    FeaturedListingDtos.SettingsResponse updateSettings(
            @Valid @RequestBody FeaturedListingDtos.UpdateSettingsRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return FeaturedListingDtos.SettingsResponse.from(service.updateSettings(request, currentUser));
    }

    @PostMapping("/properties/{id}/feature/checkout")
    PaymentDtos.CheckoutResponse featureCheckout(
            @PathVariable Long id,
            @Valid @RequestBody PaymentDtos.CheckoutRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return service.initiateFeatureCheckout(id, request.provider(), currentUser);
    }
}