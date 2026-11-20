# Historias de usuario

Cada historia indica la prueba automatizada (o la verificación documentada) que la comprueba.
Estado: ✅ verificada · 🟡 implementada, falta prueba o una parte · ⬜ pendiente.
Las reglas citadas (RN, RL, RC) están en el [PRD](PRD.md) y el comportamiento detallado en [`openspec/`](openspec/project.md).
Lo hecho y lo pendiente, tarea por tarea, está en [`openspec/changes/`](openspec/project.md#tareas) (`[x]` / `[ ]`).

| Fase | Historias |
|---|---|
| 1 · Fundamentos | HU-01 a HU-12 ✅ |
| 2 · Procesamiento masivo | HU-13 a HU-18 (HU-17 🟡) |
| 3 · Conciliación | HU-19 a HU-22 (HU-21 🟡) |
| 4 · Python e integración | HU-23 a HU-26 ✅ |
| 5 · Calidad | HU-27 a HU-31 (HU-29 🟡, HU-30 ⬜) |

---

## Módulo 1 · Seguridad y trazabilidad

**HU-01 · Iniciar sesión** ✅
Como usuario quiero entrar con usuario y contraseña para acceder solo a lo que mi rol permite.
- *Dado* credenciales válidas, *cuando* consulto `GET /api/v1/session`, *entonces* recibo mi usuario y mis roles.
- *Dado* credenciales inválidas o ausentes, *entonces* recibo 401 `application/problem+json` **sin** cabecera `WWW-Authenticate` (el navegador no abre su ventana de login).
- *Cuando* recibo un 401 con la sesión abierta, *entonces* Angular cierra la sesión y vuelve al login.
- Verifica: `TransactionControllerTest.requiresAuthentication`; pantalla Login (demostración en video).

**HU-02 · Autorización por rol** ✅
Como responsable de seguridad quiero que cada rol haga solo lo suyo, decidido en el servidor.
- *Dado* un auditor, *cuando* intenta registrar una operación, *entonces* recibe 403.
- *Dado* un operador, *cuando* consulta la auditoría, *entonces* recibe 403; *dado* un auditor, recibe 200.
- *Entonces* `/actuator/metrics` solo lo ve `ADMIN`; `/actuator/health` y Swagger son públicos.
- Verifica: `TransactionControllerTest.auditorCannotRegisterOperations`, `SmartOpsEndToEndIT.auditTrailIsVisibleToAuditorOnly`.

**HU-03 · Auditoría inmutable** ✅
Como auditor quiero saber quién recibió, rechazó o contabilizó cada operación y quién cargó cada archivo.
- *Entonces* cada operación deja `RECEIVED` y luego `POSTED` o `REJECTED`; cada archivo `RECEIVED` o `DUPLICATE_REJECTED`; cada lote y conciliación `COMPLETED` o `FAILED`, con actor y detalle en JSON.
- *Entonces* la auditoría se escribe en la misma transacción que el cambio: si el cambio se revierte, no queda rastro de algo que no ocurrió.
- *Cuando* alguien intenta `UPDATE` o `DELETE` sobre `audit_log`, incluso desde SQL, *entonces* falla.
- Verifica: `SmartOpsEndToEndIT.auditLogIsAppendOnly`, `TransactionRegistrarTest.invalidOperationIsPersistedAsRejectedAndNeverPosted` (audita `REJECTED`).

**HU-04 · Seguir una petición de punta a punta** ✅
Como soporte quiero un identificador que una la respuesta, los logs y la auditoría de una petición.
- *Entonces* la API acepta `X-Correlation-Id` (8 a 64 caracteres seguros) o genera uno, lo devuelve siempre y lo agrega a cada línea de log y a la auditoría.
- *Entonces* los errores incluyen el `correlationId` en el cuerpo.
- Verifica: `TransactionControllerTest.createsOperation` (cabecera en la respuesta). Pendiente: propagarlo a la auditoría de los jobs batch (tarea 5.6).

**HU-05 · Errores controlados** ✅
Como consumidor de la API quiero errores consistentes y sin trazas internas.
- *Entonces* todo error es RFC 7807 con `code` estable: `VALIDATION_ERROR` (400, con la lista de campos), `IDEMPOTENCY_CONFLICT` y `DUPLICATE_FILE` (409), `NOT_FOUND` (404), `INTERNAL_ERROR` (500).
- Verifica: `TransactionControllerTest.validatesRequestShapeAndHeader`, `conflictingReuseOfKeyReturns409`; `importsFileWithValidAndInvalidRecords` (`DUPLICATE_FILE`).

**HU-06 · Salud del sistema** ✅
Como operador de TI quiero saber si la API y la base responden.
- *Entonces* `/actuator/health/liveness` y `/readiness` responden sin autenticación; Docker usa readiness como healthcheck y el frontend espera a la API sana.
- Verifica: healthcheck de `backend/Dockerfile` y `depends_on: service_healthy` de `docker-compose.yml` (arranque verificado).

## Módulo 2 · Operaciones financieras

**HU-07 · Registrar una operación** ✅
Como operador quiero registrar transferencias, pagos y recaudos y que queden contabilizados al instante.
- *Dado* una transferencia válida, *cuando* la registro, *entonces* recibo 201 con `Location`, estado `PROCESSED`, el origen debitado y el destino acreditado.
- *Dado* un cuerpo con forma inválida (monto negativo, moneda que no son 3 letras), *entonces* recibo 400 con el error de cada campo.
- *Dado* que no envío la fecha valor, *entonces* se usa la fecha de hoy en Colombia.
- Verifica: `SmartOpsEndToEndIT.registersTransferIdempotently` (saldos exactos), `TransactionControllerTest.createsOperation`, `validatesRequestShapeAndHeader`, `TransactionRegistrarTest.validOperationIsValidatedThenPosted`.

**HU-08 · No duplicar operaciones por reintentos** ✅
Como cliente quiero reintentar sin miedo a que me debiten dos veces (RN-04, RN-10).
- *Dado* una `Idempotency-Key` ya usada con el mismo contenido, *entonces* recibo 200, `Idempotent-Replayed: true` y la operación original; el saldo no cambia.
- *Dado* la misma clave con otro contenido, *entonces* recibo 409 `IDEMPOTENCY_CONFLICT`.
- *Dado* dos peticiones idénticas simultáneas, *entonces* una crea la operación y la otra recibe el replay (la restricción `UNIQUE` resuelve la carrera).
- *Entonces* `100` y `100.00` se consideran el mismo contenido.
- Verifica: `TransactionRegistrationServiceTest` (4 casos, incluido `concurrentDuplicateIsResolvedAsReplay`), `TransactionTest.payloadHashIgnoresTrailingZerosAndWhitespaceButDetectsChanges`, `SmartOpsEndToEndIT.registersTransferIdempotently`.

**HU-09 · Una operación inválida se registra, no se pierde** ✅
Como auditor quiero ver también lo que se intentó y no se pudo (RN-09).
- *Dado* un pago desde una cuenta bloqueada, *entonces* recibo 201 con estado `REJECTED` y `rejectionReason` que empieza por `ACCOUNT_BLOCKED`.
- *Entonces* se valida en este orden: formato (monto, moneda, fecha, cuentas según el tipo) y luego existencia, estado y moneda de las cuentas, sin consultar cuentas si el formato ya falló.
- Verifica: `SmartOpsEndToEndIT.businessRejectionIsRecordedNotLost`, `TransactionPolicyTest` (6 casos), `TransactionRegistrarTest.invalidOperationIsPersistedAsRejectedAndNeverPosted`.

**HU-10 · Contabilizar sin descuadres** ✅
Como responsable financiero quiero que los saldos siempre cuadren (RN-06, RN-08).
- *Dado* saldo insuficiente, *entonces* la operación queda `REJECTED` (`INSUFFICIENT_FUNDS`) y **ningún** saldo cambia.
- *Dado* una cuenta bloqueada entre la validación y la contabilización, *entonces* se rechaza al contabilizar.
- *Entonces* un recaudo solo acredita y un pago solo debita.
- *Entonces* tras 10.000 operaciones, la suma de saldos = saldo inicial − pagos + recaudos, al centavo.
- Verifica: `LedgerPostingServiceTest` (4 casos), `importsFileWithValidAndInvalidRecords` (`INSUFFICIENT_FUNDS`), consulta 6 de `database/scripts/useful-queries.sql` ejecutada tras la demo.

**HU-11 · Consultar operaciones** ✅
Como operador quiero buscar por referencia, estado, tipo, canal, lote y fechas, con paginación.
- *Entonces* la búsqueda por referencia es por prefijo y sin distinguir mayúsculas; el tamaño de página máximo es 200.
- Verifica: `GET /api/v1/transactions` en la pantalla Operaciones (demostración en video y capturas).

**HU-12 · Ciclo de vida protegido** ✅
Como responsable financiero quiero que una operación no pueda saltarse estados ni procesarse dos veces (RN-07).
- *Dado* una operación `PROCESSED`, *cuando* se intenta procesar otra vez, *entonces* falla con `INVALID_STATE_TRANSITION`.
- *Entonces* `REJECTED` y `RECONCILED` son finales; la base también restringe los estados válidos (`CHECK`).
- Verifica: `TransactionStatusTest` (10 transiciones), `TransactionTest.cannotProcessTwice`.

## Módulo 3 · Procesamiento masivo

**HU-13 · Cargar un archivo y seguir su ejecución** ✅
Como operador quiero subir un CSV de operaciones y ver cuántas se procesaron y cuántas se rechazaron.
- *Cuando* lo subo, *entonces* recibo 202 con `executionId` y la carga corre en segundo plano.
- *Entonces* la ejecución muestra leídas, procesadas, rechazadas, duración y estado; leídas = aceptadas + rechazadas.
- *Dado* un archivo que no es `.csv` o está vacío, *entonces* recibo 400.
- Verifica: `SmartOpsEndToEndIT.importsFileWithValidAndInvalidRecords` (1.005 líneas → 1.000 procesadas, 5 rechazadas), `FileIntakeServiceTest.registersFileAndLaunchesImport`.

**HU-14 · Saber qué línea se rechazó y por qué** ✅
Como operador quiero corregir el archivo de origen con información precisa (RL-03, RL-04).
- *Entonces* cada rechazo tiene línea, referencia, código, detalle y el contenido original.
- *Entonces* hay un resumen por motivo que incluye los rechazos al contabilizar (`INSUFFICIENT_FUNDS`).
- *Entonces* un registro inválido nunca detiene el lote.
- Verifica: `OperationValidationProcessorTest.rejectsInvalidLinesWithReasonCode` (9 códigos), `importsFileWithValidAndInvalidRecords` (resumen por motivo); demo: los 9 códigos coinciden uno a uno con el manifest.

**HU-15 · Detectar referencias duplicadas** ✅
Como operador quiero que una referencia repetida no se procese dos veces.
- *Dado* una referencia repetida dentro del mismo archivo, *entonces* la segunda se rechaza (`DUPLICATE_REFERENCE`).
- *Dado* una referencia ya registrada por API o por otro lote, *entonces* se rechaza.
- *Si* el API registra la misma referencia entre la validación y la escritura, *entonces* el ítem se omite y queda como rechazo, sin tumbar el lote.
- Verifica: `OperationValidationProcessorTest.detectsDuplicateWithinSameFile`, `rejectsInvalidLinesWithReasonCode` (`ALREADY-0001`), `importsFileWithValidAndInvalidRecords`.

**HU-16 · No procesar dos veces el mismo archivo** ✅
Como operador quiero que un reenvío accidental no duplique el lote (RL-01).
- *Dado* un archivo con el mismo contenido que uno ya recibido, aunque tenga otro nombre, *entonces* recibo 409 `DUPLICATE_FILE` con el id del original y la copia se descarta.
- Verifica: `FileIntakeServiceTest.rejectsAlreadyReceivedFileAndDiscardsCopy`, `importsFileWithValidAndInvalidRecords`; demo (reenvío con otro nombre → 409).

**HU-17 · Recuperarse de errores transitorios** 🟡
Como operador quiero que un bloqueo o timeout momentáneo de la base no haga fallar el lote.
- *Entonces* los errores transitorios se reintentan hasta 3 veces por chunk; el procesador no se reevalúa en el reintento (`processorNonTransactional`), así que ninguna línea se marca falsamente como duplicada.
- *Si* el lanzamiento del job falla (pool lleno), *entonces* la ejecución queda `FAILED` con el motivo.
- Verifica: `FileIntakeServiceTest.marksExecutionFailedWhenLaunchFails`. Pendiente: prueba que inyecte un error transitorio (tarea 2.8).

**HU-18 · Procesar 10.000 operaciones en menos de un minuto** ✅
Como responsable de operación quiero tiempos predecibles en lotes grandes.
- *Entonces* 10.000 líneas se validan, cargan y contabilizan en 29,7 s (~340 registros/s) en un equipo de desarrollo.
- Verifica: demo en Docker documentada en el README (sección Rendimiento).

## Módulo 4 · Conciliación bancaria

**HU-19 · Conciliar contra un extracto externo** ✅
Como operador quiero saber qué coincidió, qué tiene otro monto y qué falta de cada lado (RC-02 a RC-04).
- *Cuando* subo el extracto, *entonces* recibo 202 y la conciliación corre en segundo plano.
- *Entonces* cada referencia recibe un resultado: `MATCHED`, `AMOUNT_MISMATCH`, `MISSING_EXTERNAL` o `MISSING_INTERNAL`.
- *Entonces* el rango de fechas se toma del extracto y una moneda distinta cuenta como diferencia.
- Verifica: `SmartOpsEndToEndIT.reconcilesAgainstExternalStatement` (985 / 5 / 11 / 2), `ReconciliationMatcherTest` (5 casos); demo: 9.484 / 44 / 89 / 5, igual al manifest.

**HU-20 · Ver el detalle por resultado** ✅
Como operador quiero ver primero lo que requiere acción.
- *Entonces* puedo filtrar el detalle por resultado; las diferencias aparecen antes que las conciliadas, con monto interno, externo y diferencia.
- Verifica: `GET /api/v1/reconciliations/{id}/details?result=`; pantalla Conciliación (tarjetas seleccionables); `reconciliation_report.py` contra la API en la demo.

**HU-21 · Un extracto con líneas malas no se detiene** 🟡
Como operador quiero conciliar aunque el extracto tenga algunas líneas defectuosas (RC-01).
- *Entonces* las líneas mal formadas y las referencias repetidas se omiten y quedan registradas con su línea y motivo.
- Verifica: `reconcilesAgainstExternalStatement` (una duplicada y una inválida omitidas sin afectar los conteos), `CsvLineTest.statementMapperParsesAndValidates`. Pendiente: exponer esos rechazos en la API y la pantalla (tarea 3.3).

**HU-22 · Marcar las operaciones conciliadas** ✅
Como responsable financiero quiero que lo conciliado quede marcado y no vuelva a cambiar (RC-05).
- *Entonces* las `MATCHED` pasan de `PROCESSED` a `RECONCILED`; la actualización exige `status = 'PROCESSED'`, así que respeta la máquina de estados.
- Verifica: `reconcilesAgainstExternalStatement` (985 en `RECONCILED`).

## Módulo 5 · Herramientas Python y dashboard

**HU-23 · Generar archivos de prueba realistas** ✅
Como desarrollador quiero archivos con errores conocidos para comprobar que el sistema los detecta.
- *Entonces* el generador produce operaciones, extracto y un `manifest` con los conteos esperados; con la misma semilla produce lo mismo.
- *Entonces* los montos están acotados para que no aparezcan rechazos por fondos no previstos.
- Verifica: `test_generation_is_deterministic`, `test_manifest_counts_are_consistent`, `test_valid_rows_respect_type_account_rules`.

**HU-24 · Validar un archivo antes de enviarlo** ✅
Como integrador quiero detectar errores de formato antes de enviar el archivo al banco.
- *Entonces* el validador revisa cabecera, columnas, tipos, fechas, montos y duplicados, entrega un reporte JSON y termina con código 1 si se supera el umbral de errores.
- *Entonces* encuentra exactamente los errores de formato que declaró el generador (contrato entre las dos herramientas).
- Verifica: `test_validator_detects_exactly_the_injected_format_errors`, `test_validator_rejects_wrong_header`, `test_validator_exit_code_honours_threshold`.

**HU-25 · Reporte de conciliación** ✅
Como responsable financiero quiero un resumen legible y el detalle de diferencias para analizar en Excel.
- *Entonces* el script consulta la API y genera un Markdown con la tasa de conciliación y un CSV con cada diferencia.
- Verifica: `test_markdown_contains_summary_and_differences`, `test_csv_export`, `test_match_rate_uses_union_of_both_sides`; ejecución contra la API en la demo (98,57 %, 138 diferencias).

**HU-26 · Dashboard operativo** ✅
Como operador quiero ver en un solo lugar el estado de las operaciones, los lotes y las conciliaciones.
- *Entonces* el tablero muestra operaciones, monto procesado, rechazos y tasa de conciliación; las ejecuciones en curso se actualizan solas.
- *Entonces* el menú de Auditoría solo aparece para el rol `AUDITOR`.
- Verifica: capturas del dashboard con los datos de la demo; video del camino feliz.

## Módulo 6 · Calidad y producción

**HU-27 · Reconstruir la base desde cero** ✅
Como desarrollador quiero crear la base completa con un comando y saber qué migraciones se aplicaron.
- *Entonces* Flyway aplica `V001` a `V007` (más las semillas en el perfil `dev`) y Hibernate valida que las entidades coinciden con el esquema.
- Verifica: `SmartOpsEndToEndIT` arranca sobre un PostgreSQL vacío en cada ejecución.

**HU-28 · Todo el sistema con un comando** ✅
Como evaluador quiero levantar base, API y dashboard sin instalar Java ni Node.
- *Cuando* ejecuto `docker compose up --build`, *entonces* quedan PostgreSQL, la API (sana) y el dashboard en `:4200`.
- Verifica: arranque verificado y demo de 10.000 operaciones ejecutada sobre ese entorno.

**HU-29 · Integración continua** 🟡
Como equipo queremos que cada cambio compile y pase las pruebas antes de integrarse.
- *Entonces* el pipeline ejecuta `mvnw verify` (con Testcontainers), `pytest`, el build de Angular y el build de las imágenes.
- Verifica: `.github/workflows/ci.yml` definido. Pendiente: ejecutarlo en GitHub (tarea 5.4).

**HU-30 · Pruebas del frontend** ⬜
Como equipo queremos pruebas unitarias de los servicios y componentes de Angular.
- *Entonces* `AuthService`, el interceptor, `problemMessage` y las páginas con lógica (lote, conciliación) tienen pruebas con Vitest.
- Pendiente (tarea 5.5).

**HU-31 · Video del camino feliz** ✅
Como evaluador quiero ver el sistema funcionando sin instalarlo.
- *Entonces* un video narrado muestra el recorrido completo sobre la aplicación real: operación por API, reintento sin duplicar, carga de 10.000 operaciones, conciliación y auditoría.
- Verifica: `docs/presentacion/videos/01-camino.mp4`, generado con `docs/presentacion/video/grabar.js`.
