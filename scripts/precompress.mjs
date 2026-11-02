#!/usr/bin/env node
// Ronda 87/89: deja un <archivo>.br (y un .gz para los navegadores sin brotli) al lado de cada wasm grande y de cada pack por hash
// (los sirve dev-server/server.mjs con Content-Encoding). Los .br/.gz son derivados: viven donde viven sus originales (build/ y port/target/, ignorados por git) y nunca se versionan.
//   node scripts/precompress.mjs [--packs build/real-browser/packs] [--web port/target/web] [--quality 9] [--min-ratio 0.9] [--gzip 1|0]
// Se salta lo que ya tiene sus derivados más nuevos que el original, y no guarda uno que no baje de min-ratio (p. ej. Tiles2x: PNG ya comprimido).
import { readdirSync, statSync, readFileSync, writeFileSync, renameSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { brotliCompressSync, gzipSync, constants } from 'node:zlib';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i += 2) A[argv[i].replace(/^--/, '')] = argv[i + 1];
const packs = A.packs || 'build/real-browser/packs', web = A.web || 'port/target/web';
const quality = +(A.quality || 9), minRatio = +(A['min-ratio'] || 0.9), withGzip = (A.gzip ?? '1') !== '0';

const walk = (dir, out = []) => { for (const e of readdirSync(dir, { withFileTypes: true })) { const f = path.join(dir, e.name); if (e.isDirectory()) walk(f, out); else out.push(f); } return out; };
const targets = [];
if (existsSync(packs)) {
  const index = JSON.parse(readFileSync(path.join(packs, 'index.json'), 'utf8'));
  for (const p of index.packs) targets.push(path.join(packs, p.name)); // sólo los del índice vigente
}
if (existsSync(web)) for (const f of walk(web)) if (f.endsWith('.wasm') && statSync(f).size > 1 << 20) targets.push(f);

let before = 0, after = 0;
const fresh = (out, f) => existsSync(out) && statSync(out).mtimeMs >= statSync(f).mtimeMs;
const save = (out, bytes) => { writeFileSync(`${out}.tmp`, bytes); renameSync(`${out}.tmp`, out); };
for (const f of targets) {
  const size = statSync(f).size, name = path.basename(f).slice(0, 14).padEnd(14), br = `${f}.br`, gz = `${f}.gz`;
  const t0 = Date.now(), brFresh = fresh(br, f);
  if (brFresh && (!withGzip || fresh(gz, f))) { console.log(`ok     ${name} ya precomprimido`); continue; }
  const raw = readFileSync(f);
  // Muestra de 4 MiB del medio (a calidad 5): si ni así baja, el archivo (PNG ya comprimido, como Tiles2x) se salta sin pasar por el compresor lento.
  const sample = raw.subarray(Math.max(0, (raw.length >> 1) - (2 << 20)), (raw.length >> 1) + (2 << 20));
  if (!brFresh && sample.length > 1 << 20 && brotliCompressSync(sample, { params: { [constants.BROTLI_PARAM_QUALITY]: 5 } }).length / sample.length > Math.max(minRatio, 0.97)) {
    rmSync(br, { force: true }); rmSync(gz, { force: true }); console.log(`salto  ${name} ${(size / 1e6).toFixed(1)} MB (la muestra no comprime)`); continue;
  }
  if (brFresh) { // el .br ya estaba; solo falta el .gz (gzip es rápido)
    const g = gzipSync(raw, { level: 9 });
    if (g.length < size * minRatio) { save(gz, g); console.log(`gz     ${name} ${(size / 1e6).toFixed(1)} -> ${(g.length / 1e6).toFixed(1)} MB (${Date.now() - t0} ms)`); }
    continue;
  }
  const packed = brotliCompressSync(raw, { params: { [constants.BROTLI_PARAM_QUALITY]: quality, [constants.BROTLI_PARAM_LGWIN]: 24, [constants.BROTLI_PARAM_SIZE_HINT]: raw.length } });
  const ratio = packed.length / raw.length;
  if (ratio > minRatio) { rmSync(br, { force: true }); rmSync(gz, { force: true }); console.log(`salto  ${name} ${(size / 1e6).toFixed(1)} MB ratio ${ratio.toFixed(2)} (no comprime)`); continue; }
  save(br, packed);
  let gzNote = '';
  if (withGzip) { const g = gzipSync(raw, { level: 9 }); if (g.length < size * minRatio) { save(gz, g); gzNote = `, gz ${(g.length / 1e6).toFixed(1)} MB`; } else rmSync(gz, { force: true }); }
  before += size; after += packed.length;
  console.log(`br     ${name} ${(size / 1e6).toFixed(1)} -> ${(packed.length / 1e6).toFixed(1)} MB${gzNote} (${Date.now() - t0} ms)`);
}
console.log(`total comprimido (br): ${(before / 1e6).toFixed(1)} -> ${(after / 1e6).toFixed(1)} MB`);
