# Fase 3 · Conciliación

## Por qué
Procesar no basta: hay que comprobar contra el extracto externo qué coincidió, qué tiene otro monto y qué falta de cada lado.
Hoy esa comparación es manual.

## Qué cambia
- `reconciliationJob`: carga del extracto a staging con *skip* de líneas defectuosas y comparación por `FULL OUTER JOIN`.
- Clasificación en el dominio (`ReconciliationMatcher`) con tolerancia configurable; cuatro resultados.
- Marcado `PROCESSED → RECONCILED` respetando la máquina de estados.
- Tablas `statement_record`, `reconciliation`, `reconciliation_detail` (`V004`), consulta del detalle por resultado y auditoría.

## Specs afectadas
[conciliacion-bancaria](../../specs/conciliacion-bancaria/spec.md)

## Historias
HU-19 a HU-22.
