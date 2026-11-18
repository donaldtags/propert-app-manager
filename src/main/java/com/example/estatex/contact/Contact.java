package com.example.estatex.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person the agency deals with: owner, buyer, tenant, landowner, investor or referral partner
 * (one person can be several). Matched on the normalised phone number, so an enquiry from a
 * number the agency already knows lands on the same contact.
 */
@Entity
@Table(name = "contacts", indexes = {
        @Index(name = "uq_contacts_org_phone", columnList = "organisationId,phoneNormalised", unique = true),
        @Index(name = "idx_contacts_org_name", columnList = "organisationId,fullName"),
        @Index(name = "uq_contacts_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Contact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false, length = 160)
    private String fullName;

    /** As typed, for display. */
    @Column(length = 40)
    private String phone;

    /** E.164, the matching key. Unique per organisation when present. */
    @Column(length = 20)
    private String phoneNormalised;

    @Column(length = 160)
    private String email;

    @Convert(converter = ContactType.SetConverter.class)
    @Column(length = 200)
    private Set<ContactType> types = EnumSet.noneOf(ContactType.class);

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "source", length = 20)
    private String sourceCode;

    /** Comma-separated free tags ("diaspora,uk,cash buyer"). */
    @Column(length = 500)
    private String tags;

    @Column(length = 4000)
    private String notes;

    private Long referredByContactId;
    private Long linkedUserId;

    // Buyer / tenant qualification
    private Long budgetCents;
    @Column(length = 3) private String budgetCurrency;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "timeline", length = 20)
    private String timelineCode;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "financing", length = 20)
    private String financingCode;
    @Column(length = 300) private String preferredAreas;
    private Integer minBedrooms;
    /** Null = unknown; false = someone else decides (see decisionMakerNote). */
    private Boolean decisionMaker;
    @Column(length = 300) private String decisionMakerNote;
    private Instant qualifiedAt;

    private Long createdByUserId;
    private Instant createdAt = Instant.now();
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public ContactSource getSource() {
        return sourceCode == null ? null : ContactSource.valueOf(sourceCode);
    }

    public void setSource(ContactSource source) {
        this.sourceCode = source == null ? null : source.name();
    }

    public BuyingTimeline getTimeline() {
        return timelineCode == null ? null : BuyingTimeline.valueOf(timelineCode);
    }

    public void setTimeline(BuyingTimeline timeline) {
        this.timelineCode = timeline == null ? null : timeline.name();
    }

    public Financing getFinancing() {
        return financingCode == null ? null : Financing.valueOf(financingCode);
    }

    public void setFinancing(Financing financing) {
        this.financingCode = financing == null ? null : financing.name();
    }

    /** All five qualification answers present. */
    public boolean isQualified() {
        return budgetCents != null && timelineCode != null && financingCode != null
                && preferredAreas != null && !preferredAreas.isBlank() && decisionMaker != null;
    }
}
