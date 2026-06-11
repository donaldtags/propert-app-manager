package com.example.primenestprop.user;

import static com.example.primenestprop.user.UserDtos.UserResponse;

import com.example.primenestprop.auth.AuthService;
import com.example.primenestprop.user.UserDtos.AdminRequestResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;
    private final AuthService authService;

    public UserController(UserService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    UserResponse create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        return UserResponse.from(service.create(request));
    }

    @GetMapping
    List<UserResponse> list(@RequestParam(required = false) UserRole role) {
        return service.list(role).stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    UserResponse get(@PathVariable Long id) {
        return UserResponse.from(service.require(id));
    }

    @PatchMapping("/{id}/verify")
    UserResponse verify(@PathVariable Long id) {
        return UserResponse.from(service.verify(id));
    }

    @PatchMapping("/{id}/profile")
    UserResponse updateProfile(@PathVariable Long id, @RequestBody UserDtos.UpdateProfileRequest request) {
        return UserResponse.from(service.updateProfile(id, request));
    }

    @PostMapping("/{id}/roles")
    UserResponse addRole(
            @PathVariable Long id,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody UserDtos.AddRoleRequest request
    ) {
        return UserResponse.from(service.addRole(id, request, authService.currentUser(authorization)));
    }

    @PostMapping("/{id}/admin-request")
    AdminRequestResponse requestAdminAccess(
            @PathVariable Long id,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        return AdminRequestResponse.from(service.requestAdminAccess(id, authService.currentUser(authorization)));
    }
}
