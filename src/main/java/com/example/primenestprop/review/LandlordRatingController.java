package com.example.primenestprop.review;

import com.example.primenestprop.auth.AuthService;
import com.example.primenestprop.review.RatingDtos.LandlordRatingResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ratings")
public class LandlordRatingController {
    private final LandlordRatingService service;
    private final AuthService authService;

    public LandlordRatingController(LandlordRatingService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    LandlordRatingResponse create(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody RatingDtos.CreateLandlordRatingRequest request
    ) {
        return LandlordRatingResponse.from(service.create(request, authService.currentUser(authorization)));
    }

    @GetMapping
    List<LandlordRatingResponse> list(@RequestParam Long landlordId) {
        return service.forLandlord(landlordId).stream().map(LandlordRatingResponse::from).toList();
    }

    @GetMapping("/landlords/{landlordId}")
    List<LandlordRatingResponse> forLandlord(@PathVariable Long landlordId) {
        return service.forLandlord(landlordId).stream().map(LandlordRatingResponse::from).toList();
    }
}
