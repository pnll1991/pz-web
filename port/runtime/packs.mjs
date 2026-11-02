import { normalizePath, READ_ONLY_MOUNTABLE } from './vfs.mjs';

const hex = bytes => [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
const digest = async bytes => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)));

// Round 52: /Zomboid/options.ini is mounted from the pack as a read-only game-owned file.
// Everything else under /Zomboid is user data and must never come from an immutable blob.
const mountablePath = path => path.startsWith('/media/') || READ_ONLY_MOUNTABLE.has(path);
// Caching a 321 MB response in the Cache API risks a quota error on a small profile, and the
// dev server already serves it from disk with ETag/Range. Blobs above this size are fetched
// every boot and still verified against their sha256, so skipping the cache is honest and
// observable rather than a silent failure.
const CACHE_MAX_BYTES = 64 * 1024 * 1024;

// Round 81 (front 2): the per-file sha256 pass (~9.6k digests over ~500 MB) is a pure function of the index
// bytes and the pack bytes. Each pack is still verified in full on every boot; when the index is byte-identical
// (same sha256) to one whose per-file pass already succeeded in this browser, that pass is not repeated.
// '?nocache=1' (or { memo:false }) always repeats it. The first pass runs the digests concurrently.
const VERIFIED_KEY = 'pz-web-packs-verified-index';
const memoAllowed = () => typeof location === 'undefined' || new URLSearchParams(location.search).get('nocache') !== '1';
const readMemo = () => { try { return localStorage.getItem(VERIFIED_KEY); } catch { return null; } };
const writeMemo = value => { try { localStorage.setItem(VERIFIED_KEY, value); } catch { /* sin localStorage: se verifica siempre */ } };

/** Cache immutable local pack blobs and preload verified files into synchronous memory. */
export async function preloadPacks(vfs, indexUrl = '/packs/index.json', { memo = memoAllowed() } = {}) {
  const response = await fetch(indexUrl, { cache:'no-cache' });
  if (!response.ok) throw new Error(`Pack index HTTP ${response.status}`);
  const indexBytes = await response.arrayBuffer();
  const indexHash = await digest(indexBytes);
  const index = JSON.parse(new TextDecoder().decode(indexBytes));
  const skipFileDigests = memo && readMemo() === indexHash;
  const stats = globalThis.pzPackStats = { indexHash, fileDigests: skipFileDigests ? 'memo' : 'computed', t0: performance.now(), ms: 0 };
  if (index.schema_version !== 1 || !Array.isArray(index.packs) || !Array.isArray(index.files))
    throw new Error('Unsupported pack schema');
  if (index.packs.some(pack => !Number.isSafeInteger(pack.bytes) || pack.bytes < 0) ||
      new Set(index.packs.map(pack => pack.name)).size !== index.packs.length)
    throw new Error('Invalid pack metadata');
  for (const file of index.files) {
    if (normalizePath(file.path) !== file.path || !mountablePath(file.path))
      throw new Error('Invalid mounted resource path');
  }
  if (index.packs.reduce((sum, pack) => sum + pack.bytes, 0) > vfs.maxBytes)
    throw new Error('Pack preload exceeds VFS memory budget');
  const base = new URL(indexUrl, location.href);
  const cache = await caches.open('pz-web-packs-v1');
  const pending = [];
  // Round 80 (front H): several paths may alias one pack range (a clip listed in media/anims_X/Bob and
  // read from media/anims/bob): it is verified once and counted once, as the VFS does for its views.
  const verifiedRanges = new Map();
  const inFlight = { set:new Set(), bytes:0 };
  // Ronda 88: los packs se descargaban (o leian de la Cache API), se hasheaban y se guardaban uno tras otro. Son
  // independientes: todos a la vez. Cada pack sigue verificandose completo (tamano + sha256) antes de montarse y un
  // pack corrupto sigue borrandose de la cache; solo cambia que las esperas de red/disco/hash se solapan.
  const loadPack = async pack => {
    if (!/^[a-f0-9]{64}\.bin$/.test(pack.name) || pack.name !== `${pack.sha256}.bin`)
      throw new Error('Invalid pack name');
    const url = new URL(pack.name, base);
    if (url.origin !== location.origin) throw new Error('Packs must be local and same-origin');
    let cached = await cache.match(url);
    if (!cached) {
      const downloaded = await fetch(url);
      if (!downloaded.ok) throw new Error(`Pack HTTP ${downloaded.status}`);
      cached = downloaded;
    }
    const bytes = await cached.arrayBuffer();
    if (bytes.byteLength !== pack.bytes || await digest(bytes) !== pack.sha256) {
      await cache.delete(url);
      throw new Error(`Pack integrity mismatch: ${pack.name}`);
    }
    if (pack.bytes <= CACHE_MAX_BYTES) {
      await cache.put(url, new Response(bytes, { headers:{ 'Content-Type':'application/octet-stream' } }));
    } else {
      (globalThis.pzPackCacheSkipped ??= []).push({ name: pack.name, bytes: pack.bytes });
    }
    return bytes;
  };
  const packBytes = await Promise.all(index.packs.map(loadPack));
  for (const [packIndex, pack] of index.packs.entries()) {
    const bytes = packBytes[packIndex];
    // One view over the whole downloaded blob; every mounted file is a subarray of it, so the
    // blob's bytes are resident exactly once (a copy per file would double a 321 MB pack).
    const blob = new Uint8Array(bytes);
    const files = index.files.filter(file => file.pack === pack.name);
    for (const file of files) {
      if (!Number.isSafeInteger(file.offset) || !Number.isSafeInteger(file.length) || file.offset < 0 || file.length < 0 || file.offset + file.length > blob.byteLength)
        throw new Error('Invalid file bounds in pack');
      const range = `${pack.name}:${file.offset}:${file.length}`;
      if (!skipFileDigests && !verifiedRanges.has(range)) {
        // WebCrypto copies its input: bound the bytes in flight (a single 321 MB file waits alone).
        while (inFlight.bytes > 0 && inFlight.bytes + file.length > 64 * 1024 * 1024) await Promise.race(inFlight.set);
        const job = digest(new Uint8Array(bytes, file.offset, file.length));
        const tracked = job.finally(() => { inFlight.set.delete(tracked); inFlight.bytes -= file.length; });
        inFlight.set.add(tracked); inFlight.bytes += file.length;
        verifiedRanges.set(range, tracked);
      }
      pending.push([file.path, blob, file.offset, file.length, range]);
    }
    if (!skipFileDigests) for (const file of files) {
      const range = `${pack.name}:${file.offset}:${file.length}`;
      if (await verifiedRanges.get(range) !== file.sha256) throw new Error(`File integrity mismatch: ${file.path}`);
    }
  }
  if (pending.length !== index.files.length || new Set(pending.map(([path]) => path)).size !== pending.length)
    throw new Error('Missing or duplicate pack entries');
  // Validate all paths and total memory before mutating the VFS.
  for (const [path] of pending) if (!mountablePath(path) || path.split('/').some(p => p === '..' || p === '.'))
    throw new Error('Invalid mounted resource path');
  // Views, not copies: the blob stays alive because every mounted file is a subarray of it, so a
  // 321 MB pack (media/texturepacks/Tiles2x.pack, which the game always asks for) costs its own
  // bytes once instead of blob + per-file slices.
  const counted = new Set();
  const total = pending.reduce((n, [, , , length, range]) => counted.has(range) ? n : (counted.add(range), n + length), 0);
  if (total > vfs.maxBytes) throw new Error('Pack files exceed VFS memory budget');
  for (const [path, blob, offset, length] of pending) vfs.mountView(path, blob, offset, length);
  if (!skipFileDigests && memo) writeMemo(indexHash);
  stats.ms = performance.now() - stats.t0;
  return { files:pending.length, bytes:total, fileDigests:stats.fileDigests, packs:index.packs.map(pack => pack.name) };
}
