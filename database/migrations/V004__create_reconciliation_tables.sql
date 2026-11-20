-- =====================================================================
-- V004 - Conciliación bancaria
-- statement_record es una tabla de staging con el extracto externo.
-- =====================================================================
CREATE TABLE smartops.statement_record (
    id            BIGSERIAL     PRIMARY KEY,
    batch_file_id BIGINT        NOT NULL REFERENCES smartops.batch_file (id),
    line_number   INT           NOT NULL,
    reference     VARCHAR(64)   NOT NULL,
    amount        NUMERIC(19,2) NOT NULL,
    currency      VARCHAR(3)    NOT NULL,
    value_date    DATE          NOT NULL,
    CONSTRAINT uq_statement_record UNIQUE (batch_file_id, reference)
);

CREATE TABLE smartops.reconciliation (
    id                     BIGSERIAL   PRIMARY KEY,
    statement_file_id      BIGINT      NOT NULL REFERENCES smartops.batch_file (id),
    value_date_from        DATE,
    value_date_to          DATE,
    status                 VARCHAR(12) NOT NULL,
    total_internal         INT         NOT NULL DEFAULT 0,
    total_external         INT         NOT NULL DEFAULT 0,
    matched_count          INT         NOT NULL DEFAULT 0,
    amount_mismatch_count  INT         NOT NULL DEFAULT 0,
    missing_external_count INT         NOT NULL DEFAULT 0,
    missing_internal_count INT         NOT NULL DEFAULT 0,
    started_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at            TIMESTAMPTZ,
    exit_message           VARCHAR(1000),
    started_by             VARCHAR(60) NOT NULL,
    CONSTRAINT ck_reconciliation_status CHECK (status IN ('STARTED','COMPLETED','FAILED'))
);

CREATE TABLE smartops.reconciliation_detail (
    id                BIGSERIAL     PRIMARY KEY,
    reconciliation_id BIGINT        NOT NULL REFERENCES smartops.reconciliation (id) ON DELETE CASCADE,
    reference         VARCHAR(64)   NOT NULL,
    result            VARCHAR(20)   NOT NULL,
    internal_amount   NUMERIC(19,2),
    external_amount   NUMERIC(19,2),
    difference        NUMERIC(19,2),
    transaction_id    UUID REFERENCES smartops.financial_transaction (id),
    CONSTRAINT ck_reconciliation_detail_result
        CHECK (result IN ('MATCHED','AMOUNT_MISMATCH','MISSING_EXTERNAL','MISSING_INTERNAL'))
);
CREATE INDEX ix_reconciliation_detail ON smartops.reconciliation_detail (reconciliation_id, result);
