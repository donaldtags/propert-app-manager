package com.example.estatex.user;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Static role -> capability bundles. Built once at class-load into immutable {@link EnumSet}s,
 * so resolving "does this user have permission X" is a fixed number of O(1) bitset membership
 * checks (one per role the user holds) with no database access - the cost of an authorization
 * check does not grow with request volume or concurrent users.
 */
public final class RolePermissions {
    private static final Map<UserRole, Set<Permission>> BY_ROLE = Map.ofEntries(
            Map.entry(UserRole.ADMIN, EnumSet.allOf(Permission.class)),
            Map.entry(UserRole.LANDLORD, EnumSet.of(Permission.PROPERTY_CREATE)),
            // Verifying listings is EstateX staff work (ADMIN): an agent - a self-chosen role at sign-up -
            // must never be able to put a "Verified" badge on a listing, least of all their own.
            Map.entry(UserRole.AGENT, EnumSet.of(Permission.PROPERTY_CREATE)),
            Map.entry(UserRole.DEVELOPER, EnumSet.of(Permission.PROPERTY_CREATE, Permission.INVESTMENT_MANAGE)),
            Map.entry(UserRole.PRIVATE, EnumSet.of(Permission.PROPERTY_CREATE)),
            Map.entry(UserRole.TENANT, EnumSet.of(Permission.TENANT_APPLY)),
            Map.entry(UserRole.DIASPORA, EnumSet.of(Permission.TENANT_APPLY, Permission.INVESTMENT_CREATE, Permission.PROPERTY_CREATE)),
            Map.entry(UserRole.INVESTOR, EnumSet.of(Permission.INVESTMENT_CREATE)),
            Map.entry(UserRole.SERVICE_PROVIDER, EnumSet.of(Permission.VENDOR_SERVICE_PROVIDE)),
            // Staff roles, granted only by an admin. ADMIN (above) is the super administrator.
            Map.entry(UserRole.FINANCE_ADMIN, EnumSet.of(Permission.ANALYTICS_FINANCE_VIEW, Permission.REPORTS_EXPORT)),
            Map.entry(UserRole.MARKET_ANALYST, EnumSet.of(Permission.ANALYTICS_MARKET_VIEW, Permission.ANALYTICS_DEMAND_VIEW,
                    Permission.REPORTS_EXPORT)),
            Map.entry(UserRole.OPERATIONS_ADMIN, EnumSet.of(Permission.ANALYTICS_OPS_VIEW, Permission.ANALYTICS_MARKET_VIEW,
                    Permission.ANALYTICS_DEMAND_VIEW)),
            Map.entry(UserRole.VERIFICATION_OFFICER, EnumSet.of(Permission.PROPERTY_VERIFY, Permission.ANALYTICS_OPS_VIEW))
    );

    private RolePermissions() {
    }

    public static Set<Permission> of(UserRole role) {
        return BY_ROLE.getOrDefault(role, Set.of());
    }
}
