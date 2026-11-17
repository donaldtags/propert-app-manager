package com.example.primenestprop.organisation;

import com.example.primenestprop.organisation.OrganisationDtos.AuditEntryResponse;
import com.example.primenestprop.organisation.OrganisationDtos.MemberResponse;
import com.example.primenestprop.organisation.OrganisationDtos.MyOrgContextResponse;
import com.example.primenestprop.organisation.OrganisationDtos.OrganisationResponse;
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
@RequestMapping("/api/v1/orgs")
public class OrganisationController {
    private final OrganisationService service;

    public OrganisationController(OrganisationService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    List<MyOrgContextResponse> mine(@AuthenticationPrincipal AppUser currentUser) {
        return service.mine(currentUser);
    }

    @PostMapping
    OrganisationResponse create(@Valid @RequestBody OrganisationDtos.CreateOrganisationRequest request,
                                @AuthenticationPrincipal AppUser currentUser) {
        return OrganisationResponse.from(service.create(request, currentUser), true);
    }

    /** Public profile (theme, contact, EAC registration); internal fields only for staff. */
    @GetMapping("/{slug}")
    OrganisationResponse get(@PathVariable String slug, @AuthenticationPrincipal AppUser currentUser) {
        return service.profile(slug, currentUser);
    }

    @PatchMapping("/{slug}")
    OrganisationResponse update(@PathVariable String slug,
                                @Valid @RequestBody OrganisationDtos.UpdateOrganisationRequest request,
                                @AuthenticationPrincipal AppUser currentUser) {
        return OrganisationResponse.from(service.update(slug, request, currentUser), true);
    }

    @GetMapping("/{slug}/me")
    MyOrgContextResponse me(@PathVariable String slug, @AuthenticationPrincipal AppUser currentUser) {
        return service.myContext(slug, currentUser);
    }

    @GetMapping("/{slug}/members")
    List<MemberResponse> members(@PathVariable String slug, @AuthenticationPrincipal AppUser currentUser) {
        return service.members(slug, currentUser).stream().map(MemberResponse::from).toList();
    }

    @PostMapping("/{slug}/members")
    MemberResponse addMember(@PathVariable String slug,
                             @Valid @RequestBody OrganisationDtos.AddMemberRequest request,
                             @AuthenticationPrincipal AppUser currentUser) {
        return MemberResponse.from(service.addMember(slug, request, currentUser));
    }

    @PatchMapping("/{slug}/members/{membershipId}")
    MemberResponse updateMember(@PathVariable String slug, @PathVariable Long membershipId,
                                @RequestBody OrganisationDtos.UpdateMemberRequest request,
                                @AuthenticationPrincipal AppUser currentUser) {
        return MemberResponse.from(service.updateMember(slug, membershipId, request, currentUser));
    }

    @GetMapping("/{slug}/audit")
    List<AuditEntryResponse> audit(@PathVariable String slug, @RequestParam(defaultValue = "100") int limit,
                                   @AuthenticationPrincipal AppUser currentUser) {
        return service.auditTrail(slug, limit, currentUser);
    }
}
