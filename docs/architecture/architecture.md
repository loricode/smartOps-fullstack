# Arquitectura de SmartOps

## 1. Vista de contexto

```mermaid
flowchart LR
    OP([Operador]) -- dashboard --> FE[Angular SPA]
    AUD([Auditor]) -- dashboard --> FE
    SYS([Sistemas externos]) -- REST / CSV --> API
    FE -- REST /api/v1 --> API[SmartOps API<br/>Spring Boot]
    API --> DB[(PostgreSQL)]
    PY[Python<br/>generador · validador · reportes] -- archivos CSV --> API
    PY -- consulta REST --> API
```

## 2. Vista de componentes (monolito modular)

```mermaid
flowchart TB
    subgraph api["smartops-api (rol ASO-like)"]
        C[Controllers REST<br/>DTO + Bean Validation]
        S[Spring Security<br/>roles OPERATOR/AUDITOR/ADMIN]
        E[GlobalExceptionHandler<br/>RFC 7807]
        F[CorrelationIdFilter]
    end
    subgraph app["smartops-application (rol APX-like)"]
        UC[Casos de uso<br/>TransactionRegistrationService<br/>TransactionPostingService<br/>FileIntakeService]
        P[Puertos de salida<br/>TransactionRepository · AccountRepository<br/>AuditTrail · BatchRegistry · FileStorage<br/>BatchJobLauncher · OperationsQueries]
    end
    subgraph dom["smartops-domain (Java puro)"]
        D[Transaction · BankAccount<br/>TransactionPolicy · LedgerPostingService<br/>ReconciliationMatcher]
    end
    subgraph infra["smartops-infrastructure"]
        JPA[Adaptadores JPA]
        JDBC[Adaptadores JDBC<br/>lado de lectura]
        FS[LocalFileStorage]
        FW[Flyway ← /database]
    end
    subgraph batch["smartops-batch"]
        J1[importOperationsJob]
        J2[reconciliationJob]
        L[SpringBatchJobLauncher async]
    end
    C --> UC
    UC --> D
    UC --> P
    JPA -. implementa .-> P
    JDBC -. implementa .-> P
    FS -. implementa .-> P
    L -. implementa .-> P
    J1 --> UC
    J1 --> D
    J2 --> D
```

Las dependencias entre módulos apuntan siempre hacia el dominio. Maven impide en compilación que el
dominio importe Spring o que la aplicación importe JPA.

| Módulo | Depende de | No puede depender de |
|---|---|---|
| domain | — | Spring, JPA, Web |
| application | domain, spring-context, spring-tx | JPA, Web, Batch |
| infrastructure | application | Web, Batch |
| batch | application | Web, infrastructure |
| api | todos (ensambla la aplicación) | — |

## 3. Registro idempotente de una operación (API)

```mermaid
sequenceDiagram
    autonumber
    participant Cli as Cliente
    participant Ctl as TransactionController
    participant Svc as TransactionRegistrationService
    participant Reg as TransactionRegistrar (@Transactional)
    participant Pol as TransactionPolicy
    participant Post as TransactionPostingService
    participant DB as PostgreSQL

    Cli->>Ctl: POST /transactions (Idempotency-Key: K)
    Ctl->>Svc: register(draft, actor)
    Svc->>DB: SELECT por referencia K
    alt ya existe y mismo hash
        Svc-->>Ctl: replay (200, Idempotent-Replayed)
    else ya existe con otro hash
        Svc-->>Ctl: 409 IDEMPOTENCY_CONFLICT
    else no existe
        Svc->>Reg: registerNew
        Reg->>Pol: validate(draft, cuentas)
        alt viola reglas
            Reg->>DB: INSERT estado REJECTED + auditoría
        else válida
            Reg->>DB: INSERT VALIDATED (flush → UNIQUE)
            Reg->>Post: post(tx)
            Post->>DB: SELECT cuentas ORDER BY número FOR UPDATE
            Post->>DB: UPDATE saldos, UPDATE PROCESSED, auditoría
        end
        Reg-->>Svc: COMMIT
        Note over Svc,DB: Si otra petición idéntica ganó la carrera,<br/>el UNIQUE falla → rollback → se responde como replay
        Svc-->>Ctl: 201 Created
    end
```

## 4. Procesamiento masivo

```mermaid
flowchart LR
    U[POST /batch/imports] --> CK{checksum<br/>ya recibido?}
    CK -- sí --> X[409 DUPLICATE_FILE]
    CK -- no --> R[(batch_file<br/>batch_execution)] --> A[202 Accepted<br/>job asíncrono]
    A --> S1
    subgraph S1[validateAndLoadStep · chunk 500]
        direction TB
        rd[FlatFileItemReader<br/>línea + número] --> pr[OperationValidationProcessor<br/>parseo → formato → duplicado en archivo<br/>→ duplicado en BD → cuentas]
        pr --> wr[ImportOutcomeWriter<br/>batch INSERT aceptadas / rechazadas]
    end
    S1 --> S2
    subgraph S2[postingStep · chunk 200]
        direction TB
        rd2[JdbcPagingItemReader<br/>VALIDATED, paginación por clave] --> wr2[PostingWriter<br/>TransactionPostingService]
    end
    S2 --> LS[ImportJobListener<br/>conteos, duración, auditoría]
```

Decisiones relevantes:

* **Rechazo como dato, no como excepción.** Un registro inválido es un resultado esperado del negocio:
  el procesador devuelve `Rejected` y el writer lo persiste con su línea y motivo. El lote nunca se
  detiene por datos malos.
* **Skip solo para lo excepcional.** Si una referencia aceptada choca en el `INSERT` porque el canal API
  la registró entre la validación y la escritura, Spring Batch omite ese ítem (`skip(DuplicateKeyException)`)
  y el `ImportSkipListener` lo registra como `DUPLICATE_REFERENCE`.
* **Reintentos controlados** (`retry(TransientDataAccessException)`, límite 3) para bloqueos y timeouts.
  Como el procesador tiene estado, se usa `processorNonTransactional()` para que un reintento reutilice
  su salida en vez de reevaluar las líneas (que se marcarían como duplicadas).
* **Paginación por clave** en `postingStep`: cada página cambia el estado de sus filas, de modo que
  un `OFFSET` saltaría registros; `WHERE id > :último` no.
* **Mismo servicio de contabilización** que el canal API (`TransactionPostingService`): una única
  implementación de las reglas contables, probada una vez.

## 5. Conciliación

```mermaid
flowchart LR
    U[POST /reconciliations] --> L1
    subgraph L1[loadStatementStep · chunk]
        r[FlatFileItemReader + StatementLineMapper] --> d[filtro de duplicados] --> w[(statement_record<br/>staging)]
        r -. FlatFileParseException .-> sk[StatementSkipListener → rejected_record]
        d -. duplicada .-> sk
    end
    L1 --> C
    subgraph C[compareStep · tasklet]
        j[FULL OUTER JOIN por referencia<br/>internas PROCESSED/RECONCILED del rango<br/>vs. extracto] --> m[ReconciliationMatcher<br/>dominio: tolerancia y moneda]
        m --> o[(reconciliation_detail)]
        m --> u[UPDATE PROCESSED → RECONCILED]
    end
```

PostgreSQL hace el emparejamiento (es lo que mejor hace, con índices) y el dominio hace la
clasificación, para que la regla de negocio viva en un solo lugar con pruebas unitarias.

## 6. Modelo de datos

```mermaid
erDiagram
    transaction_type ||--o{ financial_transaction : clasifica
    bank_account ||--o{ financial_transaction : "origen/destino (por número)"
    batch_file ||--o{ financial_transaction : origina
    batch_file ||--o{ batch_execution : "se procesa en"
    batch_file ||--o{ rejected_record : rechaza
    batch_file ||--o{ statement_record : contiene
    batch_file ||--o{ reconciliation : "extracto de"
    reconciliation ||--o{ reconciliation_detail : detalla
    financial_transaction ||--o{ reconciliation_detail : "se concilia en"

    financial_transaction {
        uuid id PK
        varchar reference UK "clave de idempotencia"
        varchar type_code FK
        numeric amount "CHECK > 0"
        varchar status "CHECK ciclo de vida"
        varchar channel "API | BATCH"
        varchar payload_hash "SHA-256"
        bigint version "bloqueo optimista"
    }
    bank_account {
        bigint id PK
        varchar account_number UK
        numeric balance "CHECK >= 0"
        varchar status "ACTIVE | BLOCKED"
    }
    batch_file {
        bigint id PK
        varchar checksum "UNIQUE(kind, checksum)"
    }
    audit_log {
        bigint id PK
        varchar entity_type
        varchar action
        varchar actor
        varchar correlation_id
        text details "JSON"
    }
```

`audit_log` es de solo inserción: un trigger rechaza cualquier `UPDATE` o `DELETE`.

## 7. Ciclo de vida de una operación

```mermaid
stateDiagram-v2
    [*] --> RECEIVED
    RECEIVED --> VALIDATED: supera TransactionPolicy
    RECEIVED --> REJECTED: viola reglas
    VALIDATED --> PROCESSING
    VALIDATED --> REJECTED
    PROCESSING --> PROCESSED: débito/crédito aplicados
    PROCESSING --> REJECTED: saldo insuficiente / cuenta bloqueada
    PROCESSED --> RECONCILED: coincide con extracto
    REJECTED --> [*]
    RECONCILED --> [*]
```

La máquina de estados está en el dominio (`TransactionStatus`) y se refuerza en la base de datos con
`CHECK` sobre los valores; el paso a `RECONCILED` por SQL masivo usa la guarda `WHERE status = 'PROCESSED'`.
