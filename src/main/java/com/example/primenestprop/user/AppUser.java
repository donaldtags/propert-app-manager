package com.example.primenestprop.user;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(length = 120)
    private String passwordHash;

    private String country = "Zimbabwe";
    private String preferredCurrency = "USD";
    private String avatarUrl;
    private String bio;
    private String city;
    private String diasporaLocation;
    private String nationalIdNumber;
    private String occupation;
    private String companyName;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private boolean emailNotifications = true;
    private boolean smsNotifications = true;
    private boolean twoFactorEnabled;
    private boolean identityVerified;
    private boolean verified;
    private int trustScore = 50;
    private String primaryProfile = "USER";
    private int profileCompletion;
    private Instant createdAt = Instant.now();
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<UserRole> roles = new HashSet<>();

    @PrePersist
    @PreUpdate
    void refreshComputedProfileFields() {
        updatedAt = Instant.now();
        primaryProfile = computePrimaryProfile();
        profileCompletion = computeProfileCompletion();
    }

    private String computePrimaryProfile() {
        if (roles.contains(UserRole.DIASPORA)) {
            return "DIASPORA";
        }
        if (roles.contains(UserRole.LANDLORD)) {
            return "LANDLORD";
        }
        if (roles.contains(UserRole.AGENT)) {
            return "AGENT";
        }
        if (roles.contains(UserRole.INVESTOR)) {
            return "INVESTOR";
        }
        if (roles.contains(UserRole.TENANT)) {
            return "TENANT";
        }
        if (roles.contains(UserRole.ADMIN)) {
            return "ADMIN";
        }
        return "USER";
    }

    private int computeProfileCompletion() {
        int completed = 0;
        int total = 8;
        completed += hasText(fullName) ? 1 : 0;
        completed += hasText(email) ? 1 : 0;
        completed += hasText(phone) ? 1 : 0;
        completed += hasText(country) ? 1 : 0;
        completed += hasText(city) ? 1 : 0;
        completed += hasText(preferredCurrency) ? 1 : 0;
        completed += identityVerified || verified ? 1 : 0;
        completed += hasText(emergencyContactPhone) ? 1 : 0;
        return Math.round((completed * 100f) / total);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
