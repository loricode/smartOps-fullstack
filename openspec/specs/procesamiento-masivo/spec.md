# Procesamiento masivo

Implementación: `FileIntakeService` (aplicación) y el job `importOperationsJob` de
[`ImportJobConfig`](../../../backend/smartops-batch/src/main/java/com/smartops/batch/importjob/ImportJobConfig.java)
([ADR-004](../../../docs/architecture/adr/004-diseno-batch.md)).

## Endpoints

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| POST | `/api/v1/batch/imports` (multipart `file`) | OPERATOR | 202 `{ fileId, executionId, statusUrl }` · 400 · 409 |
| GET | `/api/v1/batch/executions?page&size` | autenticado | 200 página |
| GET | `/api/v1/batch/executions/{id}` | autenticado | 200 · 404 |
| GET | `/api/v1/batch/executions/{id}/rejections?page&size` | autenticado | 200 página (línea, referencia, código, detalle, contenido) |
| GET | `/api/v1/batch/executions/{id}/rejections/summary` | autenticado | 200 `[{ reasonCode, total }]` |

## Requisito: recepción del archivo
El archivo DEBE ser `.csv` y no vacío (400 en otro caso). Se guarda en la bandeja calculando su SHA-256 en streaming.
Si ya existe un archivo con el mismo tipo y checksum, la copia se descarta y se responde 409 `DUPLICATE_FILE` con `existingFileId`.
Las filas de `batch_file` y `batch_execution` DEBEN confirmarse **antes** de lanzar el job (que corre en otro hilo).

Si el job no se puede lanzar (pool de 2–4 hilos y cola de 20 llenos), la ejecución DEBE quedar `FAILED` con el motivo.

## Requisito: formato
```
reference,type,source_account,target_account,amount,currency,value_date,description
OP-0001,TRANSFER,ACC0000001,ACC0000002,1500.50,COP,2026-11-19,"Pago, factura 12"
```
La cabecera DEBE coincidir (sin distinguir mayúsculas, se acepta BOM); si no, el job falla antes de leer datos.
La descripción es opcional y admite comas entre comillas. Las líneas vacías se ignoran.

## Requisito: validateAndLoadStep (chunk 500)
Cada línea DEBE terminar aceptada (`financial_transaction` en `VALIDATED`, canal `BATCH`) o rechazada (`rejected_record`), por la
primera regla que incumpla, en este orden:

| # | Regla | Código |
|---|---|---|
| 1 | 7 u 8 columnas, comillas cerradas | `MALFORMED_RECORD` |
| 2 | Tipo, monto y fecha interpretables | `INVALID_TYPE`, `INVALID_AMOUNT`, `INVALID_DATE` |
| 3 | Reglas de formato de `TransactionPolicy` | ver [operaciones-financieras](../operaciones-financieras/spec.md) |
| 4 | Referencia no repetida en el archivo | `DUPLICATE_REFERENCE` |
| 5 | Referencia no registrada antes (API u otro lote) | `DUPLICATE_REFERENCE` |
| 6 | Cuentas existentes, activas y en la moneda | `ACCOUNT_NOT_FOUND`, `ACCOUNT_BLOCKED`, `CURRENCY_MISMATCH` |

Un rechazo de negocio NO DEBE ser una excepción ni hacer rollback del chunk. Las cuentas se consultan una vez por lote (caché).

## Requisito: robustez
- Errores transitorios (`TransientDataAccessException`) DEBEN reintentarse hasta 3 veces (`smartops.batch.retry-limit`).
- El procesador DEBE ser no transaccional (`processorNonTransactional`): en un reintento se reutiliza su salida.
- Si el `INSERT` de una aceptada choca con el `UNIQUE(reference)` (el API la registró durante el lote), el ítem DEBE omitirse
  y registrarse como `DUPLICATE_REFERENCE`, sin detener el lote.

## Requisito: postingStep (chunk 200)
Las operaciones `VALIDATED` del archivo DEBEN contabilizarse con `TransactionPostingService` (las mismas reglas que el API).
La lectura DEBE paginar por clave (`id > último`), nunca por `OFFSET`, porque cada página cambia el estado de sus filas.
Solo se contabiliza lo que sigue `VALIDATED`: reiniciar el job no repite asientos.

## Requisito: resumen
Al terminar, `batch_execution` DEBE guardar estado (`COMPLETED`/`FAILED`), leídas, aceptadas, rechazadas, procesadas, duración y
mensaje de error, y auditar `BATCH_EXECUTION COMPLETED|FAILED`. Se cumple: leídas = aceptadas + rechazadas, donde los rechazos
incluyen los de contabilización.

### Escenario: lote de prueba de integración
- **Dado** 1.000 transferencias válidas más 5 líneas: una referencia repetida, una cuenta inexistente, un monto `abc`, un pago de
  $999.999.999 y la línea `linea,rota`
- **Cuando** se carga el archivo
- **Entonces** estado `COMPLETED`, leídas 1.005, aceptadas 1.000, procesadas 1.000, rechazadas 5
- **Y** el resumen por motivo incluye `DUPLICATE_REFERENCE`, `ACCOUNT_NOT_FOUND`, `INVALID_AMOUNT`, `MALFORMED_RECORD`, `INSUFFICIENT_FUNDS`
- **Y** reenviar el mismo contenido como `copia.csv` responde 409 `DUPLICATE_FILE`

### Escenario: demo de 10.000 operaciones
- **Dado** `operations_DEMO.csv` del generador (semilla 42)
- **Entonces** 10.000 leídas, 9.617 procesadas, 383 rechazadas en 9 códigos, iguales al `manifest_DEMO.json`, en menos de 60 s
