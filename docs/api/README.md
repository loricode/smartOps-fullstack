# API REST v1

Documentación interactiva: **http://localhost:8080/swagger-ui.html** (OpenAPI en `/v3/api-docs`).

Autenticación HTTP Basic. Usuarios demo:

| Usuario | Contraseña | Roles | Puede |
|---|---|---|---|
| operator | operator123 | OPERATOR | registrar operaciones, cargar archivos, consultar |
| auditor | auditor123 | AUDITOR | consultar y ver auditoría |
| admin | admin123 | OPERATOR, AUDITOR, ADMIN | todo, más `/actuator/metrics` |

Todas las respuestas llevan `X-Correlation-Id` (se respeta el que envíe el cliente). Los errores siguen
RFC 7807 (`application/problem+json`) con un `code` estable.

## Endpoints

| Método | Ruta | Descripción | Rol |
|---|---|---|---|
| POST | `/api/v1/transactions` | Registrar operación (cabecera `Idempotency-Key`) | OPERATOR |
| GET | `/api/v1/transactions` | Buscar: `status, type, channel, reference, batchFileId, from, to, page, size` | autenticado |
| GET | `/api/v1/transactions/{id}` | Detalle | autenticado |
| POST | `/api/v1/batch/imports` | Subir CSV de operaciones (multipart `file`) → 202 | OPERATOR |
| GET | `/api/v1/batch/executions` | Ejecuciones de lote | autenticado |
| GET | `/api/v1/batch/executions/{id}` | Estado, conteos y duración | autenticado |
| GET | `/api/v1/batch/executions/{id}/rejections` | Líneas rechazadas | autenticado |
| GET | `/api/v1/batch/executions/{id}/rejections/summary` | Rechazos por motivo | autenticado |
| POST | `/api/v1/reconciliations` | Subir extracto (multipart `file`) → 202 | OPERATOR |
| GET | `/api/v1/reconciliations` | Conciliaciones | autenticado |
| GET | `/api/v1/reconciliations/{id}` | Resumen | autenticado |
| GET | `/api/v1/reconciliations/{id}/details?result=` | Detalle por referencia | autenticado |
| GET | `/api/v1/audit` | Auditoría: `entityType, entityId, actor` | AUDITOR |
| GET | `/api/v1/dashboard` | Indicadores | autenticado |
| GET | `/api/v1/accounts`, `/api/v1/accounts/{n}` | Cuentas simuladas | autenticado |
| GET | `/api/v1/session` | Usuario y roles | autenticado |
| GET | `/actuator/health` | Salud (liveness/readiness) | público |

## Ejemplos

```bash
# Registrar una transferencia
curl -u operator:operator123 -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" -H "Idempotency-Key: DEMO-0001" \
  -d '{"type":"TRANSFER","sourceAccount":"ACC0000001","targetAccount":"ACC0000002","amount":150000,"currency":"COP"}'
# → 201 Created

# Repetir exactamente la misma petición
# → 200 OK  +  Idempotent-Replayed: true   (no se debita dos veces)

# Misma clave con otro monto
# → 409 {"code":"IDEMPOTENCY_CONFLICT", ...}

# Carga masiva
curl -u operator:operator123 -F "file=@python/out/operations_DEMO.csv" http://localhost:8080/api/v1/batch/imports
# → 202 {"fileId":1,"executionId":1,"statusUrl":"/api/v1/batch/executions/1"}

curl -u operator:operator123 http://localhost:8080/api/v1/batch/executions/1

# Conciliación
curl -u operator:operator123 -F "file=@python/out/statement_DEMO.csv" http://localhost:8080/api/v1/reconciliations
```

## Códigos de error

| HTTP | code | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Forma inválida (Bean Validation, cabecera, JSON) |
| 401 | — | Sin credenciales o credenciales inválidas |
| 403 | — | El rol no permite la operación |
| 404 | `NOT_FOUND`, `TRANSACTION_NOT_FOUND` | Recurso inexistente |
| 409 | `IDEMPOTENCY_CONFLICT` | Clave reutilizada con otro contenido |
| 409 | `DUPLICATE_FILE` | Archivo ya recibido (incluye `existingFileId`) |
| 409 | `INVALID_STATE_TRANSITION` | Transición de estado no permitida |
| 500 | `INTERNAL_ERROR` | Error no controlado; reporte el `correlationId` |

Una operación que **no cumple las reglas de negocio** (cuenta inexistente o bloqueada, saldo insuficiente,
etc.) no es un error HTTP: se registra con estado `REJECTED` y `rejectionReason`, y se responde `201`.
Así queda trazabilidad de todo lo que se intentó.
