# ADR-003 · Idempotencia

**Estado:** aceptada

## Contexto
En pagos, una petición repetida (timeout del cliente, reintento de red, doble clic) **no puede** mover
dinero dos veces. Lo mismo vale para un archivo reenviado.

## Decisión
**Operaciones:**
1. La cabecera `Idempotency-Key` es la referencia de negocio de la operación (`reference UNIQUE`).
2. Se guarda `payload_hash` = SHA-256 del contenido económico normalizado (referencia, tipo, cuentas,
   monto sin ceros finales, moneda, fecha valor).
3. Misma clave + mismo hash → se devuelve la operación original (`200`, `Idempotent-Replayed: true`).
   Misma clave + hash distinto → `409 IDEMPOTENCY_CONFLICT`.
4. **Carrera:** dos peticiones idénticas simultáneas pasan la consulta inicial; la restricción `UNIQUE`
   hace fallar el `INSERT` de la segunda (`saveAndFlush` lo detecta antes de contabilizar), su transacción
   se revierte y el servicio, ya fuera de esa transacción, la resuelve como *replay*.
5. En lotes, la referencia ya existente se rechaza como `DUPLICATE_REFERENCE`.

**Archivos:** `UNIQUE(file_kind, checksum)` con SHA-256 calculado en streaming. El mismo contenido con
otro nombre se rechaza (`409 DUPLICATE_FILE`).

**Contabilización:** el `postingStep` solo contabiliza filas `VALIDATED`, así que reiniciar un job
nunca vuelve a aplicar un asiento.

## Consecuencias
* (+) La garantía final la da la base de datos, no una caché ni un *lock* en memoria: funciona con varias
  instancias.
* (−) Una referencia no se puede reutilizar nunca. Es el comportamiento deseado para una clave de negocio.
