# Fase 1 · Fundamentos

## Por qué
Sin una estructura en capas, una base de datos versionada y la gestión de operaciones no hay dónde construir el procesamiento
masivo ni la conciliación. Esta fase deja el núcleo del negocio funcionando por API, con seguridad y trazabilidad.

## Qué cambia
- Maven multi-módulo con Clean Architecture: `domain` (Java puro) ← `application` ← `infrastructure` / `batch` ← `api`.
- PostgreSQL con migraciones Flyway como única fuente del esquema; invariantes en la base (`UNIQUE`, `CHECK`, trigger de auditoría).
- Dominio: agregado `Transaction` con máquina de estados, `TransactionPolicy`, `LedgerPostingService`.
- Registro idempotente por API con contabilización inmediata y bloqueo ordenado de cuentas.
- Spring Security por roles, errores RFC 7807, `X-Correlation-Id`, auditoría de solo inserción, OpenAPI y health checks.

## Specs afectadas
[seguridad-acceso](../../../specs/seguridad-acceso/spec.md) · [operaciones-financieras](../../../specs/operaciones-financieras/spec.md) ·
[soporte-produccion](../../../specs/soporte-produccion/spec.md)

## Historias
HU-01 a HU-12.
