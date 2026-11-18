package com.example.estatex.showing;

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
 * An agency viewing: a negotiator showing a listing to a contact. Separate from the platform's
 * self-service ViewingRequest, which needs the viewer to have an account (agency buyers usually
 * don't).
 */
@Entity
@Table(name = "agency_viewings", indexes = {
        @Index(name = "idx_agency_viewings_property", columnList = "propertyId"),
        @Index(name = "idx_agency_viewings_org_time", columnList = "organisationId,scheduledAt"),
        @Index(name = "idx_agency_viewings_contact", columnList = "contactId"),
        @Index(name = "uq_agency_viewings_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Showing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private Long contactId;

    private Long negotiatorUserId;

    @Column(nullable = false)
    private Instant scheduledAt;

    private int durationMinutes = 30;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "status", length = 20, nullable = false)
    private String statusCode = ShowingStatus.SCHEDULED.name();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "interest", length = 10)
    private String interestCode;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "price_opinion", length = 20)
    private String priceOpinionCode;

    @Column(length = 2000)
    private String feedback;

    private Instant completedAt;
    private Instant createdAt = Instant.now();

    public ShowingStatus getStatus() {
        return ShowingStatus.valueOf(statusCode);
    }

    public void setStatus(ShowingStatus status) {
        this.statusCode = status.name();
    }

    public BuyerInterest getInterest() {
        return interestCode == null ? null : BuyerInterest.valueOf(interestCode);
    }

    public void setInterest(BuyerInterest interest) {
        this.interestCode = interest == null ? null : interest.name();
    }

    public PriceOpinion getPriceOpinion() {
        return priceOpinionCode == null ? null : PriceOpinion.valueOf(priceOpinionCode);
    }

    public void setPriceOpinion(PriceOpinion opinion) {
        this.priceOpinionCode = opinion == null ? null : opinion.name();
    }
}
