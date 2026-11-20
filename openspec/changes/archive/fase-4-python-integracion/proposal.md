# Fase 4 · Python e integración

## Por qué
Para demostrar el sistema con volumen real hacen falta archivos con errores conocidos, una validación previa para quien envía los
archivos y un reporte que lleve el resultado a negocio. Y una interfaz para operarlo sin `curl`.

## Qué cambia
- Generador determinista de operaciones, extracto y manifest con los conteos esperados.
- Validador previo con reporte JSON y código de salida para usar en CI.
- Reporte de conciliación en Markdown y CSV a partir de la API.
- Dashboard Angular 22 con Material 3: tablero, operaciones, lotes, conciliación y auditoría.

## Specs afectadas
[herramientas-datos](../../../specs/herramientas-datos/spec.md)

## Historias
HU-23 a HU-26.
