# ADR-006 · Base de datos como proyecto versionado

**Estado:** aceptada

## Decisión
* El esquema vive en `/database`, fuera del código Java, con migraciones Flyway numeradas (`V001__…`).
* El módulo de infraestructura empaqueta esas carpetas como recursos (`db/migration`, `db/seed`):
  hay una sola fuente de verdad.
* Los datos semilla son una migración **repetible** (`R__seed_accounts.sql`) que solo se carga con el
  perfil `dev`.
* Las invariantes críticas también se aplican en la base de datos, no solo en Java:
  `CHECK (amount > 0)`, `CHECK (balance >= 0)`, estados válidos, `UNIQUE(reference)`,
  `UNIQUE(file_kind, checksum)` y un trigger que hace `audit_log` de solo inserción.
* Hibernate valida el esquema (`ddl-auto=validate`) pero nunca lo modifica.

## Consecuencias
* (+) Cualquier cambio de esquema queda revisado en el control de versiones y es reproducible.
* (+) Aunque un error en la aplicación se salte una regla, la base de datos la sigue garantizando.
* (−) Una migración aplicada no se edita: los cambios se hacen con una migración nueva.
