package com.example.estatex.organisation;

import com.example.estatex.organisation.OrganisationDtos.AuditEntryResponse;
import com.example.estatex.organisation.OrganisationDtos.MemberResponse;
import com.example.estatex.organisation.OrganisationDtos.MyOrgContextResponse;
import com.example.estatex.organisation.OrganisationDtos.OrganisationResponse;
import com.example.estatex.user.AppUser;
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

    private final com.example.estatex.property.PropertyService properties;
    private final com.example.estatex.notification.NotificationService notifications;

    public OrganisationController(OrganisationService service, com.example.estatex.property.PropertyService properties,
                                  com.example.estatex.notification.NotificationService notifications) {
        this.service = service;
        this.properties = properties;
        this.notifications = notifications;
    }

    /** Public directory of approved companies. */
    @GetMapping
    List<OrganisationResponse> directory() {
        return service.directory().stream().map(o -> OrganisationResponse.from(o, false)).toList();
    }

    /** EstateX staff: companies that registered themselves and are waiting for review. */
    @GetMapping("/pending-review")
    List<OrganisationResponse> pendingReview(@AuthenticationPrincipal AppUser currentUser) {
        return service.awaitingApproval(currentUser).stream().map(o -> OrganisationResponse.from(o, true)).toList();
    }

    @PatchMapping("/{slug}/approval")
    OrganisationResponse decide(@PathVariable String slug, @RequestBody OrganisationDtos.ApprovalRequest request,
                                @AuthenticationPrincipal AppUser currentUser) {
        return OrganisationResponse.from(service.decideApproval(slug, request.approved() == null || request.approved(),
                request.note(), currentUser, notifications), true);
    }

    /** A company's live listings for its public page (404 until the company is approved). */
    @GetMapping("/{slug}/public-listings")
    List<com.example.estatex.property.PropertyDtos.PropertyResponse> publicListings(@PathVariable String slug,
                                                                                   @AuthenticationPrincipal AppUser currentUser) {
        OrganisationResponse org = service.profile(slug, currentUser);
        return properties.liveListingsOfOrganisation(org.id()).stream()
                .map(com.example.estatex.property.PropertyDtos.PropertyResponse::from).toList();
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
