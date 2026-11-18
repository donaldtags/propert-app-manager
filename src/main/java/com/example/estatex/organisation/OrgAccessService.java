package com.example.estatex.organisation;

import com.example.estatex.common.ApiException;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place organisation-scoped authorization is decided. Every org service calls
 * {@link #require} before doing anything, the same way platform services test a {@link Permission}.
 *
 * <p>A platform admin (ADMIN_OVERRIDE) may act on any organisation for support and bootstrapping
 * (e.g. adding the first ORG_ADMIN), <em>except</em> for {@link OrgRolePermissions#PREA_RESERVED}
 * actions and the external portals, which are never granted by override.
 */
@Service
public class OrgAccessService {
    private static final Set<OrgPermission> NEVER_BY_OVERRIDE = EnumSet.of(
            OrgPermission.LISTING_APPROVE_PUBLISH,
            OrgPermission.MANDATE_SIGN_OFF,
            OrgPermission.OWNER_PORTAL_VIEW,
            OrgPermission.TENANT_PORTAL_VIEW,
            OrgPermission.BUYER_PORTAL_VIEW,
            OrgPermission.INVESTOR_PORTAL_VIEW,
            OrgPermission.LANDOWNER_PORTAL_VIEW);

    private final OrganisationRepository organisations;
    private final OrganisationMembershipRepository memberships;

    public OrgAccessService(OrganisationRepository organisations, OrganisationMembershipRepository memberships) {
        this.organisations = organisations;
        this.memberships = memberships;
    }

    /** Resolved caller context for one organisation. {@code membership} is null for a platform admin acting by override. */
    public record OrgContext(Organisation organisation, OrganisationMembership membership, AppUser user, Set<OrgPermission> permissions) {
        public boolean can(OrgPermission permission) {
            return permissions.contains(permission);
        }
    }

    @Transactional(readOnly = true)
    public Organisation requireOrganisation(String slug) {
        return organisations.findBySlugIgnoreCase(slug)
                .filter(Organisation::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Organisation not found"));
    }

    @Transactional(readOnly = true)
    public OrgContext context(AppUser user, String slug) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to access this workspace");
        }
        Organisation organisation = requireOrganisation(slug);
        OrganisationMembership membership = memberships
                .findByOrganisationIdAndUserId(organisation.getId(), user.getId())
                .filter(OrganisationMembership::isActive)
                .orElse(null);
        return new OrgContext(organisation, membership, user, effectivePermissions(user, membership));
    }

    @Transactional(readOnly = true)
    public OrgContext require(AppUser user, String slug, OrgPermission permission) {
        OrgContext context = context(user, slug);
        if (context.can(permission)) {
            return context;
        }
        if (OrgRolePermissions.PREA_RESERVED.contains(permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only the Principal Registered Estate Agent of " + context.organisation().getName() + " can do this");
        }
        if (context.membership() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not a member of " + context.organisation().getName());
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Your role in " + context.organisation().getName() + " does not allow this");
    }

    /** Same as {@link #require} for code that holds an organisation id rather than a slug. */
    @Transactional(readOnly = true)
    public OrgContext requireById(AppUser user, Long organisationId, OrgPermission permission) {
        Organisation organisation = organisations.findById(organisationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Organisation not found"));
        return require(user, organisation.getSlug(), permission);
    }

    /** True when the user may act with {@code permission} in the organisation; never throws for a signed-in user. */
    @Transactional(readOnly = true)
    public boolean can(AppUser user, Long organisationId, OrgPermission permission) {
        if (user == null || organisationId == null) {
            return false;
        }
        OrganisationMembership membership = memberships.findByOrganisationIdAndUserId(organisationId, user.getId())
                .filter(OrganisationMembership::isActive)
                .orElse(null);
        return effectivePermissions(user, membership).contains(permission);
    }

    static Set<OrgPermission> effectivePermissions(AppUser user, OrganisationMembership membership) {
        Set<OrgPermission> result = EnumSet.noneOf(OrgPermission.class);
        if (membership != null) {
            result.addAll(membership.permissions());
        }
        if (user.hasPermission(Permission.ADMIN_OVERRIDE)) {
            EnumSet<OrgPermission> override = EnumSet.allOf(OrgPermission.class);
            override.removeAll(NEVER_BY_OVERRIDE);
            result.addAll(override);
        }
        return result;
    }
}
