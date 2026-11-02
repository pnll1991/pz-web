// Round 84: over the public tunnel every synchronous /media read costs a round trip (~0.5 s) and a first load makes ~2000 of them.
// The dev server remembers the ranges real sessions asked for (/warm-media.json); this fetches them BEFORE the game needs them
// and parks the bytes in vfs.prefetched, where fetchRange()/bundled() find them. Anything missing still falls back to the normal read.
// Entries already in vfs.prefetched (restored from the persistent media cache, port/runtime/mediacache.mjs) are not fetched
// again; onFetched(url, range, bytes) sees every response that did come from the network so the cache can keep it.
//
// Ronda 89 (frente B): en vez de ~4700 fetch sueltos, se piden lotes a /media-ranges?i=<índices del warm-list> (dev-server/media-ranges.mjs):
// una respuesta trae muchas entradas con su `url|Range` exacto. Los lotes salen en el orden del warm-list (orden de uso), los primeros
// chicos para que lo que el juego lee primero llegue antes, y varios van en paralelo. Si el servidor no tiene /media-ranges (un dev
// server viejo) o un lote viene mal, ese trabajo cae al camino de un fetch por rango. `batch: false` (?prefetchbatch=0) fuerza el viejo.
const BATCH_SIZES = [32, 64, 128]; // el resto va de a 128; el servidor además corta cada respuesta a ~24 MiB

function parseBatch(buffer) {
  const view = new DataView(buffer), headerLength = view.getUint32(0, true);
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 4, headerLength)));
  if (!Array.isArray(header.entries) || !Number.isInteger(header.served)) throw new Error('bad /media-ranges header');
  return { header, offset: 4 + headerLength };
}

export async function prefetchMedia(vfs, { fetchImpl = globalThis.fetch.bind(globalThis), concurrency = 16, limitBytes = 300 << 20, onProgress = () => {}, onFetched = () => {},
  batch = true, batchConcurrency = 4 } = {}) {
  const t0 = Date.now();
  const response = await fetchImpl('/warm-media.json', { cache: 'no-store' });
  if (!response.ok) return { requested: 0, stored: 0, bytes: 0, failed: 0, cached: 0, ms: 0, requests: 1, batches: 0, mode: 'none' };
  const list = await response.json();
  const stats = { requested: list.length, stored: 0, bytes: 0, failed: 0, cached: 0, ms: 0, requests: 1, batches: 0, mode: batch ? 'batch' : 'single' };
  const pending = [];
  list.forEach(([url, range], index) => { if (vfs.prefetched.has(`${url}|${range}`)) stats.cached++; else pending.push(index); });
  let done = 0;
  const progress = () => onProgress(Math.min(1, (stats.cached + done) / Math.max(1, list.length)));
  const keep = (url, range, bytes) => {
    vfs.prefetched.set(`${url}|${range}`, bytes); stats.stored++; stats.bytes += bytes.length;
    try { onFetched(url, range, bytes); } catch { /* the cache is optional */ }
  };

  // Camino original: un fetch por rango.
  const single = async indices => {
    let next = 0;
    const worker = async () => {
      while (next < indices.length && stats.bytes < limitBytes) {
        const [url, range] = list[indices[next++]];
        if (vfs.prefetched.has(`${url}|${range}`)) { stats.cached++; continue; }
        try {
          const r = await fetchImpl(url, { cache: 'no-store', headers: range ? { Range: range } : {} });
          stats.requests++;
          if (r.status !== 200 && r.status !== 206) throw new Error(`HTTP ${r.status}`);
          keep(url, range, new Uint8Array(await r.arrayBuffer()));
        } catch { stats.failed++; }
        done++;
        if (done % 50 === 0) progress();
      }
    };
    await Promise.all(Array.from({ length: concurrency }, worker));
  };

  if (!batch) await single(pending);
  else {
    const queue = []; // lotes de índices del warm-list, en orden de uso
    for (let at = 0, n = 0; at < pending.length; n++) {
      const size = BATCH_SIZES[Math.min(n, BATCH_SIZES.length - 1)];
      queue.push(pending.slice(at, at + size)); at += size;
    }
    const leftover = []; // trabajo que el camino por lotes no pudo hacer
    let broken = false;
    const worker = async () => {
      while (!broken && queue.length && stats.bytes < limitBytes) {
        const indices = queue.shift();
        try {
          const r = await fetchImpl(`/media-ranges?i=${indices.join(',')}`, { cache: 'no-store' });
          stats.requests++;
          if (r.status !== 200) throw new Error(`HTTP ${r.status}`);
          const buffer = await r.arrayBuffer(), { header, offset } = parseBatch(buffer);
          if (header.served < 1 || header.served > indices.length || header.entries.length !== header.served) throw new Error('bad /media-ranges batch');
          let at = offset;
          header.entries.forEach(([url, range, size], k) => {
            const [wantUrl, wantRange] = list[indices[k]];
            if (size < 0) { stats.failed++; return; }
            if (url !== wantUrl || range !== wantRange || at + size > buffer.byteLength) { stats.failed++; at += Math.max(0, size); return; }
            keep(url, range, new Uint8Array(buffer.slice(at, at + size))); at += size; // copia: nunca una vista del lote entero
          });
          done += header.served; stats.batches++;
          if (header.served < indices.length) queue.unshift(indices.slice(header.served)); // el servidor cortó por bytes: el resto va primero
          progress();
        } catch {
          broken = true; leftover.push(indices); // dev server sin /media-ranges o respuesta inválida: sigue el camino viejo
        }
      }
    };
    await Promise.all(Array.from({ length: batchConcurrency }, worker));
    if (broken) { stats.mode = 'mixed'; await single([...leftover.flat(), ...queue.flat()]); }
  }
  stats.ms = Date.now() - t0; onProgress(1);
  return stats;
}
