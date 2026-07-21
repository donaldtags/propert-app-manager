package com.example.primenestprop.vendor;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-bookings")
public class ServiceBookingController {
    private final ServiceBookingService service;

    public ServiceBookingController(ServiceBookingService service) {
        this.service = service;
    }

    @PostMapping
    ServiceBookingDtos.ServiceBookingResponse create(
            @Valid @RequestBody ServiceBookingDtos.CreateBookingRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ServiceBookingDtos.ServiceBookingResponse.from(service.create(request, currentUser));
    }

    @GetMapping
    List<ServiceBookingDtos.ServiceBookingResponse> list(
            @RequestParam(required = false) Long requesterId,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        if (requesterId == null) return List.of();
        if (!requesterId.equals(currentUser.getId()) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own service bookings");
        }
        return service.forRequester(currentUser).stream().map(ServiceBookingDtos.ServiceBookingResponse::from).toList();
    }

    @PatchMapping("/{id}/cancel")
    ServiceBookingDtos.ServiceBookingResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return ServiceBookingDtos.ServiceBookingResponse.from(service.cancel(id, currentUser));
    }

    @PatchMapping("/{id}/status")
    ServiceBookingDtos.ServiceBookingResponse updateStatus(
            @PathVariable Long id,
            @RequestParam BookingStatus status,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ServiceBookingDtos.ServiceBookingResponse.from(service.updateStatus(id, status, currentUser));
    }

    @PostMapping("/{id}/feedback")
    ServiceBookingDtos.ServiceBookingResponse feedback(
            @PathVariable Long id,
            @Valid @RequestBody ServiceBookingDtos.FeedbackRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ServiceBookingDtos.ServiceBookingResponse.from(service.submitFeedback(id, request, currentUser));
    }
}
