package com.example.primenestprop.vendor;

import com.example.primenestprop.property.Property;
import com.example.primenestprop.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "service_bookings", indexes = {
        @Index(name = "idx_service_bookings_vendor", columnList = "vendor_id"),
        @Index(name = "idx_service_bookings_requester", columnList = "requester_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ServiceBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;

    private LocalDate preferredDate;
    @Column(length = 2000)
    private String notes;

    private Instant createdAt = Instant.now();
    private Instant completedAt;
    private Instant cancelledAt;

    private Integer feedbackRating;
    @Column(length = 2000)
    private String feedbackComment;
}
