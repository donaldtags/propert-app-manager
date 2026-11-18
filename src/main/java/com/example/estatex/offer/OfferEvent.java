package com.example.estatex.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only history of an offer: what happened, for how much, by whom. */
@Entity
@Table(name = "offer_events", indexes = @Index(name = "idx_offer_events_offer", columnList = "offerId"))
@Getter
@Setter
@NoArgsConstructor
public class OfferEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long offerId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "event_type", length = 20, nullable = false)
    private String typeCode;

    private Long amountCents;

    @Column(length = 1000)
    private String note;

    private Long actorUserId;
    private Instant at = Instant.now();

    public OfferEventType getType() {
        return OfferEventType.valueOf(typeCode);
    }

    public void setType(OfferEventType type) {
        this.typeCode = type.name();
    }
}
