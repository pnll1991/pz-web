#!/usr/bin/env node
// Ronda 89: convierte en "perezosos" los packs enormes de texturas del índice (Tiles2x.pack, 321 MB) para que el navegador no los baje enteros.
// El juego lee las cabeceras de las 369 páginas del .pack (nombre + rectángulos) saltando el PNG de cada una, y lee un PNG solo cuando usa una
// de sus texturas. Este script parte el archivo en partes contiguas:
//   - cabeceras (sin sha256): se juntan en un blob chico nuevo `<sha256>.bin` que se baja como un pack normal;
//   - PNG (con sha256): se piden con Range al blob grande, uno por vez, y se verifican contra ese sha256.
// y reescribe `index.json` (copia previa en index.json.before-lazy): el pack grande lleva `lazy: true`, el archivo lleva `lazy: { head, parts }`.
// Es idempotente (un índice ya perezoso no se toca). Todo queda en build/ (ignorado por git); nada de esto se versiona.
//   node scripts/lazy-packs.mjs [--packs build/real-browser/packs] [--min-bytes 67108864]
//   node scripts/lazy-packs.mjs --undo     (vuelve al índice anterior)
import { openSync, readSync, closeSync, fstatSync, readFileSync, writeFileSync, renameSync, copyFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Recorre un .pack PZPK sin leer los PNG: devuelve [{ name, entries, headerStart, pngStart, pngLength }] y el fin; lanza si el formato no cierra. */
export function parsePzpk(fd, fileSize) {
  let position = 0;
  const window = Buffer.alloc(1 << 16); let windowStart = -1, windowEnd = -1;
  const read = count => {
    if (count > window.length) throw new Error('pzpk: lectura demasiado grande');
    if (position < windowStart || position + count > windowEnd) {
      const got = readSync(fd, window, 0, window.length, position);
      windowStart = position; windowEnd = position + got;
      if (got < count) throw new Error('pzpk: fin de archivo inesperado');
    }
    const out = window.subarray(position - windowStart, position - windowStart + count); position += count; return out;
  };
  const int = () => read(4).readInt32LE(0);
  const string = () => { const length = int(); if (length < 0 || length > 4096) throw new Error('pzpk: cadena invalida'); return read(length).toString('utf8'); };
  if (read(4).toString('latin1') !== 'PZPK') throw new Error('pzpk: firma invalida');
  const version = int(), pageCount = int();
  if (version !== 1 || pageCount <= 0 || pageCount > 100000) throw new Error('pzpk: version o paginas invalidas');
  const pages = [];
  for (let page = 0; page < pageCount; page++) {
    const headerStart = position, name = string(), entries = int(); int(); // flags de la página
    if (entries < 0 || entries > 1e6) throw new Error('pzpk: entradas invalidas');
    for (let entry = 0; entry < entries; entry++) { string(); read(32); }
    const pngLength = int();
    if (pngLength < 0 || position + pngLength > fileSize) throw new Error('pzpk: PNG fuera del archivo');
    pages.push({ name, entries, headerStart, pngStart: position, pngLength });
    position += pngLength;
  }
  return { pages, end: position };
}

const sha256Range = (fd, start, length) => {
  const hash = createHash('sha256'), buffer = Buffer.alloc(Math.min(length, 4 << 20)); let done = 0;
  while (done < length) { const got = readSync(fd, buffer, 0, Math.min(buffer.length, length - done), start + done); if (got <= 0) throw new Error('lectura corta'); hash.update(buffer.subarray(0, got)); done += got; }
  return hash.digest('hex');
};

/** Partes [at, length, sha256|null] que cubren el archivo, y los bytes de las cabeceras (en orden). */
export function buildParts(fd, pages, fileSize) {
  const parts = [], headChunks = []; let cursor = 0;
  const head = (start, end) => { if (end > start) { parts.push([start, end - start, null]); const b = Buffer.alloc(end - start); readSync(fd, b, 0, b.length, start); headChunks.push(b); } };
  for (const page of pages) {
    head(cursor, page.pngStart);
    if (page.pngLength > 0) parts.push([page.pngStart, page.pngLength, sha256Range(fd, page.pngStart, page.pngLength)]);
    cursor = page.pngStart + page.pngLength;
  }
  head(cursor, fileSize);
  return { parts, head: Buffer.concat(headChunks) };
}

export async function makeLazy(packsDir, { minBytes = 64 << 20 } = {}) {
  const indexPath = path.join(packsDir, 'index.json'), index = JSON.parse(readFileSync(indexPath, 'utf8'));
  const report = [];
  for (const pack of [...index.packs]) {
    if (pack.lazy || pack.bytes < minBytes) continue;
    const files = index.files.filter(file => file.pack === pack.name);
    if (files.length === 0) continue; // blob de cabeceras de un pack perezoso (o sin archivos): nada que partir
    if (files.length !== 1 || files[0].offset !== 0 || files[0].length !== pack.bytes || !files[0].path.endsWith('.pack')) { report.push(`salto  ${pack.name.slice(0, 12)} (no es un .pack entero)`); continue; }
    const fd = openSync(path.join(packsDir, pack.name), 'r');
    try {
      const { pages, end } = parsePzpk(fd, fstatSync(fd).size);
      if (end !== pack.bytes) throw new Error(`pzpk: el recorrido termina en ${end}, no en ${pack.bytes}`);
      const { parts, head } = buildParts(fd, pages, pack.bytes);
      const headSha = createHash('sha256').update(head).digest('hex'), headName = `${headSha}.bin`;
      if (!existsSync(path.join(packsDir, headName))) { writeFileSync(path.join(packsDir, `${headName}.tmp`), head); renameSync(path.join(packsDir, `${headName}.tmp`), path.join(packsDir, headName)); }
      if (!index.packs.some(other => other.name === headName)) index.packs.push({ name: headName, bytes: head.length, sha256: headSha });
      pack.lazy = true; files[0].lazy = { head: headName, parts };
      report.push(`lazy   ${files[0].path}: ${pages.length} paginas, cabeceras ${(head.length / 1e6).toFixed(2)} MB, PNG ${((pack.bytes - head.length) / 1e6).toFixed(1)} MB`);
    } finally { closeSync(fd); }
  }
  if (report.some(line => line.startsWith('lazy'))) {
    const backup = `${indexPath}.before-lazy`;
    if (!existsSync(backup)) copyFileSync(indexPath, backup);
    writeFileSync(`${indexPath}.tmp`, JSON.stringify(index)); renameSync(`${indexPath}.tmp`, indexPath);
  }
  return report;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const argv = process.argv.slice(2), A = {};
  for (let i = 0; i < argv.length; i++) { const k = argv[i].replace(/^--/, ''); A[k] = i + 1 < argv.length && !argv[i + 1].startsWith('--') ? argv[++i] : true; }
  const dir = A.packs || 'build/real-browser/packs';
  if (A.undo) {
    const backup = path.join(dir, 'index.json.before-lazy');
    if (!existsSync(backup)) throw new Error(`no hay ${backup}`);
    copyFileSync(backup, path.join(dir, 'index.json')); console.log('índice anterior restaurado');
  } else for (const line of await makeLazy(dir, { minBytes: +(A['min-bytes'] || 64 << 20) })) console.log(line);
}
