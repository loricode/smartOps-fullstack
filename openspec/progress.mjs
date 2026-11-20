#!/usr/bin/env node
/**
 * Avance de las tareas de OpenSpec (SmartOps): lee openspec/changes/** /tasks.md y cuenta las casillas.
 *
 *   node openspec/progress.mjs              → resumen por cambio y tareas pendientes
 *   node openspec/progress.mjs --check      → además falla (código 1) si un cambio archivado tiene tareas sin marcar
 *
 * Convención: "- [x]" hecha · "- [ ]" pendiente. Un cambio va a changes/archive/ solo cuando todas sus tareas están en [x].
 */
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), 'changes');
const check = process.argv.includes('--check');

function changes(dir, archived = false) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir, { withFileTypes: true }).filter((d) => d.isDirectory()).flatMap((d) =>
    d.name === 'archive' ? changes(join(dir, d.name), true) : [{ name: d.name, dir: join(dir, d.name), archived }]);
}

let problems = 0;
const rows = [];
const pending = [];
for (const c of changes(root).sort((a, b) => a.name.localeCompare(b.name))) {
  const file = join(c.dir, 'tasks.md');
  if (!existsSync(file)) { console.warn(`⚠ ${c.name} no tiene tasks.md`); problems++; continue; }
  const tasks = [...readFileSync(file, 'utf8').matchAll(/^\s*- \[( |x|X)\] (.+)$/gm)].map((m) => ({ done: m[1] !== ' ', text: m[2] }));
  const done = tasks.filter((t) => t.done).length;
  rows.push({ name: c.name, archived: c.archived, done, total: tasks.length });
  for (const t of tasks.filter((t) => !t.done)) pending.push(`${c.name}: ${t.text}`);
  if (c.archived && done < tasks.length) {
    console.error(`✖ ${relative(process.cwd(), file)} está archivado con ${tasks.length - done} tarea(s) sin marcar`);
    problems++;
  }
}

const bar = (d, t) => { const n = t ? Math.round((10 * d) / t) : 0; return '█'.repeat(n) + '░'.repeat(10 - n); };
console.log('\nCambio                         Avance              Estado');
for (const r of rows) {
  const pct = r.total ? Math.round((100 * r.done) / r.total) : 0;
  console.log(`${r.name.padEnd(30)} ${bar(r.done, r.total)} ${String(r.done).padStart(2)}/${String(r.total).padEnd(2)} ${String(pct).padStart(3)}%  ${r.archived ? 'archivado' : 'activo'}`);
}
const all = rows.reduce((a, r) => ({ d: a.d + r.done, t: a.t + r.total }), { d: 0, t: 0 });
console.log(`${'Total'.padEnd(30)} ${bar(all.d, all.t)} ${all.d}/${all.t}`);
if (pending.length) {
  console.log('\nPendientes:');
  for (const p of pending) console.log(`  [ ] ${p}`);
}
if (check && problems) process.exit(1);
