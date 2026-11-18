package com.example.estatex.enquiry;

import com.example.estatex.enquiry.EnquiryDtos.EnquiryResponse;
import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/orgs/{slug}/enquiries")
public class EnquiryController {
    private final EnquiryService service;

    public EnquiryController(EnquiryService service) {
        this.service = service;
    }

    @GetMapping
    List<EnquiryResponse> list(@PathVariable String slug, @RequestParam(defaultValue = "200") int limit,
                               @AuthenticationPrincipal AppUser user) {
        return service.list(slug, limit, user);
    }

    @PostMapping
    EnquiryResponse capture(@PathVariable String slug, @Valid @RequestBody EnquiryDtos.CaptureEnquiryRequest request,
                            @AuthenticationPrincipal AppUser user) {
        return EnquiryResponse.from(service.capture(slug, request, user), null);
    }

    @PatchMapping("/{id}/status")
    EnquiryResponse status(@PathVariable String slug, @PathVariable Long id,
                           @Valid @RequestBody EnquiryDtos.UpdateStatusRequest request, @AuthenticationPrincipal AppUser user) {
        return EnquiryResponse.from(service.updateStatus(slug, id, request.status(), user), null);
    }
}
