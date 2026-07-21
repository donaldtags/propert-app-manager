package com.example.primenestprop.auth;

import com.example.primenestprop.user.UserDtos.UserResponse;
import com.example.primenestprop.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Email @NotBlank @Size(max = 255) String email,
            @Size(max = 32) String phone,
            @Size(min = 8, max = 72) String password,
            @Size(max = 100) String country,
            @NotEmpty Set<UserRole> roles
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 255) String identifier,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record ForgotPasswordRequest(@Email @NotBlank @Size(max = 255) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 255) String token,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record MessageResponse(String message) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
