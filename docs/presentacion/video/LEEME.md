# Videos de SmartOps

Graban la **aplicación real** (Docker, http://localhost:4200) en Microsoft Edge con subtítulos, cursor, narración y una portada, y
dejan el MP4 en `docs/presentacion/videos/`.

| # | Video | Qué muestra | Archivo |
|---|---|---|---|
| 1 | El camino feliz | El operador registra una transferencia por API → un reintento no la duplica → carga un lote de 5.000 operaciones con errores a propósito → ve los rechazos por motivo → concilia el extracto → la auditora revisa el rastro | `01-camino.mp4` |

Última grabación: 4 min 14 s, 1440×900, narrada, con el login rediseñado. Se grabó sin recrear la base: había una prueba manual
(`operations_PRUEBA1.csv`, 963 operaciones procesadas hoy), por eso la conciliación del video muestra 1.001 "faltan en extracto" y una
tasa de 82,2 %. Con la base recreada, la grabación anterior dio 4.770 / 24 / 43 / 5, igual al manifest.

## Requisitos

- `docker compose up -d` con el perfil `dev` (las 200 cuentas semilla y los usuarios de demostración).
- Microsoft Edge (cámbialo con `NAVEGADOR=chrome`) y Python 3: el guion genera su propio lote con `python/file-generator`.
- `playwright-core` **1.49 o superior** y `ffmpeg-static`: en esta carpeta (`npm i`, que instala `playwright-core`, y
  `npm i ffmpeg-static`) o en la carpeta de herramientas `%USERPROFILE%\OneDrive\Documentos\video` (cámbiala con `VIDEO_TOOLS`).
  Con versiones anteriores de Playwright, Edge se cierra al arrancar (lanzan `--headless=old`, que Edge ya no soporta).
- Narración (opcional): `python -m venv tts` y `tts\Scripts\python -m pip install edge-tts`, o apunta `EDGE_TTS` a un
  `edge-tts.exe` existente. Sin ella, el video queda con subtítulos. Voz por defecto `es-CO-SalomeNeural` (cámbiala con `VOZ_TTS`).
  El texto de cada escena está en `voz.js` y se envía al servicio de voz de Microsoft para generar el audio (queda en caché en `voz/`).

## Uso

```sh
node grabar.js --prueba          # sin video: una captura por escena en ./capturas (revisión rápida del guion)
node grabar.js                   # graba el video
node grabar.js --sin-voz         # solo subtítulos
```

Cada ejecución **escribe datos reales** (una operación por API, un lote y una conciliación) con un prefijo único, así que se puede
repetir. Pero la conciliación toma las operaciones procesadas **del día**: si ya grabaste (o probaste) hoy, las del lote anterior
aparecerán como "faltan en extracto". Para números limpios, recrea la base antes de grabar:

```sh
docker compose down -v && docker compose up -d
```

Si una escena falla, queda `error-<video>.png` con la pantalla en ese momento.
