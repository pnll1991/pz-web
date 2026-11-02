// Caché persistente de las lecturas de /media que no están en los packs (lua, scripts, texturas sueltas, lotheaders…).
// Sin ella cada recarga repite ~2150 XHR síncronos con `no-store` (vfs.fetchRange/bundled) y la precarga del warm-list
// vuelve a bajar lo mismo. La copia de PZ_HOME es fija, así que esos bytes no cambian entre recargas.
//
// Cómo funciona:
// - Cada respuesta que la VFS o la precarga traen de la red se guarda en la Cache API con la misma clave que usa
//   vfs.prefetched (`${url}|${Range}`), en segundo plano y sin bloquear la lectura síncrona.
// - En el arranque siguiente, antes del wasm, restore() mete esas entradas en vfs.prefetched: fetchRange()/bundled()
//   las encuentran ahí y no salen a la red. Lo que falte se lee como siempre (y se guarda para la próxima).
// - Versión = `version` de /media-manifest.json (nombres + tamaños + mtimes de PZ_HOME/media), o el SHA-256 del manifest
//   si el dev server todavía no la manda. Si cambia, se borra la caché entera antes de usarla.
// - fetchRange() exige el largo exacto del rango y bundled() valida cada archivo contra el tamaño del manifest, así que
//   una entrada inválida nunca llega al juego: se descarta y se lee de la red.
// ?nocache=1 o ?mediacache=0 la apagan (camino original puro).
const CACHE = 'pz-web-media-v1';
const VERSION_PATH = '/__pz-media-cache/version';
const ENTRY_PATH = '/__pz-media-cache/e';
const base = () => globalThis.location?.href ?? 'http://127.0.0.1/';
const entryUrl = (url, range) => new URL(`${ENTRY_PATH}?u=${encodeURIComponent(url)}&r=${encodeURIComponent(range)}`, base()).href;
const hex = buffer => [...new Uint8Array(buffer)].map(v => v.toString(16).padStart(2, '0')).join('');

/** Versión de la caché a partir del manifest: su campo `version`, o el SHA-256 de sus bytes. */
export async function mediaCacheVersion(manifestBytes, listed) {
  if (typeof listed?.version === 'string' && listed.version) return listed.version;
  return `sha256:${hex(await crypto.subtle.digest('SHA-256', manifestBytes))}`;
}

export class MediaCache {
  /** mode: 0 apagada, 1 activa. maxBytes acota lo guardado y lo que restore() deja en memoria. */
  constructor({ mode = 1, maxBytes = 320 << 20, maxPendingBytes = 64 << 20, cachesImpl = globalThis.caches } = {}) {
    this.mode = cachesImpl ? mode : 0; this.cachesImpl = cachesImpl; this.cache = null; this.version = '';
    this.maxBytes = maxBytes; this.maxPendingBytes = maxPendingBytes; this.pendingBytes = 0;
    this.known = new Set(); this.writes = Promise.resolve();
    this.stats = { restored:0, restoredBytes:0, restoreMs:0, stored:0, storedBytes:0, dropped:0, writeErrors:0,
      skippedRestore:0, cleared:0, lastError:null };
  }

  static fromLocation(search, options = {}) {
    const q = new URLSearchParams(search);
    const mode = q.get('nocache') === '1' || q.get('mediacache') === '0' ? 0 : 1;
    return new MediaCache({ ...options, mode });
  }

  async open(version) {
    if (this.mode === 0) return this;
    if (!version) throw new Error('mediacache: falta la versión del manifest');
    this.version = version;
    let cache = await this.cachesImpl.open(CACHE);
    const stored = await cache.match(new URL(VERSION_PATH, base()).href);
    const previous = stored ? await stored.text() : null;
    if (previous !== version) {
      // Otra copia de PZ_HOME (o primera vez): nada de lo guardado vale.
      if (previous !== null) { await this.cachesImpl.delete(CACHE); cache = await this.cachesImpl.open(CACHE); this.stats.cleared = 1; }
      await cache.put(new URL(VERSION_PATH, base()).href, new Response(version));
    }
    this.cache = cache;
    return this;
  }

  /** Copia lo guardado a vfs.prefetched (orden de primer uso), hasta maxBytes. Nunca pisa una entrada ya presente. */
  async restore(vfs, { concurrency = 8 } = {}) {
    if (!this.cache) return this.stats;
    const t0 = performance.now();
    const requests = (await this.cache.keys()).filter(request => new URL(request.url).pathname === ENTRY_PATH);
    let next = 0, budget = this.maxBytes;
    const worker = async () => {
      while (next < requests.length) {
        const request = requests[next++], params = new URL(request.url).searchParams;
        const url = params.get('u'), range = params.get('r') ?? '', key = `${url}|${range}`;
        if (!url) continue;
        this.known.add(key);
        if (budget <= 0 || vfs.prefetched.has(key)) { this.stats.skippedRestore++; continue; }
        try {
          const response = await this.cache.match(request);
          if (!response) continue;
          const bytes = new Uint8Array(await response.arrayBuffer());
          if (budget < bytes.length) { this.stats.skippedRestore++; continue; }
          budget -= bytes.length;
          vfs.prefetched.set(key, bytes); this.stats.restored++; this.stats.restoredBytes += bytes.length;
        } catch (error) { this.stats.lastError = String(error); }
      }
    };
    await Promise.all(Array.from({ length: concurrency }, worker));
    this.stats.restoreMs = Math.round(performance.now() - t0);
    return this.stats;
  }

  /** Guarda en segundo plano una respuesta traída de la red. Copia ya: la vista puede ser compartida o mutar. */
  record(url, range = '', bytes) {
    if (!this.cache || this.mode !== 1 || !(bytes instanceof Uint8Array)) return false;
    const key = `${url}|${range}`;
    if (this.known.has(key)) return false;
    if (this.stats.restoredBytes + this.stats.storedBytes + this.pendingBytes + bytes.length > this.maxBytes ||
        this.pendingBytes + bytes.length > this.maxPendingBytes) { this.stats.dropped++; return false; }
    this.known.add(key);
    const copy = bytes.slice(); this.pendingBytes += copy.length;
    this.writes = this.writes.then(async () => {
      try {
        await this.cache.put(entryUrl(url, range), new Response(copy, { headers:{ 'Content-Type':'application/octet-stream' } }));
        this.stats.stored++; this.stats.storedBytes += copy.length;
      } catch (error) {
        // Cuota llena u otro error: se deja de guardar esa entrada, la lectura ya se hizo por la red.
        this.known.delete(key); this.stats.writeErrors++; this.stats.lastError = String(error);
      } finally { this.pendingBytes -= copy.length; }
    });
    return true;
  }

  flush() { return this.writes; }
}
