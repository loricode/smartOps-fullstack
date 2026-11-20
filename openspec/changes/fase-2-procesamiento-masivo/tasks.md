# Tareas · Fase 2 · Procesamiento masivo

## 1. Base de datos
- [x] 1.1 `V003`: `batch_file` con `UNIQUE(file_kind, checksum)`, `batch_execution`, `rejected_record`
- [x] 1.2 `V006`: tablas `BATCH_*` oficiales de Spring Batch 5.2 en el esquema `smartops`

## 2. Batch y aplicación
- [x] 2.1 HU-13 · `FileIntakeService`: guardar, checksum, registrar, lanzar; filas confirmadas antes del job
- [x] 2.2 HU-16 · Archivo repetido → `DuplicateFileException` (409) y copia descartada
- [x] 2.3 HU-14 · `OperationLineParser` y `OperationValidationProcessor` con el orden de reglas de la spec
- [x] 2.4 HU-14 · `ImportOutcomeWriter` con `batchUpdate` de aceptadas y rechazadas
- [x] 2.5 HU-15 · Duplicados en el archivo, contra la base y por carrera con el API (`ImportSkipListener`)
- [x] 2.6 HU-13 · `postingStep` con `JdbcPagingItemReader` por clave y `TransactionPostingService`
- [x] 2.7 HU-13 · `ImportJobListener`: conteos, duración, estado y auditoría
- [ ] 2.8 HU-17 · Prueba que inyecte una `TransientDataAccessException` y compruebe el reintento sin falsos duplicados

## 3. API
- [x] 3.1 `POST /batch/imports` (202), consulta de ejecuciones, rechazos paginados y resumen por motivo
- [x] 3.2 `TaskExecutorJobLauncher` con pool propio (no reemplaza el `applicationTaskExecutor` de Boot)

## 4. Pruebas y medición
- [x] 4.1 `OperationValidationProcessorTest` (13 casos), `CsvLineTest`, `FileIntakeServiceTest`
- [x] 4.2 `SmartOpsEndToEndIT.importsFileWithValidAndInvalidRecords` (1.005 líneas)
- [x] 4.3 HU-18 · Demo de 10.000 operaciones en Docker: 29,7 s, conteos iguales al manifest
- [ ] 4.4 Prueba de concurrencia: API y lote contabilizando sobre las mismas cuentas sin interbloqueos
