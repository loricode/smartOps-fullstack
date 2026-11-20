# Tareas · Fase 4 · Python e integración

## 1. Python
- [x] 1.1 HU-23 · `generate_operations.py`: errores inyectados, extracto con diferencias y manifest
- [x] 1.2 HU-24 · `validate_file.py`: reglas de formato, reporte JSON, umbral y códigos de salida
- [x] 1.3 HU-25 · `reconciliation_report.py`: paginación sobre la API, Markdown y CSV
- [x] 1.4 Pruebas `pytest` (11), incluido el contrato generador ↔ validador

## 2. Dashboard (Angular 22 + Material 3)
- [x] 2.1 Tema Material 3 con la paleta de la sala (índigo, turquesa, coñac, ocre) en tonos pastel
- [x] 2.2 Login con usuarios de demostración, guard e interceptor (cierra sesión ante 401)
- [x] 2.3 HU-26 · Tablero con indicadores, operaciones por estado y últimos lotes y conciliaciones
- [x] 2.4 Operaciones: alta con clave de idempotencia visible, aviso de replay y búsqueda paginada
- [x] 2.5 Lotes: carga por arrastre, ejecuciones con refresco automático y detalle de rechazos
- [x] 2.6 Conciliación: carga del extracto, tarjetas por resultado y detalle filtrable
- [x] 2.7 Auditoría visible solo para `AUDITOR`

## 3. Integración
- [x] 3.1 Proxy `/api` en desarrollo y nginx en Docker (mismo origen, sin CORS)
- [x] 3.2 Recorrido completo verificado: generador → validador → carga → conciliación → reporte
