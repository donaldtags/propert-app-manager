package com.example.primenestprop.property;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Every active listing costs a flat monthly fee (see PropertyBillingService), billed the same
 * way subscription plans are: an instant simulated charge on creation, renewed by a daily sweep. */
@Entity
@Table(name = "property_billings")
@Getter
@Setter
@NoArgsConstructor
public class PropertyBilling {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private Property property;

    @Enumerated(EnumType.STRING)
    private PropertyBillingStatus status = PropertyBillingStatus.ACTIVE;

    private Instant currentPeriodEnd;
    private Instant pastDueSince;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}