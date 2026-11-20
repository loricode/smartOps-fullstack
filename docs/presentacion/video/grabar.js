// Graba el video del camino feliz de SmartOps sobre la aplicación real (Docker en http://localhost:4200) y arma un MP4.
// Uso: node grabar.js [video ...] [--prueba] [--sin-voz]
//   video: camino (sin nombres = todos)
//   --prueba: no graba; deja una captura por escena en ./capturas (revisión rápida del guion)
//   --sin-voz: solo subtítulos, aunque edge-tts esté instalado
// Requisitos: `docker compose up -d` (perfil dev con las cuentas semilla), Microsoft Edge y Python 3 (genera los archivos del lote).
const fs = require('fs'), path = require('path'), os = require('os'), crypto = require('crypto');
const { execFileSync, spawnSync } = require('child_process');

// Dependencias aquí (npm i playwright-core ffmpeg-static) o en la carpeta de herramientas de video (VIDEO_TOOLS).
const TOOLS = process.env.VIDEO_TOOLS || path.join(os.homedir(), 'OneDrive', 'Documentos', 'video');
const req = (m) => { try { return require(m); } catch { return require(path.join(TOOLS, 'node_modules', m)); } };
const { chromium } = req('playwright-core');
const FFMPEG = req('ffmpeg-static');
const VOZ = require('./voz.js');

const args = process.argv.slice(2);
const PRUEBA = args.includes('--prueba');
const SIN_VOZ = args.includes('--sin-voz');
const VOICE = process.env.VOZ_TTS || 'es-CO-SalomeNeural';
const APP = process.env.SMARTOPS_URL || 'http://localhost:4200';
const W = 1440, H = 900;
const RAIZ = path.join(__dirname, '..', '..', '..');
const SALIDA = path.join(__dirname, '..', 'videos');
const CAPTURAS = path.join(__dirname, 'capturas');
const DATOS = path.join(__dirname, 'datos');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------- Datos de cada ejecución (prefijo único: el video se puede grabar varias veces) ----------
const HOY = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date()); // yyyy-MM-dd
const PREFIJO = 'VID' + String(Date.now()).slice(-7);
const LOTE = 5000;

function generarArchivos() {
  fs.mkdirSync(DATOS, { recursive: true });
  const py = process.env.PYTHON || 'python';
  execFileSync(py, [path.join(RAIZ, 'python', 'file-generator', 'generate_operations.py'), '--count', String(LOTE),
    '--prefix', PREFIJO, '--date', HOY, '--out', DATOS, '--seed', String(Date.now() % 100000)], { stdio: 'ignore' });
  return {
    operaciones: path.join(DATOS, `operations_${PREFIJO}.csv`),
    extracto: path.join(DATOS, `statement_${PREFIJO}.csv`),
    manifest: JSON.parse(fs.readFileSync(path.join(DATOS, `manifest_${PREFIJO}.json`), 'utf8')),
  };
}

// ---------- Voz (opcional) ----------
function buscarEdge() {
  if (SIN_VOZ) return null;
  if (process.env.EDGE_TTS) return [process.env.EDGE_TTS, []];
  const local = path.join(__dirname, 'tts', 'Scripts', 'edge-tts.exe');
  if (fs.existsSync(local)) return [local, []];
  for (const [c, a] of [['edge-tts', []], ['python', ['-m', 'edge_tts']], ['py', ['-m', 'edge_tts']]]) {
    if (spawnSync(c, [...a, '--help'], { stdio: 'ignore' }).status === 0) return [c, a];
  }
  return null;
}
const EDGE = buscarEdge();
const DIRVOZ = path.join(__dirname, 'voz');
function audio(texto) {
  fs.mkdirSync(DIRVOZ, { recursive: true });
  const f = path.join(DIRVOZ, crypto.createHash('sha1').update(VOICE + texto).digest('hex').slice(0, 16) + '.mp3');
  if (!fs.existsSync(f)) execFileSync(EDGE[0], [...EDGE[1], '--voice', VOICE, '--rate', '+4%', '--text', texto, '--write-media', f], { stdio: 'ignore' });
  const r = spawnSync(FFMPEG, ['-i', f], { encoding: 'utf8' });
  const m = (r.stderr || '').match(/Duration: (\d+):(\d+):([\d.]+)/);
  return { f, dur: m ? (+m[1]) * 3600 + (+m[2]) * 60 + parseFloat(m[3]) : 3 };
}
const lectura = (txt) => Math.max(3200, String(txt).split(/\s+/).length * 330);

// ---------- Capa de presentación: subtítulo, cursor, clic y portada con la paleta de SmartOps ----------
const OVERLAY = `(() => {
  if (document.getElementById('vx')) return;
  const css = document.createElement('style');
  css.textContent = \`
  #vx-cap{position:fixed;left:292px;right:28px;bottom:22px;z-index:2147483600;background:rgba(31,42,92,.96);color:#fff;border-radius:20px;
    padding:14px 20px 14px 16px;display:flex;gap:16px;align-items:center;box-shadow:0 14px 34px rgba(31,42,92,.35);
    font-family:'Plus Jakarta Sans',system-ui,sans-serif;transition:opacity .35s, transform .35s;pointer-events:none}
  #vx-cap.wide{left:28px}
  #vx-cap.off{opacity:0;transform:translateY(12px)}
  #vx-n{flex:none;width:46px;height:46px;border-radius:50%;background:#f9edcc;color:#7a5a00;display:grid;place-items:center;font:800 19px 'Plus Jakarta Sans',sans-serif}
  #vx-t{font:800 20px/1.25 'Plus Jakarta Sans',sans-serif}
  #vx-d{font:500 16px/1.4 'Plus Jakarta Sans',sans-serif;color:rgba(255,255,255,.88);margin-top:2px}
  #vx-role{margin-left:auto;flex:none;font:700 13px 'Plus Jakarta Sans',sans-serif;background:#d4f0f2;color:#0f5f69;border-radius:999px;padding:4px 12px}
  #vx-cur{position:fixed;left:0;top:0;z-index:2147483647;width:26px;height:26px;pointer-events:none;transition:left .65s cubic-bezier(.3,.7,.3,1), top .65s cubic-bezier(.3,.7,.3,1);filter:drop-shadow(0 2px 3px rgba(0,0,0,.4))}
  .vx-rip{position:fixed;z-index:2147483646;width:44px;height:44px;margin:-22px 0 0 -22px;border-radius:50%;border:3px solid #d4a22c;pointer-events:none;animation:vxr .55s ease-out forwards}
  @keyframes vxr{from{transform:scale(.3);opacity:1}to{transform:scale(1.4);opacity:0}}
  #vx-card{position:fixed;inset:0;z-index:2147483645;display:grid;place-items:center;text-align:center;font-family:'Plus Jakarta Sans',sans-serif;transition:opacity .6s;
    /* formas sólidas de borde nítido (corte de 1px), igual que la ilustración del login: sin desenfoque */
    background:radial-gradient(circle at 12% 86%,#2c8c99 0 15%,transparent calc(15% + 1px)),radial-gradient(circle at 88% 76%,#b5653a 0 11%,transparent calc(11% + 1px)),
    radial-gradient(circle at 76% 18%,#f2c14e 0 4.5%,transparent calc(4.5% + 1px)),radial-gradient(circle at 22% 22%,#7cd4e2 0 1.6%,transparent calc(1.6% + 1px)),#1f2a5c;color:#fff}
  #vx-card.off{opacity:0;pointer-events:none}
  #vx-card h1{font:800 64px/1.1 'Plus Jakarta Sans',sans-serif;letter-spacing:-1px;margin:0 0 18px}
  #vx-card p{font:500 24px/1.5 'Plus Jakarta Sans',sans-serif;margin:0 auto;max-width:920px;color:#d6dbf2}
  #vx-card small{display:inline-block;margin-top:30px;font:800 14px 'Plus Jakarta Sans',sans-serif;letter-spacing:.12em;text-transform:uppercase;background:#f9edcc;color:#7a5a00;border-radius:999px;padding:6px 16px}
  .cdk-global-overlay-wrapper:has(.mat-mdc-snack-bar-container){align-items:flex-start!important;justify-content:flex-end!important;padding:18px 28px}\`;
  document.head.appendChild(css);
  const root = document.createElement('div'); root.id = 'vx';
  root.innerHTML = '<div id="vx-cap" class="off"><div id="vx-n"></div><div><div id="vx-t"></div><div id="vx-d"></div></div><div id="vx-role"></div></div>'
    + '<svg id="vx-cur" viewBox="0 0 24 24"><path d="M4 2l16 9-7 2-3 7z" fill="#fff" stroke="#1f2a5c" stroke-width="1.6" stroke-linejoin="round"/></svg>'
    + '<div id="vx-card" class="off"><div><h1 id="vx-ch"></h1><p id="vx-cp"></p><small id="vx-cs"></small></div></div>';
  document.body.appendChild(root);
  const s = window.__vx || {};
  const cur = document.getElementById('vx-cur'); cur.style.left = (s.x ?? 720) + 'px'; cur.style.top = (s.y ?? 450) + 'px';
  if (s.cap) { const c = document.getElementById('vx-cap'); c.classList.remove('off'); c.classList.toggle('wide', !!s.cap.wide); for (const k of ['n','t','d','role']) document.getElementById('vx-' + k).textContent = s.cap[k] || ''; const r = document.getElementById('vx-role'); r.style.display = s.cap.role ? '' : 'none'; }
})()`;

// ---------- Guiones ----------
const VIDEOS = [
  {
    id: 'camino', n: 1, titulo: 'El camino feliz',
    sub: 'Una operación en línea, un lote de cinco mil, la conciliación con el banco y la auditoría, sobre la aplicación real',
    run: async (v, d) => {
      await v.cap(1, 'Bienvenidos a SmartOps', 'Procesamiento y conciliación de operaciones bancarias, por API y en lotes.', '', true);
      await v.apuntar('.login-form'); await v.hold(1200);
      await v.login('operator');

      await v.cap(2, 'El tablero operativo', 'Operaciones, monto procesado, rechazos y tasa de conciliación.', 'Operador');
      await v.apuntar('.kpis'); await v.hold(1800);

      await v.cap(3, 'Registrar una transferencia', 'Cuenta origen, destino y monto. La clave de idempotencia identifica la operación.', 'Operador');
      await v.nav('Operaciones');
      await v.clic('button:has-text("Nueva operación")', 1400);
      await v.elegir('source', 'ACC0000010');
      await v.elegir('target', 'ACC0000020');
      await v.escribir('input[name="amount"]', '1250000');
      await v.escribir('input[name="description"]', 'Pago a proveedor, factura 4471', 500, 30);
      await v.apuntar('input[name="key"]'); await v.hold(1400);
      await v.clic('button:has-text("Registrar")', 2200);
      await v.cap(4, 'Procesada al instante', 'Validar, debitar, acreditar y auditar en una sola transacción.', 'Operador');
      await v.apuntar('mat-card .alert'); await v.hold(2000);

      await v.cap(5, 'Un reintento no duplica', 'El mismo envío con la misma clave devuelve la operación original.', 'Operador');
      await v.clic('button:has-text("Registrar")', 2200);
      await v.apuntar('mat-card .alert.info'); await v.hold(2400);

      await v.cap(6, 'Llega un archivo de cinco mil operaciones', 'Generado con Python, con registros defectuosos a propósito.', 'Operador');
      await v.nav('Procesamiento masivo');
      await v.subir('.dropzone', d.operaciones);
      await v.clic('button:has-text("Procesar lote")', 1800);
      await v.cap(7, 'Spring Batch lo procesa por bloques', 'Validación y carga en chunks de 500; después, la contabilización.', 'Operador');
      await v.clic('tr.mat-mdc-row.clickable >> nth=0', 1500);
      await v.esperar('h1:has-text("Completado")', 180000);
      await v.cap(8, 'Nada se pierde', `${LOTE.toLocaleString('es-CO')} leídos = procesados + rechazados. Lo malo no detuvo el lote.`, 'Operador');
      await v.apuntar('.kpis'); await v.hold(2400);
      await v.cap(9, 'Cada rechazo con su motivo', 'Línea, código y detalle de cada registro rechazado.', 'Operador');
      await v.apuntar('.bar-row'); await v.hold(1400);
      await v.apuntar('table.mat-mdc-table'); await v.hold(2000);

      await v.cap(10, 'El extracto del banco', 'Se carga el extracto externo para compararlo con lo procesado.', 'Operador');
      await v.nav('Conciliación');
      await v.subir('.dropzone', d.extracto);
      await v.clic('button:has-text("Conciliar")', 1800);
      await v.clic('tr.mat-mdc-row.clickable >> nth=0', 1500);
      await v.esperar('h1:has-text("Completado")', 120000);
      await v.cap(11, 'Conciliación automática', 'Conciliadas, diferencias de monto y faltantes de cada lado.', 'Operador');
      await v.apuntar('.kpis'); await v.hold(2600);
      await v.cap(12, 'Lo que requiere acción', 'Las diferencias primero, con monto interno, externo y diferencia.', 'Operador');
      await v.apuntar('table.mat-mdc-table'); await v.hold(1600);
      await v.clic('.kpi.selectable:has-text("Faltan en SmartOps")', 2200);

      await v.login('auditor');
      await v.cap(13, 'Todo queda auditado', 'Quién hizo qué y cuándo; en el API, también con qué identificador de correlación.', 'Auditora');
      await v.nav('Auditoría');
      await v.apuntar('table.mat-mdc-table'); await v.hold(2600);
      await v.cap(14, 'Un rastro que no se borra', 'La auditoría es de solo inserción, también desde la base de datos.', 'Auditora');
      await v.apuntar('table.mat-mdc-table .tag'); await v.hold(2400);
    },
  },
];

// ---------- Grabación de un video ----------
async function grabar(vid, browser, datos) {
  const TMP = path.join(__dirname, 'frames', vid.id);
  fs.rmSync(TMP, { recursive: true, force: true }); fs.mkdirSync(TMP, { recursive: true });
  const conVoz = !!EDGE && !PRUEBA;
  const narracion = []; let vozFin = 0, wall0 = 0;
  const esperarVoz = async () => { if (PRUEBA) return; const ms = vozFin - Date.now(); if (ms > 0) await sleep(ms); };
  const hablar = (t, d) => {
    if (PRUEBA) return;
    const texto = VOZ[t];
    if (conVoz && texto) { const a = audio(texto); narracion.push({ f: a.f, t: Date.now() }); vozFin = Date.now() + a.dur * 1000 + 450; }
    else vozFin = Date.now() + lectura(t + ' ' + (d || ''));
  };

  const context = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 1, locale: 'es-CO' });
  const page = await context.newPage();
  const errores = [];
  page.on('pageerror', (e) => errores.push(e.message));
  page.on('load', () => page.evaluate(OVERLAY).catch(() => {}));
  await page.goto(`${APP}/login`);
  await page.waitForSelector('.login-form', { timeout: 60000 });
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(OVERLAY);
  await sleep(600);

  const frames = []; let cdp = null;
  if (!PRUEBA) {
    cdp = await context.newCDPSession(page);
    cdp.on('Page.screencastFrame', async (f) => {
      const file = path.join(TMP, `f${String(frames.length).padStart(6, '0')}.jpg`);
      fs.writeFileSync(file, Buffer.from(f.data, 'base64'));
      if (!frames.length) wall0 = Date.now();
      frames.push({ file, t: f.metadata.timestamp, w: Date.now() });
      cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }).catch(() => {});
    });
    await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 88, maxWidth: W, maxHeight: H, everyNthFrame: 1 });
  }

  let shot = 0;
  fs.mkdirSync(CAPTURAS, { recursive: true });
  const snap = async (n) => { if (PRUEBA) await page.screenshot({ path: path.join(CAPTURAS, `${String(vid.n).padStart(2, '0')}-${vid.id}-${String(++shot).padStart(2, '0')}-${n}.png`) }); };
  const hold = (ms) => sleep(PRUEBA ? Math.min(ms, 400) : ms);

  const v = { page, hold };
  v.cap = async (n, t, d, role, wide = false) => {
    await esperarVoz();
    await page.evaluate(([n, t, d, role, wide]) => {
      window.__vx = { ...(window.__vx || {}), cap: { n, t, d, role, wide } };
      const c = document.getElementById('vx-cap'); c.classList.remove('off'); c.classList.toggle('wide', wide);
      document.getElementById('vx-n').textContent = n; document.getElementById('vx-t').textContent = t;
      document.getElementById('vx-d').textContent = d; const r = document.getElementById('vx-role'); r.textContent = role || ''; r.style.display = role ? '' : 'none';
    }, [n, t, d, role, wide]);
    hablar(t, d);
    await snap(t.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-zA-Z0-9]+/g, '-').toLowerCase());
  };
  const card = async (h, p, s) => {
    await esperarVoz();
    await page.evaluate(([h, p, s]) => {
      document.getElementById('vx-ch').textContent = h; document.getElementById('vx-cp').textContent = p; document.getElementById('vx-cs').textContent = s || '';
      document.getElementById('vx-card').classList.remove('off');
    }, [h, p, s]);
    hablar(h, p);
  };
  const cardOff = async () => { await esperarVoz(); await page.evaluate(() => document.getElementById('vx-card').classList.add('off')); };
  const capOff = async () => { await esperarVoz(); await page.evaluate(() => { document.getElementById('vx-cap').classList.add('off'); if (window.__vx) window.__vx.cap = null; }); };

  v.apuntar = async (sel) => {
    const loc = page.locator(sel).first();
    await loc.waitFor({ state: 'visible', timeout: 20000 });
    let b = null;
    for (let k = 0; k < 6 && !b; k++) { try { await loc.scrollIntoViewIfNeeded({ timeout: 2000 }); b = await loc.boundingBox(); } catch { await sleep(250); } }
    if (!b) throw new Error('no pude ubicar ' + sel);
    const x = b.x + Math.min(b.width / 2, 60), y = b.y + Math.min(b.height / 2, 40);
    await page.evaluate(([x, y]) => { window.__vx = { ...(window.__vx || {}), x, y }; const c = document.getElementById('vx-cur'); if (c) { c.style.left = (x - 4) + 'px'; c.style.top = (y - 2) + 'px'; } }, [x, y]);
    await hold(700);
    return { loc, x, y };
  };
  const ripple = (x, y) => page.evaluate(([x, y]) => { const r = document.createElement('div'); r.className = 'vx-rip'; r.style.left = x + 'px'; r.style.top = y + 'px'; document.body.appendChild(r); setTimeout(() => r.remove(), 700); }, [x, y]);
  v.clic = async (sel, pausa = 700) => { const { loc, x, y } = await v.apuntar(sel); await ripple(x, y); await loc.click(); await hold(pausa); };
  // En campos de Material el rótulo flotante a veces tapa el centro del input: si el clic normal no entra, se fuerza.
  v.escribir = async (sel, txt, pausa = 500, delay = 45) => { const { loc, x, y } = await v.apuntar(sel); await ripple(x, y); await loc.click({ timeout: 2500 }).catch(() => loc.click({ force: true })); await loc.fill(''); await loc.pressSequentially(txt, { delay: PRUEBA ? 0 : delay }); await hold(pausa); };
  // mat-select: abre el campo por su atributo name y elige la opción.
  // (el rótulo flotante del form-field también puede tapar el select vacío: mismo respaldo que en `escribir`).
  v.elegir = async (nombre, opcion, pausa = 800) => {
    const { loc, x, y } = await v.apuntar(`mat-select[name="${nombre}"]`); await ripple(x, y);
    await loc.click({ timeout: 2500 }).catch(() => loc.click({ force: true })); await hold(600);
    await v.clic(`mat-option:has-text("${opcion}")`, pausa);
  };
  // Carga de archivo: apunta a la zona de arrastre y entrega el archivo al input oculto.
  v.subir = async (zona, archivo, pausa = 1400) => {
    const { x, y } = await v.apuntar(zona); await ripple(x, y);
    await page.locator('input[type=file]').first().setInputFiles(archivo);
    await hold(pausa);
  };
  v.esperar = async (sel, ms) => { await page.locator(sel).first().waitFor({ state: 'visible', timeout: ms }); await hold(600); };
  v.nav = async (texto) => { await v.clic(`.nav a:has-text("${texto}")`, 1500); };
  v.salir = async () => {
    if (await page.locator('button.logout').count()) await v.clic('button.logout', 1000);
    await page.waitForURL('**/login');
  };
  v.login = async (usuario) => {
    await esperarVoz();   // termina de narrar la escena anterior antes de cambiar de usuario
    if (!page.url().endsWith('/login')) await v.salir();
    await v.clic(`.demo-users button:has-text("${usuario}")`, 500);
    await v.clic('button:has-text("Ingresar")', 300);
    await page.waitForURL('**/dashboard', { timeout: 20000 });
    await hold(1200);
  };

  // Portada, guion y cierre
  await card(vid.titulo, vid.sub, `SmartOps · Video ${vid.n} de ${VIDEOS.length}`);
  await hold(2600); await snap('portada'); await cardOff(); await hold(700);
  try {
    await vid.run(v, datos);
  } catch (e) {
    await page.screenshot({ path: path.join(__dirname, `error-${vid.id}.png`) }).catch(() => {});
    throw e;
  }
  await capOff(); await hold(900);

  const wallFin = Date.now();
  if (cdp) await cdp.send('Page.stopScreencast');
  await context.close();
  if (errores.length) console.log(`  errores de la página (${vid.id}):`, errores);
  if (PRUEBA) return null;

  // Armar el MP4: cuadros con su duración real + narración en su instante.
  fs.mkdirSync(SALIDA, { recursive: true });
  const OUT = path.join(SALIDA, `${String(vid.n).padStart(2, '0')}-${vid.id}.mp4`);
  const lista = frames.map((f, i) => { const d = i + 1 < frames.length ? frames[i + 1].t - f.t : Math.max(0.5, (wallFin - f.w) / 1000); return `file '${f.file.replace(/\\/g, '/')}'\nduration ${Math.max(d, 0.001).toFixed(4)}`; });
  lista.push(`file '${frames[frames.length - 1].file.replace(/\\/g, '/')}'`);
  fs.writeFileSync(path.join(TMP, 'lista.txt'), lista.join('\n'));
  const video = path.join(TMP, 'video.mp4');
  execFileSync(FFMPEG, ['-y', '-f', 'concat', '-safe', '0', '-i', path.join(TMP, 'lista.txt'),
    '-vf', `fps=30,scale=${W}:${H}:flags=lanczos,format=yuv420p`, '-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-movflags', '+faststart', video], { stdio: 'ignore' });
  if (narracion.length) {
    const a = ['-y', '-i', video];
    narracion.forEach((n) => a.push('-i', n.f));
    const filtros = narracion.map((n, i) => `[${i + 1}:a]adelay=${Math.max(0, Math.round(n.t - wall0))}:all=1[a${i}]`);
    filtros.push(narracion.map((_, i) => `[a${i}]`).join('') + `amix=inputs=${narracion.length}:normalize=0:dropout_transition=0,loudnorm=I=-16:TP=-1.5[a]`);
    a.push('-filter_complex', filtros.join(';'), '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-ar', '48000', '-b:a', '160k', '-movflags', '+faststart', OUT);
    execFileSync(FFMPEG, a, { stdio: 'ignore' });
  } else {
    fs.copyFileSync(video, OUT);
  }
  fs.rmSync(TMP, { recursive: true, force: true });
  return { OUT, seg: Math.round(frames[frames.length - 1].t - frames[0].t), voz: narracion.length };
}

process.on('unhandledRejection', (e) => { if (!/Target page, context or browser has been closed/.test(String(e?.message))) { console.error('FALLO:', e?.message ?? e); process.exit(1); } });

(async () => {
  const pedidos = args.filter((a) => !a.startsWith('--'));
  const lista = pedidos.length ? VIDEOS.filter((m) => pedidos.includes(m.id)) : VIDEOS;
  if (!lista.length) { console.error('Videos válidos:', VIDEOS.map((m) => m.id).join(', ')); process.exit(1); }
  console.log(PRUEBA ? 'Modo prueba (capturas, sin video)' : `Narración: ${EDGE ? 'sí (' + VOICE + ')' : 'no, solo subtítulos'}`);
  const datos = generarArchivos();
  console.log(`Lote ${PREFIJO}: ${datos.manifest.total_lines} operaciones (${datos.manifest.expected_valid} válidas), fecha valor ${HOY}`);

  // Requiere playwright-core reciente (≥ 1.49): las versiones viejas lanzan Edge con --headless=old y el Edge actual se cierra.
  const browser = await chromium.launch({ channel: process.env.NAVEGADOR || 'msedge', headless: true });
  try {
    for (const m of lista) {
      process.stdout.write(`Video ${m.n} · ${m.titulo}… `);
      const r = await grabar(m, browser, datos);
      console.log(r ? `${path.basename(r.OUT)} · ${r.seg} s${r.voz ? ' · con voz' : ''}` : 'capturas listas');
    }
  } finally {
    await browser.close();
  }
})().catch((e) => { console.error('FALLO:', e.message); process.exit(1); });
