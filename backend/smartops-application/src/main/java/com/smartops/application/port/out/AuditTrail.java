package com.smartops.application.port.out;

import java.util.Map;

/** Registro de auditoría append-only. */
public interface AuditTrail {

    void record(AuditEntry entry);

    record AuditEntry(String entityType, String entityId, String action, String actor, Map<String, Object> details) {

        public static AuditEntry of(String entityType, Object entityId, String action, String actor,
                                    Map<String, Object> details) {
            return new AuditEntry(entityType, String.valueOf(entityId), action, actor, details);
        }
    }
}
