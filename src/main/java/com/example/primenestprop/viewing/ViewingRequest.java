package com.example.primenestprop.viewing;

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
@Table(name = "viewing_requests", indexes = {
        @Index(name = "idx_viewing_requests_property", columnList = "property_id"),
        @Index(name = "idx_viewing_requests_requester", columnList = "requester_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ViewingRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViewingMode mode = ViewingMode.IN_PERSON;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViewingStatus status = ViewingStatus.REQUESTED;

    private LocalDate preferredDate;
    private String preferredTime;
    private String notes;
    private String videoCallLink;

    @Column(unique = true, length = 12)
    private String checkInCode;

    private Instant createdAt = Instant.now();
    private Instant confirmedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    private Integer feedbackRating;
    @Column(length = 2000)
    private String feedbackComment;
}
