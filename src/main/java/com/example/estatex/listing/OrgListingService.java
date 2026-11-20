package com.example.estatex.listing;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.notification.NotificationService;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.organisation.Division;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.organisation.Organisation;
import com.example.estatex.organisation.OrganisationMembershipRepository;
import com.example.estatex.organisation.OrgRole;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyDtos;
import com.example.estatex.property.PropertyMaterialChangeEvent;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.property.PropertyService;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.property.TitleType;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organisation listings and their publication gates (see {@link PublicationGuard}). Every
 * mutation that could invalidate a gate re-checks a live listing and takes it off the market
 * if it no longer qualifies, so "published" always means "every gate met".
 */
@Service
public class OrgListingService {
    private static final Set<PropertyStatus> PUBLISHABLE_FROM = Set.of(PropertyStatus.DRAFT, PropertyStatus.INACTIVE);

    private final OrgAccessService access;
    private final PropertyService propertyService;
    private final PropertyRepository properties;
    private final ListingComplianceRepository compliances;
    private final MandateRepository mandates;
    private final OrganisationMembershipRepository memberships;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final BusinessTime time;

    public OrgListingService(OrgAccessService access, PropertyService propertyService, PropertyRepository properties,
                             ListingComplianceRepository compliances, MandateRepository mandates,
                             OrganisationMembershipRepository memberships, UserRepository users, AuditService audit,
                             NotificationService notifications, BusinessTime time) {
        this.access = access;
        this.propertyService = propertyService;
        this.properties = properties;
        this.compliances = compliances;
        this.mandates = mandates;
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.time = time;
    }

    /** A listing resolved together with its organisation context and compliance row. */
    record Listing(OrgContext ctx, Property property, ListingCompliance compliance) {
    }

    // -- reads -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ListingDtos.ListingSummary> list(String slug, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        List<ListingCompliance> rows = compliances.findByOrganisationIdOrderByCreatedAtDesc(ctx.organisation().getId());
        Map<Long, String> names = names(rows.stream().map(ListingCompliance::getNegotiatorUserId).toList());
        return rows.stream().map(c -> {
            Property p = propertyService.require(c.getPropertyId());
            PublicationGuard.Report report = report(p, c);
            int open = (int) report.checks().stream().filter(ch -> !ch.met()).count();
            return new ListingDtos.ListingSummary(p.getId(), p.getTitle(), p.getListingType(), p.getStatus(), p.getCity(),
                    p.getSuburb(), toCents(p.getPrice()), p.getCurrency(), p.getTitleType(), riskFlags(p),
                    names.get(c.getNegotiatorUserId()), report.publishable(), open, p.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ListingDtos.ListingDetail detail(String slug, Long propertyId, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.ORG_VIEW);
        List<Mandate> list = mandates.findByPropertyIdOrderByCreatedAtDesc(propertyId);
        Map<Long, String> names = names(list.stream().map(Mandate::getPreaSignedOffByUserId).toList());
        return new ListingDtos.ListingDetail(
                PropertyDtos.PropertyResponse.from(l.property()),
                complianceResponse(l.property(), l.compliance()),
                list.stream().map(m -> ListingDtos.MandateResponse.from(m, names.get(m.getPreaSignedOffByUserId()))).toList());
    }

    // -- writes ------------------------------------------------------------------------------

    @Transactional
    public Property create(String slug, ListingDtos.CreateListingRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_CREATE);
        Organisation org = ctx.organisation();
        if (r.clientId() != null) {
            ListingCompliance existing = compliances.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                if (!existing.getOrganisationId().equals(org.getId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "clientId already used");
                }
                return propertyService.require(existing.getPropertyId()); // offline replay
            }
        }
        Division division = r.division() == null ? Division.PROPERTIES : r.division();
        if (!org.getDivisions().contains(division)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, division + " is not enabled for " + org.getName());
        }
        Long negotiatorId = r.negotiatorUserId() == null ? user.getId() : r.negotiatorUserId();
        if (!negotiatorId.equals(user.getId()) && !access.can(users.findById(negotiatorId).orElse(null), org.getId(), OrgPermission.LISTING_EDIT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The negotiator must be a member of " + org.getName() + " who can edit listings");
        }
        String currency = r.currency() == null ? org.getDefaultCurrency() : r.currency();
        PropertyDtos.CreatePropertyRequest request = new PropertyDtos.CreatePropertyRequest(
                r.title().trim(), r.description(), r.listingType(), r.city(), r.suburb(), r.address(), "Zimbabwe",
                r.bedrooms(), r.bathrooms(), BigDecimal.valueOf(r.priceCents(), 2), currency,
                null, null,
                false, true, false, false, null, false, false, false, false, false,
                negotiatorId, null, null, null, null, null);
        Property property = propertyService.create(request, org.getId(), r.titleType());

        ListingCompliance c = new ListingCompliance();
        c.setPropertyId(property.getId());
        c.setClientId(r.clientId());
        c.setOrganisationId(org.getId());
        c.setDivision(division);
        c.setNegotiatorUserId(negotiatorId);
        compliances.save(c);

        audit.record(user, entry("LISTING_CREATED", "Property", property.getId())
                .organisation(org.getId())
                .transition(null, PropertyStatus.DRAFT)
                .money(r.priceCents(), currency));
        return property;
    }

    @Transactional
    public ListingDtos.ListingDetail setTitle(String slug, Long propertyId, ListingDtos.SetTitleRequest r, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.LISTING_EDIT);
        Property p = l.property();
        ListingCompliance c = l.compliance();
        TitleType before = p.getTitleType();
        String reference = blankToNull(r.titleReference());
        boolean typeChanged = before != r.titleType();
        boolean referenceChanged = !Objects.equals(reference, c.getTitleReference());
        if (!typeChanged && !referenceChanged) {
            return detail(slug, propertyId, user);
        }
        p.setTitleType(r.titleType());
        c.setTitleReference(reference);
        if (typeChanged) {
            Set<String> applicable = new LinkedHashSet<>();
            VerificationChecklist.itemsFor(r.titleType()).forEach(i -> applicable.add(i.key()));
            Set<String> kept = new LinkedHashSet<>(c.getCheckedItemSet());
            kept.retainAll(applicable);
            c.setCheckedItemSet(kept);
        }
        if (c.getTitleVerificationStatus() != TitleVerificationStatus.NOT_STARTED) {
            // Whatever was verified was a different title; it has to be checked again.
            c.setTitleVerificationStatus(TitleVerificationStatus.NOT_STARTED);
            c.setTitleVerifiedAt(null);
            c.setTitleVerifiedByUserId(null);
        }
        audit.record(user, entry("LISTING_TITLE_CHANGED", "Property", p.getId())
                .organisation(c.getOrganisationId())
                .transition(before, r.titleType())
                .metadata(reference == null ? Map.of() : Map.of("titleReference", reference)));
        if (typeChanged && before != null) {
            withdrawApproval(p, c, List.of("title type"), user);
        }
        enforceStillPublishable(p, c, user, "Title details changed");
        return detail(slug, propertyId, user);
    }

    /** Sets which checklist items are ticked. Unticking one on a VERIFIED title moves it back to IN_PROGRESS. */
    @Transactional
    public ListingDtos.ListingDetail setChecklist(String slug, Long propertyId, Set<String> checked, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.LISTING_EDIT);
        Property p = l.property();
        ListingCompliance c = l.compliance();
        if (p.getTitleType() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Record the title type first; the checklist depends on it.");
        }
        Set<String> valid = new LinkedHashSet<>();
        VerificationChecklist.itemsFor(p.getTitleType()).forEach(i -> valid.add(i.key()));
        for (String key : checked) {
            if (!valid.contains(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, key + " doesn't apply to a " + p.getTitleType() + " title.");
            }
        }
        Set<String> ordered = new LinkedHashSet<>();
        valid.stream().filter(checked::contains).forEach(ordered::add);
        int before = c.getCheckedItemSet().size();
        c.setCheckedItemSet(ordered);
        if (c.getTitleVerificationStatus() == TitleVerificationStatus.VERIFIED && ordered.size() < valid.size()) {
            c.setTitleVerificationStatus(TitleVerificationStatus.IN_PROGRESS);
            c.setTitleVerifiedAt(null);
            c.setTitleVerifiedByUserId(null);
        }
        audit.record(user, entry("LISTING_VERIFICATION_CHECKLIST", "Property", propertyId)
                .organisation(c.getOrganisationId())
                .transition(before + "/" + valid.size(), ordered.size() + "/" + valid.size())
                .metadata(Map.of("checked", List.copyOf(ordered))));
        enforceStillPublishable(p, c, user, "Title verification is no longer complete");
        return detail(slug, propertyId, user);
    }

    @Transactional
    public ListingDtos.ListingDetail verifyTitle(String slug, Long propertyId, ListingDtos.TitleVerificationRequest r, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.TITLE_VERIFY);
        Property p = l.property();
        ListingCompliance c = l.compliance();
        if (r.status() == TitleVerificationStatus.VERIFIED && (p.getTitleType() == null || c.getTitleReference() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Record the title type and title reference number before verifying.");
        }
        if (r.status() == TitleVerificationStatus.VERIFIED) {
            List<String> missing = VerificationChecklist.itemsFor(p.getTitleType()).stream()
                    .filter(i -> !c.getCheckedItemSet().contains(i.key()))
                    .map(VerificationChecklist.Item::label).toList();
            if (!missing.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "Finish the verification checklist first: " + String.join("; ", missing) + ".");
            }
        }
        TitleVerificationStatus before = c.getTitleVerificationStatus();
        c.setTitleVerificationStatus(r.status());
        c.setTitleVerificationNotes(blankToNull(r.notes()));
        boolean verified = r.status() == TitleVerificationStatus.VERIFIED;
        c.setTitleVerifiedAt(verified ? Instant.now() : null);
        c.setTitleVerifiedByUserId(verified ? user.getId() : null);
        // The agency having checked title documents is what the platform's "verified listing" means.
        propertyService.setVerification(p, verified ? com.example.estatex.property.VerificationStatus.VERIFIED
                : r.status() == TitleVerificationStatus.FAILED ? com.example.estatex.property.VerificationStatus.REJECTED
                : com.example.estatex.property.VerificationStatus.PENDING);
        audit.record(user, entry("LISTING_TITLE_VERIFICATION", "Property", p.getId())
                .organisation(c.getOrganisationId())
                .transition(before, r.status()));
        enforceStillPublishable(p, c, user, "Title verification is no longer complete");
        return detail(slug, propertyId, user);
    }

    @Transactional
    public ListingDtos.ListingDetail recordConsent(String slug, Long propertyId, ListingDtos.ConsentRequest r, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.LISTING_EDIT);
        ListingCompliance c = l.compliance();
        String before = consentLabel(c.isOwnerConsentPhotos(), c.isOwnerConsentAddress());
        c.setOwnerConsentPhotos(r.photos());
        c.setOwnerConsentAddress(r.address());
        c.setConsentRecordedAt(Instant.now());
        c.setConsentRecordedByUserId(user.getId());
        audit.record(user, entry("LISTING_OWNER_CONSENT", "Property", propertyId)
                .organisation(c.getOrganisationId())
                .transition(before, consentLabel(r.photos(), r.address())));
        enforceStillPublishable(l.property(), c, user, "Owner withdrew consent");
        return detail(slug, propertyId, user);
    }

    @Transactional
    public ListingDtos.ListingDetail approve(String slug, Long propertyId, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.LISTING_APPROVE_PUBLISH);
        PublicationGuard.Report report = report(l.property(), l.compliance());
        if (!report.readyForApproval()) {
            throw new ApiException(HttpStatus.CONFLICT, "Not ready for approval: " + String.join(" ", report.unmetDetails(false)));
        }
        ListingCompliance c = l.compliance();
        c.setPreaApprovedAt(Instant.now());
        c.setPreaApprovedByUserId(user.getId());
        audit.record(user, entry("LISTING_PREA_APPROVED", "Property", propertyId)
                .organisation(c.getOrganisationId())
                .metadata(Map.of("activeMandateId", String.valueOf(c.getActiveMandateId()))));
        return detail(slug, propertyId, user);
    }

    @Transactional
    public ListingDtos.ListingDetail publish(String slug, Long propertyId, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.LISTING_EDIT);
        Property p = l.property();
        if (p.getStatus() == PropertyStatus.AVAILABLE) {
            return detail(slug, propertyId, user);
        }
        if (!PUBLISHABLE_FROM.contains(p.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "A listing marked " + p.getStatus() + " can't be published.");
        }
        if (!l.ctx().organisation().isApproved()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Your company is waiting for EstateX approval. Listings can go live once it's approved.");
        }
        PublicationGuard.Report report = report(p, l.compliance());
        if (!report.publishable()) {
            throw new ApiException(HttpStatus.CONFLICT, "Can't publish yet: " + String.join(" ", report.unmetDetails(true)));
        }
        PropertyStatus before = p.getStatus();
        p.setStatus(PropertyStatus.AVAILABLE);
        l.compliance().setPublishedAt(Instant.now());
        l.compliance().setPublishedByUserId(user.getId());
        audit.record(user, entry("LISTING_PUBLISHED", "Property", propertyId)
                .organisation(l.compliance().getOrganisationId())
                .transition(before, PropertyStatus.AVAILABLE));
        return detail(slug, propertyId, user);
    }

    @Transactional
    public ListingDtos.ListingDetail unpublish(String slug, Long propertyId, String reason, AppUser user) {
        Listing l = load(slug, propertyId, user, OrgPermission.LISTING_EDIT);
        if (l.property().getStatus() == PropertyStatus.AVAILABLE) {
            takeOffMarket(l.property(), l.compliance(), user,
                    reason == null || reason.isBlank() ? "Unpublished by " + user.getFullName() : reason, false);
        }
        return detail(slug, propertyId, user);
    }

    /**
     * A reserved listing whose accepted offer fell through goes back on the market if every gate
     * is still met (mandate in force etc.), otherwise back to draft.
     */
    @Transactional
    public void relistAfterFallThrough(Property p, AppUser actor) {
        ListingCompliance c = compliances.findByPropertyId(p.getId()).orElse(null);
        boolean publishable = c != null && report(p, c).publishable();
        PropertyStatus next = publishable ? PropertyStatus.AVAILABLE : PropertyStatus.DRAFT;
        p.setStatus(next);
        audit.record(actor, entry(publishable ? "LISTING_RELISTED" : "LISTING_UNPUBLISHED", "Property", p.getId())
                .organisation(p.getOrganisationId())
                .transition(PropertyStatus.RESERVED, next)
                .metadata(Map.of("reason", "Accepted offer withdrawn")));
    }

    /** Price or address edited through the general property endpoint: approval no longer stands. */
    @EventListener
    public void onMaterialChange(PropertyMaterialChangeEvent event) {
        compliances.findByPropertyId(event.propertyId()).ifPresent(c -> {
            Property p = propertyService.require(event.propertyId());
            withdrawApproval(p, c, event.changedFields(), event.actor());
            enforceStillPublishable(p, c, event.actor(), "Changed " + String.join(" and ", event.changedFields()) + " needs PREA approval");
        });
    }

    // -- package API used by MandateService and the expiry job --------------------------------

    Listing load(String slug, Long propertyId, AppUser user, OrgPermission permission) {
        OrgContext ctx = access.require(user, slug, permission);
        Property property = properties.findById(propertyId)
                .filter(p -> ctx.organisation().getId().equals(p.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found in " + ctx.organisation().getName()));
        ListingCompliance compliance = compliances.findByPropertyId(propertyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing compliance record missing"));
        return new Listing(ctx, property, compliance);
    }

    PublicationGuard.Report report(Property p, ListingCompliance c) {
        Mandate mandate = c.getActiveMandateId() == null ? null : mandates.findById(c.getActiveMandateId()).orElse(null);
        return PublicationGuard.evaluate(p, c, mandate, time.today());
    }

    /** If the listing is live but a gate is no longer met, take it off the market. */
    void enforceStillPublishable(Property p, ListingCompliance c, AppUser actor, String reason) {
        if (p.getStatus() == PropertyStatus.AVAILABLE && !report(p, c).publishable()) {
            takeOffMarket(p, c, actor, reason, true);
        }
    }

    void takeOffMarket(Property p, ListingCompliance c, AppUser actor, String reason, boolean automatic) {
        PropertyStatus before = p.getStatus();
        p.setStatus(PropertyStatus.DRAFT);
        audit.record(actor, entry("LISTING_UNPUBLISHED", "Property", p.getId())
                .organisation(c.getOrganisationId())
                .transition(before, PropertyStatus.DRAFT)
                .metadata(Map.of("reason", reason, "automatic", automatic)));
        if (automatic) {
            notifyResponsible(c, p.getId(), "Listing taken off the market",
                    "\"" + p.getTitle() + "\" was unpublished: " + reason + ".");
        }
    }

    void notifyResponsible(ListingCompliance c, Long relatedId, String title, String message) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (c.getNegotiatorUserId() != null) {
            recipients.add(c.getNegotiatorUserId());
        }
        memberships.findByOrganisationIdAndActiveTrue(c.getOrganisationId()).stream()
                .filter(m -> m.getRoles().contains(OrgRole.PREA))
                .forEach(m -> recipients.add(m.getUser().getId()));
        users.findAllById(recipients).forEach(u -> notifications.notify(u, NotificationType.LISTING_COMPLIANCE, relatedId, title, message));
    }

    private void withdrawApproval(Property p, ListingCompliance c, List<String> fields, AppUser actor) {
        boolean hadApproval = c.getPreaApprovedAt() != null;
        c.setMaterialChangedAt(Instant.now());
        c.clearPreaApproval();
        if (hadApproval) {
            audit.record(actor, entry("LISTING_APPROVAL_WITHDRAWN", "Property", p.getId())
                    .organisation(c.getOrganisationId())
                    .metadata(Map.of("changed", fields)));
        }
    }

    ListingDtos.ComplianceResponse complianceResponse(Property p, ListingCompliance c) {
        PublicationGuard.Report report = report(p, c);
        Map<Long, String> names = names(List.of(
                c.getTitleVerifiedByUserId() == null ? -1L : c.getTitleVerifiedByUserId(),
                c.getPreaApprovedByUserId() == null ? -1L : c.getPreaApprovedByUserId(),
                c.getNegotiatorUserId() == null ? -1L : c.getNegotiatorUserId()));
        return new ListingDtos.ComplianceResponse(c.getTitleVerificationStatus(), c.getTitleReference(),
                c.getTitleVerificationNotes(), c.getTitleVerifiedAt(), names.get(c.getTitleVerifiedByUserId()),
                c.isOwnerConsentPhotos(), c.isOwnerConsentAddress(), c.getConsentRecordedAt(), c.getPreaApprovedAt(),
                names.get(c.getPreaApprovedByUserId()), c.getMaterialChangedAt(), c.getActiveMandateId(),
                c.getPublishedAt(), c.getDivision(), c.getNegotiatorUserId(), names.get(c.getNegotiatorUserId()),
                report.checks().stream().map(ListingDtos.CheckResponse::from).toList(),
                VerificationChecklist.itemsFor(p.getTitleType()).stream()
                        .map(i -> new ListingDtos.ChecklistItemResponse(i.key(), i.label(), c.getCheckedItemSet().contains(i.key())))
                        .toList(),
                report.publishable(), report.readyForApproval(), c.getVersion());
    }

    private Map<Long, String> names(List<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        users.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList())
                .forEach(u -> result.put(u.getId(), u.getFullName()));
        return result;
    }

    private static List<String> riskFlags(Property p) {
        return p.getTitleType() == TitleType.CESSION ? List.of("CESSION") : List.of();
    }

    static Long toCents(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static String consentLabel(boolean photos, boolean address) {
        return "photos=" + photos + ",address=" + address;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
