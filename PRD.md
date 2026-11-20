# PRD · SmartOps

Plataforma de procesamiento y conciliación de operaciones bancarias.

## Problema

Un banco recibe a diario miles de operaciones (transferencias, pagos de servicios y recaudos) por dos vías: en línea, desde los
canales digitales, y en lote, como archivos que envían otros sistemas. Hoy:

- Un reintento del cliente (doble clic, timeout, corte de red) puede registrar y **debitar dos veces** la misma operación.
- Un archivo con unos pocos registros malos detiene todo el lote, o los registros malos se pierden sin que nadie sepa por qué.
- El mismo archivo se puede procesar dos veces si alguien lo reenvía con otro nombre.
- La conciliación contra el extracto externo se hace a mano: nadie sabe con certeza qué coincidió, qué tiene otro monto y qué falta de cada lado.
- Cuando algo sale mal, soporte no puede reconstruir quién hizo qué, cuándo y en qué petición.

## Objetivo del MVP

Recibir, validar, procesar, conciliar y auditar operaciones por API y por lotes **sin duplicar dinero, sin perder registros y
dejando trazabilidad completa**, con una arquitectura que pueda evolucionar.

| Métrica | Meta del MVP | Resultado medido | Cómo se mide |
|---|---|---|---|
| Operaciones duplicadas por reintento del cliente | 0 | 0 (el débito se aplica una sola vez) | `SmartOpsEndToEndIT.registersTransferIdempotently` |
| Registros perdidos en un lote | 0: leídos = procesados + rechazados | 10.000 = 9.617 + 383 | `ImportJobListener` + demo de 10.000 operaciones |
| Rechazos sin motivo | 0: cada rechazo con línea, código y detalle | 9 códigos, cada uno igual al manifest del generador | `rejected_record`, `manifest_DEMO.json` |
| Archivos procesados dos veces | 0 | Reenvío con otro nombre → 409 | `UNIQUE(file_kind, checksum)`, `SmartOpsEndToEndIT` |
| Referencias de conciliación sin clasificar | 0: cada una recibe uno de 4 resultados | 9.484 + 44 + 89 + 5 = todas | `reconcilesAgainstExternalStatement`, demo |
| Carga y contabilización de 10.000 operaciones | < 60 s en un equipo de desarrollo | 29,7 s (~340 registros/s) | Demo en Docker (README · Rendimiento) |
| Conciliación de 10.000 referencias | < 30 s | 8,5 s | Demo en Docker |
| Descuadre contable | 0 | Saldos = inicial − pagos + recaudos, al centavo | `database/scripts/useful-queries.sql` (consulta 6) |

## Usuarios y roles

| Rol | Qué necesita | Puede |
|---|---|---|
| **Operador** (`OPERATOR`) | Registrar operaciones, cargar archivos y extractos, seguir su resultado | Registrar, cargar, consultar |
| **Auditor** (`AUDITOR`) | Saber quién hizo qué, cuándo y con qué identificador de correlación | Consultar y ver la auditoría |
| **Administrador** (`ADMIN`) | Operar el sistema y revisar su salud | Todo lo anterior, más métricas (`/actuator/metrics`) |
| **Sistema externo** | Enviar operaciones en línea con reintentos seguros | API REST con `Idempotency-Key` |

Los usuarios del MVP son de demostración (`operator`, `auditor`, `admin`) y se configuran en `application.yml`.

## Alcance

### MVP 1.0 (entregado)

1. Registro idempotente de transferencias, pagos y recaudos por API, con validación y contabilización inmediata.
2. Carga masiva de archivos CSV con Spring Batch: validación por chunks, rechazos trazables, duplicados y contabilización.
3. Conciliación contra un extracto externo con cuatro resultados y detalle por referencia.
4. Auditoría de solo inserción con `X-Correlation-Id`.
5. Seguridad por roles, errores RFC 7807 y documentación OpenAPI.
6. Base de datos versionada con Flyway e invariantes en la propia base.
7. Dashboard Angular (Material 3) y herramientas Python: generador de archivos, validador previo y reporte.
8. Docker Compose, pruebas unitarias, de integración (Testcontainers) y pipeline de CI.

### Versión 2

- Autenticación OAuth2/OIDC con el proveedor de identidad corporativo (hoy HTTP Basic con usuarios en memoria).
- Resolución manual de diferencias de conciliación con nota y responsable.
- Reversión de operaciones procesadas con operación espejo.
- Particionado del paso de contabilización para paralelizar por rango de cuentas.
- Almacenamiento de archivos en objetos (S3/Blob) en lugar de disco local.
- Monitoreo con Prometheus/Grafana y alertas sobre lotes fallidos.

### Fuera de alcance

Conexión con bancos reales, plataformas internas ASO/APX (se **simula** su separación de responsabilidades,
[ADR-005](docs/architecture/adr/005-simulacion-aso-apx.md)), multimoneda real (solo COP y USD de catálogo), contabilidad general
(plan de cuentas, asientos dobles), impuestos (GMF) y firma de operaciones con segundo factor.

## Reglas de negocio

### Operaciones

| Id | Regla |
|---|---|
| RN-01 | El monto es positivo, con máximo 2 decimales y hasta $1.000.000.000 por operación (`smartops.limits.max-amount-per-operation`). |
| RN-02 | Monedas soportadas: COP y USD. La moneda de la operación debe coincidir con la de cada cuenta involucrada. |
| RN-03 | La fecha valor no puede ser posterior a mañana ni anterior a 90 días (hora de Colombia, `America/Bogota`). |
| RN-04 | La referencia es única en todo el sistema, de 4 a 64 caracteres (`A-Z a-z 0-9 - _`). Es la clave de idempotencia. |
| RN-05 | Transferencia exige origen y destino distintos; Pago exige solo origen; Recaudo exige solo destino. |
| RN-06 | Las cuentas deben existir y estar activas, al validar y otra vez al contabilizar. |
| RN-07 | Estados: `RECEIVED → VALIDATED → PROCESSING → PROCESSED → RECONCILED`; `RECEIVED / VALIDATED / PROCESSING → REJECTED`. `REJECTED` y `RECONCILED` son finales. |
| RN-08 | Contabilizar debita el origen antes de acreditar el destino. Sin saldo suficiente la operación queda `REJECTED` (`INSUFFICIENT_FUNDS`) y ningún saldo cambia. |
| RN-09 | Una operación que no cumple las reglas **se registra** con estado `REJECTED` y su motivo; no se descarta. |
| RN-10 | Reenviar la misma `Idempotency-Key` con el mismo contenido devuelve la operación original (200, `Idempotent-Replayed: true`). Con otro contenido, 409. |
| RN-11 | Las operaciones no se eliminan. El paso a `RECONCILED` solo lo hace el proceso de conciliación. |

### Procesamiento masivo

| Id | Regla |
|---|---|
| RL-01 | Un archivo con el mismo contenido (SHA-256) que uno ya recibido se rechaza (409), sin importar su nombre. |
| RL-02 | La cabecera debe ser exactamente `reference,type,source_account,target_account,amount,currency,value_date,description`; si no, el lote falla antes de leer datos. |
| RL-03 | Todo registro leído termina procesado o rechazado con línea, código y detalle. Leídos = aceptados + rechazados. |
| RL-04 | Un registro se rechaza por la **primera** regla que incumple, en este orden: estructura → formato de negocio → duplicado en el archivo → referencia ya registrada → cuentas. |
| RL-05 | Los registros válidos se contabilizan con las mismas reglas que el canal API (RN-06, RN-08). |
| RL-06 | Un lote se procesa de forma asíncrona: el API responde 202 y la ejecución se consulta por su id. |

### Conciliación

| Id | Regla |
|---|---|
| RC-01 | El extracto tiene la cabecera `reference,amount,currency,value_date`. Líneas mal formadas o repetidas se omiten y se registran. |
| RC-02 | Participan las operaciones `PROCESSED` o `RECONCILED` cuya fecha valor está en el rango de fechas del extracto. |
| RC-03 | Se empareja por referencia. Resultados: `MATCHED` (mismo monto y moneda, con tolerancia configurable, por defecto 0), `AMOUNT_MISMATCH`, `MISSING_EXTERNAL` (procesada pero no está en el extracto), `MISSING_INTERNAL` (está en el extracto pero no en SmartOps). |
| RC-04 | Cada referencia de ambos lados recibe exactamente un resultado; la diferencia es interno − externo. |
| RC-05 | Las operaciones `MATCHED` pasan de `PROCESSED` a `RECONCILED`; las ya conciliadas no cambian. |

## Requisitos no funcionales

- **Seguridad**: autenticación en cada petición (stateless), autorización por rol en el servidor, errores 401/403 sin
  `WWW-Authenticate`. Ocultar un botón en Angular no es seguridad: decide la API.
- **Consistencia**: registrar, contabilizar y auditar ocurre en una sola transacción. Cuentas bloqueadas con
  `SELECT … ORDER BY account_number FOR UPDATE` para evitar interbloqueos.
- **Idempotencia** garantizada por la base de datos (restricciones `UNIQUE`), no por memoria: soporta varias instancias.
- **Trazabilidad**: auditoría de solo inserción (trigger) con actor y `correlationId`; el `X-Correlation-Id` llega a cada línea de log.
- **Resiliencia**: reintentos acotados (3) ante errores transitorios de base de datos; un registro inválido nunca detiene un lote.
- **Mantenibilidad**: dominio sin framework, dependencias entre módulos verificadas por Maven, esquema reconstruible desde cero con Flyway.
- **Operación**: health checks (`/actuator/health/liveness`, `/readiness`), imágenes Docker con usuario no root.

## Tecnologías y responsabilidad

| Tecnología | Responsabilidad | Fase |
|---|---|---|
| Java 17 · Spring Boot 3.5 | API REST, casos de uso, seguridad | 1 ✅ |
| Dominio Java puro | Reglas, máquina de estados, asiento, clasificación de conciliación | 1 ✅ |
| PostgreSQL 16 · Flyway | Persistencia, invariantes, auditoría inmutable, vistas de reporte | 1 ✅ |
| Spring Batch 5.2 | Carga masiva y conciliación | 2–3 ✅ |
| Python 3 | Generación de archivos, validación previa, reportes | 4 ✅ |
| Angular 22 · Material 3 | Dashboard | 4 ✅ |
| JUnit 5 · Mockito · Testcontainers | Pruebas | 1–5 ✅ |
| Docker · GitHub Actions | Entornos reproducibles e integración continua | 5 (CI definido, falta ejecutarlo en GitHub) |

## Riesgos y decisiones

| Riesgo | Mitigación |
|---|---|
| Duplicar reglas entre el canal API y el lote | Un único `TransactionPostingService` y una única `TransactionPolicy` para los dos canales ([ADR-004](docs/architecture/adr/004-diseno-batch.md)). |
| Carrera entre dos peticiones idénticas | La restricción `UNIQUE(reference)` hace fallar a la segunda, que se responde como replay ([ADR-003](docs/architecture/adr/003-idempotencia.md)). |
| Esquema y entidades desalineados | Flyway es la única fuente del esquema; Hibernate solo valida (`ddl-auto=validate`) ([ADR-006](docs/architecture/adr/006-base-de-datos-versionada.md)). |
| Sobrediseño (microservicios) para un MVP | Monolito modular con puertos como contrato de extracción futura ([ADR-001](docs/architecture/adr/001-monolito-modular.md)). |
| Contabilización lenta en lotes grandes | Medido: 340 registros/s. Siguiente paso documentado: particionar por rango de cuentas. |
