package com.example.primenestprop.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One owner/property opportunity moving from first contact to completed sale (or lost). */
@Entity
@Table(name = "pipeline_cards", indexes = {
        @Index(name = "idx_pipeline_org_stage", columnList = "organisationId,stage"),
        @Index(name = "idx_pipeline_owner", columnList = "ownerContactId"),
        @Index(name = "uq_pipeline_client_id", columnList = "clientId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class PipelineCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 40)
    private String clientId;

    @Column(nullable = false)
    private Long organisationId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Long ownerContactId;

    private Long propertyId;
    private Long negotiatorUserId;

    private Long estimatedValueCents;
    @Column(length = 3) private String currency = "USD";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "stage", length = 20, nullable = false)
    private String stageCode = PipelineStage.PROSPECT.name();

    @Column(length = 300)
    private String lostReason;

    private LocalDate nextActionOn;
    @Column(length = 300) private String nextAction;
    @Column(length = 2000) private String notes;

    private Instant stageChangedAt = Instant.now();
    private Long createdByUserId;
    private Instant createdAt = Instant.now();

    public PipelineStage getStage() {
        return PipelineStage.valueOf(stageCode);
    }

    void setStage(PipelineStage stage) {
        this.stageCode = stage.name();
    }
}
