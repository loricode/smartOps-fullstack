# SmartOps: procesamiento y conciliación de operaciones bancarias

Plataforma que **recibe, valida, procesa, concilia y audita** operaciones financieras (transferencias,
pagos y recaudos) por API y en lotes masivos. Es un proyecto de referencia que simula una necesidad real
de un banco: no es un CRUD, sino un flujo empresarial con idempotencia, procesamiento por chunks,
reintentos controlados, consistencia transaccional y trazabilidad completa.

| | |
|---|---|
| **Backend** | Java 17 · Spring Boot 3.5 · Spring Batch 5.2 · Spring Security · JPA + JDBC |
| **Base de datos** | PostgreSQL 16 · migraciones Flyway versionadas |
| **Frontend** | Angular 22 (standalone, signals, zoneless) · Angular Material 3 con tema propio en tonos pastel |
| **Herramientas** | Python 3 (generador de archivos, validador previo, reportes) |
| **Calidad** | JUnit 5 · Mockito · Testcontainers · OpenAPI · Docker · GitHub Actions |

**Documentación de producto:** [PRD](PRD.md) (problema, métricas, reglas de negocio) ·
[Historias de usuario](HISTORIAS_DE_USUARIO.md) (criterios Dado/Cuando/Entonces y la prueba que verifica cada una) ·
[OpenSpec](openspec/project.md) (specs por módulo y tareas por fase; `node openspec/progress.mjs` muestra el avance).

**Video del camino feliz (narrado, 4 min):** [`docs/presentacion/videos/01-camino.mp4`](docs/presentacion/videos/01-camino.mp4),
grabado sobre la aplicación real ([cómo se genera](docs/presentacion/video/LEEME.md)).

---

## Qué demuestra

| Capacidad | Dónde verlo |
|---|---|
| **Idempotencia** en API y archivos, incluida la carrera entre peticiones simultáneas | [ADR-003](docs/architecture/adr/003-idempotencia.md), `TransactionRegistrationService` |
| **Procesamiento masivo por chunks** con rechazos trazables y sin detener el lote | `smartops-batch/importjob`, [ADR-004](docs/architecture/adr/004-diseno-batch.md) |
| **Reintentos controlados** ante errores transitorios | `ImportJobConfig` (`retry`, `processorNonTransactional`) |
| **Consistencia transaccional** y bloqueo de cuentas sin interbloqueos | `TransactionPostingService`, `SELECT … ORDER BY … FOR UPDATE` |
| **Conciliación** interno vs. externo con 4 resultados | `reconciliationJob`, `ReconciliationMatcher` |
| **Auditoría inmutable** con correlationId | `audit_log` + trigger, `CorrelationIdFilter` |
| **Clean Architecture / SOLID**: dominio sin framework, puertos y adaptadores | [arquitectura](docs/architecture/architecture.md), [ADR-002](docs/architecture/adr/002-clean-architecture-pragmatica.md) |
| **Seguridad por roles** y errores RFC 7807 | `SecurityConfig`, `GlobalExceptionHandler` |
| **Base de datos versionada** con invariantes en la propia BD | [`/database`](database/README.md), [ADR-006](docs/architecture/adr/006-base-de-datos-versionada.md) |
| **Separación de responsabilidades estilo ASO / APX** (simulada) | [ADR-005](docs/architecture/adr/005-simulacion-aso-apx.md) |

---

## Inicio rápido

Requisito: Docker.

```bash
docker compose up --build
```

| Servicio | URL | Credenciales |
|---|---|---|
| Dashboard | http://localhost:4200 | `operator` / `operator123` · `auditor` / `auditor123` · `admin` / `admin123` |
| Swagger UI | http://localhost:8080/swagger-ui.html | mismas |
| Health | http://localhost:8080/actuator/health | público |

### Demostración de extremo a extremo (10.000 operaciones)

```bash
cd python
python file-generator/generate_operations.py --count 10000 --prefix DEMO --out out
python data-validator/validate_file.py out/operations_DEMO.csv
```

1. En el dashboard → **Procesamiento masivo** → cargue `python/out/operations_DEMO.csv`.
2. Abra la ejecución: verá los conteos, la duración, los rechazos por motivo y cada línea rechazada.
3. Vuelva a cargar el mismo archivo: se rechaza como duplicado (`409`).
4. **Conciliación** → cargue `python/out/statement_DEMO.csv`.
5. Compare el resultado con `python/out/manifest_DEMO.json`, que tiene los conteos esperados.
6. `python reports/reconciliation_report.py --id 1 --out out` genera el reporte en Markdown y CSV.
7. Ingrese como `auditor` → **Auditoría**: quién hizo qué y cuándo.

---

## Arquitectura

Monolito modular ([ADR-001](docs/architecture/adr/001-monolito-modular.md)) con dependencias dirigidas
hacia el dominio:

```text
smartops-api ──► smartops-application ──► smartops-domain
     │                  ▲    ▲                (Java puro)
     ├─► smartops-infrastructure  (JPA, JDBC, Flyway, archivos)
     └─► smartops-batch           (Spring Batch)
```

| Módulo | Responsabilidad |
|---|---|
| `smartops-domain` | Agregados (`Transaction`, `BankAccount`), máquina de estados, `TransactionPolicy` (reglas), `LedgerPostingService` (asiento), `ReconciliationMatcher`. Sin dependencias. |
| `smartops-application` | Casos de uso transaccionales y **puertos** (interfaces) hacia la infraestructura. Rol *APX-like*. |
| `smartops-infrastructure` | Adaptadores: JPA para el modelo de escritura, JDBC para el de lectura, almacenamiento de archivos y auditoría. |
| `smartops-batch` | `importOperationsJob` (validar → cargar → contabilizar) y `reconciliationJob` (staging → comparar). |
| `smartops-api` | REST `/api/v1`, seguridad, validación de forma, errores, OpenAPI. Rol *ASO-like*. Ensambla la aplicación. |

Los diagramas de componentes, de secuencia, de flujo de los jobs, entidad-relación y de estados están en
**[docs/architecture/architecture.md](docs/architecture/architecture.md)**.

### Flujo de un lote

```text
CSV ─► checksum único? ─► 202 Accepted
          │
          ▼  validateAndLoadStep (chunk 500)
       parseo → reglas de formato → duplicado en archivo → duplicado en BD → cuentas
          ├─ válida   → INSERT VALIDATED
          └─ inválida → rejected_record (línea, código, detalle, contenido)
          ▼  postingStep (chunk 200)
       bloqueo ordenado de cuentas → débito/crédito → PROCESSED | REJECTED (fondos)
          ▼
       resumen (leídas / procesadas / rechazadas / duración) + auditoría
```

---

## Estructura del repositorio

```text
SmartOps/
├── backend/                 Maven multi-módulo (domain, application, infrastructure, batch, api)
├── database/                Migraciones Flyway, semillas y consultas de soporte
├── frontend/                Dashboard Angular
├── python/                  Generador, validador previo y reportes (+ pruebas pytest)
├── openspec/                Specs por módulo y tareas por fase (proposal + tasks)
├── docs/
│   ├── architecture/        Diagramas y ADR
│   ├── api/                 Guía del API y ejemplos curl
│   └── presentacion/        Video del camino feliz y el guion que lo graba
├── PRD.md · HISTORIAS_DE_USUARIO.md
├── docker-compose.yml
└── .github/workflows/ci.yml
```

Las pruebas Java siguen la convención de Maven y viven dentro de cada módulo (`src/test`):
las unitarias junto al código que prueban y la de integración (`*IT`) en `smartops-api`.

---

## Pruebas

| Nivel | Qué cubre | Cómo |
|---|---|---|
| Dominio (JUnit 5) | Máquina de estados, reglas de validación, asiento contable, clasificación de conciliación | `./mvnw test` |
| Aplicación (Mockito) | Idempotencia (replay, conflicto, carrera), recepción de archivos, fallo al lanzar jobs | `./mvnw test` |
| Batch (unitarias) | Parseo CSV, validación línea a línea, duplicados, caché de cuentas | `./mvnw test` |
| API (`@WebMvcTest`) | Seguridad 401/403, contrato de idempotencia, validación, formato de errores | `./mvnw test` |
| **Integración (Testcontainers)** | PostgreSQL real: Flyway → API → lote de 1.005 líneas → conciliación → auditoría inmutable | `./mvnw verify` |
| Python (pytest) | Generador determinista, contrato generador↔validador, reportes | `python -m pytest` |

```bash
cd backend && ./mvnw verify          # requiere Docker para Testcontainers
cd python  && python -m pytest
cd frontend && npm ci && npm run build
```

---

## Desarrollo local sin Docker para la aplicación

```bash
docker compose up -d postgres
cd backend && ./mvnw -pl smartops-api -am spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm start        # http://localhost:4200, con proxy a :8080
```

---

## Rendimiento

Medido en un equipo de desarrollo con Docker Desktop (PostgreSQL y la API en contenedores):

| Proceso | Volumen | Resultado | Duración |
|---|---|---|---|
| Carga y contabilización (`operations_DEMO.csv`) | 10.000 líneas | 9.617 procesadas · 383 rechazadas (9 motivos), **idéntico al manifest** | 29,7 s (~340 registros/s) |
| Conciliación (`statement_DEMO.csv`) | 9.533 líneas | 9.484 conciliadas · 44 diferencias · 89 faltan en extracto · 5 solo externas, **idéntico al manifest** | 8,5 s |
| Reenvío del mismo archivo con otro nombre | — | `409 DUPLICATE_FILE` | — |

Verificación contable posterior: la suma de los saldos de las 200 cuentas coincide al centavo con
`saldo inicial − pagos + recaudos` (consulta 6 de `database/scripts/useful-queries.sql`).

El costo dominante es la contabilización: cada operación bloquea sus cuentas y escribe un asiento de
auditoría. Los siguientes pasos de optimización están documentados en [ADR-004](docs/architecture/adr/004-diseno-batch.md).

---

## Decisiones y límites conscientes del MVP

* **Autenticación Basic con usuarios en memoria.** Es suficiente para demostrar la autorización por roles.
  En producción sería OAuth2/OIDC con el proveedor de identidad corporativo.
* **Almacenamiento local de archivos.** El puerto `FileStorage` permite cambiarlo por un almacenamiento
  de objetos sin tocar los casos de uso.
* **ASO / APX se simulan**, no se implementan. Son plataformas internas del banco; el proyecto muestra
  la separación de responsabilidades que representan ([ADR-005](docs/architecture/adr/005-simulacion-aso-apx.md)).
* **Monoinstancia.** La idempotencia y los bloqueos ya dependen de la base de datos y no de la memoria,
  así que soportan varias instancias. Los jobs se coordinarían con el `JobRepository` compartido.
