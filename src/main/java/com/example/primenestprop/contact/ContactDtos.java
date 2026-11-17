package com.example.primenestprop.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class ContactDtos {
    private ContactDtos() {
    }

    public record CreateContactRequest(
            @Size(max = 40) String clientId,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 40) String phone,
            @Email @Size(max = 160) String email,
            Set<ContactType> types,
            ContactSource source,
            List<@Size(max = 40) String> tags,
            @Size(max = 4000) String notes,
            Long referredByContactId
    ) {
    }

    public record UpdateContactRequest(
            @Size(max = 160) String fullName,
            @Size(max = 40) String phone,
            @Email @Size(max = 160) String email,
            Set<ContactType> types,
            ContactSource source,
            List<@Size(max = 40) String> tags,
            @Size(max = 4000) String notes,
            /** Optimistic lock: the version the editor loaded. */
            Long version
    ) {
    }

    public record QualificationRequest(
            @Positive Long budgetCents,
            @Pattern(regexp = "^(USD|ZWG)$") String budgetCurrency,
            BuyingTimeline timeline,
            Financing financing,
            @Size(max = 300) String preferredAreas,
            @Min(0) @Max(20) Integer minBedrooms,
            Boolean decisionMaker,
            @Size(max = 300) String decisionMakerNote
    ) {
    }

    public record ContactResponse(
            Long id, String clientId, String fullName, String phone, String phoneNormalised, String whatsapp,
            String email, Set<ContactType> types, ContactSource source, List<String> tags, String notes,
            Long referredByContactId, Long budgetCents, String budgetCurrency, BuyingTimeline timeline,
            Financing financing, String preferredAreas, Integer minBedrooms, Boolean decisionMaker,
            String decisionMakerNote, boolean qualified, Instant qualifiedAt, Instant createdAt, Instant updatedAt,
            Long version
    ) {
        public static ContactResponse from(Contact c) {
            List<String> tags = c.getTags() == null || c.getTags().isBlank() ? List.of()
                    : Arrays.stream(c.getTags().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
            return new ContactResponse(c.getId(), c.getClientId(), c.getFullName(), c.getPhone(),
                    c.getPhoneNormalised(), c.getPhoneNormalised() == null ? null : c.getPhoneNormalised().substring(1),
                    c.getEmail(), c.getTypes(), c.getSource(), tags, c.getNotes(), c.getReferredByContactId(),
                    c.getBudgetCents(), c.getBudgetCurrency(), c.getTimeline(), c.getFinancing(), c.getPreferredAreas(),
                    c.getMinBedrooms(), c.getDecisionMaker(), c.getDecisionMakerNote(), c.isQualified(),
                    c.getQualifiedAt(), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
        }
    }

    /** One line of a contact's history (enquiry, viewing, offer, deal). */
    public record ActivityItem(String kind, Long id, Instant at, String summary, Long propertyId, String propertyTitle) {
    }

    public record ContactDetail(ContactResponse contact, List<ActivityItem> activity) {
    }
}
