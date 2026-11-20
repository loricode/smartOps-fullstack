package com.smartops.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartops.application.port.out.AuditTrail;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Escribe en smartops.audit_log. Se une a la transacción en curso: si la operación
 * se revierte, su rastro de auditoría también (no se audita lo que no ocurrió).
 */
@Component
class JdbcAuditTrail implements AuditTrail {

    public static final String CORRELATION_ID = "correlationId";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    JdbcAuditTrail(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditEntry entry) {
        jdbc.update("""
                INSERT INTO smartops.audit_log (entity_type, entity_id, action, actor, correlation_id, details)
                VALUES (?, ?, ?, ?, ?, ?)
                """, entry.entityType(), entry.entityId(), entry.action(), entry.actor(),
                MDC.get(CORRELATION_ID), toJson(entry));
    }

    private String toJson(AuditEntry entry) {
        if (entry.details() == null || entry.details().isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(entry.details());
        } catch (JsonProcessingException e) {
            return "{\"serializationError\":\"" + e.getOriginalMessage() + "\"}";
        }
    }
}
