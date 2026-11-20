# Seguridad y acceso

Implementación: [`SecurityConfig`](../../../backend/smartops-api/src/main/java/com/smartops/api/config/SecurityConfig.java),
usuarios en `smartops.security.users` de `application.yml`.

## Endpoints

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/api/v1/session` | autenticado | 200 `{ username, roles[] }` |
| GET | `/actuator/health/**`, `/actuator/info` | público | 200 |
| GET | `/v3/api-docs/**`, `/swagger-ui/**` | público | 200 |
| GET | `/actuator/**` (resto) | `ADMIN` | 200 · 401 · 403 |

## Requisito: autenticación
Cada petición a `/api/**` DEBE autenticarse con HTTP Basic. La API NO DEBE crear sesión de servidor (stateless), por lo que CSRF no aplica.

### Escenario: sin credenciales
- **Dado** una petición sin `Authorization`
- **Cuando** llama a `POST /api/v1/transactions`
- **Entonces** recibe 401 `application/problem+json` con `status: 401`
- **Y** la respuesta NO DEBE incluir `WWW-Authenticate` (el navegador no muestra su ventana de login)

## Requisito: autorización por rol
La autorización DEBE decidirse en el servidor según esta matriz. Ocultar opciones en Angular es solo usabilidad.

| Operación | OPERATOR | AUDITOR | ADMIN |
|---|---|---|---|
| `POST /api/v1/transactions`, `/batch/**`, `/reconciliations/**` | ✔ | ✖ 403 | ✔ |
| `GET /api/v1/audit/**` | ✖ 403 | ✔ | ✔ |
| Resto de `GET /api/**` | ✔ | ✔ | ✔ |
| `/actuator/metrics` | ✖ | ✖ | ✔ |
| Cualquier otra ruta | denegada | denegada | denegada |

### Escenario: el auditor no registra operaciones
- **Dado** el usuario `auditor`
- **Cuando** envía `POST /api/v1/transactions`
- **Entonces** recibe 403 con el mensaje "Su rol no permite esta operación"

### Escenario: el operador no ve la auditoría
- **Dado** el usuario `operator`
- **Cuando** consulta `GET /api/v1/audit`
- **Entonces** recibe 403; con `auditor` recibe 200

## Requisito: credenciales
Las contraseñas DEBEN almacenarse con `DelegatingPasswordEncoder` (BCrypt) y PUEDEN sobrescribirse con las variables
`OPERATOR_PASSWORD`, `AUDITOR_PASSWORD` y `ADMIN_PASSWORD`. En Angular la credencial vive solo en `sessionStorage` y se borra al
cerrar la sesión o al recibir un 401.

## Fuera de alcance del MVP
OAuth2/OIDC, bloqueo por intentos fallidos y alcance por sucursal (ver Versión 2 en el [PRD](../../../PRD.md)).
