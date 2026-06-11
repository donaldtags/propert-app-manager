package com.example.primenestprop.common;

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

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_actor", columnList = "actor_user_id"),
        @Index(name = "idx_audit_logs_entity", columnList = "entity_type,entity_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;
    private String action;
    @Column(name = "entity_type")
    private String entityType;
    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 4000)
    private String metadataJson;

    private Instant createdAt = Instant.now();
}
