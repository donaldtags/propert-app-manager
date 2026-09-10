package com.example.primenestprop.construction;

import static com.example.primenestprop.construction.ConstructionDtos.ConstructionMilestoneResponse;
import static com.example.primenestprop.construction.ConstructionDtos.ConstructionProjectResponse;
import static com.example.primenestprop.construction.ConstructionDtos.ConstructionUpdateResponse;

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
@RequestMapping("/api/v1/construction-projects")
public class ConstructionController {
    private final ConstructionService service;

    public ConstructionController(ConstructionService service) {
        this.service = service;
    }

    @PostMapping
    ConstructionProjectResponse create(
            @Valid @RequestBody ConstructionDtos.CreateConstructionProjectRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        ConstructionProject project = service.create(request, currentUser);
        return ConstructionProjectResponse.from(project, service.latestUpdateFor(project));
    }

    @GetMapping
    List<ConstructionProjectResponse> list(@RequestParam Long ownerId, @AuthenticationPrincipal AppUser currentUser) {
        return service.listForOwner(ownerId, currentUser).stream()
                .map(p -> ConstructionProjectResponse.from(p, service.latestUpdateFor(p)))
                .toList();
    }

    @GetMapping("/{id}")
    ConstructionProjectResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        ConstructionProject project = service.get(id, currentUser);
        return ConstructionProjectResponse.from(project, service.latestUpdateFor(project));
    }

    @PatchMapping("/{id}")
    ConstructionProjectResponse update(
            @PathVariable Long id,
            @RequestBody ConstructionDtos.UpdateConstructionProjectRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        ConstructionProject project = service.update(id, request, currentUser);
        return ConstructionProjectResponse.from(project, service.latestUpdateFor(project));
    }

    @PostMapping("/{id}/milestones")
    ConstructionMilestoneResponse addMilestone(
            @PathVariable Long id,
            @Valid @RequestBody ConstructionDtos.CreateMilestoneRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ConstructionMilestoneResponse.from(service.addMilestone(id, request, currentUser));
    }

    @GetMapping("/{id}/milestones")
    List<ConstructionMilestoneResponse> listMilestones(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return service.milestonesFor(id, currentUser).stream().map(ConstructionMilestoneResponse::from).toList();
    }

    @PatchMapping("/milestones/{milestoneId}/submit")
    ConstructionMilestoneResponse submitMilestone(@PathVariable Long milestoneId, @AuthenticationPrincipal AppUser currentUser) {
        return ConstructionMilestoneResponse.from(service.submitMilestone(milestoneId, currentUser));
    }

    @PatchMapping("/milestones/{milestoneId}/approve")
    ConstructionMilestoneResponse approveMilestone(@PathVariable Long milestoneId, @AuthenticationPrincipal AppUser currentUser) {
        return ConstructionMilestoneResponse.from(service.approveMilestone(milestoneId, currentUser));
    }

    @PatchMapping("/milestones/{milestoneId}/mark-paid")
    ConstructionMilestoneResponse markMilestonePaid(@PathVariable Long milestoneId, @AuthenticationPrincipal AppUser currentUser) {
        return ConstructionMilestoneResponse.from(service.markMilestonePaid(milestoneId, currentUser));
    }

    @PostMapping("/{id}/updates")
    ConstructionUpdateResponse postUpdate(
            @PathVariable Long id,
            @Valid @RequestBody ConstructionDtos.PostUpdateRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return ConstructionUpdateResponse.from(service.postUpdate(id, request, currentUser));
    }

    @GetMapping("/{id}/updates")
    List<ConstructionUpdateResponse> listUpdates(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return service.updatesFor(id, currentUser).stream().map(ConstructionUpdateResponse::from).toList();
    }
}
