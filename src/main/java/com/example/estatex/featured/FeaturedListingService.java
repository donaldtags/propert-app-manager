package com.example.estatex.featured;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.PlatformAccountService;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.payment.PaymentSucceededEvent;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyService;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.event.EventListener;
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
        if (!currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can change the featured listing price");
        }
        FeaturedListingSettings settings = settings();
        settings.setPrice(request.price());
        settings.setCurrency(request.currency().toUpperCase());
        settings.setDurationDays(request.durationDays());
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }

    /** Starts a real Paynow/Stripe checkout for the featured-listing fee; the listing itself is
     * featured by {@link #onPaymentSucceeded} once the gateway confirms payment. */
    @Transactional
    public PaymentDtos.CheckoutResponse initiateFeatureCheckout(Long propertyId, String provider, AppUser currentUser) {
        Property property = properties.require(propertyId);
        boolean owns = property.getLandlord().getId().equals(currentUser.getId())
                || (property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId()));
        if (!owns && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the listing's landlord or agent can feature it");
        }

        FeaturedListingSettings settings = settings();
        AppUser platformAccount = platformAccountService.billingAccount();

        Payment payment = payments.create(
                new PaymentDtos.CreatePaymentRequest(
                        platformAccount.getId(),
                        property.getId(),
                        null,
                        settings.getPrice(),
                        settings.getCurrency(),
                        provider,
                        "Featured listing: " + property.getTitle()
                ),
                currentUser,
                PaymentPurpose.FEATURED_LISTING,
                null,
                null
        );
        return payments.initiateCheckout(payment.getId(), provider, currentUser);
    }

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Payment payment = event.payment();
        if (payment.getPurposeType() != PaymentPurpose.FEATURED_LISTING || payment.getProperty() == null) {
            return;
        }
        Property property = payment.getProperty();
        FeaturedListingSettings settings = settings();
        property.setFeatured(true);
        property.setFeaturedUntil(Instant.now().plus(settings.getDurationDays(), ChronoUnit.DAYS));
    }
}