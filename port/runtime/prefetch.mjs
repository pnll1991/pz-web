// Round 84: over the public tunnel every synchronous /media read costs a round trip (~0.5 s) and a first load makes ~2000 of them.
// The dev server remembers the ranges real sessions asked for (/warm-media.json); this fetches them in parallel BEFORE the game starts
// and parks the bytes in vfs.prefetched, where fetchRange()/bundled() find them. Anything missing still falls back to the normal read.
export async function prefetchMedia(vfs, { fetchImpl = globalThis.fetch.bind(globalThis), concurrency = 16, limitBytes = 300 << 20, onProgress = () => {} } = {}) {
  const response = await fetchImpl('/warm-media.json', { cache: 'no-store' });
  if (!response.ok) return { requested: 0, stored: 0, bytes: 0, failed: 0, ms: 0 };
  const list = await response.json(), t0 = Date.now();
  const stats = { requested: list.length, stored: 0, bytes: 0, failed: 0, ms: 0 };
  let next = 0;
  const worker = async () => {
    while (next < list.length && stats.bytes < limitBytes) {
      const [url, range] = list[next++];
      try {
        const r = await fetchImpl(url, { cache: 'no-store', headers: range ? { Range: range } : {} });
        if (r.status !== 200 && r.status !== 206) throw new Error(`HTTP ${r.status}`);
        const bytes = new Uint8Array(await r.arrayBuffer());
        vfs.prefetched.set(`${url}|${range}`, bytes); stats.stored++; stats.bytes += bytes.length;
      } catch { stats.failed++; }
      if (next % 50 === 0) onProgress(Math.min(1, next / list.length));
    }
  };
  await Promise.all(Array.from({ length: concurrency }, worker));
  stats.ms = Date.now() - t0; onProgress(1);
  return stats;
}
