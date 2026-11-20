# Herramientas Python

Solo usan la librería estándar (Python 3.10+). `pytest` es necesario únicamente para las pruebas.

| Herramienta | Qué hace |
|---|---|
| `file-generator/generate_operations.py` | Genera un archivo de operaciones con errores inyectados, su extracto externo con diferencias y un `manifest` con los conteos esperados |
| `data-validator/validate_file.py` | Validación previa de estructura y calidad de datos (sin reglas de cuentas); reporte JSON y código de salida para CI |
| `reports/reconciliation_report.py` | Descarga una conciliación del API y genera un resumen en Markdown y el detalle de diferencias en CSV |

## Flujo de demostración (10.000 operaciones)

```bash
cd python

# 1. Generar archivos
python file-generator/generate_operations.py --count 10000 --prefix DEMO --out out
#   Operaciones: 10000 líneas (9617 válidas, 383 con error)
#   Extracto:    9533 líneas -> esperado: 9484 conciliadas, 44 con diferencia, 89 faltantes, 5 solo externas

# 2. Validación previa (falla si más del 5 % de los registros tienen errores de formato)
python data-validator/validate_file.py out/operations_DEMO.csv --kind operations --max-error-rate 0.05

# 3. Cargar en SmartOps (o desde el dashboard: Procesamiento masivo)
curl -u operator:operator123 -F "file=@out/operations_DEMO.csv" http://localhost:8080/api/v1/batch/imports

# 4. Conciliar
curl -u operator:operator123 -F "file=@out/statement_DEMO.csv" http://localhost:8080/api/v1/reconciliations

# 5. Reporte
python reports/reconciliation_report.py --id 1 --out out
```

El `manifest_DEMO.json` permite comparar lo que SmartOps reporta con lo que se inyectó a propósito.

## Pruebas

```bash
pip install -r requirements-dev.txt
python -m pytest
```

La prueba `test_validator_detects_exactly_the_injected_format_errors` es un contrato entre las dos
herramientas: el validador debe encontrar exactamente los errores que el generador declara.
