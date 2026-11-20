# Tareas · Fase 1 · Fundamentos

## 1. Documentación
- [x] 1.1 PRD, historias de usuario con criterios verificables y specs por módulo
- [x] 1.2 Arquitectura (componentes, secuencia, flujos, ER, estados) y ADR 001 a 006

## 2. Base de datos
- [x] 2.1 `V001`–`V002`: esquema, catálogo de tipos, cuentas y operaciones con `UNIQUE(reference)` y `CHECK`
- [x] 2.2 `V005`: auditoría con trigger de solo inserción
- [x] 2.3 `V007`: vistas de reporte (`v_transaction_summary`, `v_daily_volume`)
- [x] 2.4 `R__seed_accounts.sql`: 200 cuentas demo (una bloqueada), solo con el perfil `dev`
- [x] 2.5 Hibernate en `ddl-auto=validate` contra el esquema de Flyway

## 3. Dominio y aplicación
- [x] 3.1 HU-12 · `TransactionStatus` con transiciones permitidas y `Transaction` que las aplica
- [x] 3.2 HU-09 · `TransactionPolicy`: formato primero, cuentas después
- [x] 3.3 HU-10 · `LedgerPostingService`: débito antes que crédito, rechazo sin tocar saldos
- [x] 3.4 HU-08 · Idempotencia con `payload_hash` y resolución de la carrera por `UNIQUE`
- [x] 3.5 HU-07 · `TransactionPostingService` con `SELECT … ORDER BY account_number FOR UPDATE`

## 4. API
- [x] 4.1 HU-01 / HU-02 · Spring Security stateless con roles y 401/403 en RFC 7807 sin `WWW-Authenticate`
- [x] 4.2 HU-07 / HU-08 · `POST /transactions` con `Idempotency-Key` (201 / 200 replay / 409)
- [x] 4.3 HU-11 · Búsqueda paginada de operaciones y consulta de cuentas
- [x] 4.4 HU-05 · `GlobalExceptionHandler` con `code` y `correlationId`
- [x] 4.5 HU-04 · `CorrelationIdFilter` (respuesta, MDC y auditoría)
- [x] 4.6 HU-03 · Auditoría `RECEIVED` / `POSTED` / `REJECTED` en la misma transacción
- [x] 4.7 HU-06 · Actuator con probes de liveness y readiness; OpenAPI con esquema Basic

## 5. Pruebas
- [x] 5.1 Dominio: `TransactionStatusTest`, `TransactionTest`, `TransactionPolicyTest`, `LedgerPostingServiceTest`
- [x] 5.2 Aplicación con Mockito: `TransactionRegistrationServiceTest`, `TransactionRegistrarTest`
- [x] 5.3 API: `TransactionControllerTest` (seguridad, idempotencia, validación)
- [x] 5.4 Integración: `SmartOpsEndToEndIT` (idempotencia con saldos exactos, rechazo registrado, auditoría)
