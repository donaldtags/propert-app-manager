package com.example.estatex.common;

import com.example.estatex.user.AppUser;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * One way to write audit entries: approvals, status changes and money movements. Writes join the
 * caller's transaction, so an audit row is only ever committed together with the change it
 * describes, never for a change that rolled back.
 */
@Service
public class AuditService {
    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogs, ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    public static Entry entry(String action, String entityType, Long entityId) {
        return new Entry(action, entityType, entityId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(AppUser actor, Entry entry) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actor == null ? null : actor.getId());
        log.setOrganisationId(entry.organisationId);
        log.setAction(entry.action);
        log.setEntityType(entry.entityType);
        log.setEntityId(entry.entityId);
        log.setFromState(entry.fromState);
        log.setToState(entry.toState);
        log.setAmountCents(entry.amountCents);
        log.setCurrency(entry.currency);
        log.setMetadataJson(toJson(entry.metadata));
        return auditLogs.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> forOrganisation(Long organisationId, int limit) {
        return auditLogs.findByOrganisationIdOrderByCreatedAtDescIdDesc(organisationId,
                PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            return "{}";
        }
    }

    /** Fluent description of one audit entry. */
    public static final class Entry {
        private final String action;
        private final String entityType;
        private final Long entityId;
        private Long organisationId;
        private String fromState;
        private String toState;
        private Long amountCents;
        private String currency;
        private Map<String, ?> metadata;

        private Entry(String action, String entityType, Long entityId) {
            this.action = action;
            this.entityType = entityType;
            this.entityId = entityId;
        }

        public Entry organisation(Long id) {
            this.organisationId = id;
            return this;
        }

        public Entry transition(Object from, Object to) {
            this.fromState = from == null ? null : String.valueOf(from);
            this.toState = to == null ? null : String.valueOf(to);
            return this;
        }

        public Entry money(long cents, String currency) {
            this.amountCents = cents;
            this.currency = currency;
            return this;
        }

        public Entry metadata(Map<String, ?> metadata) {
            this.metadata = metadata;
            return this;
        }
    }
}
