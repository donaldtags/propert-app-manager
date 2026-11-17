package com.example.primenestprop.organisation;

import static com.example.primenestprop.organisation.OrgPermission.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Static per-organisation role to permission bundles (same shape as
 * {@link com.example.primenestprop.user.RolePermissions}): built once, checked in O(1), no DB hit.
 *
 * <p>{@link #PREA_RESERVED} permissions are held by {@link OrgRole#PREA} only. Not ORG_ADMIN, and
 * not a platform admin's ADMIN_OVERRIDE either: under the Estate Agents Act the registered
 * principal is personally accountable for what the firm publishes and signs.
 */
public final class OrgRolePermissions {
    public static final Set<OrgPermission> PREA_RESERVED =
            EnumSet.of(LISTING_APPROVE_PUBLISH, MANDATE_SIGN_OFF);

    private static final Set<OrgPermission> STAFF_BASE = EnumSet.of(ORG_VIEW, MEMBERS_VIEW);

    private static final Map<OrgRole, Set<OrgPermission>> BY_ROLE = new EnumMap<>(OrgRole.class);

    static {
        Set<OrgPermission> admin = EnumSet.allOf(OrgPermission.class);
        admin.removeAll(PREA_RESERVED);
        admin.removeAll(EnumSet.of(OWNER_PORTAL_VIEW, TENANT_PORTAL_VIEW, BUYER_PORTAL_VIEW,
                INVESTOR_PORTAL_VIEW, LANDOWNER_PORTAL_VIEW));
        BY_ROLE.put(OrgRole.ORG_ADMIN, admin);

        Set<OrgPermission> prea = EnumSet.copyOf(admin);
        prea.addAll(PREA_RESERVED);
        BY_ROLE.put(OrgRole.PREA, prea);

        BY_ROLE.put(OrgRole.NEGOTIATOR, with(LISTING_CREATE, LISTING_EDIT, MARKET_DATA_WRITE));
        BY_ROLE.put(OrgRole.PROPERTY_MANAGER, with(LISTING_CREATE, LISTING_EDIT, MAINTENANCE_MANAGE,
                TRUST_LEDGER_VIEW, TRUST_LEDGER_WRITE));
        BY_ROLE.put(OrgRole.ANALYST, with(MARKET_DATA_WRITE, MARKET_DATA_ACHIEVED_VIEW));

        BY_ROLE.put(OrgRole.OWNER, EnumSet.of(OWNER_PORTAL_VIEW));
        BY_ROLE.put(OrgRole.TENANT, EnumSet.of(TENANT_PORTAL_VIEW));
        BY_ROLE.put(OrgRole.BUYER, EnumSet.of(BUYER_PORTAL_VIEW));
        BY_ROLE.put(OrgRole.INVESTOR, EnumSet.of(INVESTOR_PORTAL_VIEW));
        BY_ROLE.put(OrgRole.LANDOWNER, EnumSet.of(LANDOWNER_PORTAL_VIEW));
    }

    private OrgRolePermissions() {
    }

    private static Set<OrgPermission> with(OrgPermission... extra) {
        Set<OrgPermission> set = EnumSet.copyOf(STAFF_BASE);
        set.addAll(EnumSet.of(extra[0], extra));
        return set;
    }

    public static Set<OrgPermission> of(OrgRole role) {
        return BY_ROLE.getOrDefault(role, Set.of());
    }

    public static Set<OrgPermission> of(Set<OrgRole> roles) {
        Set<OrgPermission> result = EnumSet.noneOf(OrgPermission.class);
        roles.forEach(role -> result.addAll(of(role)));
        return result;
    }
}
