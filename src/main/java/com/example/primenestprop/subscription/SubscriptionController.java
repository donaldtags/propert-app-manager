package com.example.primenestprop.subscription;

import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/plans")
    List<SubscriptionDtos.PlanSettingsResponse> plans() {
        return service.allPlanSettings().stream().map(SubscriptionDtos.PlanSettingsResponse::from).toList();
    }

    @PutMapping("/plans/{plan}")
    SubscriptionDtos.PlanSettingsResponse updatePlan(
            @PathVariable SubscriptionPlan plan,
            @Valid @RequestBody SubscriptionDtos.UpdatePlanSettingsRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return SubscriptionDtos.PlanSettingsResponse.from(service.updatePlanSettings(plan, request, currentUser));
    }

    @GetMapping("/me")
    SubscriptionDtos.SubscriptionResponse mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.describe(currentUser);
    }

    @PostMapping("/subscribe")
    SubscriptionDtos.SubscriptionResponse subscribe(
            @Valid @RequestBody SubscriptionDtos.SubscribeRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return service.subscribe(currentUser, request.plan());
    }
}