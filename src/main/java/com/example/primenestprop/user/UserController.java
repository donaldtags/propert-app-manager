package com.example.primenestprop.user;

import static com.example.primenestprop.user.UserDtos.UserResponse;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.kyc.VerificationLevelService;
import com.example.primenestprop.lease.LeaseRepository;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.review.LandlordRatingService;
import com.example.primenestprop.user.UserDtos.AdminRequestResponse;
import com.example.primenestprop.user.UserDtos.LandlordProfileResponse;
import com.example.primenestprop.user.UserDtos.UserSummaryResponse;
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
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;
    private final VerificationLevelService verificationLevelService;
    private final PropertyService properties;
    private final LandlordRatingService landlordRatings;
    private final PassportService passportService;
    private final LeaseRepository leases;

    public UserController(
            UserService service,
            VerificationLevelService verificationLevelService,
            PropertyService properties,
            LandlordRatingService landlordRatings,
            PassportService passportService,
            LeaseRepository leases
    ) {
        this.service = service;
        this.verificationLevelService = verificationLevelService;
        this.properties = properties;
        this.landlordRatings = landlordRatings;
        this.passportService = passportService;
        this.leases = leases;
    }

    @PostMapping
    UserResponse create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        return UserResponse.from(service.create(request));
    }

    @GetMapping
    List<UserResponse> list(@RequestParam(required = false) UserRole role) {
        return service.list(role).stream().map(UserResponse::from).toList();
    }

    @GetMapping("/search")
    List<UserSummaryResponse> search(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String q
    ) {
        return service.search(role, q).stream().map(UserSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    UserResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        requireSelfOrAdmin(id, currentUser);
        return UserResponse.from(service.require(id));
    }

    @PatchMapping("/{id}/verify")
    UserResponse verify(@PathVariable Long id) {
        return UserResponse.from(service.verify(id));
    }

    @PatchMapping("/{id}/verify-business")
    UserResponse verifyBusiness(@PathVariable Long id) {
        return UserResponse.from(service.verifyBusiness(id));
    }

    @GetMapping("/{id}/landlord-profile")
    LandlordProfileResponse landlordProfile(@PathVariable Long id) {
        AppUser landlord = service.require(id);
        LandlordRatingService.SatisfactionSummary satisfaction = landlordRatings.satisfactionSummary(id);
        return new LandlordProfileResponse(
                landlord.getId(),
                landlord.getFullName(),
                landlord.getCompanyName(),
                landlord.getAvatarUrl(),
                landlord.isVerified(),
                landlord.isIdentityVerified(),
                landlord.getTrustScore(),
                properties.forLandlord(id).size(),
                satisfaction.averageRating(),
                satisfaction.ratingCount()
        );
    }

    @GetMapping("/{id}/landlord-passport")
    PassportDtos.LandlordPassport landlordPassport(@PathVariable Long id) {
        return passportService.landlordPassport(id);
    }

    @GetMapping("/{id}/tenant-passport")
    PassportDtos.TenantPassport tenantPassport(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        if (!currentUser.getId().equals(id) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            AppUser tenant = service.require(id);
            boolean hasBusinessRelationship = leases.findByTenant(tenant).stream()
                    .anyMatch(l -> l.getLandlord().getId().equals(currentUser.getId())
                            || (l.getProperty().getAgent() != null && l.getProperty().getAgent().getId().equals(currentUser.getId())));
            if (!hasBusinessRelationship) {
                throw new ApiException(HttpStatus.FORBIDDEN,
                        "You can only view a tenant passport for your own account, or a tenant you have a lease with");
            }
        }
        return passportService.tenantPassport(id);
    }

    @GetMapping("/{id}/verification-level")
    VerificationLevelService.Result verificationLevel(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        requireSelfOrAdmin(id, currentUser);
        return verificationLevelService.levelFor(service.require(id));
    }

    @PatchMapping("/{id}/profile")
    UserResponse updateProfile(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUser currentUser,
            @RequestBody UserDtos.UpdateProfileRequest request
    ) {
        requireSelfOrAdmin(id, currentUser);
        return UserResponse.from(service.updateProfile(id, request));
    }

    @PostMapping("/{id}/roles")
    UserResponse addRole(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUser currentUser,
            @Valid @RequestBody UserDtos.AddRoleRequest request
    ) {
        return UserResponse.from(service.addRole(id, request, currentUser));
    }

    @PostMapping("/{id}/admin-request")
    AdminRequestResponse requestAdminAccess(@PathVariable Long id, @AuthenticationPrincipal AppUser currentUser) {
        return AdminRequestResponse.from(service.requestAdminAccess(id, currentUser));
    }

    @GetMapping("/admin-requests")
    List<AdminRequestResponse> adminRequests(@RequestParam(required = false) AdminRequestStatus status) {
        return service.listAdminRequests(status).stream().map(AdminRequestResponse::from).toList();
    }

    @PatchMapping("/admin-requests/{id}")
    AdminRequestResponse decideAdminRequest(
            @PathVariable Long id,
            @RequestBody UserDtos.DecideAdminRequestRequest request,
            @AuthenticationPrincipal AppUser currentUser
    ) {
        return AdminRequestResponse.from(service.decideAdminRequest(id, request.approve(), currentUser));
    }

    private void requireSelfOrAdmin(Long id, AppUser currentUser) {
        if (!currentUser.getId().equals(id) && !currentUser.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only access your own account");
        }
    }
}
