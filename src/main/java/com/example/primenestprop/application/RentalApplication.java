package com.example.primenestprop.application;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rental_applications", indexes = {
        @Index(name = "idx_applications_applicant", columnList = "applicant_id"),
        @Index(name = "idx_applications_property", columnList = "property_id")
})
@Getter
@Setter
@NoArgsConstructor
public class RentalApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Property property;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser applicant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    private LocalDate desiredMoveInDate;
    private BigDecimal monthlyIncome;

    @Column(length = 2000)
    private String message;

    @Column(length = 2000)
    private String reviewNote;

    private Instant createdAt = Instant.now();
    private Instant submittedAt;
    private Instant reviewedAt;
}
