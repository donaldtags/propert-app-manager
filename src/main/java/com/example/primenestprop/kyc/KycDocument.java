package com.example.primenestprop.kyc;

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
@Table(name = "kyc_documents", indexes = {
        @Index(name = "idx_kyc_documents_submission", columnList = "submission_id")
})
@Getter
@Setter
@NoArgsConstructor
public class KycDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private KycSubmission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycDocumentType documentType;

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

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();
}
