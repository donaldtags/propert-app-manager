package com.example.primenestprop.user;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.PasswordPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final AdminAccessRequestRepository adminRequests;
    private final AdminRequestRepository adminRequestRecords;
    private final RoleLookupRepository roleLookups;
    private final UserRoleAssignmentRepository roleAssignments;
    private final PasswordEncoder passwordEncoder;
    private final TrustScoreService trustScoreService;

    public UserService(
            UserRepository users,
            AdminAccessRequestRepository adminRequests,
            AdminRequestRepository adminRequestRecords,
            RoleLookupRepository roleLookups,
            UserRoleAssignmentRepository roleAssignments,
            PasswordEncoder passwordEncoder,
            TrustScoreService trustScoreService
    ) {
        this.users = users;
        this.adminRequests = adminRequests;
        this.adminRequestRecords = adminRequestRecords;
        this.roleLookups = roleLookups;
        this.roleAssignments = roleAssignments;
        this.passwordEncoder = passwordEncoder;
        this.trustScoreService = trustScoreService;
    }

    @Transactional
    public AppUser create(UserDtos.CreateUserRequest request) {
        users.findByEmailIgnoreCase(request.email()).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this email already exists");
        });


        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (phone != null) {
            users.findFirstByPhone(phone).ifPresent(existing -> {
                throw new ApiException(HttpStatus.CONFLICT, "A user with this phone number already exists");
            });
        }
        PasswordPolicy.validate(request.password());
        AppUser user = new AppUser();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCountry(request.country() == null || request.country().isBlank() ? "Zimbabwe" : request.country());
        user.getRoles().addAll(request.roles());
        AppUser saved = users.save(user);
        syncRoleAssignments(saved);
        return saved;
    }

    public AppUser requireByEmail(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    public Optional<AppUser> findByEmail(String email) {
        return users.findByEmailIgnoreCase(email);
    }

    /**
     * Resolves a login identifier that may be either an email address or a phone number.
     */
    public AppUser requireByIdentifier(String identifier) {
        return findByIdentifier(identifier)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }

    public Optional<AppUser> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        return users.findByEmailIgnoreCase(trimmed).or(() -> users.findFirstByPhone(trimmed));
    }

    public AppUser require(Long id) {
        AppUser user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        trustScoreService.recompute(user);
        return user;
    }

    public List<AppUser> list(UserRole role) {
        return role == null ? users.findAll() : users.findByRolesContaining(role);
    }

    public List<AppUser> search(UserRole role, String query) {
        List<AppUser> candidates = list(role);
        String needle = query == null ? "" : query.trim().toLowerCase();
        return candidates.stream()
                .filter(user -> needle.isBlank() || user.getFullName().toLowerCase().contains(needle))
                .limit(20)
                .toList();
    }

    @Transactional
    public AppUser addRole(Long id, UserDtos.AddRoleRequest request, AppUser currentUser) {
        if (!id.equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only add roles to your own account");
        }
        if (request.role() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "role is required");
        }
        if (request.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin role requires approval");
        }
        AppUser user = require(id);
        if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Password confirmation failed");
        }
        user.getRoles().add(request.role());
        syncRoleAssignments(user);
        return user;
    }

    private void syncRoleAssignments(AppUser user) {
        for (UserRole userRole : user.getRoles()) {
            RoleLookup role = roleLookups.findByName(userRole).orElseGet(() -> {
                RoleLookup lookup = new RoleLookup();
                lookup.setName(userRole);
                return roleLookups.save(lookup);
            });
            roleAssignments.save(new UserRoleAssignment(user.getId(), role.getId()));
        }
    }

    @Transactional
    public AdminAccessRequest requestAdminAccess(Long id, AppUser currentUser) {
        if (!id.equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only request admin access for your own account");
        }
        AppUser user = require(id);
        if (user.getRoles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User already has admin access");
        }
        return adminRequests.findByUserAndStatus(user, AdminRequestStatus.PENDING)
                .orElseGet(() -> {
                    AdminAccessRequest request = new AdminAccessRequest();
                    request.setUser(user);
                    adminRequestRecords.findByUserIdAndStatus(user.getId(), AdminRequestStatus.PENDING)
                            .orElseGet(() -> {
                                AdminRequest record = new AdminRequest();
                                record.setUserId(user.getId());
                                record.setRequestedRole(UserRole.ADMIN);
                                return adminRequestRecords.save(record);
                            });
                    return adminRequests.save(request);
                });
    }

    @Transactional(readOnly = true)
    public List<AdminAccessRequest> listAdminRequests(AdminRequestStatus status) {
        return status == null ? adminRequests.findAll() : adminRequests.findByStatusOrderByRequestedAtDesc(status);
    }

    @Transactional
    public AdminAccessRequest decideAdminRequest(Long requestId, boolean approve, AppUser currentAdmin) {
        AdminAccessRequest request = adminRequests.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admin request not found"));
        if (request.getStatus() != AdminRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request has already been reviewed");
        }
        request.setStatus(approve ? AdminRequestStatus.APPROVED : AdminRequestStatus.REJECTED);
        request.setReviewedAt(Instant.now());
        request.setReviewedBy(currentAdmin.getId());
        if (approve) {
            AppUser user = request.getUser();
            user.getRoles().add(UserRole.ADMIN);
            syncRoleAssignments(user);
        }
        adminRequestRecords.findByUserIdAndStatus(request.getUser().getId(), AdminRequestStatus.PENDING)
                .ifPresent(record -> record.setStatus(request.getStatus()));
        return request;
    }

    @Transactional
    public AppUser updatePassword(Long id, String password) {
        AppUser user = require(id);
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }

    @Transactional
    public AppUser verify(Long id) {
        AppUser user = require(id);
        user.setVerified(true);
        user.setIdentityVerified(true);
        trustScoreService.recompute(user);
        return user;
    }

    @Transactional
    public AppUser applyKycApproval(Long id) {
        AppUser user = require(id);
        user.setVerified(true);
        user.setIdentityVerified(true);
        user.setFaceVerified(true);
        trustScoreService.recompute(user);
        return user;
    }

    @Transactional
    public AppUser verifyBusiness(Long id) {
        AppUser user = require(id);
        user.setBusinessVerified(true);
        trustScoreService.recompute(user);
        return user;
    }

    @Transactional(readOnly = true)
    public long countPendingAdminRequests() {
        return adminRequests.countByStatus(AdminRequestStatus.PENDING);
    }

    @Transactional
    public AppUser updateProfile(Long id, UserDtos.UpdateProfileRequest request) {
        AppUser user = require(id);
        setIfPresent(request.fullName(), user::setFullName);
        setIfPresent(request.phone(), user::setPhone);
        setIfPresent(request.country(), user::setCountry);
        setIfPresent(request.preferredCurrency(), user::setPreferredCurrency);
        setIfPresent(request.avatarUrl(), user::setAvatarUrl);
        setIfPresent(request.bio(), user::setBio);
        setIfPresent(request.city(), user::setCity);
        setIfPresent(request.diasporaLocation(), user::setDiasporaLocation);
        setIfPresent(request.nationalIdNumber(), user::setNationalIdNumber);
        setIfPresent(request.occupation(), user::setOccupation);
        setIfPresent(request.companyName(), user::setCompanyName);
        setIfPresent(request.emergencyContactName(), user::setEmergencyContactName);
        setIfPresent(request.emergencyContactPhone(), user::setEmergencyContactPhone);
        if (request.emailNotifications() != null) {
            user.setEmailNotifications(request.emailNotifications());
        }
        if (request.smsNotifications() != null) {
            user.setSmsNotifications(request.smsNotifications());
        }
        if (request.twoFactorEnabled() != null) {
            user.setTwoFactorEnabled(request.twoFactorEnabled());
        }
        return user;
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value.isBlank() ? null : value);
        }
    }
}
