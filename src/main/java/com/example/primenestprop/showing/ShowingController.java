package com.example.primenestprop.showing;

import com.example.primenestprop.showing.ShowingDtos.ShowingResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{slug}")
public class ShowingController {
    private final ShowingService service;

    public ShowingController(ShowingService service) {
        this.service = service;
    }

    @GetMapping("/viewings")
    List<ShowingResponse> upcoming(@PathVariable String slug, @RequestParam(defaultValue = "14") int days,
                                   @AuthenticationPrincipal AppUser user) {
        return service.upcoming(slug, days, user);
    }

    @GetMapping("/listings/{propertyId}/viewings")
    List<ShowingResponse> forListing(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return service.forListing(slug, propertyId, user);
    }

    @PostMapping("/listings/{propertyId}/viewings")
    ShowingResponse schedule(@PathVariable String slug, @PathVariable Long propertyId,
                             @Valid @RequestBody ShowingDtos.ScheduleRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.schedule(slug, propertyId, request, user));
    }

    @PutMapping("/viewings/{id}/outcome")
    ShowingResponse outcome(@PathVariable String slug, @PathVariable Long id,
                            @Valid @RequestBody ShowingDtos.OutcomeRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.recordOutcome(slug, id, request, user));
    }
}
