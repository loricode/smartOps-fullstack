# ADR-004 · Diseño de los procesos masivos

**Estado:** aceptada

## Decisiones

| Tema | Decisión | Motivo |
|---|---|---|
| Registros inválidos (importación) | El procesador devuelve `Rejected` y se persiste | Un rechazo de negocio es un dato esperado, no un error; no consume el *skip limit* ni hace rollback |
| Registros inválidos (extracto) | `skip(FlatFileParseException)` + `SkipListener` | Muestra el mecanismo nativo de Spring Batch donde el registro inválido no aporta datos |
| Errores transitorios | `retry(TransientDataAccessException)`, límite 3 | Bloqueos o timeouts no deben tumbar el lote |
| Procesador con estado | `processorNonTransactional()` | Sin esto, un reintento reprocesaría las líneas y las marcaría como duplicadas |
| Escritura | `JdbcTemplate.batchUpdate` por chunk | Cientos de filas por ida y vuelta |
| Contabilización | Mismo `TransactionPostingService` que el API | Una sola implementación de las reglas contables |
| Lectura de pendientes | `JdbcPagingItemReader` con clave de orden | El `OFFSET` salta filas cuando cambian de estado |
| Bloqueo de cuentas | `SELECT ... ORDER BY account_number FOR UPDATE` | El orden determinista evita interbloqueos entre API y lote |
| Lanzamiento | `TaskExecutorJobLauncher` con un pool propio (2–4 hilos, cola 20) | El API responde `202` y el lote no compite con el pool HTTP |
| Metadatos Spring Batch | Tablas `BATCH_*` versionadas por Flyway (V006) | El esquema no se crea "mágicamente" en producción |

## Rendimiento medido
Ver la sección *Rendimiento* del README (10.000 operaciones en Docker local).

## Evolución
* Particionar el `postingStep` por rango de cuentas para paralelizar sin conflicto de bloqueos.
* Escribir la auditoría por chunk en lugar de por operación si su volumen se vuelve el cuello de botella.
