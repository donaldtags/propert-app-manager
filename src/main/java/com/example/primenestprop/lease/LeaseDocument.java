package com.example.primenestprop.lease;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lease_documents", indexes = {
        @Index(name = "idx_lease_documents_lease", columnList = "lease_id"),
        @Index(name = "idx_lease_documents_user", columnList = "user_id"),
        @Index(name = "idx_lease_documents_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class LeaseDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Lease lease;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseDocumentType documentType;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false, length = 1000)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseDocumentStatus status = LeaseDocumentStatus.SUBMITTED;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    private Instant reviewedAt;
    private Long reviewedBy;

    @Column(length = 1000)
    private String reviewNote;
}
