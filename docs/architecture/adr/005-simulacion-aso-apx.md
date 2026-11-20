# ADR-005 · Simulación de responsabilidades ASO / APX

**Estado:** aceptada

## Contexto
ASO y APX son plataformas internas del ecosistema BBVA. Este proyecto **no** las implementa ni pretende
replicarlas: simula la **separación de responsabilidades** que representan, para mostrar que se entiende
el modelo y facilitar la adopción de las herramientas propias del banco.

## Correspondencia

| Responsabilidad | Plataforma de referencia | En SmartOps |
|---|---|---|
| Exposición de servicios REST, contrato, versionado, seguridad, validación de forma, formato de errores | ASO (capa de servicios) | `smartops-api`: controllers `/api/v1`, DTO con Bean Validation, Spring Security, RFC 7807, OpenAPI |
| Lógica de negocio transaccional, reglas, orquestación de persistencia | APX (backend de lógica) | `smartops-application` + `smartops-domain`: casos de uso `@Transactional`, políticas de dominio |
| Procesos masivos | Batch | `smartops-batch` (Spring Batch) |

## Regla que se respeta
Un controller **nunca** contiene reglas de negocio: solo traduce HTTP ↔ caso de uso. Así, la misma
lógica (`TransactionPostingService`) se invoca desde el API y desde el lote sin duplicarse, igual que una
transacción APX puede exponerse por varios canales.
