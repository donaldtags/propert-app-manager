package com.example.estatex.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An estate agent's Estate Agents Council (EAC) registration, with the certificate they uploaded
 * and EstateX's review decision. One per agent; resubmitting replaces it and starts a new review.
 */
@Entity
@Table(name = "agent_licences", indexes = {
        @Index(name = "idx_agent_licences_user", columnList = "user_id", unique = true),
        @Index(name = "idx_agent_licences_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class AgentLicence {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String registrationNumber;

    /** PENDING, APPROVED or REJECTED (string-coded: no CHECK constraint to widen later). */
    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(nullable = false, length = 500)
    private String documentStorageKey;
    @Column(nullable = false, length = 120)
    private String documentContentType;
    @Column(length = 255)
    private String documentFileName;
    /** SHA-256 of the certificate as uploaded (tamper evidence). */
    @Column(nullable = false, length = 64)
    private String documentSha256;

    @Column(nullable = false)
    private Instant submittedAt = Instant.now();
    private Instant reviewedAt;
    private Long reviewedByUserId;
    @Column(length = 1000)
    private String reviewNote;
}
