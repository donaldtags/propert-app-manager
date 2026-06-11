package com.example.primenestprop.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class UserDtos {
    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String phone,
            @Size(min = 8) String password,
            String country,
            @NotEmpty Set<UserRole> roles
    ) {
    }

    public record UserResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            String country,
            String preferredCurrency,
            String avatarUrl,
            String bio,
            String city,
            String diasporaLocation,
            String occupation,
            String companyName,
            String emergencyContactName,
            String emergencyContactPhone,
            boolean emailNotifications,
            boolean smsNotifications,
            boolean twoFactorEnabled,
            boolean identityVerified,
            boolean verified,
            int trustScore,
            String primaryProfile,
            int profileCompletion,
            Set<UserRole> roles
    ) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(
                    user.getId(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getCountry(),
                    user.getPreferredCurrency(),
                    user.getAvatarUrl(),
                    user.getBio(),
                    user.getCity(),
                    user.getDiasporaLocation(),
                    user.getOccupation(),
                    user.getCompanyName(),
                    user.getEmergencyContactName(),
                    user.getEmergencyContactPhone(),
                    user.isEmailNotifications(),
                    user.isSmsNotifications(),
                    user.isTwoFactorEnabled(),
                    user.isIdentityVerified(),
                    user.isVerified(),
                    user.getTrustScore(),
                    user.getPrimaryProfile(),
                    user.getProfileCompletion(),
                    user.getRoles()
            );
        }
    }

    public record UpdateProfileRequest(
            String fullName,
            String phone,
            String country,
            String preferredCurrency,
            String avatarUrl,
            String bio,
            String city,
            String diasporaLocation,
            String nationalIdNumber,
            String occupation,
            String companyName,
            String emergencyContactName,
            String emergencyContactPhone,
            Boolean emailNotifications,
            Boolean smsNotifications,
            Boolean twoFactorEnabled
    ) {
    }

    public record AddRoleRequest(UserRole role, String password) {
    }

    public record AdminRequestResponse(Long id, Long userId, String status, java.time.Instant requestedAt) {
        public static AdminRequestResponse from(AdminAccessRequest request) {
            return new AdminRequestResponse(
                    request.getId(),
                    request.getUser().getId(),
                    request.getStatus().name(),
                    request.getRequestedAt()
            );
        }
    }
}
