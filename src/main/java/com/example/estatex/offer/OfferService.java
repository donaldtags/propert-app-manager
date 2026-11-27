package com.example.estatex.offer;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.common.PhoneNumbers;
import com.example.estatex.contact.Contact;
import com.example.estatex.contact.ContactActivitySource;
import com.example.estatex.contact.ContactDtos;
import com.example.estatex.contact.ContactRepository;
import com.example.estatex.contact.ContactService;
import com.example.estatex.contact.ContactType;
import com.example.estatex.listing.ListingCompliance;
import com.example.estatex.listing.ListingComplianceRepository;
import com.example.estatex.listing.Mandate;
import com.example.estatex.listing.MandateRepository;
import com.example.estatex.listing.OrgListingService;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.user.AppUser;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offers are worked strictly within the mandate: they can only be recorded while a signed-off
 * mandate is in force, and the agency can't accept or counter below the owner's minimum without
 * recording the owner's own written instruction. Accepting one offer reserves the listing.
 */
@Service
public class OfferService implements ContactActivitySource {
    private final OrgAccessService access;
    private final OfferRepository offers;
    private final OfferEventRepository events;
    private final PropertyRepository properties;
    private final ListingComplianceRepository compliances;
    private final MandateRepository mandates;
    private final ContactService contactService;
    private final ContactRepository contactRepository;
    private final OrgListingService listings;
    private final AuditService audit;
    private final BusinessTime time;

    public OfferService(OrgAccessService access, OfferRepository offers, OfferEventRepository events,
                        PropertyRepository properties, ListingComplianceRepository compliances, MandateRepository mandates,
                        ContactService contactService, ContactRepository contactRepository, OrgListingService listings,
                        AuditService audit, BusinessTime time) {
        this.access = access;
        this.offers = offers;
        this.events = events;
        this.properties = properties;
        this.compliances = compliances;
        this.mandates = mandates;
        this.contactService = contactService;
        this.contactRepository = contactRepository;
        this.listings = listings;
        this.audit = audit;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<OfferDtos.OfferResponse> forListing(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        Property p = listingIn(ctx, propertyId);
        return offers.findByPropertyIdOrderByCreatedAtDesc(p.getId()).stream().map(this::response).toList();
    }

    @Transactional
    public Offer record(String slug, Long propertyId, OfferDtos.RecordOfferRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        if (r.clientId() != null) {
            Offer existing = offers.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Property p = listingIn(ctx, propertyId);
        Mandate mandate = mandateInForce(p);
        Contact buyer = contactService.requireInOrganisation(ctx.organisation().getId(), r.buyerContactId());
        Offer o = new Offer();
        o.setClientId(r.clientId());
        o.setOrganisationId(ctx.organisation().getId());
        o.setPropertyId(p.getId());
        o.setMandateId(mandate.getId());
        o.setBuyerContactId(buyer.getId());
        o.setAmountCents(r.amountCents());
        o.setCurrency(mandate.getCurrency());
        o.setDepositCents(r.depositCents());
        o.setFinancing(r.financing());
        o.setConditions(r.conditions());
        o.setExpiresOn(r.expiresOn());
        o.setNegotiatorUserId(user.getId());
        offers.save(o);
        EnumSet<ContactType> types = buyer.getTypes().isEmpty() ? EnumSet.noneOf(ContactType.class) : EnumSet.copyOf(buyer.getTypes());
        types.add(p.getListingType() == ListingType.SALE ? ContactType.BUYER : ContactType.TENANT);
        buyer.setTypes(types);
        event(o, OfferEventType.RECORDED, o.getAmountCents(), r.conditions(), user);
        audit.record(user, entry("OFFER_RECORDED", "Offer", o.getId())
                .organisation(o.getOrganisationId())
                .money(o.getAmountCents(), o.getCurrency())
                .metadata(Map.of("propertyId", p.getId(), "buyerContactId", buyer.getId(),
                        "withinAuthority", withinAuthority(o.getAmountCents(), mandate))));
        return o;
    }

    @Transactional
    public Offer present(String slug, Long offerId, String via, AppUser user) {
        Offer o = load(slug, offerId, user);
        if (o.getStatus() != OfferStatus.RECEIVED && o.getStatus() != OfferStatus.COUNTERED) {
            throw new ApiException(HttpStatus.CONFLICT, "This offer is " + o.getStatus() + ".");
        }
        mandateInForce(properties.findById(o.getPropertyId()).orElseThrow());
        OfferStatus before = o.getStatus();
        o.setStatus(OfferStatus.PRESENTED);
        o.setPresentedVia(via);
        o.setPresentedAt(Instant.now());
        event(o, OfferEventType.PRESENTED, o.getAmountCents(), "Sent to the owner via " + via.toLowerCase().replace('_', ' '), user);
        audit.record(user, entry("OFFER_PRESENTED", "Offer", o.getId()).organisation(o.getOrganisationId())
                .transition(before, OfferStatus.PRESENTED));
        return o;
    }

    /** Records the OWNER's decision. The agency can't make it for them outside the mandate. */
    @Transactional
    public Offer respond(String slug, Long offerId, OfferDtos.OwnerResponseRequest r, AppUser user) {
        Offer o = load(slug, offerId, user);
        if (!o.getStatus().isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "This offer is already " + o.getStatus() + ".");
        }
        Property p = properties.findById(o.getPropertyId()).orElseThrow();
        Mandate mandate = mandateInForce(p);
        String instruction = r.ownerInstructionReference() == null || r.ownerInstructionReference().isBlank()
                ? null : r.ownerInstructionReference().trim();
        OfferStatus before = o.getStatus();
        switch (r.decision()) {
            case ACCEPT -> {
                if (!withinAuthority(o.getAmountCents(), mandate) && instruction == null) {
                    throw new ApiException(HttpStatus.CONFLICT, "This offer is below the owner's minimum in the mandate ("
                            + money(mandate.getMinimumPriceCents(), mandate.getCurrency())
                            + "). Record the owner's written instruction to accept it.");
                }
                if (offers.existsByPropertyIdAndStatusCode(p.getId(), OfferStatus.ACCEPTED.name())) {
                    throw new ApiException(HttpStatus.CONFLICT, "Another offer on this listing has already been accepted.");
                }
                o.setStatus(OfferStatus.ACCEPTED);
                if (p.getStatus() == PropertyStatus.AVAILABLE) {
                    p.setStatus(PropertyStatus.UNDER_OFFER);
                    audit.record(user, entry("LISTING_UNDER_OFFER", "Property", p.getId()).organisation(o.getOrganisationId())
                            .transition(PropertyStatus.AVAILABLE, PropertyStatus.UNDER_OFFER)
                            .metadata(Map.of("offerId", o.getId())));
                }
            }
            case COUNTER -> {
                if (r.counterAmountCents() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Give the owner's counter-offer amount.");
                }
                if (!withinAuthority(r.counterAmountCents(), mandate) && instruction == null) {
                    throw new ApiException(HttpStatus.CONFLICT, "That counter is below the owner's minimum in the mandate ("
                            + money(mandate.getMinimumPriceCents(), mandate.getCurrency())
                            + "). Record the owner's written instruction first.");
                }
                o.setCounterAmountCents(r.counterAmountCents());
                o.setStatus(OfferStatus.COUNTERED);
            }
            case REJECT -> o.setStatus(OfferStatus.REJECTED);
        }
        if (instruction != null) {
            o.setOwnerInstructionReference(instruction);
        }
        o.setDecidedAt(Instant.now());
        OfferEventType type = switch (r.decision()) {
            case ACCEPT -> OfferEventType.ACCEPTED;
            case REJECT -> OfferEventType.REJECTED;
            case COUNTER -> OfferEventType.COUNTERED;
        };
        event(o, type, r.decision() == OfferDtos.Decision.COUNTER ? r.counterAmountCents() : o.getAmountCents(),
                joinNote(r.note(), instruction == null ? null : "Owner's instruction: " + instruction), user);
        audit.record(user, entry("OFFER_" + type.name(), "Offer", o.getId()).organisation(o.getOrganisationId())
                .transition(before, o.getStatus())
                .money(r.decision() == OfferDtos.Decision.COUNTER ? r.counterAmountCents() : o.getAmountCents(), o.getCurrency())
                .metadata(instruction == null ? Map.of() : Map.of("ownerInstruction", instruction)));
        return o;
    }

    /** The buyer walked away. An accepted offer falling through puts the listing back on the market if it still qualifies. */
    @Transactional
    public Offer withdraw(String slug, Long offerId, String note, AppUser user) {
        Offer o = load(slug, offerId, user);
        if (o.getStatus() != OfferStatus.ACCEPTED && !o.getStatus().isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "This offer is already " + o.getStatus() + ".");
        }
        OfferStatus before = o.getStatus();
        o.setStatus(OfferStatus.WITHDRAWN);
        event(o, OfferEventType.WITHDRAWN, null, note, user);
        audit.record(user, entry("OFFER_WITHDRAWN", "Offer", o.getId()).organisation(o.getOrganisationId())
                .transition(before, OfferStatus.WITHDRAWN));
        if (before == OfferStatus.ACCEPTED) {
            Property p = properties.findById(o.getPropertyId()).orElseThrow();
            if (p.getStatus() == PropertyStatus.UNDER_OFFER) {
                listings.relistAfterFallThrough(p, user);
            }
        }
        return o;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDtos.ActivityItem> activityFor(Long organisationId, Long contactId) {
        return offers.findByOrganisationIdAndBuyerContactIdOrderByCreatedAtDesc(organisationId, contactId).stream()
                .map(o -> new ContactDtos.ActivityItem("OFFER", o.getId(), o.getCreatedAt(),
                        "Offer " + money(o.getAmountCents(), o.getCurrency()) + " · " + o.getStatus().name().toLowerCase(),
                        o.getPropertyId(), properties.findById(o.getPropertyId()).map(Property::getTitle).orElse(null)))
                .toList();
    }

    OfferDtos.OfferResponse response(Offer o) {
        Mandate m = mandates.findById(o.getMandateId()).orElse(null);
        String buyer = contactRepository.findById(o.getBuyerContactId()).map(Contact::getFullName).orElse(null);
        String title = properties.findById(o.getPropertyId()).map(Property::getTitle).orElse(null);
        return new OfferDtos.OfferResponse(o.getId(), o.getPropertyId(), title, o.getMandateId(), o.getBuyerContactId(),
                buyer, o.getAmountCents(), o.getCurrency(), o.getDepositCents(), o.getCounterAmountCents(), o.getFinancing(),
                o.getConditions(), o.getExpiresOn(), o.getStatus(), o.getPresentedVia(), o.getPresentedAt(), o.getDecidedAt(),
                o.getOwnerInstructionReference(), m == null || withinAuthority(o.getAmountCents(), m),
                m == null ? null : m.getMinimumPriceCents(), m == null ? null : m.getOwnerName(),
                m == null ? null : PhoneNumbers.whatsappDigits(m.getOwnerPhone()), o.getCreatedAt(),
                events.findByOfferIdOrderByAtAscIdAsc(o.getId()).stream().map(OfferDtos.EventResponse::from).toList(),
                o.getVersion());
    }

    private Property listingIn(OrgContext ctx, Long propertyId) {
        return properties.findById(propertyId)
                .filter(p -> ctx.organisation().getId().equals(p.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
    }

    private Mandate mandateInForce(Property p) {
        ListingCompliance c = compliances.findByPropertyId(p.getId()).orElse(null);
        Mandate m = c == null || c.getActiveMandateId() == null ? null : mandates.findById(c.getActiveMandateId()).orElse(null);
        if (m == null || !m.isInForceOn(time.today())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "There's no signed-off mandate in force for this listing, so the agency has no authority to work offers on it.");
        }
        return m;
    }

    static boolean withinAuthority(long amountCents, Mandate m) {
        return m.getMinimumPriceCents() == null || amountCents >= m.getMinimumPriceCents();
    }

    private Offer load(String slug, Long offerId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        return offers.findById(offerId)
                .filter(o -> o.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
    }

    private void event(Offer o, OfferEventType type, Long amount, String note, AppUser user) {
        OfferEvent e = new OfferEvent();
        e.setOfferId(o.getId());
        e.setType(type);
        e.setAmountCents(amount);
        e.setNote(note == null || note.isBlank() ? null : note.trim());
        e.setActorUserId(user.getId());
        events.save(e);
    }

    private static String joinNote(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null) return a;
        return a.trim() + " · " + b;
    }

    static String money(Long cents, String currency) {
        return cents == null ? "-" : currency + " " + String.format("%,.0f", cents / 100.0);
    }
}
