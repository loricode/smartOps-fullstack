# Soporte a producción

Implementación: `CorrelationIdFilter`, `GlobalExceptionHandler`, `JdbcAuditTrail`, migraciones de [`database/`](../../../database/README.md),
`docker-compose.yml` y `.github/workflows/ci.yml`.

## Requisito: identificador de correlación
La API DEBE aceptar `X-Correlation-Id` si cumple `^[A-Za-z0-9-]{8,64}$` o generar un UUID; DEBE devolverlo en cada respuesta, ponerlo en
el MDC (aparece en cada línea de log) y guardarlo en cada registro de auditoría de la petición.

## Requisito: auditoría
`audit_log` DEBE ser de solo inserción: un trigger rechaza `UPDATE` y `DELETE`, también desde SQL.
Cada registro guarda entidad, id, acción, actor, `correlationId`, detalle JSON y fecha. La auditoría se escribe en la misma transacción
que el cambio.

| Entidad | Acciones |
|---|---|
| `TRANSACTION` | `RECEIVED`, `POSTED`, `REJECTED` |
| `BATCH_FILE` | `RECEIVED`, `DUPLICATE_REJECTED` |
| `BATCH_EXECUTION` | `COMPLETED`, `FAILED` |
| `RECONCILIATION` | `STARTED`, `COMPLETED`, `FAILED` |

### Escenario: auditoría inmutable
- **Cuando** se ejecuta `DELETE FROM smartops.audit_log`
- **Entonces** falla con "audit_log es de solo insercion"

## Requisito: errores
Todo error DEBE responder `application/problem+json` con `status`, `title`, `detail`, `code` y `correlationId`, sin trazas internas.

| HTTP | code |
|---|---|
| 400 | `VALIDATION_ERROR` (con `errors`), `BAD_REQUEST` |
| 401 / 403 | sin `code` (los responde la capa de seguridad) |
| 404 | `NOT_FOUND`, `TRANSACTION_NOT_FOUND` |
| 409 | `IDEMPOTENCY_CONFLICT`, `DUPLICATE_FILE`, `INVALID_STATE_TRANSITION` |
| 422 | otros `DomainException` |
| 500 | `INTERNAL_ERROR` |

## Requisito: salud
`/actuator/health/liveness` y `/readiness` DEBEN responder sin autenticación. La imagen de la API usa readiness como `HEALTHCHECK`
y el frontend arranca solo cuando la API está sana.

## Requisito: base de datos versionada
El esquema DEBE vivir solo en `database/migrations` (Flyway `V###__…`). Una migración aplicada NO DEBE editarse. Hibernate valida el
esquema (`ddl-auto=validate`) y nunca lo modifica. Las semillas son repetibles (`R__`) y solo se cargan con el perfil `dev`.
Las tablas de metadatos de Spring Batch se versionan en `V006` (`initialize-schema=never`).

## Requisito: entorno reproducible
`docker compose up --build` DEBE levantar PostgreSQL 16, la API (usuario no root, imagen por capas) y el dashboard (nginx con proxy
`/api/` al mismo origen, sin CORS).

## Requisito: integración continua
El pipeline DEBE ejecutar `./mvnw -B verify` (unitarias e integración con Testcontainers), `pytest`, `npm ci && npm run build` y
`docker compose build`, y publicar los reportes de pruebas.
