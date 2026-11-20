-- =====================================================================
-- V005 - Auditoría (append-only)
-- =====================================================================
CREATE TABLE smartops.audit_log (
    id             BIGSERIAL   PRIMARY KEY,
    entity_type    VARCHAR(40) NOT NULL,
    entity_id      VARCHAR(64) NOT NULL,
    action         VARCHAR(40) NOT NULL,
    actor          VARCHAR(60) NOT NULL,
    correlation_id VARCHAR(64),
    details        TEXT,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity      ON smartops.audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_occurred_at ON smartops.audit_log (occurred_at DESC);

-- El log de auditoría no admite modificaciones ni borrados.
CREATE OR REPLACE FUNCTION smartops.fn_audit_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log es de solo insercion (operacion % no permitida)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON smartops.audit_log
    FOR EACH ROW EXECUTE FUNCTION smartops.fn_audit_log_immutable();
