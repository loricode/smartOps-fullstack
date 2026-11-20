-- =====================================================================
-- V003 - Archivos recibidos, ejecuciones de lote y registros rechazados
-- =====================================================================
CREATE TABLE smartops.batch_file (
    id           BIGSERIAL    PRIMARY KEY,
    file_name    VARCHAR(255) NOT NULL,
    file_kind    VARCHAR(12)  NOT NULL,
    checksum     VARCHAR(64)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_by  VARCHAR(60)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Idempotencia a nivel de archivo: el mismo contenido no se procesa dos veces.
    CONSTRAINT uq_batch_file_checksum UNIQUE (file_kind, checksum),
    CONSTRAINT ck_batch_file_kind CHECK (file_kind IN ('OPERATIONS','STATEMENT'))
);

ALTER TABLE smartops.financial_transaction
    ADD CONSTRAINT fk_ft_batch_file FOREIGN KEY (batch_file_id) REFERENCES smartops.batch_file (id);

CREATE TABLE smartops.batch_execution (
    id               BIGSERIAL     PRIMARY KEY,
    batch_file_id    BIGINT        NOT NULL REFERENCES smartops.batch_file (id),
    job_name         VARCHAR(60)   NOT NULL,
    job_execution_id BIGINT,
    status           VARCHAR(12)   NOT NULL,
    read_count       INT           NOT NULL DEFAULT 0,
    accepted_count   INT           NOT NULL DEFAULT 0,
    rejected_count   INT           NOT NULL DEFAULT 0,
    processed_count  INT           NOT NULL DEFAULT 0,
    started_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    duration_ms      BIGINT,
    exit_message     VARCHAR(1000),
    started_by       VARCHAR(60)   NOT NULL,
    CONSTRAINT ck_batch_execution_status CHECK (status IN ('STARTED','COMPLETED','FAILED'))
);
CREATE INDEX ix_batch_execution_file ON smartops.batch_execution (batch_file_id);

CREATE TABLE smartops.rejected_record (
    id            BIGSERIAL    PRIMARY KEY,
    batch_file_id BIGINT       NOT NULL REFERENCES smartops.batch_file (id),
    line_number   INT          NOT NULL,
    reference     VARCHAR(64),
    reason_code   VARCHAR(30)  NOT NULL,
    reason_detail VARCHAR(500) NOT NULL,
    raw_line      TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_rejected_record_file ON smartops.rejected_record (batch_file_id, line_number);
