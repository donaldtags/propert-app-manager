package com.example.primenestprop.featured;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.PlatformAccountService;
import com.example.primenestprop.payment.Payment;
import com.example.primenestprop.payment.PaymentDtos;
import com.example.primenestprop.payment.PaymentService;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets a landlord/agent pay to feature one of their own listings. The price and how long a
 * feature lasts are both admin-controlled ({@link FeaturedListingSettings}), not hardcoded, so
 * this is a real (if in-app) revenue lever for the platform, not just a cosmetic toggle.
 */
@Service
public class FeaturedListingService {
    private final FeaturedListingSettingsRepository settingsRepository;
    private final PropertyService properties;
    private final PaymentService payments;
    private final PlatformAccountService platformAccountService;

    public FeaturedListingService(
            FeaturedListingSettingsRepository settingsRepository,
            PropertyService properties,
            PaymentService payments,
            PlatformAccountService platformAccountService
    ) {
        this.settingsRepository = settingsRepository;
        this.properties = properties;
        this.payments = payments;
        this.platformAccountService = platformAccountService;
    }

    @Transactional(readOnly = true)
    public FeaturedListingSettings settings() {
        return settingsRepository.findById(FeaturedListingSettings.SINGLETON_ID)
                .orElseGet(() -> settingsRepository.save(new FeaturedListingSettings()));
    }

    @Transactional
    public FeaturedListingSettings updateSettings(FeaturedListingDtos.UpdateSettingsRequest request, AppUser currentUser) {
        if (!currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can change the featured listing price");
        }
        FeaturedListingSettings settings = settings();
        settings.setPrice(request.price());
        settings.setCurrency(request.currency().toUpperCase());
        settings.setDurationDays(request.durationDays());
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }

    @Transactional
    public FeaturedListingDtos.FeatureListingResponse feature(Long propertyId, AppUser currentUser) {
        Property property = properties.require(propertyId);
        boolean owns = property.getLandlord().getId().equals(currentUser.getId())
                || (property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId()));
        if (!owns && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the listing's landlord or agent can feature it");
        }

        FeaturedListingSettings settings = settings();
        AppUser platformAccount = platformAccountService.billingAccount();

        Payment payment = payments.create(new PaymentDtos.CreatePaymentRequest(
                platformAccount.getId(),
                property.getId(),
                null,
                settings.getPrice(),
                settings.getCurrency(),
                "platform",
                "Featured listing: " + property.getTitle()
        ), currentUser);
        payments.markSuccessful(payment.getId(), platformAccount);

        Instant featuredUntil = Instant.now().plus(settings.getDurationDays(), ChronoUnit.DAYS);
        property.setFeatured(true);
        property.setFeaturedUntil(featuredUntil);

        return new FeaturedListingDtos.FeatureListingResponse(
                property.getId(), true, featuredUntil, payment.getId(), settings.getPrice(), settings.getCurrency());
    }
}