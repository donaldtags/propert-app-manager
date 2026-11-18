package com.example.estatex.organisation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserRole;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrgRolePermissionsTest {

    @Test
    void onlyPreaHoldsPublicationApprovalAndMandateSignOff() {
        for (OrgRole role : OrgRole.values()) {
            Set<OrgPermission> perms = OrgRolePermissions.of(role);
            boolean prea = role == OrgRole.PREA;
            assertTrue(prea == perms.contains(OrgPermission.LISTING_APPROVE_PUBLISH), role + " approve-publish");
            assertTrue(prea == perms.contains(OrgPermission.MANDATE_SIGN_OFF), role + " mandate sign-off");
        }
    }

    @Test
    void platformAdminOverrideNeverGrantsPreaOrPortalPermissions() {
        AppUser admin = new AppUser();
        admin.setRoles(Set.of(UserRole.ADMIN));
        Set<OrgPermission> perms = OrgAccessService.effectivePermissions(admin, null);
        assertTrue(perms.contains(OrgPermission.MEMBERS_MANAGE));
        assertFalse(perms.contains(OrgPermission.LISTING_APPROVE_PUBLISH));
        assertFalse(perms.contains(OrgPermission.MANDATE_SIGN_OFF));
        assertFalse(perms.contains(OrgPermission.OWNER_PORTAL_VIEW));
    }

    @Test
    void inactiveMembershipHasNoPermissions() {
        OrganisationMembership m = new OrganisationMembership();
        m.setRoles(Set.of(OrgRole.PREA, OrgRole.ORG_ADMIN));
        m.setActive(false);
        assertTrue(m.permissions().isEmpty());
    }

    @Test
    void externalRolesGetOnlyTheirOwnPortal() {
        assertTrue(OrgRolePermissions.of(OrgRole.OWNER).equals(Set.of(OrgPermission.OWNER_PORTAL_VIEW)));
        assertFalse(OrgRolePermissions.of(OrgRole.TENANT).contains(OrgPermission.ORG_VIEW));
    }
}
