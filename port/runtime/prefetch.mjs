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
// /media-ranges solo sirve /media/ y /media-bundle. El resto del warm-list (los tramos `/packs/<sha>.bin` de los packs perezosos, #27) va
// siempre por el camino de a uno, y ahí pasa primero por onEntry (lazypack.mjs), que los trae como tramos verificados. Los dos caminos corren
// a la vez y comparten limitBytes (bytes de vfs.prefetched + bytes de tramos perezosos).
// Ronda 90: si el VFS usa tramos sin Range (vfs.remoteSlices), una entrada `/media/…?r=a-b` + `bytes=a-(b-1)` se pide igual que la
// pediría fetchRange: sin Range, para que llegue comprimida. La clave `${url}|${range}` no cambia (la caché de media sigue valiendo).
export function sliceEntry(url, range) {
  const q = /^\/media\/[^?]*\?(?:.*&)?r=(\d+)-(\d+)(?:&|$)/.exec(url), m = /^bytes=(\d+)-(\d+)$/.exec(range || '');
  return !!(q && m && q[1] === m[1] && Number(q[2]) === Number(m[2]) + 1);
}
const BATCH_SIZES = [32, 64, 128]; // el resto va de a 128; el servidor además corta cada respuesta a ~24 MiB

function parseBatch(buffer) {
  const view = new DataView(buffer), headerLength = view.getUint32(0, true);
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 4, headerLength)));
  if (!Array.isArray(header.entries) || !Number.isInteger(header.served)) throw new Error('bad /media-ranges header');
  return { header, offset: 4 + headerLength };
}

const batchable = url => { const path = String(url).split('?')[0]; return path.startsWith('/media/') || path === '/media-bundle'; };

export async function prefetchMedia(vfs, { fetchImpl = globalThis.fetch.bind(globalThis), concurrency = 16, limitBytes = 300 << 20, onProgress = () => {}, onFetched = () => {},
  onEntry = null, batch = true, batchConcurrency = 4 } = {}) {
  const t0 = Date.now();
  const response = await fetchImpl('/warm-media.json', { cache: 'no-store' });
  if (!response.ok) return { requested: 0, stored: 0, bytes: 0, failed: 0, cached: 0, ms: 0, requests: 1, batches: 0, mode: 'none' };
  const list = await response.json();
  const stats = { requested: list.length, stored: 0, bytes: 0, failed: 0, cached: 0, ms: 0, requests: 1, batches: 0, mode: batch ? 'batch' : 'single', lazy: 0, lazyBytes: 0 };
  const underLimit = () => stats.bytes + stats.lazyBytes < limitBytes;
  const pending = [], direct = []; // direct: lo que no va por /media-ranges (tramos perezosos de /packs/ y cualquier otra ruta)
  list.forEach(([url, range], index) => {
    if (batch && !batchable(url)) direct.push(index);
    else if (vfs.prefetched.has(`${url}|${range}`)) stats.cached++;
    else pending.push(index);
  });
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
      while (next < indices.length && underLimit()) {
        const [url, range] = list[indices[next++]];
        // Ronda 89: los tramos de un pack perezoso los reconoce y verifica lazypack.mjs (onEntry); no pasan por vfs.prefetched.
        const claimed = onEntry?.(url, range);
        if (claimed) {
          try { const n = await claimed; if (n) { stats.lazy++; stats.lazyBytes += n; done++; } else stats.cached++; } catch { stats.failed++; done++; }
          continue;
        }
        if (vfs.prefetched.has(`${url}|${range}`)) { stats.cached++; continue; }
        try {
          const slice = vfs.remoteSlices && sliceEntry(url, range);
          const r = await fetchImpl(url, { cache: 'no-store', headers: range && !slice ? { Range: range } : {} });
          stats.requests++;
          if (slice ? r.status !== 200 : r.status !== 200 && r.status !== 206) throw new Error(`HTTP ${r.status}`);
          const bytes = new Uint8Array(await r.arrayBuffer());
          if (slice) { const m = /^bytes=(\d+)-(\d+)$/.exec(range); if (bytes.length !== m[2] - m[1] + 1) throw new Error('short slice'); }
          keep(url, range, bytes);
        } catch { stats.failed++; }
        done++;
        if (done % 50 === 0) progress();
      }
    };
    await Promise.all(Array.from({ length: concurrency }, worker));
  };

  if (!batch) await single(pending);
  else await Promise.all([single(direct), (async () => {
    const queue = []; // lotes de índices del warm-list, en orden de uso
    for (let at = 0, n = 0; at < pending.length; n++) {
      const size = BATCH_SIZES[Math.min(n, BATCH_SIZES.length - 1)];
      queue.push(pending.slice(at, at + size)); at += size;
    }
    const leftover = []; // trabajo que el camino por lotes no pudo hacer
    let broken = false;
    const worker = async () => {
      while (!broken && queue.length && underLimit()) {
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
  })()]);
  stats.ms = Date.now() - t0; onProgress(1);
  return stats;
}
