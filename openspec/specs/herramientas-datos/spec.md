# Herramientas de datos (Python)

Implementación: [`python/`](../../../python/README.md). Solo librería estándar; `pytest` para las pruebas.

## Requisito: generador (`file-generator/generate_operations.py`)
DEBE producir para una corrida `operations_<prefix>.csv`, `statement_<prefix>.csv` y `manifest_<prefix>.json`, de forma determinista
con `--seed`.

- Las operaciones válidas usan las cuentas activas `ACC0000001`–`ACC0000199` (las mismas de `R__seed_accounts.sql`) y montos de
  1.000 a 200.000, para que no aparezcan rechazos por fondos no previstos.
- Errores inyectados con su código esperado: `MALFORMED_RECORD`, `DUPLICATE_REFERENCE` (repitiendo solo referencias válidas),
  `INVALID_AMOUNT`, `ACCOUNT_NOT_FOUND`, `ACCOUNT_BLOCKED`, `INVALID_DATE`, `MISSING_FIELD`, `INVALID_TYPE`, `INVALID_CURRENCY`.
- El extracto contiene solo operaciones válidas, con faltantes (`--missing-rate`), montos distintos (`--mismatch-rate`) y
  movimientos solo externos (`--extra-external`).
- El manifest DEBE cumplir: válidas + rechazadas = total; conciliadas + diferencias + faltantes = válidas.

### Escenario: el sistema reproduce el manifest
- **Dado** `--count 10000 --prefix DEMO` (semilla 42)
- **Cuando** se cargan el archivo y el extracto en SmartOps
- **Entonces** cada conteo por código de rechazo y por resultado de conciliación es igual al del manifest

## Requisito: validador previo (`data-validator/validate_file.py`)
DEBE revisar cabecera, número de columnas, tipos, montos, fechas, combinación de cuentas por tipo y referencias repetidas, sin conocer
el estado de las cuentas. Salida JSON con conteos por código y hasta 20 ejemplos.
Código de salida: 0 si la tasa de errores ≤ `--max-error-rate` (por defecto 0,05); 1 si la supera o la cabecera es inválida; 2 si el
archivo es ilegible.

### Escenario: contrato con el generador
- **Dado** un archivo generado
- **Entonces** el validador encuentra exactamente los errores declarados en el manifest, excepto `ACCOUNT_NOT_FOUND` y
  `ACCOUNT_BLOCKED`, que dependen de la base

## Requisito: reporte (`reports/reconciliation_report.py`)
DEBE consultar la API con autenticación, descargar todas las páginas de las diferencias y generar `reconciliation_<id>.md`
(resumen, tasa = conciliadas / (internas + faltantes internas), diferencia neta y primeras 25 diferencias) y
`reconciliation_<id>.csv` (detalle completo).
