# OpenSpec · SmartOps

Las specs describen **comportamiento observable** con escenarios Dado/Cuando/Entonces. Son el contrato entre el negocio,
el código y los asistentes de IA: antes de cambiar un comportamiento se actualiza su spec, en el mismo commit.

```
openspec/
├── specs/<módulo>/spec.md            qué hace el sistema HOY (requisitos y escenarios)
├── changes/<cambio>/proposal.md      por qué y qué cambia
├── changes/<cambio>/tasks.md         tareas con casillas: [x] hecha · [ ] pendiente
├── changes/archive/<cambio>/         cambios con todas las tareas en [x]
└── progress.mjs                      avance por cambio y lista de pendientes
```

| Spec | Qué cubre | Estado |
|---|---|---|
| [seguridad-acceso](specs/seguridad-acceso/spec.md) | Autenticación, roles, reglas de acceso por endpoint | Implementada |
| [operaciones-financieras](specs/operaciones-financieras/spec.md) | Registro idempotente, validación, ciclo de vida, contabilización | Implementada |
| [procesamiento-masivo](specs/procesamiento-masivo/spec.md) | Recepción de archivos, job de carga, rechazos, duplicados, reintentos | Implementada (falta prueba de reintento transitorio) |
| [conciliacion-bancaria](specs/conciliacion-bancaria/spec.md) | Extracto, emparejamiento, clasificación, marcado de conciliadas | Implementada (falta exponer rechazos del extracto) |
| [herramientas-datos](specs/herramientas-datos/spec.md) | Generador, validador previo y reporte en Python | Implementada |
| [soporte-produccion](specs/soporte-produccion/spec.md) | Auditoría, CorrelationId, errores, salud, base versionada, Docker, CI | Implementada (falta ejecutar CI y pruebas de Angular) |

## Tareas

| Cambio | Historias | Estado |
|---|---|---|
| [fase-1-fundamentos](changes/archive/fase-1-fundamentos/tasks.md) | HU-01 a HU-12 | Archivado |
| [fase-2-procesamiento-masivo](changes/fase-2-procesamiento-masivo/tasks.md) | HU-13 a HU-18 | Activo: falta prueba de reintento transitorio |
| [fase-3-conciliacion](changes/fase-3-conciliacion/tasks.md) | HU-19 a HU-22 | Activo: falta exponer rechazos del extracto |
| [fase-4-python-integracion](changes/archive/fase-4-python-integracion/tasks.md) | HU-23 a HU-26 | Archivado |
| [fase-5-calidad](changes/fase-5-calidad/tasks.md) | HU-27 a HU-31 | Activo |

```sh
node openspec/progress.mjs           # avance por cambio y tareas pendientes
node openspec/progress.mjs --check   # falla si un cambio archivado tiene tareas sin marcar
```

**Cómo se trabaja con las tareas**

1. Un trabajo nuevo se agrega como `- [ ] N.M descripción` en el `tasks.md` del cambio activo, o en un cambio nuevo
   (`changes/<nombre>/proposal.md` + `tasks.md`) si es un tema aparte.
2. Al terminarlo se marca `- [x]` **en el mismo commit** que el código, con su prueba. Si cambia el comportamiento, también se actualiza
   la spec de `specs/`. Una tarea solo se marca cuando está verificada (prueba automatizada o verificación documentada), no cuando "casi" está.
3. Si al trabajar aparece algo que falta, se agrega como `[ ]` en vez de dejarlo en un comentario.
4. Cuando todas las tareas de un cambio están en `[x]`, la carpeta se mueve a `changes/archive/` y se actualiza esta tabla.

## Convenciones

- **Stack**: Java 17, Spring Boot 3.5, Spring Batch 5.2, PostgreSQL 16, Flyway, Angular 22 con Material 3, Python 3.10+.
- **Arquitectura**: monolito modular `domain ← application ← infrastructure / batch ← api` ([arquitectura](../docs/architecture/architecture.md)).
- **Idioma**: documentación, mensajes de error e interfaz en español; identificadores de código y de base de datos en inglés.
- **REST**: prefijo `/api/v1`, JSON en camelCase, errores `application/problem+json` (RFC 7807) con `code` y `correlationId`,
  paginación `page` (desde 0) / `size` (máx. 200), fechas de negocio `yyyy-MM-dd`, instantes en UTC ISO 8601,
  montos como número decimal con 2 decimales.
- **Cabeceras**: `Authorization: Basic`, `X-Correlation-Id` (entrada opcional, salida siempre), `Idempotency-Key` en el alta de
  operaciones, `Idempotent-Replayed: true` en las respuestas de reintento.
- **Archivos**: CSV UTF-8 (se acepta BOM) con cabecera fija; los procesos masivos responden 202 y se consultan por id.
- **Códigos de rechazo**: los valores de `ValidationCode` (`MALFORMED_RECORD`, `INVALID_AMOUNT`, `DUPLICATE_REFERENCE`, …) son estables:
  se persisten y se usan en reportes; no se renombran.
- **Palabras clave**: DEBE / NO DEBE son obligatorias; PUEDE es opcional.
