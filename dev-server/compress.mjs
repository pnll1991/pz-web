// Ronda 89: compresión de las respuestas de texto (JS, JSON, HTML, el manifest de media…) con br/gzip según el Accept-Encoding del navegador.
// Lo pesado (wasm y packs) se sirve precomprimido desde disco (scripts/precompress.mjs); esto cubre lo que se genera en memoria o es chico.
// Cada variante se comprime una sola vez y se guarda (acotado en bytes), porque estos archivos casi nunca cambian.
// Ronda 90: también los tramos de /media (?r=a-b sin Range, ver server.mjs) con un compresor propio más grande y `minSaving`.
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

/** `maxBytes`: tope de lo guardado (comprimido). `minSaving`: fracción mínima que tiene que bajar (0.1 = al menos 10 %) para usarlo.
 *  `maxEntries` acota también las entradas "no vale la pena" (null), que no ocupan bytes pero sí claves. */
export function createCompressor({ maxBytes = 64 << 20, minSaving = 0, maxEntries = 50000 } = {}) {
  const cache = new Map(), pending = new Map(); let held = 0;
  const counters = { hits: 0, misses: 0, skipped: 0, rawBytes: 0, packedBytes: 0 };
  const evict = () => {
    for (const [k, v] of cache) { if (held <= maxBytes && cache.size <= maxEntries) break; cache.delete(k); held -= v?.length ?? 0; }
  };
  const compress = async (coding, body) => coding === 'br'
    ? brotli(body, { params: { [constants.BROTLI_PARAM_QUALITY]: 5, [constants.BROTLI_PARAM_SIZE_HINT]: body.length } })
    : deflate(body, { level: 6 });
  return {
    /** Cuerpo comprimido con `coding` (cacheado por `key`), o null si no vale la pena (chico o no baja lo suficiente).
     *  `body` puede ser una función async que lo devuelve: solo se llama si no está en la caché. Pedidos simultáneos de la misma
     *  clave comparten una sola compresión. */
    async get(key, coding, body, size = typeof body === 'function' ? Infinity : body.length) {
      if (size < MIN_COMPRESS_BYTES) return null;
      const id = `${key}|${coding}`, hit = cache.get(id);
      if (hit !== undefined) { cache.delete(id); cache.set(id, hit); counters.hits++; return hit; }
      if (pending.has(id)) return pending.get(id);
      const work = (async () => {
        const bytes = typeof body === 'function' ? await body() : body;
        if (bytes.length < MIN_COMPRESS_BYTES) return null;
        const packed = await compress(coding, bytes);
        const result = packed.length <= bytes.length * (1 - minSaving) && packed.length < bytes.length ? packed : null;
        counters.misses++; if (!result) counters.skipped++; counters.rawBytes += bytes.length; counters.packedBytes += (result ?? bytes).length;
        cache.set(id, result); held += result?.length ?? 0; evict();
        return result;
      })();
      pending.set(id, work);
      try { return await work; } finally { pending.delete(id); }
    },
    stats: () => ({ entries: cache.size, bytes: held, ...counters }),
  };
}
