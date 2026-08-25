package com.example.primenestprop.user;

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
    private static final Map<UserRole, Set<Permission>> BY_ROLE = Map.of(
            UserRole.ADMIN, EnumSet.allOf(Permission.class),
            UserRole.LANDLORD, EnumSet.of(Permission.PROPERTY_CREATE),
            UserRole.AGENT, EnumSet.of(Permission.PROPERTY_CREATE, Permission.PROPERTY_VERIFY),
            UserRole.DEVELOPER, EnumSet.of(Permission.PROPERTY_CREATE, Permission.INVESTMENT_MANAGE),
            UserRole.PRIVATE, EnumSet.of(Permission.PROPERTY_CREATE),
            UserRole.TENANT, EnumSet.of(Permission.TENANT_APPLY),
            UserRole.DIASPORA, EnumSet.of(Permission.TENANT_APPLY, Permission.INVESTMENT_CREATE),
            UserRole.INVESTOR, EnumSet.of(Permission.INVESTMENT_CREATE),
            UserRole.SERVICE_PROVIDER, EnumSet.of(Permission.VENDOR_SERVICE_PROVIDE)
    );

    private RolePermissions() {
    }

    public static Set<Permission> of(UserRole role) {
        return BY_ROLE.getOrDefault(role, Set.of());
    }
}
