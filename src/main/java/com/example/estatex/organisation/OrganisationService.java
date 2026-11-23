package com.example.estatex.organisation;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditLog;
import com.example.estatex.common.AuditService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.UserRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganisationService {
    /** Fonts the frontend bundles; anything else would silently fall back, so reject it up front. */
    static final Set<String> SUPPORTED_FONTS = Set.of("Montserrat", "Inter", "Geist");
    private static final Set<String> RESERVED_SLUGS = Set.of("mine", "new", "admin", "api", "pending-review");

    private final OrganisationRepository organisations;
    private final OrganisationMembershipRepository memberships;
    private final UserRepository users;
    private final OrgAccessService access;
    private final AuditService audit;

    public OrganisationService(OrganisationRepository organisations, OrganisationMembershipRepository memberships,
                               UserRepository users, OrgAccessService access, AuditService audit) {
        this.organisations = organisations;
        this.memberships = memberships;
        this.users = users;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OrganisationDtos.OrganisationResponse profile(String slug, AppUser currentUser) {
        Organisation organisation = access.requireOrganisation(slug);
        boolean internal = false;
        if (currentUser != null) {
            internal = access.context(currentUser, slug).can(OrgPermission.ORG_SETTINGS_EDIT)
                    || isStaffMember(organisation, currentUser);
        }
        boolean platformAdmin = currentUser != null && currentUser.hasPermission(Permission.ORGANISATION_MANAGE);
        if (!organisation.isApproved() && !internal && !platformAdmin) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Organisation not found");
        }
        return OrganisationDtos.OrganisationResponse.from(organisation, internal);
    }

    @Transactional(readOnly = true)
    public OrganisationDtos.MyOrgContextResponse myContext(String slug, AppUser currentUser) {
        OrgContext ctx = access.context(currentUser, slug);
        OrganisationMembership m = ctx.membership();
        if (m == null && ctx.permissions().isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not a member of " + ctx.organisation().getName());
        }
        boolean staff = m != null && m.getRoles().stream().anyMatch(OrgRole::isStaff);
        return new OrganisationDtos.MyOrgContextResponse(
                OrganisationDtos.OrganisationResponse.from(ctx.organisation(), staff || m == null),
                m == null ? null : m.getId(),
                m == null ? Set.of() : m.getRoles(),
                m == null ? Set.of() : m.getDivisions(),
                ctx.permissions(),
                m == null);
    }

    @Transactional(readOnly = true)
    public List<OrganisationDtos.MyOrgContextResponse> mine(AppUser currentUser) {
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to see your workspaces");
        }
        return memberships.findByUserIdAndActiveTrue(currentUser.getId()).stream()
                .filter(m -> m.getOrganisation().isActive())
                .map(m -> new OrganisationDtos.MyOrgContextResponse(
                        OrganisationDtos.OrganisationResponse.from(m.getOrganisation(),
                                m.getRoles().stream().anyMatch(OrgRole::isStaff)),
                        m.getId(), m.getRoles(), m.getDivisions(),
                        OrgAccessService.effectivePermissions(currentUser, m), false))
                .toList();
    }

    @Transactional
    public Organisation create(OrganisationDtos.CreateOrganisationRequest request, AppUser currentUser) {
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to register a company");
        }
        boolean platformAdmin = currentUser.hasPermission(Permission.ORGANISATION_MANAGE);
        if (!platformAdmin) {
            // Self sign-up: the company is reviewed by EstateX before anything of it is public.
            if (request.eacRegistrationNumber() == null || request.eacRegistrationNumber().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Enter the company's Estate Agents Council registration number");
            }
            if (organisations.countByCreatedByUserIdAndApprovalStatus(currentUser.getId(), "PENDING") > 0) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "You already have a company waiting for approval. We'll let you know as soon as it's reviewed.");
            }
        }
        String slug = request.slug().toLowerCase();
        if (RESERVED_SLUGS.contains(slug)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That slug is reserved");
        }
        if (organisations.findBySlugIgnoreCase(slug).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "An organisation with that slug already exists");
        }
        Organisation organisation = new Organisation();
        organisation.setSlug(slug);
        organisation.setName(request.name().trim());
        organisation.setDivisions(request.divisions() == null || request.divisions().isEmpty()
                ? EnumSet.of(Division.PROPERTIES) : EnumSet.copyOf(request.divisions()));
        organisation.setEacRegistrationNumber(blankToNull(request.eacRegistrationNumber()));
        organisation.setPhone(blankToNull(request.phone()));
        organisation.setEmail(blankToNull(request.email()));
        organisation.setAddress(blankToNull(request.address()));
        organisation.setCreatedByUserId(currentUser.getId());
        organisation.setApprovalStatus(platformAdmin ? "APPROVED" : "PENDING");
        organisations.save(organisation);
        if (!platformAdmin) {
            // The person who registered the company runs its workspace.
            OrganisationMembership founder = new OrganisationMembership();
            founder.setOrganisation(organisation);
            founder.setUser(currentUser);
            founder.setRoles(EnumSet.of(OrgRole.ORG_ADMIN));
            founder.setDivisions(EnumSet.copyOf(organisation.getDivisions()));
            founder.setInvitedByUserId(currentUser.getId());
            memberships.save(founder);
        }
        audit.record(currentUser, entry("ORG_CREATED", "Organisation", organisation.getId())
                .organisation(organisation.getId())
                .metadata(Map.of("slug", slug, "name", organisation.getName())));
        return organisation;
    }

    @Transactional
    public Organisation update(String slug, OrganisationDtos.UpdateOrganisationRequest r, AppUser currentUser) {
        OrgContext ctx = access.require(currentUser, slug, OrgPermission.ORG_SETTINGS_EDIT);
        Organisation o = ctx.organisation();
        if (r.version() != null && !Objects.equals(r.version(), o.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "These settings were changed by someone else since you opened them. Reload and try again.");
        }
        List<String> changed = new ArrayList<>();
        changed.addAll(set(r.name(), o.getName(), v -> { o.setName(v.trim()); return "name"; }));
        changed.addAll(set(r.slogan(), o.getSlogan(), v -> { o.setSlogan(v); return "slogan"; }));
        if (r.divisions() != null && !r.divisions().equals(o.getDivisions())) {
            o.setDivisions(r.divisions().isEmpty() ? EnumSet.noneOf(Division.class) : EnumSet.copyOf(r.divisions()));
            changed.add("divisions");
        }
        changed.addAll(set(r.logoOnLightUrl(), o.getLogoOnLightUrl(), v -> { o.setLogoOnLightUrl(v); return "logoOnLightUrl"; }));
        changed.addAll(set(r.logoOnDarkUrl(), o.getLogoOnDarkUrl(), v -> { o.setLogoOnDarkUrl(v); return "logoOnDarkUrl"; }));
        if (r.theme() != null) {
            requireSupportedFont(r.theme().headingFont());
            requireSupportedFont(r.theme().bodyFont());
            r.theme().applyTo(o.getTheme());
            changed.add("theme");
        }
        changed.addAll(set(r.phone(), o.getPhone(), v -> { o.setPhone(v); return "phone"; }));
        changed.addAll(set(r.whatsappNumber(), o.getWhatsappNumber(), v -> { o.setWhatsappNumber(v); return "whatsappNumber"; }));
        changed.addAll(set(r.instagramHandle(), o.getInstagramHandle(),
                v -> { o.setInstagramHandle(v.replaceFirst("^@", "")); return "instagramHandle"; }));
        changed.addAll(set(r.email(), o.getEmail(), v -> { o.setEmail(v); return "email"; }));
        changed.addAll(set(r.address(), o.getAddress(), v -> { o.setAddress(v); return "address"; }));
        changed.addAll(set(r.website(), o.getWebsite(), v -> { o.setWebsite(v); return "website"; }));
        changed.addAll(set(r.eacRegistrationNumber(), o.getEacRegistrationNumber(),
                v -> { o.setEacRegistrationNumber(v); return "eacRegistrationNumber"; }));
        changed.addAll(set(r.trustAccountReference(), o.getTrustAccountReference(),
                v -> { o.setTrustAccountReference(v); return "trustAccountReference"; }));
        changed.addAll(set(r.trustAccountBank(), o.getTrustAccountBank(),
                v -> { o.setTrustAccountBank(v); return "trustAccountBank"; }));
        changed.addAll(set(r.defaultCurrency(), o.getDefaultCurrency(), v -> { o.setDefaultCurrency(v); return "defaultCurrency"; }));
        if (r.saleCommissionBps() != null && r.saleCommissionBps() != o.getSaleCommissionBps()) {
            o.setSaleCommissionBps(r.saleCommissionBps());
            changed.add("saleCommissionBps");
        }
        if (r.managementFeeBps() != null && r.managementFeeBps() != o.getManagementFeeBps()) {
            o.setManagementFeeBps(r.managementFeeBps());
            changed.add("managementFeeBps");
        }
        if (r.lettingFeeMonthsX100() != null && r.lettingFeeMonthsX100() != o.getLettingFeeMonthsX100()) {
            o.setLettingFeeMonthsX100(r.lettingFeeMonthsX100());
            changed.add("lettingFeeMonthsX100");
        }
        if (!changed.isEmpty()) {
            audit.record(currentUser, entry("ORG_SETTINGS_UPDATED", "Organisation", o.getId())
                    .organisation(o.getId())
                    .metadata(Map.of("fields", changed)));
        }
        return o;
    }

    @Transactional(readOnly = true)
    public List<OrganisationMembership> members(String slug, AppUser currentUser) {
        OrgContext ctx = access.require(currentUser, slug, OrgPermission.MEMBERS_VIEW);
        return memberships.findByOrganisationIdOrderByCreatedAtAsc(ctx.organisation().getId());
    }

    @Transactional
    public OrganisationMembership addMember(String slug, OrganisationDtos.AddMemberRequest request, AppUser currentUser) {
        OrgContext ctx = access.require(currentUser, slug, OrgPermission.MEMBERS_MANAGE);
        Organisation organisation = ctx.organisation();
        AppUser user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No account uses that email. Ask them to register first, then add them."));
        if (memberships.findByOrganisationIdAndUserId(organisation.getId(), user.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, user.getFullName() + " is already a member. Edit their roles instead.");
        }
        Set<OrgRole> roles = EnumSet.copyOf(request.roles());
        requireDivisionsEnabled(organisation, request.divisions());
        if (roles.contains(OrgRole.PREA)) {
            assertNoOtherActivePrea(organisation, null);
        }

        OrganisationMembership membership = new OrganisationMembership();
        membership.setOrganisation(organisation);
        membership.setUser(user);
        membership.setRoles(roles);
        membership.setDivisions(request.divisions() == null || request.divisions().isEmpty()
                ? EnumSet.noneOf(Division.class) : EnumSet.copyOf(request.divisions()));
        membership.setInvitedByUserId(currentUser.getId());
        memberships.save(membership);
        syncPrea(organisation, membership, currentUser);

        audit.record(currentUser, entry("ORG_MEMBER_ADDED", "OrganisationMembership", membership.getId())
                .organisation(organisation.getId())
                .transition(null, roleList(roles))
                .metadata(Map.of("userId", user.getId())));
        return membership;
    }

    @Transactional
    public OrganisationMembership updateMember(String slug, Long membershipId, OrganisationDtos.UpdateMemberRequest request,
                                               AppUser currentUser) {
        OrgContext ctx = access.require(currentUser, slug, OrgPermission.MEMBERS_MANAGE);
        Organisation organisation = ctx.organisation();
        OrganisationMembership membership = memberships.findById(membershipId)
                .filter(m -> m.getOrganisation().getId().equals(organisation.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found"));

        Set<OrgRole> before = EnumSet.noneOf(OrgRole.class);
        before.addAll(membership.getRoles());
        boolean activeBefore = membership.isActive();

        Set<OrgRole> after = request.roles() == null ? before : request.roles().isEmpty()
                ? EnumSet.noneOf(OrgRole.class) : EnumSet.copyOf(request.roles());
        boolean activeAfter = request.active() == null ? activeBefore : request.active();
        if (after.isEmpty() && activeAfter) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A member needs at least one role. Deactivate them instead.");
        }
        if (activeAfter && after.contains(OrgRole.PREA) && !(activeBefore && before.contains(OrgRole.PREA))) {
            assertNoOtherActivePrea(organisation, membership.getId());
        }
        boolean losesAdmin = activeBefore && before.contains(OrgRole.ORG_ADMIN)
                && (!activeAfter || !after.contains(OrgRole.ORG_ADMIN));
        if (losesAdmin && countActiveWithRole(organisation, OrgRole.ORG_ADMIN, membership.getId()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This is the last workspace admin. Make someone else an admin first.");
        }
        if (request.divisions() != null) {
            requireDivisionsEnabled(organisation, request.divisions());
            membership.setDivisions(request.divisions().isEmpty()
                    ? EnumSet.noneOf(Division.class) : EnumSet.copyOf(request.divisions()));
        }
        membership.setRoles(after);
        membership.setActive(activeAfter);
        syncPrea(organisation, membership, currentUser);

        if (!before.equals(after) || activeBefore != activeAfter) {
            audit.record(currentUser, entry(activeBefore != activeAfter
                            ? (activeAfter ? "ORG_MEMBER_REACTIVATED" : "ORG_MEMBER_DEACTIVATED")
                            : "ORG_MEMBER_ROLES_CHANGED", "OrganisationMembership", membership.getId())
                    .organisation(organisation.getId())
                    .transition(roleList(before) + (activeBefore ? "" : " (inactive)"),
                            roleList(after) + (activeAfter ? "" : " (inactive)"))
                    .metadata(Map.of("userId", membership.getUser().getId())));
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public List<OrganisationDtos.AuditEntryResponse> auditTrail(String slug, int limit, AppUser currentUser) {
        OrgContext ctx = access.require(currentUser, slug, OrgPermission.AUDIT_VIEW);
        List<AuditLog> logs = audit.forOrganisation(ctx.organisation().getId(), limit);
        Set<Long> actorIds = logs.stream().map(AuditLog::getActorUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        users.findAllById(actorIds).forEach(u -> names.put(u.getId(), u.getFullName()));
        return logs.stream()
                .map(l -> OrganisationDtos.AuditEntryResponse.from(l, names.get(l.getActorUserId())))
                .toList();
    }

    // -- helpers ---------------------------------------------------------------------------

    private boolean isStaffMember(Organisation organisation, AppUser user) {
        return memberships.findByOrganisationIdAndUserId(organisation.getId(), user.getId())
                .filter(OrganisationMembership::isActive)
                .map(m -> m.getRoles().stream().anyMatch(OrgRole::isStaff))
                .orElse(false);
    }

    private void assertNoOtherActivePrea(Organisation organisation, Long exceptMembershipId) {
        if (countActiveWithRole(organisation, OrgRole.PREA, exceptMembershipId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    organisation.getName() + " already has a Principal Registered Estate Agent. "
                            + "Remove the PREA role from them first.");
        }
    }

    private long countActiveWithRole(Organisation organisation, OrgRole role, Long exceptMembershipId) {
        return memberships.findByOrganisationIdOrderByCreatedAtAsc(organisation.getId()).stream()
                .filter(OrganisationMembership::isActive)
                .filter(m -> !m.getId().equals(exceptMembershipId))
                .filter(m -> m.getRoles().contains(role))
                .count();
    }

    /** Keeps the organisation's EAC principal fields in step with whoever holds the PREA role. */
    private void syncPrea(Organisation organisation, OrganisationMembership membership, AppUser actor) {
        boolean isPrea = membership.isActive() && membership.getRoles().contains(OrgRole.PREA);
        Long userId = membership.getUser().getId();
        if (isPrea && !userId.equals(organisation.getPreaUserId())) {
            Long previous = organisation.getPreaUserId();
            organisation.setPreaUserId(userId);
            organisation.setPreaName(membership.getUser().getFullName());
            audit.record(actor, entry("ORG_PREA_ASSIGNED", "Organisation", organisation.getId())
                    .organisation(organisation.getId())
                    .transition(previous, userId));
        } else if (!isPrea && userId.equals(organisation.getPreaUserId())) {
            organisation.setPreaUserId(null);
            organisation.setPreaName(null);
            audit.record(actor, entry("ORG_PREA_REMOVED", "Organisation", organisation.getId())
                    .organisation(organisation.getId())
                    .transition(userId, null));
        }
    }

    private void requireDivisionsEnabled(Organisation organisation, Set<Division> divisions) {
        if (divisions == null) {
            return;
        }
        for (Division d : divisions) {
            if (!organisation.getDivisions().contains(d)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, d + " is not enabled for " + organisation.getName());
            }
        }
    }

    private void requireSupportedFont(String font) {
        if (font != null && !SUPPORTED_FONTS.contains(font)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported font " + font + ". Use one of " + SUPPORTED_FONTS);
        }
    }

    private static String roleList(Set<OrgRole> roles) {
        return roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static List<String> set(String incoming, String current, Function<String, String> apply) {
        if (incoming == null || incoming.equals(current)) {
            return List.of();
        }
        return List.of(apply.apply(incoming));
    }

    /** Approved companies, for the public "Agencies" directory. */
    @Transactional(readOnly = true)
    public List<Organisation> directory() {
        return organisations.findPublicDirectory();
    }

    @Transactional(readOnly = true)
    public List<Organisation> awaitingApproval(AppUser currentUser) {
        requirePlatformAdmin(currentUser);
        return organisations.findByApprovalStatusOrderByCreatedAtAsc("PENDING");
    }

    /** EstateX staff decide on a self-registered company. Rejections need a reason for the company. */
    @Transactional
    public Organisation decideApproval(String slug, boolean approved, String note, AppUser currentUser,
                                       com.example.estatex.notification.NotificationService notifications) {
        requirePlatformAdmin(currentUser);
        Organisation o = access.requireOrganisation(slug);
        if (!approved && (note == null || note.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give the company a reason when rejecting it");
        }
        String from = o.isApproved() ? "APPROVED" : o.getApprovalStatus();
        o.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
        o.setApprovalNote(note == null || note.isBlank() ? null : note.trim());
        o.setApprovalDecidedAt(java.time.Instant.now());
        o.setApprovalDecidedByUserId(currentUser.getId());
        audit.record(currentUser, entry(approved ? "ORG_APPROVED" : "ORG_REJECTED", "Organisation", o.getId())
                .organisation(o.getId())
                .transition(from, o.getApprovalStatus())
                .metadata(Map.of("note", o.getApprovalNote() == null ? "" : o.getApprovalNote())));
        if (o.getCreatedByUserId() != null) {
            users.findById(o.getCreatedByUserId()).ifPresent(founder -> notifications.notify(founder,
                    com.example.estatex.notification.NotificationType.ACCOUNT_VERIFICATION, o.getId(),
                    approved ? o.getName() + " is approved" : o.getName() + " needs attention",
                    approved ? "Your company is now public on EstateX. Listings approved by your PREA can go live."
                            : "We couldn't approve your company yet: " + o.getApprovalNote()));
        }
        return o;
    }

    private void requirePlatformAdmin(AppUser user) {
        if (user == null || !user.hasPermission(Permission.ORGANISATION_MANAGE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only EstateX staff review companies");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
