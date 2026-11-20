# ADR-001 · Monolito modular en lugar de microservicios

**Estado:** aceptada

## Contexto
El MVP tiene cuatro capacidades (operaciones, lotes, conciliación, auditoría) que comparten el mismo
modelo de datos y necesitan **consistencia transaccional fuerte**: contabilizar una operación implica
bloquear cuentas, mover saldos, cambiar el estado y auditar, todo o nada.

## Decisión
Un único desplegable Spring Boot dividido en cinco módulos Maven con dependencias dirigidas
(`domain ← application ← infrastructure/batch ← api`).

## Consecuencias
* (+) Transacciones ACID locales en lugar de sagas o compensaciones.
* (+) Un solo pipeline, una sola imagen y depuración sencilla.
* (+) Los límites entre módulos los verifica el compilador: un módulo no puede usar lo que no declara.
* (−) Escala como una unidad. Mitigación: el job batch corre en un pool acotado e independiente del de
  peticiones HTTP, y el puerto `BatchJobLauncher` permite mover los jobs a un worker separado (por ejemplo,
  con una cola) sin tocar los casos de uso.
* Camino de evolución: si un módulo necesita escalar o desplegarse por separado (por ejemplo, la
  conciliación), sus puertos ya son el contrato de extracción.
