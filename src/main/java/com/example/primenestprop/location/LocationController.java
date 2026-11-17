package com.example.primenestprop.location;

import com.example.primenestprop.location.LocationDtos.LocationResponse;
import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {
    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    /** The whole verified hierarchy as a flat list; the client builds the tree and caches it offline. */
    @GetMapping
    ResponseEntity<List<LocationResponse>> all() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .body(service.allVerified().stream().map(LocationResponse::from).toList());
    }

    @GetMapping("/pending")
    List<LocationResponse> pending(@AuthenticationPrincipal AppUser currentUser) {
        return service.pending(currentUser).stream().map(LocationResponse::from).toList();
    }

    @PostMapping("/suggestions")
    LocationResponse suggest(@Valid @RequestBody LocationDtos.SuggestSuburbRequest request,
                             @AuthenticationPrincipal AppUser currentUser) {
        return LocationResponse.from(service.suggestSuburb(request, currentUser));
    }

    @PatchMapping("/{id}")
    LocationResponse update(@PathVariable Long id, @Valid @RequestBody LocationDtos.UpdateLocationRequest request,
                            @AuthenticationPrincipal AppUser currentUser) {
        return LocationResponse.from(service.update(id, request, currentUser));
    }

    @DeleteMapping("/{id}")
    void reject(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        service.rejectSuggestion(id, currentUser);
    }
}
