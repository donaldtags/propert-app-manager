package com.example.estatex.stay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Short-stay rules for one SHORT_STAY listing. The nightly rate is the listing's price. */
@Entity
@Table(name = "stay_settings")
@Getter
@Setter
@NoArgsConstructor
public class StaySettings {
    public static final int DEFAULT_MAX_GUESTS = 2;

    @Id
    private Long propertyId;

    @Column(nullable = false)
    private int maxGuests = DEFAULT_MAX_GUESTS;

    @Column(nullable = false)
    private int minNights = 1;

    @Column(nullable = false)
    private int maxNights = 90;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal cleaningFee = BigDecimal.ZERO;

    @Column(length = 5, nullable = false)
    private String checkInTime = "14:00";

    @Column(length = 5, nullable = false)
    private String checkOutTime = "10:00";

    /** Instant book skips the host's approval: the booking goes straight to payment. */
    @Column(nullable = false)
    private boolean instantBook;

    @Column(length = 2000)
    private String houseRules;

    private Instant updatedAt = Instant.now();

    public static StaySettings defaultsFor(Long propertyId) {
        StaySettings s = new StaySettings();
        s.setPropertyId(propertyId);
        return s;
    }
}
