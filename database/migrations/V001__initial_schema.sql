-- =====================================================================
-- V001 - Esquema base, catálogo de tipos de operación y cuentas
-- =====================================================================
CREATE SCHEMA IF NOT EXISTS smartops;

CREATE TABLE smartops.transaction_type (
    code            VARCHAR(20)  PRIMARY KEY,
    description     VARCHAR(120) NOT NULL,
    requires_source BOOLEAN      NOT NULL,
    requires_target BOOLEAN      NOT NULL
);

INSERT INTO smartops.transaction_type (code, description, requires_source, requires_target) VALUES
    ('TRANSFER',   'Transferencia entre cuentas',        TRUE,  TRUE),
    ('PAYMENT',    'Pago de servicios (débito)',         TRUE,  FALSE),
    ('COLLECTION', 'Recaudo (crédito a cuenta destino)', FALSE, TRUE);

CREATE TABLE smartops.bank_account (
    id             BIGSERIAL     PRIMARY KEY,
    account_number VARCHAR(20)   NOT NULL UNIQUE,
    holder_name    VARCHAR(120)  NOT NULL,
    currency       VARCHAR(3)    NOT NULL DEFAULT 'COP',
    balance        NUMERIC(19,2) NOT NULL DEFAULT 0,
    status         VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_bank_account_status  CHECK (status IN ('ACTIVE', 'BLOCKED')),
    CONSTRAINT ck_bank_account_balance CHECK (balance >= 0)
);
