package com.example.estatex.user;

public enum UserRole {
    TENANT,
    LANDLORD,
    AGENT,
    DIASPORA,
    INVESTOR,
    ADMIN,
    DEVELOPER,
    PRIVATE,
    SERVICE_PROVIDER,
    // EstateX staff roles. Granted only by an admin (never self-registered); see RolePermissions.
    FINANCE_ADMIN,
    MARKET_ANALYST,
    OPERATIONS_ADMIN,
    VERIFICATION_OFFICER;

    /** EstateX staff: ADMIN and the specialist roles. Nobody can give themselves one of these. */
    public boolean isStaff() {
        return this == ADMIN || this == FINANCE_ADMIN || this == MARKET_ANALYST || this == OPERATIONS_ADMIN
                || this == VERIFICATION_OFFICER;
    }

}
