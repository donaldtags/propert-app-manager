package com.example.primenestprop.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public final class UserDtos {
    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            String phone,
            @NotBlank String password,
            String country,
            @NotEmpty Set<UserRole> roles
    ) {
    }

    public record UserSummaryResponse(Long id, String fullName, String primaryProfile) {
        public static UserSummaryResponse from(AppUser user) {
            return new UserSummaryResponse(user.getId(), user.getFullName(), user.getPrimaryProfile());
        }
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
            boolean businessVerified,
            String emergencyContactName,
            String emergencyContactPhone,
            boolean emailNotifications,
            boolean smsNotifications,
            boolean twoFactorEnabled,
            boolean identityVerified,
            boolean faceVerified,
            boolean verified,
            int trustScore,
            long yearsOnPlatform,
            String primaryProfile,
            int profileCompletion,
            Set<UserRole> roles
    ) {
        public static UserResponse from(AppUser user) {
            long yearsOnPlatform = user.getCreatedAt() == null
                    ? 0
                    : java.time.Duration.between(user.getCreatedAt(), java.time.Instant.now()).toDays() / 365;
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
                    user.isBusinessVerified(),
                    user.getEmergencyContactName(),
                    user.getEmergencyContactPhone(),
                    user.isEmailNotifications(),
                    user.isSmsNotifications(),
                    user.isTwoFactorEnabled(),
                    user.isIdentityVerified(),
                    user.isFaceVerified(),
                    user.isVerified(),
                    user.getTrustScore(),
                    yearsOnPlatform,
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

    public record AdminRequestResponse(
            Long id,
            Long userId,
            String userFullName,
            String userEmail,
            String status,
            java.time.Instant requestedAt
    ) {
        public static AdminRequestResponse from(AdminAccessRequest request) {
            return new AdminRequestResponse(
                    request.getId(),
                    request.getUser().getId(),
                    request.getUser().getFullName(),
                    request.getUser().getEmail(),
                    request.getStatus().name(),
                    request.getRequestedAt()
            );
        }
    }

    public record DecideAdminRequestRequest(boolean approve) {
    }

    public record LandlordProfileResponse(
            Long id,
            String fullName,
            String companyName,
            String avatarUrl,
            boolean verified,
            boolean identityVerified,
            int trustScore,
            long propertyCount,
            Double averageRating,
            long ratingCount
    ) {
    }
}
