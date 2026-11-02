// Ronda 89: compresión de las respuestas de texto (JS, JSON, HTML, el manifest de media…) con br/gzip según el Accept-Encoding del navegador.
// Lo pesado (wasm y packs) se sirve precomprimido desde disco (scripts/precompress.mjs); esto cubre lo que se genera en memoria o es chico.
// Cada variante se comprime una sola vez y se guarda (acotado en bytes), porque estos archivos casi nunca cambian.
import { brotliCompress, gzip, constants } from 'node:zlib';
import { promisify } from 'node:util';

const brotli = promisify(brotliCompress), deflate = promisify(gzip);

/** Codings aceptados por el navegador, de mayor a menor preferencia (q=0 los descarta). Solo br y gzip. */
export function acceptedCodings(header = '') {
  const q = new Map();
  for (const part of String(header).split(',')) {
    const [name, ...params] = part.trim().toLowerCase().split(';');
    if (!name) continue;
    const weight = params.map(p => /^\s*q\s*=\s*([0-9.]+)/.exec(p)).find(Boolean);
    q.set(name, weight ? Number(weight[1]) : 1);
  }
  const wanted = name => q.has(name) ? q.get(name) : (q.get('*') ?? 0);
  return ['br', 'gzip'].filter(name => wanted(name) > 0).sort((a, b) => wanted(b) - wanted(a) || (a === 'br' ? -1 : 1));
}

export const MIN_COMPRESS_BYTES = 1024;

export function createCompressor({ maxBytes = 64 << 20 } = {}) {
  const cache = new Map(); let held = 0;
  return {
    /** Cuerpo comprimido con `coding` (cacheado por `key`), o null si no vale la pena (chico o no baja). */
    async get(key, coding, body) {
      if (body.length < MIN_COMPRESS_BYTES) return null;
      const id = `${key}|${coding}`, hit = cache.get(id);
      if (hit !== undefined) { cache.delete(id); cache.set(id, hit); return hit; }
      const packed = coding === 'br'
        ? await brotli(body, { params: { [constants.BROTLI_PARAM_QUALITY]: 5, [constants.BROTLI_PARAM_SIZE_HINT]: body.length } })
        : await deflate(body, { level: 6 });
      const result = packed.length < body.length ? packed : null;
      cache.set(id, result); held += result?.length ?? 0;
      for (const [k, v] of cache) { if (held <= maxBytes) break; cache.delete(k); held -= v?.length ?? 0; }
      return result;
    },
    stats: () => ({ entries: cache.size, bytes: held }),
  };
}
