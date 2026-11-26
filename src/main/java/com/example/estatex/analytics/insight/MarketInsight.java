package com.example.estatex.analytics.insight;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An AI interpretation of a set of observations ("AI interpretation"). Generated only when the
 * set of observations changes, and every item must cite the observations it rests on.
 */
@Entity
@Table(name = "market_insights", indexes = @Index(name = "idx_market_insight_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
public class MarketInsight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    /** OK, REFUSED or FAILED. */
    @Column(nullable = false, length = 10)
    private String status;
    @Column(length = 60)
    private String model;
    @Column(name = "prompt_version", nullable = false)
    private int promptVersion;
    @Column(name = "input_fingerprint", nullable = false, length = 64)
    private String inputFingerprint;
    @Column(length = 500)
    private String headline;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "items_json")
    private String itemsJson;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "observation_ids_json")
    private String observationIdsJson;
    @Column(length = 500)
    private String error;
    @Column(name = "input_tokens")
    private Long inputTokens;
    @Column(name = "output_tokens")
    private Long outputTokens;
    @Column(name = "job_run_id")
    private Long jobRunId;
}
