// Ronda 80: resume un .cpuprofile de boot_profile.mjs.
// Uso: node tools/diag/profile_agg.mjs DIR [TOP=40]
// Imprime self-time por función, por "paquete" (prefijo de clase Java o archivo JS), tiempo total por
// función raíz-inclusiva elegida y self-time por ventana de tiempo de 30 s (para alinear con events.txt).
import { readFileSync } from 'node:fs';
const D = process.argv[2] || '/tmp/pzprof', TOP = +(process.argv[3] || 40);
const p = JSON.parse(readFileSync(`${D}/boot.cpuprofile`, 'utf8'));
const byId = new Map(p.nodes.map(n => [n.id, n]));
const parent = new Map(); for (const n of p.nodes) for (const c of n.children || []) parent.set(c, n.id);
const dt = new Map(); // ms por id
for (let i = 0; i < p.samples.length; i++) { const d = (p.timeDeltas[i + 1] ?? p.timeDeltas[i] ?? 0) / 1000; dt.set(p.samples[i], (dt.get(p.samples[i]) || 0) + d); }
const total = [...dt.values()].reduce((a, b) => a + b, 0);
const name = n => { const f = n.callFrame; const u = f.url ? f.url.replace(/^.*\/(?=[^/]+$)/, '') : '';
  return `${f.functionName || '(anon)'}${u && !u.endsWith('.wasm') ? ' [' + u + ':' + f.lineNumber + ']' : ''}`; };
const pkg = n => { const f = n.callFrame.functionName || '';
  if (f.startsWith('(')) return f;
  const m = f.match(/^([a-z][\w]*(?:\.[a-z][\w]*)*)\.[A-Z]/); if (m) return m[1];
  if (n.callFrame.url) return n.callFrame.url.replace(/^.*\//, '');
  return '(otro)'; };
const add = (m, k, v) => m.set(k, (m.get(k) || 0) + v);
const self = new Map(), pk = new Map(), incl = new Map();
for (const [id, ms] of dt) { const n = byId.get(id); add(self, name(n), ms); add(pk, pkg(n), ms);
  const seen = new Set(); for (let x = id; x !== undefined; x = parent.get(x)) { const k = name(byId.get(x)); if (!seen.has(k)) { seen.add(k); add(incl, k, ms); } } }
const show = (title, m, k = TOP) => { console.log(`\n== ${title}`); for (const [a, b] of [...m].sort((x, y) => y[1] - x[1]).slice(0, k))
  console.log(`${(b / 1000).toFixed(1).padStart(8)} s ${(100 * b / total).toFixed(1).padStart(5)}%  ${a.slice(0, 200)}`); };
console.log(`total muestreado ${(total / 1000).toFixed(1)} s, muestras ${p.samples.length}, duración ${((p.endTime - p.startTime) / 1e6).toFixed(1)} s`);
show('self-time por función', self);
show('self-time por paquete/archivo', pk, 30);
show('tiempo inclusivo por función', incl, TOP);
// Por ventana de 30 s: top-3 self.
const W = 30e6; const win = new Map(); let ts = p.startTime;
for (let i = 0; i < p.samples.length; i++) { ts += p.timeDeltas[i]; const w = Math.floor((ts - p.startTime) / W);
  const d = (p.timeDeltas[i + 1] ?? 0) / 1000; if (!win.has(w)) win.set(w, new Map()); add(win.get(w), name(byId.get(p.samples[i])), d); }
console.log('\n== top-4 self por ventana de 30 s');
for (const [w, m] of [...win].sort((a, b) => a[0] - b[0])) console.log(`${w * 30}-${w * 30 + 30}s: ` +
  [...m].sort((x, y) => y[1] - x[1]).slice(0, 4).map(([a, b]) => `${a.slice(0, 70)}=${(b / 1000).toFixed(1)}s`).join(' | '));
