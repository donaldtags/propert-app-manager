package com.example.primenestprop.organisation;

/**
 * Roles a user holds <em>within one organisation</em>, separate from their platform-wide
 * {@link com.example.primenestprop.user.UserRole}s. Staff roles run the agency; external roles
 * give a client access to their own slice of it (owner portal, tenant portal, ...).
 */
public enum OrgRole {
    ORG_ADMIN(true),
    /** Principal Registered Estate Agent: the only role that can approve publication or sign off a mandate. */
    PREA(true),
    NEGOTIATOR(true),
    PROPERTY_MANAGER(true),
    ANALYST(true),

    OWNER(false),
    TENANT(false),
    BUYER(false),
    INVESTOR(false),
    LANDOWNER(false);

    private final boolean staff;

    OrgRole(boolean staff) {
        this.staff = staff;
    }

    public boolean isStaff() {
        return staff;
    }

    static class SetConverter extends com.example.primenestprop.common.EnumSetConverter<OrgRole> {
        SetConverter() {
            super(OrgRole.class);
        }
    }
}
