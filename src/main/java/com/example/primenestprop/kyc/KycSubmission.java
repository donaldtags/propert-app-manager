package com.example.primenestprop.kyc;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kyc_submissions", indexes = {
        @Index(name = "idx_kyc_submissions_user", columnList = "user_id"),
        @Index(name = "idx_kyc_submissions_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class KycSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Column(nullable = false)
    private String legalFullName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String nationalIdNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdDocumentType idDocumentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus status = KycStatus.PENDING;

    @Column(nullable = false)
    private Instant submittedAt = Instant.now();

    private Instant reviewedAt;
    private Long reviewedBy;

    @Column(length = 1000)
    private String reviewNote;

    @OneToMany(mappedBy = "submission")
    private List<KycDocument> documents = new ArrayList<>();
}
