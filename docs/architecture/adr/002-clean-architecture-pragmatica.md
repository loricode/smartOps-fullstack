# ADR-002 · Clean Architecture pragmática

**Estado:** aceptada

## Contexto
Queremos reglas de negocio aisladas y fáciles de probar sin multiplicar el código ceremonial.

## Decisión
* **Dominio en Java puro.** `smartops-domain` no tiene dependencias: `Transaction`, `BankAccount`,
  `TransactionPolicy`, `LedgerPostingService` y `ReconciliationMatcher` se prueban con JUnit sin levantar Spring.
* **La capa de aplicación sí conoce `spring-context` y `spring-tx`.** Usa `@Service` y `@Transactional`
  porque declarar la frontera transaccional es responsabilidad del caso de uso; reimplementarlo con un
  puerto propio no aportaría nada. No conoce JPA, web ni batch.
* **Entidades JPA separadas del dominio.** Los adaptadores traducen entre `TransactionEntity` y
  `Transaction`. Hibernate corre con `ddl-auto=validate`: si una entidad no coincide con el esquema de
  Flyway, la aplicación no arranca.
* **CQRS ligero.** Las pantallas y los reportes leen *read models* planos por SQL (`OperationsQueries`),
  paginados y agregados, sin cargar agregados. Las escrituras siempre pasan por el dominio.

## Consecuencias
* (+) Las pruebas de reglas corren en milisegundos (más de 30 pruebas de dominio).
* (+) Cambiar JPA por JDBC (como en los writers batch) no afecta los casos de uso.
* (−) Hay mapeo entidad↔dominio. Se acepta porque es explícito y está concentrado en dos adaptadores.
