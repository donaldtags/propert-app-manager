package com.example.estatex.stay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dates the host has closed (own use, maintenance, booked elsewhere). End is exclusive. */
@Entity
@Table(name = "stay_blocks", indexes = @Index(name = "idx_stay_blocks_property", columnList = "propertyId, startDate, endDate"))
@Getter
@Setter
@NoArgsConstructor
public class StayBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(length = 200)
    private String note;

    private Instant createdAt = Instant.now();
}
