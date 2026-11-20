# Tareas · Fase 5 · Calidad

## 1. Pruebas y entornos
- [x] 5.1 HU-27 · `SmartOpsEndToEndIT` sobre PostgreSQL vacío: Flyway → API → lote → conciliación → auditoría
- [x] 5.2 HU-28 · Dockerfile de la API por capas con usuario no root y healthcheck; Dockerfile del frontend con nginx
- [x] 5.3 HU-28 · `docker-compose.yml` con PostgreSQL, API y dashboard, arranque ordenado por healthchecks
- [ ] 5.4 HU-29 · Ejecutar `.github/workflows/ci.yml` en GitHub (requiere repositorio y `git update-index --chmod=+x backend/mvnw`)
- [ ] 5.5 HU-30 · Pruebas Vitest de `AuthService`, `authInterceptor`, `problemMessage` y las páginas de lote y conciliación
- [ ] 5.6 HU-04 · Propagar el `correlationId` a la auditoría de los jobs batch (hoy queda vacío porque corren en otro hilo)

## 2. Documentación y presentación
- [x] 5.7 README con inicio rápido, demo de 10.000 operaciones y rendimiento medido
- [x] 5.8 Guía del API con ejemplos `curl` y catálogo de errores
- [x] 5.9 HU-31 · Video narrado del camino feliz (`docs/presentacion/videos/01-camino.mp4`)
