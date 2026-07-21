package com.example.primenestprop.application;

import static com.example.primenestprop.application.RentalApplicationDtos.ApplicationResponse;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class RentalApplicationController {
    private final RentalApplicationService service;

    public RentalApplicationController(RentalApplicationService service) {
        this.service = service;
    }

    @PostMapping
    ApplicationResponse create(
            @Valid @RequestBody RentalApplicationDtos.CreateApplicationRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ApplicationResponse.from(service.create(request, currentUser));
    }

    @PatchMapping("/{id}/submit")
    ApplicationResponse submit(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return ApplicationResponse.from(service.submit(id, currentUser));
    }

    @PatchMapping("/{id}/review")
    ApplicationResponse review(
            @PathVariable Long id,
            @Valid @RequestBody RentalApplicationDtos.ReviewApplicationRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ApplicationResponse.from(service.review(id, currentUser, request));
    }

    @GetMapping("/{id}")
    ApplicationResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return ApplicationResponse.from(service.requireVisible(id, currentUser));
    }

    @GetMapping("/mine")
    List<ApplicationResponse> mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.mine(currentUser).stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/received")
    List<ApplicationResponse> received(@AuthenticationPrincipal AppUser currentUser) {
        return service.received(currentUser).stream().map(ApplicationResponse::from).toList();
    }
}
