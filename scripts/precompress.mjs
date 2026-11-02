#!/usr/bin/env node
// Ronda 87: deja un <archivo>.br al lado de cada wasm grande y de cada pack por hash (los sirve dev-server/server.mjs con Content-Encoding: br).
// Los .br son derivados: viven donde viven sus originales (build/ y port/target/, ignorados por git) y nunca se versionan.
//   node scripts/precompress.mjs [--packs build/real-browser/packs] [--web port/target/web] [--quality 9] [--min-ratio 0.9]
// Se salta lo que ya tiene un .br más nuevo que el original, y no guarda un .br que no baje de min-ratio (p. ej. Tiles2x: PNG ya comprimido).
import { readdirSync, statSync, readFileSync, writeFileSync, renameSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { brotliCompressSync, constants } from 'node:zlib';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i += 2) A[argv[i].replace(/^--/, '')] = argv[i + 1];
const packs = A.packs || 'build/real-browser/packs', web = A.web || 'port/target/web';
const quality = +(A.quality || 9), minRatio = +(A['min-ratio'] || 0.9);

const walk = (dir, out = []) => { for (const e of readdirSync(dir, { withFileTypes: true })) { const f = path.join(dir, e.name); if (e.isDirectory()) walk(f, out); else out.push(f); } return out; };
const targets = [];
if (existsSync(packs)) {
  const index = JSON.parse(readFileSync(path.join(packs, 'index.json'), 'utf8'));
  for (const p of index.packs) targets.push(path.join(packs, p.name)); // sólo los del índice vigente
}
if (existsSync(web)) for (const f of walk(web)) if (f.endsWith('.wasm') && statSync(f).size > 1 << 20) targets.push(f);

let before = 0, after = 0;
for (const f of targets) {
  const size = statSync(f).size, out = `${f}.br`;
  if (existsSync(out) && statSync(out).mtimeMs >= statSync(f).mtimeMs) { console.log(`ok     ${path.basename(f).slice(0, 14).padEnd(14)} ya precomprimido`); continue; }
  const t0 = Date.now(), raw = readFileSync(f);
  const br = brotliCompressSync(raw, { params: { [constants.BROTLI_PARAM_QUALITY]: quality, [constants.BROTLI_PARAM_LGWIN]: 24, [constants.BROTLI_PARAM_SIZE_HINT]: raw.length } });
  const ratio = br.length / raw.length;
  if (ratio > minRatio) { rmSync(out, { force: true }); console.log(`salto  ${path.basename(f).slice(0, 14).padEnd(14)} ${(size / 1e6).toFixed(1)} MB ratio ${ratio.toFixed(2)} (no comprime)`); continue; }
  writeFileSync(`${out}.tmp`, br); renameSync(`${out}.tmp`, out);
  before += size; after += br.length;
  console.log(`br     ${path.basename(f).slice(0, 14).padEnd(14)} ${(size / 1e6).toFixed(1)} -> ${(br.length / 1e6).toFixed(1)} MB (${Date.now() - t0} ms)`);
}
console.log(`total comprimido: ${(before / 1e6).toFixed(1)} -> ${(after / 1e6).toFixed(1)} MB`);
