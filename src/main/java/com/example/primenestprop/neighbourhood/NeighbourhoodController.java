package com.example.primenestprop.neighbourhood;

import com.example.primenestprop.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/neighbourhoods")
public class NeighbourhoodController {
    private final NeighbourhoodService service;

    public NeighbourhoodController(NeighbourhoodService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<NeighbourhoodDtos.NeighbourhoodResponse> get(
            @RequestParam String city,
            @RequestParam String suburb
    ) {
        return service.find(city, suburb)
                .map(NeighbourhoodDtos.NeighbourhoodResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping
    NeighbourhoodDtos.NeighbourhoodResponse upsert(
            @Valid @RequestBody NeighbourhoodDtos.UpsertNeighbourhoodRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return NeighbourhoodDtos.NeighbourhoodResponse.from(service.upsert(request, currentUser));
    }
}
