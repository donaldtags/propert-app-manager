package com.example.primenestprop.vendor;

import com.example.primenestprop.user.AppUser;
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
@RequestMapping("/api/v1/vendors")
public class VendorController {
    private final VendorService service;

    public VendorController(VendorService service) {
        this.service = service;
    }

    @GetMapping
    List<VendorDtos.VendorResponse> list(
            @RequestParam(required = false) VendorCategory category,
            @RequestParam(required = false) String city
    ) {
        return service.list(category, city).stream().map(service::toResponse).toList();
    }

    @PostMapping
    VendorDtos.VendorResponse create(@Valid @RequestBody VendorDtos.CreateVendorRequest request) {
        return service.toResponse(service.create(request));
    }

    @PostMapping("/self")
    VendorDtos.VendorResponse registerSelf(
            @Valid @RequestBody VendorDtos.SelfRegisterVendorRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return service.toResponse(service.registerSelf(request, currentUser));
    }

    @GetMapping("/mine")
    VendorDtos.VendorResponse mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.toResponse(service.requireMine(currentUser));
    }

    @PatchMapping("/mine")
    VendorDtos.VendorResponse updateMine(
            @RequestBody VendorDtos.UpdateVendorRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return service.toResponse(service.updateMine(request, currentUser));
    }

    @PatchMapping("/{id}/verify")
    VendorDtos.VendorResponse verify(@PathVariable Long id) {
        return service.toResponse(service.verify(id));
    }

    @PatchMapping("/{id}/deactivate")
    VendorDtos.VendorResponse deactivate(@PathVariable Long id) {
        return service.toResponse(service.deactivate(id));
    }
}
