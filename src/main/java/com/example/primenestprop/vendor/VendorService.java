package com.example.primenestprop.vendor;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorService {
    private final VendorRepository vendors;
    private final ServiceBookingRepository bookings;

    public VendorService(VendorRepository vendors, ServiceBookingRepository bookings) {
        this.vendors = vendors;
        this.bookings = bookings;
    }

    @Transactional
    public Vendor create(VendorDtos.CreateVendorRequest request) {
        Vendor vendor = new Vendor();
        vendor.setBusinessName(request.businessName());
        vendor.setCategory(request.category());
        vendor.setDescription(request.description());
        vendor.setPhone(request.phone());
        vendor.setEmail(request.email());
        vendor.setCity(request.city());
        return vendors.save(vendor);
    }

    @Transactional
    public Vendor registerSelf(VendorDtos.SelfRegisterVendorRequest request, AppUser owner) {
        if (!owner.hasPermission(Permission.VENDOR_SERVICE_PROVIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Add the Service Provider role to your account first");
        }
        if (vendors.findByOwnerId(owner.getId()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You already have a service provider profile");
        }
        Vendor vendor = new Vendor();
        vendor.setBusinessName(request.businessName());
        vendor.setCategory(request.category());
        vendor.setDescription(request.description());
        vendor.setPhone(request.phone());
        vendor.setEmail(owner.getEmail());
        vendor.setCity(request.city());
        vendor.setOwner(owner);
        vendor.setVerified(false);
        return vendors.save(vendor);
    }

    @Transactional(readOnly = true)
    public Vendor requireMine(AppUser owner) {
        return vendors.findByOwnerId(owner.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "You don't have a service provider profile yet"));
    }

    @Transactional
    public Vendor updateMine(VendorDtos.UpdateVendorRequest request, AppUser owner) {
        Vendor vendor = requireMine(owner);
        if (request.businessName() != null) vendor.setBusinessName(request.businessName());
        if (request.category() != null) vendor.setCategory(request.category());
        if (request.description() != null) vendor.setDescription(request.description());
        if (request.phone() != null) vendor.setPhone(request.phone());
        if (request.email() != null) vendor.setEmail(request.email());
        if (request.city() != null) vendor.setCity(request.city());
        return vendor;
    }

    @Transactional
    public Vendor verify(Long id) {
        Vendor vendor = require(id);
        vendor.setVerified(true);
        return vendor;
    }

    @Transactional
    public Vendor deactivate(Long id) {
        Vendor vendor = require(id);
        vendor.setActive(false);
        return vendor;
    }

    @Transactional(readOnly = true)
    public Vendor require(Long id) {
        return vendors.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Vendor not found"));
    }

    @Transactional(readOnly = true)
    public List<Vendor> list(VendorCategory category, String city) {
        if (category != null && city != null) {
            return vendors.findByActiveTrueAndCategoryAndCityIgnoreCase(category, city);
        }
        if (category != null) {
            return vendors.findByActiveTrueAndCategory(category);
        }
        if (city != null) {
            return vendors.findByActiveTrueAndCityIgnoreCase(city);
        }
        return vendors.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public VendorDtos.VendorResponse toResponse(Vendor vendor) {
        List<ServiceBooking> rated = bookings.findByVendorAndFeedbackRatingIsNotNull(vendor);
        Double average = rated.isEmpty() ? null : rated.stream().mapToInt(b -> b.getFeedbackRating()).average().orElse(0);
        return VendorDtos.VendorResponse.from(vendor, average, rated.size());
    }
}
