package com.example.primenestprop.vendor;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceBookingService {
    private final ServiceBookingRepository bookings;
    private final VendorService vendors;
    private final PropertyService properties;

    public ServiceBookingService(ServiceBookingRepository bookings, VendorService vendors, PropertyService properties) {
        this.bookings = bookings;
        this.vendors = vendors;
        this.properties = properties;
    }

    @Transactional
    public ServiceBooking create(ServiceBookingDtos.CreateBookingRequest request, AppUser requester) {
        Vendor vendor = vendors.require(request.vendorId());
        if (!vendor.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This vendor is not currently accepting bookings");
        }
        ServiceBooking booking = new ServiceBooking();
        booking.setVendor(vendor);
        booking.setRequester(requester);
        if (request.propertyId() != null) {
            Property property = properties.require(request.propertyId());
            booking.setProperty(property);
        }
        booking.setPreferredDate(request.preferredDate());
        booking.setNotes(request.notes());
        return bookings.save(booking);
    }

    @Transactional
    public ServiceBooking cancel(Long id, AppUser currentUser) {
        ServiceBooking booking = require(id);
        if (!booking.getRequester().getId().equals(currentUser.getId()) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who booked this service can cancel it");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking can no longer be cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        return booking;
    }

    @Transactional
    public ServiceBooking updateStatus(Long id, BookingStatus status, AppUser currentUser) {
        if (!currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an admin can update a service booking's status on the vendor's behalf");
        }
        ServiceBooking booking = require(id);
        booking.setStatus(status);
        if (status == BookingStatus.COMPLETED) {
            booking.setCompletedAt(Instant.now());
        }
        return booking;
    }

    @Transactional
    public ServiceBooking submitFeedback(Long id, ServiceBookingDtos.FeedbackRequest request, AppUser currentUser) {
        ServiceBooking booking = require(id);
        if (!booking.getRequester().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who booked this service can leave feedback");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Feedback can only be left after the service is completed");
        }
        booking.setFeedbackRating(request.rating());
        booking.setFeedbackComment(request.comment());
        return booking;
    }

    @Transactional(readOnly = true)
    public ServiceBooking require(Long id) {
        return bookings.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service booking not found"));
    }

    @Transactional(readOnly = true)
    public List<ServiceBooking> forRequester(AppUser requester) {
        return bookings.findByRequesterOrderByCreatedAtDesc(requester);
    }
}
