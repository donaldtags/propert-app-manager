package com.example.estatex.user;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Who works for EstateX, and with which staff role. Only an admin can change it; every change is audited. */
@RestController
class StaffAccessController {
    private final UserService users;
    private final AuditService audit;

    StaffAccessController(UserService users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    record StaffMember(Long id, String fullName, String email, String phone, Set<UserRole> staffRoles) {
        static StaffMember of(AppUser u) {
            return new StaffMember(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getRoles().stream().filter(UserRole::isStaff).collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class))));
        }
    }

    record SetStaffRolesRequest(Set<UserRole> roles) {
    }

    @GetMapping("/api/v1/admin/staff")
    List<StaffMember> list(@AuthenticationPrincipal AppUser admin) {
        requireAdmin(admin);
        return users.staff().stream().map(StaffMember::of).toList();
    }

    @PutMapping("/api/v1/admin/staff/{userId}")
    StaffMember set(@PathVariable Long userId, @RequestBody SetStaffRolesRequest request,
                    @AuthenticationPrincipal AppUser admin) {
        requireAdmin(admin);
        Set<UserRole> wanted = request.roles() == null ? Set.of() : request.roles();
        AppUser before = users.require(userId);
        String from = StaffMember.of(before).staffRoles().toString();
        AppUser after = users.setStaffRoles(userId, wanted, admin);
        audit.record(admin, entry("STAFF_ROLES_CHANGED", "User", userId).transition(from, StaffMember.of(after).staffRoles().toString())
                .metadata(Map.of("roles", wanted.stream().map(Enum::name).sorted().toList())));
        return StaffMember.of(after);
    }

    private static void requireAdmin(AppUser user) {
        if (user == null || !user.hasPermission(Permission.USER_MANAGE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can manage staff access.");
        }
    }
}
