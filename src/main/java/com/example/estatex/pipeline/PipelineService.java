package com.example.estatex.pipeline;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.commission.CommissionRepository;
import com.example.estatex.commission.CommissionStatus;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.contact.Contact;
import com.example.estatex.contact.ContactActivitySource;
import com.example.estatex.contact.ContactDtos;
import com.example.estatex.contact.ContactRepository;
import com.example.estatex.contact.ContactService;
import com.example.estatex.contact.ContactType;
import com.example.estatex.listing.ListingComplianceRepository;
import com.example.estatex.listing.MandateRepository;
import com.example.estatex.offer.OfferRepository;
import com.example.estatex.offer.OfferStatus;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mandate pipeline. Stage moves are commands the server checks against real records, so the
 * board can't claim progress the paperwork doesn't support.
 */
@Service
public class PipelineService implements ContactActivitySource {
    private static final Set<PipelineStage> NEEDS_MANDATE = EnumSet.of(
            PipelineStage.MANDATE_SIGNED, PipelineStage.MARKETING, PipelineStage.VIEWINGS, PipelineStage.OFFER);
    private static final Set<PipelineStage> NEEDS_LIVE = EnumSet.of(
            PipelineStage.MARKETING, PipelineStage.VIEWINGS, PipelineStage.OFFER);

    private final OrgAccessService access;
    private final PipelineCardRepository cards;
    private final ContactService contactService;
    private final ContactRepository contacts;
    private final PropertyRepository properties;
    private final ListingComplianceRepository compliances;
    private final MandateRepository mandates;
    private final OfferRepository offers;
    private final CommissionRepository commissions;
    private final UserRepository users;
    private final AuditService audit;
    private final BusinessTime time;

    public PipelineService(OrgAccessService access, PipelineCardRepository cards, ContactService contactService,
                           ContactRepository contacts, PropertyRepository properties, ListingComplianceRepository compliances,
                           MandateRepository mandates, OfferRepository offers, CommissionRepository commissions,
                           UserRepository users, AuditService audit, BusinessTime time) {
        this.access = access;
        this.cards = cards;
        this.contactService = contactService;
        this.contacts = contacts;
        this.properties = properties;
        this.compliances = compliances;
        this.mandates = mandates;
        this.offers = offers;
        this.commissions = commissions;
        this.users = users;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<PipelineDtos.CardResponse> board(String slug, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        List<PipelineCard> rows = cards.findByOrganisationIdOrderByStageChangedAtDesc(ctx.organisation().getId());
        Map<Long, String> owners = new HashMap<>();
        contacts.findAllById(rows.stream().map(PipelineCard::getOwnerContactId).distinct().toList())
                .forEach(c -> owners.put(c.getId(), c.getFullName()));
        Map<Long, String> titles = new HashMap<>();
        properties.findAllById(rows.stream().map(PipelineCard::getPropertyId).filter(Objects::nonNull).distinct().toList())
                .forEach(p -> titles.put(p.getId(), p.getTitle()));
        Map<Long, String> negotiators = new HashMap<>();
        users.findAllById(rows.stream().map(PipelineCard::getNegotiatorUserId).filter(Objects::nonNull).distinct().toList())
                .forEach(u -> negotiators.put(u.getId(), u.getFullName()));
        return rows.stream().map(c -> response(c, owners.get(c.getOwnerContactId()), titles.get(c.getPropertyId()),
                negotiators.get(c.getNegotiatorUserId()))).toList();
    }

    @Transactional
    public PipelineCard create(String slug, PipelineDtos.CreateCardRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_CREATE);
        Long org = ctx.organisation().getId();
        if (r.clientId() != null) {
            PipelineCard existing = cards.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Contact owner = contactService.requireInOrganisation(org, r.ownerContactId());
        EnumSet<ContactType> types = owner.getTypes().isEmpty() ? EnumSet.noneOf(ContactType.class) : EnumSet.copyOf(owner.getTypes());
        types.add(ContactType.OWNER);
        owner.setTypes(types);
        PipelineCard c = new PipelineCard();
        c.setClientId(r.clientId());
        c.setOrganisationId(org);
        c.setTitle(r.title().trim());
        c.setOwnerContactId(owner.getId());
        c.setPropertyId(propertyIn(org, r.propertyId()));
        c.setNegotiatorUserId(r.negotiatorUserId() == null ? user.getId() : r.negotiatorUserId());
        c.setEstimatedValueCents(r.estimatedValueCents());
        c.setCurrency(ctx.organisation().getDefaultCurrency());
        c.setNextActionOn(r.nextActionOn());
        c.setNextAction(r.nextAction());
        c.setNotes(r.notes());
        c.setCreatedByUserId(user.getId());
        cards.save(c);
        audit.record(user, entry("PIPELINE_CARD_CREATED", "PipelineCard", c.getId()).organisation(org)
                .transition(null, PipelineStage.PROSPECT));
        return c;
    }

    @Transactional
    public PipelineCard update(String slug, Long id, PipelineDtos.UpdateCardRequest r, AppUser user) {
        PipelineCard c = load(slug, id, user);
        if (r.title() != null && !r.title().isBlank()) c.setTitle(r.title().trim());
        if (r.propertyId() != null) c.setPropertyId(propertyIn(c.getOrganisationId(), r.propertyId()));
        if (r.estimatedValueCents() != null) c.setEstimatedValueCents(r.estimatedValueCents());
        if (r.nextActionOn() != null) c.setNextActionOn(r.nextActionOn());
        if (r.nextAction() != null) c.setNextAction(r.nextAction().isBlank() ? null : r.nextAction().trim());
        if (r.notes() != null) c.setNotes(r.notes());
        return c;
    }

    @Transactional
    public PipelineCard move(String slug, Long id, PipelineDtos.MoveRequest r, AppUser user) {
        PipelineCard c = load(slug, id, user);
        PipelineStage from = c.getStage();
        PipelineStage to = r.stage();
        if (from == to) {
            return c; // idempotent replay
        }
        if (from == PipelineStage.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "A completed deal can't be moved.");
        }
        if (to == PipelineStage.LOST && (r.lostReason() == null || r.lostReason().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Say why the deal was lost.");
        }
        if (to != PipelineStage.LOST) {
            checkEvidence(c, to);
        }
        c.setStage(to);
        c.setLostReason(to == PipelineStage.LOST ? r.lostReason().trim() : null);
        c.setStageChangedAt(Instant.now());
        audit.record(user, entry("PIPELINE_STAGE_CHANGED", "PipelineCard", c.getId()).organisation(c.getOrganisationId())
                .transition(from, to)
                .metadata(to == PipelineStage.LOST ? Map.of("lostReason", c.getLostReason()) : Map.of()));
        return c;
    }

    private void checkEvidence(PipelineCard c, PipelineStage to) {
        boolean needsListing = to.ordinal() >= PipelineStage.MANDATE_SIGNED.ordinal();
        Property p = c.getPropertyId() == null ? null : properties.findById(c.getPropertyId()).orElse(null);
        if (needsListing && p == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Link the listing to this deal first.");
        }
        if (NEEDS_MANDATE.contains(to)) {
            boolean inForce = compliances.findByPropertyId(p.getId())
                    .map(x -> x.getActiveMandateId() == null ? null : mandates.findById(x.getActiveMandateId()).orElse(null))
                    .map(m -> m.isInForceOn(time.today()))
                    .orElse(false);
            if (!inForce) {
                throw new ApiException(HttpStatus.CONFLICT, "The listing has no signed-off mandate in force.");
            }
        }
        if (NEEDS_LIVE.contains(to) && p.getStatus() != PropertyStatus.AVAILABLE && p.getStatus() != PropertyStatus.RESERVED) {
            throw new ApiException(HttpStatus.CONFLICT, "The listing isn't live yet. Publish it first.");
        }
        if (to == PipelineStage.CONVEYANCING && !offers.existsByPropertyIdAndStatusCode(p.getId(), OfferStatus.ACCEPTED.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a deal with an accepted offer can go to the conveyancer.");
        }
        if (to == PipelineStage.COMPLETED) {
            boolean earned = commissions.findByOrganisationIdOrderByCreatedAtDesc(c.getOrganisationId()).stream()
                    .anyMatch(x -> x.getPropertyId().equals(p.getId())
                            && (x.getStatus() == CommissionStatus.EARNED || x.getStatus() == CommissionStatus.RECEIVED));
            if (!earned) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "A deal is completed when the conveyancer confirms transfer. Record that on the commission first.");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDtos.ActivityItem> activityFor(Long organisationId, Long contactId) {
        return cards.findByOrganisationIdAndOwnerContactIdOrderByCreatedAtDesc(organisationId, contactId).stream()
                .map(c -> new ContactDtos.ActivityItem("DEAL", c.getId(), c.getStageChangedAt(),
                        c.getTitle() + " · " + c.getStage().name().toLowerCase().replace('_', ' '), c.getPropertyId(), null))
                .toList();
    }

    PipelineDtos.CardResponse response(PipelineCard c) {
        return response(c, contacts.findById(c.getOwnerContactId()).map(Contact::getFullName).orElse(null),
                c.getPropertyId() == null ? null : properties.findById(c.getPropertyId()).map(Property::getTitle).orElse(null),
                c.getNegotiatorUserId() == null ? null : users.findById(c.getNegotiatorUserId()).map(AppUser::getFullName).orElse(null));
    }

    private PipelineDtos.CardResponse response(PipelineCard c, String owner, String propertyTitle, String negotiator) {
        long days = Duration.between(c.getStageChangedAt(), Instant.now()).toDays();
        return new PipelineDtos.CardResponse(c.getId(), c.getClientId(), c.getTitle(), c.getStage(), c.getOwnerContactId(),
                owner, c.getPropertyId(), propertyTitle, c.getNegotiatorUserId(), negotiator, c.getEstimatedValueCents(),
                c.getCurrency(), c.getLostReason(), c.getNextActionOn(), c.getNextAction(), c.getNotes(),
                c.getStageChangedAt(), days, c.getCreatedAt(), c.getVersion());
    }

    private Long propertyIn(Long org, Long propertyId) {
        if (propertyId == null) {
            return null;
        }
        return properties.findById(propertyId).filter(p -> org.equals(p.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That listing isn't in this organisation")).getId();
    }

    private PipelineCard load(String slug, Long id, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        return cards.findById(id).filter(c -> c.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Deal not found"));
    }
}
