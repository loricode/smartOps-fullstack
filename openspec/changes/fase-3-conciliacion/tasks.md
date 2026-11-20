# Tareas · Fase 3 · Conciliación

## 1. Base de datos
- [x] 1.1 `V004`: `statement_record` (staging), `reconciliation` y `reconciliation_detail` con `CHECK` de resultados

## 2. Dominio y batch
- [x] 2.1 HU-19 · `ReconciliationMatcher` (tolerancia, moneda) y `ReconciliationSummary`
- [x] 2.2 HU-21 · `loadStatementStep` con `StatementLineMapper`, filtro de duplicados y `StatementSkipListener`
- [x] 2.3 HU-19 · `CompareTasklet`: `FULL OUTER JOIN` en streaming e inserción por lotes de 1.000
- [x] 2.4 HU-22 · `UPDATE … WHERE status = 'PROCESSED'` para las `MATCHED`
- [x] 2.5 `ReconciliationJobListener`: estado, mensaje de error y auditoría

## 3. API
- [x] 3.1 `POST /reconciliations` (202), resumen y detalle filtrable por resultado
- [x] 3.2 HU-20 · Orden del detalle: diferencias antes que conciliadas
- [ ] 3.3 HU-21 · Exponer en la API y en la pantalla las líneas del extracto omitidas

## 4. Pruebas
- [x] 4.1 `ReconciliationMatcherTest` y `CsvLineTest.statementMapperParsesAndValidates`
- [x] 4.2 `SmartOpsEndToEndIT.reconcilesAgainstExternalStatement` (985 / 5 / 11 / 2)
- [x] 4.3 Demo de 10.000: 9.484 / 44 / 89 / 5 en 8,5 s, igual al manifest
