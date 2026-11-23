package com.example.estatex.kyc.check;

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
 * The result of an automatic identity check by an outside provider: a national-register lookup of
 * an ID number (Dojah) or an international document + selfie check (Didit). Shown to the person
 * reviewing the ID; the decision itself stays with them.
 */
@Entity
@Table(name = "identity_checks", indexes = {
        @Index(name = "idx_identity_checks_user", columnList = "user_id"),
        @Index(name = "idx_identity_checks_reference", columnList = "provider_reference")
})
@Getter
@Setter
@NoArgsConstructor
public class IdentityCheck {
    /** REGISTRY = ID number looked up in a national register; DOCUMENT = photo ID + selfie checked. */
    public static final String REGISTRY = "REGISTRY";
    public static final String DOCUMENT = "DOCUMENT";

    /** MATCH, PARTIAL, NO_MATCH, NOT_FOUND, DECEASED, PENDING, ERROR, APPROVED, DECLINED, IN_REVIEW. */
    public static final String MATCH = "MATCH";
    public static final String PARTIAL = "PARTIAL";
    public static final String NO_MATCH = "NO_MATCH";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DECEASED = "DECEASED";
    public static final String PENDING = "PENDING";
    public static final String ERROR = "ERROR";
    public static final String APPROVED = "APPROVED";
    public static final String DECLINED = "DECLINED";
    public static final String IN_REVIEW = "IN_REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;
    /** The ID submission this check belongs to, when there is one. */
    private Long submissionId;

    @Column(nullable = false, length = 20)
    private String kind;
    @Column(nullable = false, length = 30)
    private String provider;
    /** Provider's own id (e.g. Didit session id), used to match webhooks. */
    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Column(nullable = false, length = 20)
    private String status;
    /** One line a reviewer can read, e.g. "Register: JOHN MUSA, born 1990-05-17". */
    @Column(length = 1000)
    private String summary;
    /** Names/dates the provider returned (no images), for the reviewer. */
    @Column(length = 4000)
    private String detailsJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant completedAt;
}
