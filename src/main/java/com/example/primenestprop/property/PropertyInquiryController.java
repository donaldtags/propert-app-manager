package com.example.primenestprop.property;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kept as its own top-level path (not nested under /properties) so it doesn't fall under the
 * public {@code GET /api/v1/properties/*} allow-list — inquiries are private to the property owner. */
@RestController
@RequestMapping("/api/v1/property-inquiries")
public class PropertyInquiryController {
    private final PropertyService service;

    public PropertyInquiryController(PropertyService service) {
        this.service = service;
    }

    @GetMapping
    List<PropertyDtos.InquiryResponse> mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.inquiriesFor(currentUser);
    }
}
