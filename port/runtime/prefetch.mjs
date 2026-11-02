// Round 84: over the public tunnel every synchronous /media read costs a round trip (~0.5 s) and a first load makes ~2000 of them.
// The dev server remembers the ranges real sessions asked for (/warm-media.json); this fetches them in parallel BEFORE the game starts
// and parks the bytes in vfs.prefetched, where fetchRange()/bundled() find them. Anything missing still falls back to the normal read.
// Entries already in vfs.prefetched (restored from the persistent media cache, port/runtime/mediacache.mjs) are not fetched
// again; onFetched(url, range, bytes) sees every response that did come from the network so the cache can keep it.
export async function prefetchMedia(vfs, { fetchImpl = globalThis.fetch.bind(globalThis), concurrency = 16, limitBytes = 300 << 20, onProgress = () => {}, onFetched = () => {}, onEntry = null } = {}) {
  const response = await fetchImpl('/warm-media.json', { cache: 'no-store' });
  if (!response.ok) return { requested: 0, stored: 0, bytes: 0, failed: 0, cached: 0, ms: 0 };
  const list = await response.json(), t0 = Date.now();
  const stats = { requested: list.length, stored: 0, bytes: 0, failed: 0, cached: 0, ms: 0, lazy: 0, lazyBytes: 0 };
  let next = 0;
  const worker = async () => {
    while (next < list.length && stats.bytes + stats.lazyBytes < limitBytes) {
      const [url, range] = list[next++];
      // Ronda 89: los tramos de un pack perezoso los reconoce y verifica lazypack.mjs (onEntry); no pasan por vfs.prefetched.
      const claimed = onEntry?.(url, range);
      if (claimed) { try { const n = await claimed; if (n) { stats.lazy++; stats.lazyBytes += n; } else stats.cached++; } catch { stats.failed++; } continue; }
      if (vfs.prefetched.has(`${url}|${range}`)) { stats.cached++; continue; }
      try {
        const r = await fetchImpl(url, { cache: 'no-store', headers: range ? { Range: range } : {} });
        if (r.status !== 200 && r.status !== 206) throw new Error(`HTTP ${r.status}`);
        const bytes = new Uint8Array(await r.arrayBuffer());
        vfs.prefetched.set(`${url}|${range}`, bytes); stats.stored++; stats.bytes += bytes.length;
        try { onFetched(url, range, bytes); } catch { /* the cache is optional */ }
      } catch { stats.failed++; }
      if (next % 50 === 0) onProgress(Math.min(1, next / list.length));
    }
  };
  await Promise.all(Array.from({ length: concurrency }, worker));
  stats.ms = Date.now() - t0; onProgress(1);
  return stats;
}
