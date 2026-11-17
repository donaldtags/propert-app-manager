package com.example.primenestprop.enquiry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An enquiry captured from any channel in one tap. Designed for offline capture: the client
 * generates {@code clientId}, and {@code capturedAt} is when the negotiator recorded it on the
 * device (it may reach the server hours later).
 */
@Entity
@Table(name = "enquiries", indexes = {
        @Index(name = "idx_enquiries_org_time", columnList = "organisationId,capturedAt"),
        @Index(name = "idx_enquiries_property", columnList = "propertyId"),
        @Index(name = "idx_enquiries_contact", columnList = "contactId"),
        @Index(name = "uq_enquiries_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Enquiry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    private Long propertyId;

    /** The contact this enquiry was matched to (by phone, then email) or created as. */
    private Long contactId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "channel", length = 20, nullable = false)
    private String channelCode;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "intent", length = 20)
    private String intentCode;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "status", length = 20, nullable = false)
    private String statusCode = EnquiryStatus.NEW.name();

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 40) private String phone;
    @Column(length = 160) private String email;
    @Column(length = 2000) private String message;

    private Long budgetCents;
    @Column(length = 3) private String currency;

    private Long capturedByUserId;
    @Column(nullable = false)
    private Instant capturedAt;
    private Instant createdAt = Instant.now();

    public EnquiryChannel getChannel() {
        return EnquiryChannel.valueOf(channelCode);
    }

    public void setChannel(EnquiryChannel channel) {
        this.channelCode = channel.name();
    }

    public EnquiryIntent getIntent() {
        return intentCode == null ? null : EnquiryIntent.valueOf(intentCode);
    }

    public void setIntent(EnquiryIntent intent) {
        this.intentCode = intent == null ? null : intent.name();
    }

    public EnquiryStatus getStatus() {
        return EnquiryStatus.valueOf(statusCode);
    }

    public void setStatus(EnquiryStatus status) {
        this.statusCode = status.name();
    }
}
