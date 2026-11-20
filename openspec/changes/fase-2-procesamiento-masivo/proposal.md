# Fase 2 · Procesamiento masivo

## Por qué
La mayor parte del volumen llega en archivos de otros sistemas. Hoy un registro malo puede detener el lote o perderse, y un archivo
reenviado se puede procesar dos veces.

## Qué cambia
- Recepción de archivos con checksum SHA-256 único y lanzamiento asíncrono del job (202).
- `importOperationsJob`: `validateAndLoadStep` (chunk 500, rechazos como datos) y `postingStep` (chunk 200, paginación por clave).
- Reintentos de errores transitorios, `processorNonTransactional` y *skip* solo para la carrera con el API.
- Resumen de la ejecución, rechazos por línea y por motivo, y auditoría.
- Tablas `batch_file`, `batch_execution`, `rejected_record` (`V003`) y metadatos de Spring Batch (`V006`).

## Specs afectadas
[procesamiento-masivo](../../specs/procesamiento-masivo/spec.md)

## Historias
HU-13 a HU-18.
