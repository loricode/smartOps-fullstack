# Operaciones financieras

Implementación: dominio [`Transaction`, `TransactionPolicy`, `LedgerPostingService`](../../../backend/smartops-domain/src/main/java/com/smartops/domain/transaction),
casos de uso `TransactionRegistrationService` → `TransactionRegistrar` → `TransactionPostingService` ([ADR-003](../../../docs/architecture/adr/003-idempotencia.md)).

## Endpoints

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| POST | `/api/v1/transactions` + `Idempotency-Key` `{ type, sourceAccount, targetAccount, amount, currency, valueDate?, description? }` | OPERATOR | 201 · 200 (replay) · 400 · 409 |
| GET | `/api/v1/transactions?status&type&channel&reference&batchFileId&from&to&page&size` | autenticado | 200 página |
| GET | `/api/v1/transactions/{id}` | autenticado | 200 · 404 |
| GET | `/api/v1/accounts`, `/api/v1/accounts/{number}` | autenticado | 200 · 404 |

## Requisito: validación
Una operación DEBE validarse con la misma `TransactionPolicy` en el canal API y en el lote. Primero el formato (sin consultar
datos) y, solo si el formato es correcto, las cuentas.

| Código | Condición |
|---|---|
| `MISSING_FIELD` | Falta referencia, monto o fecha valor |
| `MALFORMED_RECORD` | Referencia fuera de `^[A-Za-z0-9_-]{4,64}$` |
| `INVALID_TYPE` | Tipo distinto de `TRANSFER`, `PAYMENT`, `COLLECTION` |
| `INVALID_AMOUNT` | Monto ≤ 0 o con más de 2 decimales |
| `AMOUNT_LIMIT_EXCEEDED` | Monto > `smartops.limits.max-amount-per-operation` (1.000.000.000) |
| `INVALID_CURRENCY` | Moneda distinta de COP o USD |
| `VALUE_DATE_OUT_OF_RANGE` | Fecha valor > mañana o < hoy − 90 días (hora de Colombia) |
| `INVALID_ACCOUNT_COMBINATION` | `TRANSFER` sin origen o destino; `PAYMENT` con destino; `COLLECTION` con origen |
| `SAME_ACCOUNT` | Origen = destino |
| `ACCOUNT_NOT_FOUND` / `ACCOUNT_BLOCKED` / `CURRENCY_MISMATCH` | Cuenta inexistente, bloqueada o en otra moneda |

### Escenario: la regla de negocio no es un error HTTP
- **Dado** la cuenta `ACC0000200` bloqueada
- **Cuando** se registra un pago desde ella con la clave `API-E2E-0002`
- **Entonces** la respuesta es 201 con `status: REJECTED` y `rejectionReason` que empieza por `ACCOUNT_BLOCKED`
- **Y** la operación queda guardada y auditada (`RECEIVED`, `REJECTED`)

## Requisito: idempotencia
La `Idempotency-Key` es la referencia de la operación (`UNIQUE`). Se guarda `payload_hash` = SHA-256 de referencia, tipo, cuentas,
monto sin ceros finales, moneda y fecha valor.

| Caso | Respuesta |
|---|---|
| Clave nueva | 201 con `Location` |
| Misma clave y mismo hash | 200, `Idempotent-Replayed: true`, la operación original; no se vuelve a contabilizar |
| Misma clave y otro hash | 409 `IDEMPOTENCY_CONFLICT` |
| Dos peticiones idénticas simultáneas | Una 201; la otra falla en el `UNIQUE`, se revierte y responde como replay |

### Escenario: reintento sin doble débito
- **Dado** una transferencia de $250.000 de `ACC0000001` a `ACC0000002`, con saldos de $50.000.000
- **Cuando** se envía dos veces con la clave `API-E2E-0001`
- **Entonces** la primera responde 201 `PROCESSED` y la segunda 200 con el mismo `id`
- **Y** los saldos quedan en $49.750.000 y $50.250.000

## Requisito: ciclo de vida
Las transiciones DEBEN pasar por el agregado `Transaction`:

```
RECEIVED → VALIDATED → PROCESSING → PROCESSED → RECONCILED
   └──────────┴────────────┴──────→ REJECTED
```

Cualquier otra transición DEBE fallar con `INVALID_STATE_TRANSITION` (409). La base restringe los valores con `CHECK`.
Las operaciones NO DEBEN eliminarse.

## Requisito: contabilización
Para una operación `VALIDATED`, `TransactionPostingService` DEBE, dentro de la transacción del llamador:
1. bloquear las cuentas involucradas con `SELECT … ORDER BY account_number FOR UPDATE` (orden fijo: sin interbloqueos);
2. debitar el origen (si el tipo lo exige) y luego acreditar el destino;
3. si el débito falla (saldo insuficiente, cuenta bloqueada) dejar la operación `REJECTED` sin modificar ningún saldo;
4. guardar la operación y auditar `POSTED` o `REJECTED`.

### Escenario: saldo insuficiente
- **Dado** `ACC0000005` con ~$50.000.000
- **Cuando** se contabiliza un pago de $999.999.999
- **Entonces** la operación queda `REJECTED` con `INSUFFICIENT_FUNDS` y el saldo no cambia

### Escenario: cuadre contable
- **Dado** las 200 cuentas semilla con $50.000.000 cada una
- **Cuando** se procesan 10.000 operaciones
- **Entonces** la suma de saldos = 200 × 50.000.000 − pagos procesados + recaudos procesados, al centavo
