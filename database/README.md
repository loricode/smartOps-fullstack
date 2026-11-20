# smartops-database

Proyecto independiente de la base de datos (PostgreSQL 16), versionado con **Flyway**.
El backend empaqueta estas carpetas y las aplica al arrancar; Hibernate solo **valida** el esquema.

```text
database/
├── migrations/                     # V###__descripcion.sql: inmutables una vez aplicadas
│   ├── V001__initial_schema.sql            esquema, transaction_type, bank_account
│   ├── V002__create_transactions.sql       financial_transaction (reference UNIQUE = idempotencia)
│   ├── V003__create_batch_tables.sql       batch_file (checksum UNIQUE), batch_execution, rejected_record
│   ├── V004__create_reconciliation_tables.sql  statement_record, reconciliation, reconciliation_detail
│   ├── V005__create_audit_tables.sql       audit_log + trigger de solo inserción
│   ├── V006__spring_batch_metadata.sql     tablas BATCH_* oficiales de Spring Batch 5.2
│   └── V007__create_views.sql              v_transaction_summary, v_daily_volume
├── seed/
│   └── R__seed_accounts.sql        200 cuentas demo (solo perfil dev). ACC0000200 queda bloqueada
└── scripts/
    └── useful-queries.sql          consultas de operación y soporte
```

## Invariantes garantizadas por la base de datos

| Regla | Mecanismo |
|---|---|
| Una referencia no se registra dos veces | `UNIQUE (reference)` |
| Un archivo no se procesa dos veces | `UNIQUE (file_kind, checksum)` |
| Montos positivos | `CHECK (amount > 0)` |
| Sin saldos negativos | `CHECK (balance >= 0)` |
| Solo estados válidos | `CHECK (status IN (...))` |
| Auditoría inmutable | trigger `trg_audit_log_immutable` |

## Convenciones
* Esquema único `smartops`; nombres en `snake_case`.
* Una migración aplicada **no se edita**: los cambios se hacen con `V00N__…` nuevas.
* Fechas en `TIMESTAMPTZ` (UTC); montos en `NUMERIC(19,2)`, nunca en coma flotante.

## Ejecutar solo la base de datos

```bash
docker compose up -d postgres
psql -h localhost -U smartops -d smartops   # contraseña: smartops
```
