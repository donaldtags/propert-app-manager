package com.example.primenestprop.contact;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.PhoneNumbers;
import com.example.primenestprop.organisation.OrgAccessService;
import com.example.primenestprop.organisation.OrgAccessService.OrgContext;
import com.example.primenestprop.organisation.OrgPermission;
import com.example.primenestprop.user.AppUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {
    private final OrgAccessService access;
    private final ContactRepository contacts;
    /** Lazy: activity sources (e.g. EnquiryService) themselves depend on this service. */
    private final ObjectProvider<ContactActivitySource> activitySources;

    public ContactService(OrgAccessService access, ContactRepository contacts, ObjectProvider<ContactActivitySource> activitySources) {
        this.access = access;
        this.contacts = contacts;
        this.activitySources = activitySources;
    }

    @Transactional(readOnly = true)
    public List<Contact> search(String slug, String q, ContactType type, int limit, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.CONTACTS_MANAGE);
        String term = q == null || q.isBlank() ? null : q.trim();
        String phoneTerm = term == null ? null : PhoneNumbers.normalise(term);
        List<Contact> rows = contacts.search(ctx.organisation().getId(),
                phoneTerm != null ? phoneTerm.substring(1) : term, PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
        return type == null ? rows : rows.stream().filter(c -> c.getTypes().contains(type)).toList();
    }

    @Transactional(readOnly = true)
    public ContactDtos.ContactDetail detail(String slug, Long id, AppUser user) {
        Contact c = load(slug, id, user);
        List<ContactDtos.ActivityItem> activity = new ArrayList<>();
        activitySources.orderedStream().forEach(s -> activity.addAll(s.activityFor(c.getOrganisationId(), c.getId())));
        activity.sort(Comparator.comparing(ContactDtos.ActivityItem::at, Comparator.nullsLast(Comparator.reverseOrder())));
        return new ContactDtos.ContactDetail(ContactDtos.ContactResponse.from(c), activity);
    }

    @Transactional
    public Contact create(String slug, ContactDtos.CreateContactRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.CONTACTS_MANAGE);
        Long org = ctx.organisation().getId();
        if (r.clientId() != null) {
            Contact existing = contacts.findByClientId(r.clientId()).orElse(null);
            if (existing != null) {
                if (!existing.getOrganisationId().equals(org)) {
                    throw new ApiException(HttpStatus.CONFLICT, "clientId already used");
                }
                return existing;
            }
        }
        String normalised = PhoneNumbers.normalise(r.phone());
        if (normalised != null) {
            contacts.findByOrganisationIdAndPhoneNormalised(org, normalised).ifPresent(existing -> {
                throw new ApiException(HttpStatus.CONFLICT,
                        "That number is already saved as " + existing.getFullName() + " (contact #" + existing.getId() + ").");
            });
        }
        Contact c = new Contact();
        c.setClientId(r.clientId());
        c.setOrganisationId(org);
        c.setFullName(r.fullName().trim());
        c.setPhone(blankToNull(r.phone()));
        c.setPhoneNormalised(normalised);
        c.setEmail(blankToNull(r.email()));
        c.setTypes(r.types() == null || r.types().isEmpty() ? EnumSet.noneOf(ContactType.class) : EnumSet.copyOf(r.types()));
        c.setSource(r.source());
        c.setTags(joinTags(r.tags()));
        c.setNotes(blankToNull(r.notes()));
        c.setReferredByContactId(referrerIn(org, r.referredByContactId()));
        c.setCreatedByUserId(user.getId());
        return contacts.save(c);
    }

    @Transactional
    public Contact update(String slug, Long id, ContactDtos.UpdateContactRequest r, AppUser user) {
        Contact c = load(slug, id, user);
        if (r.version() != null && !Objects.equals(r.version(), c.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "Someone else changed this contact. Reload and try again.");
        }
        if (r.fullName() != null && !r.fullName().isBlank()) c.setFullName(r.fullName().trim());
        if (r.phone() != null) {
            String normalised = PhoneNumbers.normalise(r.phone());
            if (normalised != null && !normalised.equals(c.getPhoneNormalised())) {
                contacts.findByOrganisationIdAndPhoneNormalised(c.getOrganisationId(), normalised)
                        .filter(other -> !other.getId().equals(c.getId()))
                        .ifPresent(other -> {
                            throw new ApiException(HttpStatus.CONFLICT, "That number belongs to " + other.getFullName() + ".");
                        });
            }
            c.setPhone(blankToNull(r.phone()));
            c.setPhoneNormalised(normalised);
        }
        if (r.email() != null) c.setEmail(blankToNull(r.email()));
        if (r.types() != null) c.setTypes(r.types().isEmpty() ? EnumSet.noneOf(ContactType.class) : EnumSet.copyOf(r.types()));
        if (r.source() != null) c.setSource(r.source());
        if (r.tags() != null) c.setTags(joinTags(r.tags()));
        if (r.notes() != null) c.setNotes(blankToNull(r.notes()));
        return c;
    }

    @Transactional
    public Contact qualify(String slug, Long id, ContactDtos.QualificationRequest r, AppUser user) {
        Contact c = load(slug, id, user);
        if (r.budgetCents() != null) {
            c.setBudgetCents(r.budgetCents());
            c.setBudgetCurrency(r.budgetCurrency() == null ? "USD" : r.budgetCurrency());
        }
        if (r.timeline() != null) c.setTimeline(r.timeline());
        if (r.financing() != null) c.setFinancing(r.financing());
        if (r.preferredAreas() != null) c.setPreferredAreas(blankToNull(r.preferredAreas()));
        if (r.minBedrooms() != null) c.setMinBedrooms(r.minBedrooms());
        if (r.decisionMaker() != null) c.setDecisionMaker(r.decisionMaker());
        if (r.decisionMakerNote() != null) c.setDecisionMakerNote(blankToNull(r.decisionMakerNote()));
        if (c.isQualified() && c.getQualifiedAt() == null) {
            c.setQualifiedAt(Instant.now());
        }
        return c;
    }

    /**
     * Used by enquiry capture: the contact with this phone (or, failing that, email) in the
     * organisation, created if there isn't one. Adds the type the enquiry implies.
     */
    @Transactional
    public Contact findOrCreate(Long organisationId, String name, String phone, String email, ContactSource source,
                                ContactType type, Long userId) {
        String normalised = PhoneNumbers.normalise(phone);
        Contact c = normalised == null ? null
                : contacts.findByOrganisationIdAndPhoneNormalised(organisationId, normalised).orElse(null);
        if (c == null && email != null && !email.isBlank()) {
            c = contacts.findFirstByOrganisationIdAndEmailIgnoreCase(organisationId, email.trim()).orElse(null);
        }
        if (c == null) {
            c = new Contact();
            c.setOrganisationId(organisationId);
            c.setFullName(name.trim());
            c.setPhone(blankToNull(phone));
            c.setPhoneNormalised(normalised);
            c.setEmail(blankToNull(email));
            c.setSource(source);
            c.setCreatedByUserId(userId);
        } else {
            if (c.getPhoneNormalised() == null && normalised != null) {
                c.setPhone(phone);
                c.setPhoneNormalised(normalised);
            }
            if (c.getEmail() == null && email != null && !email.isBlank()) {
                c.setEmail(email.trim());
            }
        }
        if (type != null) {
            Set<ContactType> types = c.getTypes().isEmpty() ? EnumSet.noneOf(ContactType.class) : EnumSet.copyOf(c.getTypes());
            types.add(type);
            c.setTypes(types);
        }
        return contacts.save(c);
    }

    /** For other modules: a contact that must belong to the organisation. */
    @Transactional(readOnly = true)
    public Contact requireInOrganisation(Long organisationId, Long contactId) {
        return contacts.findById(contactId)
                .filter(c -> c.getOrganisationId().equals(organisationId))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Contact not found in this organisation"));
    }

    private Contact load(String slug, Long id, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.CONTACTS_MANAGE);
        return contacts.findById(id)
                .filter(c -> c.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Contact not found"));
    }

    private Long referrerIn(Long org, Long referrerId) {
        if (referrerId == null) {
            return null;
        }
        return requireInOrganisation(org, referrerId).getId();
    }

    private static String joinTags(Collection<String> tags) {
        if (tags == null) {
            return null;
        }
        LinkedHashSet<String> clean = tags.stream().filter(Objects::nonNull).map(t -> t.trim().toLowerCase())
                .filter(t -> !t.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
