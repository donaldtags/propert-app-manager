package com.example.estatex.stay;

import com.example.estatex.property.Property;
import com.example.estatex.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stay_bookings", indexes = {
        @Index(name = "idx_stay_bookings_property_dates", columnList = "property_id, checkIn, checkOut"),
        @Index(name = "idx_stay_bookings_guest", columnList = "guest_id"),
        @Index(name = "idx_stay_bookings_host", columnList = "host_id"),
        @Index(name = "idx_stay_bookings_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class StayBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 12, nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private AppUser guest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private AppUser host;

    @Column(nullable = false)
    private LocalDate checkIn;

    /** Exclusive: the morning the guest leaves, so it can be someone else's check-in. */
    @Column(nullable = false)
    private LocalDate checkOut;

    @Column(nullable = false)
    private int guests;

    @Column(nullable = false)
    private int nights;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal nightlyRate;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal cleaningFee = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(length = 3, nullable = false)
    private String currency = "USD";

    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    @Column(name = "status", length = 20, nullable = false)
    private String statusCode = StayBookingStatus.PENDING.name();

    @Column(length = 1000)
    private String guestMessage;

    @Column(length = 1000)
    private String hostNote;

    /** When an unanswered request or an unpaid hold is released. */
    private Instant holdExpiresAt;

    private Long paymentId;

    /** "online" (gateway) or how the host says they were paid (cash, EcoCash, transfer). */
    @Column(length = 30)
    private String paidVia;

    private Instant createdAt = Instant.now();
    private Instant decidedAt;
    private Instant paidAt;
    private Instant cancelledAt;

    @Column(length = 10)
    private String cancelledBy;

    public StayBookingStatus getStatus() {
        return StayBookingStatus.valueOf(statusCode);
    }

    public void setStatus(StayBookingStatus status) {
        this.statusCode = status.name();
    }
}
