package com.example.primenestprop.vendor;

import com.example.primenestprop.common.ApiException;
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
