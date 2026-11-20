# Conciliación bancaria

Implementación: job `reconciliationJob` de
[`ReconciliationJobConfig`](../../../backend/smartops-batch/src/main/java/com/smartops/batch/reconciliation/ReconciliationJobConfig.java);
la clasificación es del dominio ([`ReconciliationMatcher`](../../../backend/smartops-domain/src/main/java/com/smartops/domain/reconciliation/ReconciliationMatcher.java)).

## Endpoints

| Método | Ruta | Rol | Respuesta |
|---|---|---|---|
| POST | `/api/v1/reconciliations` (multipart `file`) | OPERATOR | 202 `{ statementFileId, reconciliationId, statusUrl }` · 400 · 409 |
| GET | `/api/v1/reconciliations?page&size` | autenticado | 200 página |
| GET | `/api/v1/reconciliations/{id}` | autenticado | 200 resumen · 404 |
| GET | `/api/v1/reconciliations/{id}/details?result&page&size` | autenticado | 200 página; diferencias antes que conciliadas |

La recepción del extracto sigue las mismas reglas de archivo que el [procesamiento masivo](../procesamiento-masivo/spec.md)
(`.csv`, checksum único → 409).

## Requisito: carga del extracto (loadStatementStep)
```
reference,amount,currency,value_date
OP-0001,1500.50,COP,2026-11-19
```
Cada línea válida DEBE guardarse en la tabla de staging `statement_record`. Una línea mal formada (columnas, monto ≤ 0 o no numérico,
moneda que no tiene 3 letras, fecha inválida) o con referencia repetida DEBE omitirse con el mecanismo *skip* de Spring Batch
(límite 1.000) y registrarse en `rejected_record` con su línea y motivo.

## Requisito: insumos
- Lado externo: las líneas del extracto cargadas.
- Lado interno: operaciones `PROCESSED` o `RECONCILED` cuya fecha valor esté entre la mínima y la máxima del extracto.

## Requisito: clasificación (compareStep)
El emparejamiento por referencia DEBE hacerse con un `FULL OUTER JOIN` en PostgreSQL y cada fila se clasifica con `ReconciliationMatcher`:

| Resultado | Condición |
|---|---|
| `MATCHED` | En ambos lados, misma moneda y `|interno − externo| ≤ tolerancia` (`smartops.reconciliation.tolerance`, por defecto 0) |
| `AMOUNT_MISMATCH` | En ambos lados, con otro monto o moneda |
| `MISSING_EXTERNAL` | Solo en SmartOps |
| `MISSING_INTERNAL` | Solo en el extracto |

Cada referencia DEBE recibir exactamente un resultado; `difference = interno − externo` (nula si falta un lado).
Los resultados se insertan por lotes de 1.000 y la cabecera guarda el rango de fechas y los cuatro conteos.

## Requisito: marcado
Las operaciones `MATCHED` DEBEN pasar a `RECONCILED` con `UPDATE … WHERE status = 'PROCESSED'`, de modo que una ya conciliada no cambie.

### Escenario: prueba de integración
- **Dado** las 1.000 operaciones del lote y la transferencia del API, todas procesadas hoy
- **Y** un extracto sin las referencias 1 a 10, con monto distinto en la 11 a la 15, dos referencias que solo existen afuera,
  una línea repetida y una inválida
- **Cuando** se concilia
- **Entonces** 985 `MATCHED`, 5 `AMOUNT_MISMATCH`, 11 `MISSING_EXTERNAL` (10 del lote + la del API), 2 `MISSING_INTERNAL`
- **Y** 985 operaciones quedan `RECONCILED`; la repetida y la inválida no afectan los conteos

### Escenario: demo de 10.000
- **Dado** `statement_DEMO.csv` del generador
- **Entonces** 9.484 `MATCHED`, 44 `AMOUNT_MISMATCH`, 89 `MISSING_EXTERNAL`, 5 `MISSING_INTERNAL`, iguales al manifest, en menos de 30 s

## Pendiente
Exponer en la API y en la pantalla las líneas del extracto omitidas (hoy solo quedan en `rejected_record`). Resolución manual de
diferencias con nota: Versión 2.
