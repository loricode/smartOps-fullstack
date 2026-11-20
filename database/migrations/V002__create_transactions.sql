-- =====================================================================
-- V002 - Operaciones financieras
-- La columna "reference" es la clave de idempotencia: una misma
-- referencia nunca puede registrarse (ni procesarse) dos veces.
-- =====================================================================
CREATE TABLE smartops.financial_transaction (
    id               UUID          PRIMARY KEY,
    reference        VARCHAR(64)   NOT NULL,
    type_code        VARCHAR(20)   NOT NULL REFERENCES smartops.transaction_type (code),
    source_account   VARCHAR(20),
    target_account   VARCHAR(20),
    amount           NUMERIC(19,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    value_date       DATE          NOT NULL,
    description      VARCHAR(255),
    status           VARCHAR(12)   NOT NULL,
    channel          VARCHAR(10)   NOT NULL,
    batch_file_id    BIGINT,
    payload_hash     VARCHAR(64)   NOT NULL,
    rejection_reason VARCHAR(255),
    created_by       VARCHAR(60)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at     TIMESTAMPTZ,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_financial_transaction_reference UNIQUE (reference),
    CONSTRAINT ck_ft_amount   CHECK (amount > 0),
    CONSTRAINT ck_ft_status   CHECK (status IN ('RECEIVED','VALIDATED','PROCESSING','PROCESSED','REJECTED','RECONCILED')),
    CONSTRAINT ck_ft_channel  CHECK (channel IN ('API','BATCH')),
    CONSTRAINT ck_ft_accounts CHECK (source_account IS NOT NULL OR target_account IS NOT NULL)
);

CREATE INDEX ix_ft_status     ON smartops.financial_transaction (status);
CREATE INDEX ix_ft_value_date ON smartops.financial_transaction (value_date);
CREATE INDEX ix_ft_batch_file ON smartops.financial_transaction (batch_file_id);
CREATE INDEX ix_ft_created_at ON smartops.financial_transaction (created_at DESC);
