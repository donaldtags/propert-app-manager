package com.example.estatex.enquiry;

import com.example.estatex.common.ApiException;
import com.example.estatex.contact.ContactActivitySource;
import com.example.estatex.contact.ContactDtos;
import com.example.estatex.contact.ContactService;
import com.example.estatex.contact.ContactSource;
import com.example.estatex.contact.ContactType;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyInquirySubmittedEvent;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.user.AppUser;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnquiryService implements ContactActivitySource {
    /** Device clocks drift; anything further ahead than this is treated as a bad clock, not trusted. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(10);

    private final OrgAccessService access;
    private final EnquiryRepository enquiries;
    private final PropertyRepository properties;
    private final ContactService contacts;

    public EnquiryService(OrgAccessService access, EnquiryRepository enquiries, PropertyRepository properties,
                          ContactService contacts) {
        this.access = access;
        this.enquiries = enquiries;
        this.properties = properties;
        this.contacts = contacts;
    }

    /** Append-only capture, idempotent on clientId so an offline replay never duplicates. */
    @Transactional
    public Enquiry capture(String slug, EnquiryDtos.CaptureEnquiryRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ENQUIRY_CAPTURE);
        Long org = ctx.organisation().getId();
        if (r.clientId() != null) {
            Enquiry existing = enquiries.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                if (!existing.getOrganisationId().equals(org)) {
                    throw new ApiException(HttpStatus.CONFLICT, "clientId already used");
                }
                return existing;
            }
        }
        if (r.propertyId() != null && properties.findById(r.propertyId())
                .filter(p -> org.equals(p.getOrganisationId())).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That listing isn't one of " + ctx.organisation().getName() + "'s");
        }
        if ((r.phone() == null || r.phone().isBlank()) && (r.email() == null || r.email().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add a phone number or email so the enquirer can be contacted.");
        }
        Instant now = Instant.now();
        Instant captured = r.capturedAt() == null || r.capturedAt().isAfter(now.plus(CLOCK_SKEW)) ? now : r.capturedAt();

        Enquiry e = new Enquiry();
        e.setClientId(r.clientId());
        e.setOrganisationId(org);
        e.setPropertyId(r.propertyId());
        e.setChannel(r.channel());
        e.setIntent(r.intent());
        e.setName(r.name().trim());
        e.setPhone(blankToNull(r.phone()));
        e.setEmail(blankToNull(r.email()));
        e.setMessage(blankToNull(r.message()));
        e.setBudgetCents(r.budgetCents());
        e.setCurrency(r.budgetCents() == null ? null : r.currency() == null ? ctx.organisation().getDefaultCurrency() : r.currency());
        e.setCapturedByUserId(user.getId());
        e.setCapturedAt(captured);
        e.setContactId(contacts.findOrCreate(org, e.getName(), e.getPhone(), e.getEmail(), sourceFor(r.channel()),
                typeFor(r.intent()), user.getId()).getId());
        return enquiries.save(e);
    }

    @Transactional(readOnly = true)
    public List<EnquiryDtos.EnquiryResponse> list(String slug, int limit, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ENQUIRY_CAPTURE);
        List<Enquiry> rows = enquiries.findByOrganisationIdOrderByCapturedAtDescIdDesc(ctx.organisation().getId(),
                PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
        Map<Long, String> titles = new HashMap<>();
        properties.findAllById(rows.stream().map(Enquiry::getPropertyId).filter(Objects::nonNull).distinct().toList())
                .forEach(p -> titles.put(p.getId(), p.getTitle()));
        return rows.stream().map(e -> EnquiryDtos.EnquiryResponse.from(e, titles.get(e.getPropertyId()))).toList();
    }

    @Transactional
    public Enquiry updateStatus(String slug, Long id, EnquiryStatus status, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ENQUIRY_CAPTURE);
        Enquiry e = enquiries.findById(id)
                .filter(x -> x.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Enquiry not found"));
        e.setStatus(status);
        return e;
    }

    /** Website enquiries on an agency listing land in the same inbox as everything else. */
    @EventListener
    public void onWebsiteInquiry(PropertyInquirySubmittedEvent event) {
        Property property = properties.findById(event.propertyId()).orElse(null);
        if (property == null) {
            return;
        }
        Enquiry e = new Enquiry();
        e.setOrganisationId(event.organisationId());
        e.setPropertyId(event.propertyId());
        e.setChannel(EnquiryChannel.WEBSITE);
        e.setIntent(switch (property.getListingType()) {
            case SALE -> EnquiryIntent.BUY;
            case RENT, SHORT_STAY -> EnquiryIntent.RENT;
        });
        e.setName(event.name());
        e.setEmail(event.email());
        e.setPhone(event.phone());
        e.setMessage(event.message());
        e.setCapturedAt(Instant.now());
        e.setContactId(contacts.findOrCreate(event.organisationId(), e.getName(), e.getPhone(), e.getEmail(),
                ContactSource.WEBSITE, typeFor(e.getIntent()), null).getId());
        enquiries.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactDtos.ActivityItem> activityFor(Long organisationId, Long contactId) {
        List<Enquiry> rows = enquiries.findByOrganisationIdAndContactIdOrderByCapturedAtDesc(organisationId, contactId);
        Map<Long, String> titles = new HashMap<>();
        properties.findAllById(rows.stream().map(Enquiry::getPropertyId).filter(Objects::nonNull).distinct().toList())
                .forEach(p -> titles.put(p.getId(), p.getTitle()));
        return rows.stream().map(e -> new ContactDtos.ActivityItem("ENQUIRY", e.getId(), e.getCapturedAt(),
                "Enquiry via " + e.getChannel().name().toLowerCase().replace('_', '-')
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()),
                e.getPropertyId(), titles.get(e.getPropertyId()))).toList();
    }

    static ContactSource sourceFor(EnquiryChannel channel) {
        return switch (channel) {
            case WHATSAPP -> ContactSource.WHATSAPP;
            case WALK_IN -> ContactSource.WALK_IN;
            case INSTAGRAM -> ContactSource.INSTAGRAM;
            case FACEBOOK -> ContactSource.FACEBOOK;
            case PORTAL -> ContactSource.PORTAL;
            case WEBSITE -> ContactSource.WEBSITE;
            case REFERRAL -> ContactSource.REFERRAL;
            case SIGNBOARD -> ContactSource.SIGNBOARD;
            case PHONE, EMAIL, OTHER -> ContactSource.OTHER;
        };
    }

    static ContactType typeFor(EnquiryIntent intent) {
        if (intent == null) {
            return null;
        }
        return switch (intent) {
            case BUY -> ContactType.BUYER;
            case RENT -> ContactType.TENANT;
            case SELL, LET -> ContactType.OWNER;
            case INVEST -> ContactType.INVESTOR;
            case OTHER -> null;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
