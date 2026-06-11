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
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String phone,
            @Size(min = 8) String password,
            String country,
            @NotEmpty Set<UserRole> roles
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record ForgotPasswordRequest(@Email @NotBlank String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String password) {
    }

    public record MessageResponse(String message) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
