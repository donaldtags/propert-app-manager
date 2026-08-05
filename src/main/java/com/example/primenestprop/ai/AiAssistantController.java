package com.example.primenestprop.ai;

import com.example.primenestprop.property.ListingType;
import com.example.primenestprop.property.Property;
import com.example.primenestprop.property.PropertyDtos.PropertyResponse;
import com.example.primenestprop.subscription.SubscriptionFeature;
import com.example.primenestprop.subscription.SubscriptionService;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {
    private final AiAssistantService service;
    private final AffordabilityService affordabilityService;
    private final RentPricingService rentPricingService;
    private final HomeAssistantService homeAssistantService;
    private final SubscriptionService subscriptions;

    public AiAssistantController(
            AiAssistantService service,
            AffordabilityService affordabilityService,
            RentPricingService rentPricingService,
            HomeAssistantService homeAssistantService,
            SubscriptionService subscriptions
    ) {
        this.service = service;
        this.affordabilityService = affordabilityService;
        this.rentPricingService = rentPricingService;
        this.homeAssistantService = homeAssistantService;
        this.subscriptions = subscriptions;
    }

    @PostMapping("/property-search")
    AiDtos.AiPropertyAnswer propertySearch(@Valid @RequestBody AiDtos.AiPropertyQuery request) {
        List<Property> matches = service.search(request.query(), request.history());
        String answer = service.answer(request.query(), matches, request.history());
        List<PropertyResponse> responses = matches.stream().map(PropertyResponse::from).toList();
        return new AiDtos.AiPropertyAnswer(answer, responses, service.aiPowered());
    }

    @PostMapping("/affordability")
    AiDtos.AffordabilityResponse affordability(@Valid @RequestBody AiDtos.AffordabilityRequest request) {
        return affordabilityService.evaluate(request);
    }

    @GetMapping("/rent-suggestion")
    AiDtos.RentSuggestionResponse rentSuggestion(
            @RequestParam(defaultValue = "RENT") ListingType listingType,
            @RequestParam String city,
            @RequestParam(required = false) String suburb,
            @RequestParam int bedrooms,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        subscriptions.requireFeature(currentUser, SubscriptionFeature.AI_PRICING);
        return rentPricingService.suggest(listingType, city, suburb, bedrooms);
    }

    @PostMapping("/home-assistant")
    AiDtos.HomeAssistantResponse homeAssistant(
            @Valid @RequestBody AiDtos.HomeAssistantRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        String answer = homeAssistantService.answer(currentUser, request.message(), request.history());
        return new AiDtos.HomeAssistantResponse(answer, homeAssistantService.aiPowered());
    }

    @GetMapping("/lease-explanation/{leaseId}")
    AiDtos.HomeAssistantResponse leaseExplanation(
            @PathVariable Long leaseId,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        String answer = homeAssistantService.explainLease(leaseId, currentUser);
        return new AiDtos.HomeAssistantResponse(answer, homeAssistantService.aiPowered());
    }
}
