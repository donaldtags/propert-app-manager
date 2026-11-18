package com.example.estatex.showing;

import static com.example.estatex.common.AuditService.entry;

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
import com.example.estatex.listing.Mandate;
import com.example.estatex.listing.MandateRepository;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.user.AppUser;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowingService implements ContactActivitySource {
    private final OrgAccessService access;
    private final ShowingRepository showings;
    private final PropertyRepository properties;
    private final ListingComplianceRepository compliances;
    private final MandateRepository mandates;
    private final ContactService contactService;
    private final ContactRepository contactRepository;
    private final AuditService audit;
    private final BusinessTime time;

    public ShowingService(OrgAccessService access, ShowingRepository showings, PropertyRepository properties,
                          ListingComplianceRepository compliances, MandateRepository mandates, ContactService contactService,
                          ContactRepository contactRepository, AuditService audit, BusinessTime time) {
        this.access = access;
        this.showings = showings;
        this.properties = properties;
        this.compliances = compliances;
        this.mandates = mandates;
        this.contactService = contactService;
        this.contactRepository = contactRepository;
        this.audit = audit;
        this.time = time;
    }

    /** Idempotent on clientId. Needs a mandate in force on the viewing date: no mandate, no authority to show. */
    @Transactional
    public Showing schedule(String slug, Long propertyId, ShowingDtos.ScheduleRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.CONTACTS_MANAGE);
        if (r.clientId() != null) {
            Showing existing = showings.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Property p = properties.findById(propertyId)
                .filter(x -> ctx.organisation().getId().equals(x.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
        LocalDate day = r.scheduledAt().atZone(time.zone()).toLocalDate();
        Mandate m = compliances.findByPropertyId(p.getId())
                .map(c -> c.getActiveMandateId() == null ? null : mandates.findById(c.getActiveMandateId()).orElse(null))
                .orElse(null);
        if (m == null || !m.isInForceOn(day)) {
            throw new ApiException(HttpStatus.CONFLICT, "There's no signed-off mandate in force on " + day
                    + " for this listing, so it can't be shown on the agency's behalf.");
        }
        if (r.scheduledAt().isBefore(Instant.now().minus(Duration.ofDays(30)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That date is too far in the past.");
        }
        Contact contact = contactService.requireInOrganisation(ctx.organisation().getId(), r.contactId());
        EnumSet<ContactType> types = contact.getTypes().isEmpty() ? EnumSet.noneOf(ContactType.class) : EnumSet.copyOf(contact.getTypes());
        types.add(p.getListingType() == ListingType.SALE ? ContactType.BUYER : ContactType.TENANT);
        contact.setTypes(types);

        Showing s = new Showing();
        s.setClientId(r.clientId());
        s.setOrganisationId(ctx.organisation().getId());
        s.setPropertyId(p.getId());
        s.setContactId(contact.getId());
        s.setNegotiatorUserId(user.getId());
        s.setScheduledAt(r.scheduledAt());
        s.setDurationMinutes(r.durationMinutes() == null ? 30 : r.durationMinutes());
        return showings.save(s);
    }

    /** Sets the outcome. Idempotent: replaying the same outcome is harmless. */
    @Transactional
    public Showing recordOutcome(String slug, Long showingId, ShowingDtos.OutcomeRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.CONTACTS_MANAGE);
        Showing s = showings.findById(showingId)
                .filter(x -> x.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Viewing not found"));
        ShowingStatus before = s.getStatus();
        s.setStatus(r.status());
        if (r.status() == ShowingStatus.COMPLETED) {
            s.setInterest(r.interest());
            s.setPriceOpinion(r.priceOpinion());
            s.setFeedback(r.feedback() == null || r.feedback().isBlank() ? null : r.feedback().trim());
            if (s.getCompletedAt() == null) {
                s.setCompletedAt(Instant.now());
            }
        }
        if (before != r.status()) {
            audit.record(user, entry("VIEWING_" + r.status().name(), "Showing", s.getId())
                    .organisation(s.getOrganisationId()).transition(before, r.status())
                    .metadata(Map.of("propertyId", s.getPropertyId())));
        }
        return s;
    }

    @Transactional(readOnly = true)
    public List<ShowingDtos.ShowingResponse> forListing(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.CONTACTS_MANAGE);
        return showings.findByPropertyIdOrderByScheduledAtDesc(propertyId).stream()
                .filter(s -> s.getOrganisationId().equals(ctx.organisation().getId()))
                .map(this::response).toList();
    }

    /** Viewings from the start of today (Harare) for the next {@code days} days. */
    @Transactional(readOnly = true)
    public List<ShowingDtos.ShowingResponse> upcoming(String slug, int days, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.CONTACTS_MANAGE);
        Instant from = time.today().atStartOfDay(time.zone()).toInstant();
        Instant to = from.plus(Duration.ofDays(Math.max(1, Math.min(days, 60))));
        return showings.findByOrganisationIdAndScheduledAtBetweenOrderByScheduledAtAsc(ctx.organisation().getId(), from, to)
                .stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDtos.ActivityItem> activityFor(Long organisationId, Long contactId) {
        return showings.findByOrganisationIdAndContactIdOrderByScheduledAtDesc(organisationId, contactId).stream()
                .map(s -> new ContactDtos.ActivityItem("VIEWING", s.getId(), s.getScheduledAt(),
                        "Viewing · " + s.getStatus().name().toLowerCase().replace('_', ' ')
                                + (s.getInterest() == null ? "" : " · " + s.getInterest().name().toLowerCase() + " interest"),
                        s.getPropertyId(), properties.findById(s.getPropertyId()).map(Property::getTitle).orElse(null)))
                .toList();
    }

    ShowingDtos.ShowingResponse response(Showing s) {
        Property p = properties.findById(s.getPropertyId()).orElse(null);
        Contact c = contactRepository.findById(s.getContactId()).orElse(null);
        return new ShowingDtos.ShowingResponse(s.getId(), s.getClientId(), s.getPropertyId(), p == null ? null : p.getTitle(),
                p == null ? null : p.getSuburb(), s.getContactId(), c == null ? null : c.getFullName(),
                c == null ? null : c.getPhone(), s.getNegotiatorUserId(), s.getScheduledAt(), s.getDurationMinutes(),
                s.getStatus(), s.getInterest(), s.getPriceOpinion(), s.getFeedback(), s.getCompletedAt(), s.getVersion());
    }
}
