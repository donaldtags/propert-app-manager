package com.example.primenestprop.viewing;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.Permission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/viewings")
public class ViewingController {
    private final ViewingService service;

    public ViewingController(ViewingService service) {
        this.service = service;
    }

    @PostMapping
    ViewingDtos.ViewingResponse create(@Valid @RequestBody ViewingDtos.CreateViewingRequest request, @AuthenticationPrincipal AppUser currentUser) {
        ViewingRequest viewing = service.create(request, currentUser);
        return respond(viewing, currentUser);
    }

    @GetMapping
    List<ViewingDtos.ViewingResponse> list(
            @RequestParam(required = false) Long requesterId,
            @RequestParam(required = false) Long landlordId,
            @RequestParam(required = false) Long agentId,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        if (requesterId != null) {
            requireSelfOrAdmin(requesterId, currentUser);
            return service.forRequester(currentUser).stream().map(v -> respond(v, currentUser)).toList();
        }
        if (landlordId != null) {
            requireSelfOrAdmin(landlordId, currentUser);
            return service.forLandlord(currentUser).stream().map(v -> respond(v, currentUser)).toList();
        }
        if (agentId != null) {
            requireSelfOrAdmin(agentId, currentUser);
            return service.forAgent(currentUser).stream().map(v -> respond(v, currentUser)).toList();
        }
        return List.of();
    }

    @PatchMapping("/{id}/confirm")
    ViewingDtos.ViewingResponse confirm(
            @PathVariable Long id,
            @RequestBody(required = false) ViewingDtos.ConfirmViewingRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        ViewingRequest viewing = service.confirm(id, request == null ? new ViewingDtos.ConfirmViewingRequest(null) : request, currentUser);
        return respond(viewing, currentUser);
    }

    @PatchMapping("/{id}/decline")
    ViewingDtos.ViewingResponse decline(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return respond(service.decline(id, currentUser), currentUser);
    }

    @PatchMapping("/{id}/cancel")
    ViewingDtos.ViewingResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return respond(service.cancel(id, currentUser), currentUser);
    }

    @PatchMapping("/{id}/check-in")
    ViewingDtos.ViewingResponse checkIn(
            @PathVariable Long id,
            @Valid @RequestBody ViewingDtos.CheckInRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return respond(service.checkIn(id, request, currentUser), currentUser);
    }

    @PostMapping("/{id}/feedback")
    ViewingDtos.ViewingResponse feedback(
            @PathVariable Long id,
            @Valid @RequestBody ViewingDtos.FeedbackRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return respond(service.submitFeedback(id, request, currentUser), currentUser);
    }

    private ViewingDtos.ViewingResponse respond(ViewingRequest viewing, AppUser currentUser) {
        boolean includeCheckInMaterials = viewing.getRequester().getId().equals(currentUser.getId())
                || currentUser.hasPermission(Permission.ADMIN_OVERRIDE);
        return ViewingDtos.ViewingResponse.from(viewing, includeCheckInMaterials);
    }

    private void requireSelfOrAdmin(Long id, AppUser currentUser) {
        if (!id.equals(currentUser.getId()) && !currentUser.hasPermission(Permission.ADMIN_OVERRIDE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own viewing requests");
        }
    }
}
