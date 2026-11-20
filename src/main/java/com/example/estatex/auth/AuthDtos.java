package com.example.estatex.auth;

import com.example.estatex.user.UserDtos.UserResponse;
import com.example.estatex.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {
    }

    /** email or phone (or both) - at least one is required. */
    public record RegisterRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Email @Size(max = 255) String email,
            @Size(max = 32) String phone,
            @Size(min = 8, max = 72) String password,
            @Size(max = 100) String country,
            @NotEmpty Set<UserRole> roles,
            /** Random id the browser keeps, so later sign-ins from other devices can be spotted. */
            @Size(max = 100) String deviceId
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 255) String identifier,
            @NotBlank @Size(max = 72) String password,
            @Size(max = 100) String deviceId
    ) {
    }

    /** identifier = email or phone. ("email" is the older field name, still accepted.) */
    public record ForgotPasswordRequest(@Size(max = 255) String identifier, @Size(max = 255) String email) {
        public String value() {
            return identifier != null && !identifier.isBlank() ? identifier : email;
        }
    }

    /** Either the token from the emailed link, or the phone/email plus the texted code. */
    public record ResetPasswordRequest(
            @Size(max = 255) String token,
            @Size(max = 255) String identifier,
            @Size(max = 12) String code,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
    }

    public record MessageResponse(String message) {
    }

    /**
     * Either a signed-in session (token + user), or - when two-step sign-in is on - a challenge to
     * answer with a code at /auth/2fa/verify.
     */
    public record AuthResponse(String token, UserResponse user, Boolean twoFactorRequired, String challengeToken,
                               List<String> methods, String maskedPhone, Boolean codeSent) {
        public AuthResponse(String token, UserResponse user) {
            this(token, user, null, null, null, null, null);
        }

        static AuthResponse challenge(String challengeToken, List<String> methods, String maskedPhone, boolean codeSent) {
            return new AuthResponse(null, null, true, challengeToken, methods, maskedPhone, codeSent);
        }
    }

    public record TwoFactorVerifyRequest(
            @NotBlank @Size(max = 100) String challengeToken,
            @NotBlank @Size(max = 20) String code,
            /** TOTP, SMS or BACKUP. */
            @NotBlank @Size(max = 10) String method,
            @Size(max = 100) String deviceId
    ) {
    }

    public record ChallengeRequest(@NotBlank @Size(max = 100) String challengeToken) {
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    public record DisableTwoFactorRequest(
            @NotBlank @Size(max = 72) String password,
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 10) String method
    ) {
    }

    /** Shown once, right after two-step sign-in is switched on. */
    public record BackupCodesResponse(List<String> backupCodes) {
    }
}
