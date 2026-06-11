package com.example.primenestprop.auth;

import com.example.primenestprop.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/forgot-password")
    AuthDtos.MessageResponse forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        return service.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    AuthDtos.MessageResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        return service.resetPassword(request);
    }

    @PostMapping("/logout")
    void logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        service.logout(authorization);
    }

    @GetMapping("/me")
    UserResponse me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return service.me(authorization);
    }
}
