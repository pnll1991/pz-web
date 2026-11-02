import { normalizePath, READ_ONLY_MOUNTABLE } from './vfs.mjs';
import { readWithProgress } from './loading.mjs';

const hex = bytes => [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
const digest = async bytes => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)));

// Round 52: /Zomboid/options.ini is mounted from the pack as a read-only game-owned file.
// Everything else under /Zomboid is user data and must never come from an immutable blob.
const mountablePath = path => path.startsWith('/media/') || READ_ONLY_MOUNTABLE.has(path);
// Caching a 321 MB response in the Cache API risks a quota error on a small profile. Blobs above this size
// go to the Origin Private File System instead (one file per content-addressed pack name, written in the
// background after verification). Without OPFS they are fetched every boot, as before, and listed in
// globalThis.pzPackCacheSkipped. Either way every pack is still verified against its sha256 before mounting.
const CACHE_MAX_BYTES = 64 * 1024 * 1024;
const CACHE_NAME = 'pz-web-packs-v1', OPFS_DIR = 'pz-web-packs';

const opfsDirectory = async storage => {
  try {
    const root = await storage?.getDirectory?.();
    return root ? await root.getDirectoryHandle(OPFS_DIR, { create:true }) : null;
  } catch { return null; }
};
const opfsRead = async (dir, name, size) => {
  try {
    const file = await (await dir.getFileHandle(name)).getFile();
    return file.size === size ? await file.arrayBuffer() : null;
  } catch { return null; }
};
// createWritable writes to a swap file and only replaces the entry on close(): a reload mid-write leaves no partial pack.
const opfsWrite = async (dir, name, bytes) => {
  const writable = await (await dir.getFileHandle(name, { create:true })).createWritable();
  try { await writable.write(bytes); await writable.close(); }
  catch (error) { await writable.abort?.().catch(() => {}); await dir.removeEntry(name).catch(() => {}); throw error; }
};

// Round 81 (front 2): the per-file sha256 pass (~9.6k digests over ~500 MB) is a pure function of the index
// bytes and the pack bytes. Each pack is still verified in full on every boot; when the index is byte-identical
// (same sha256) to one whose per-file pass already succeeded in this browser, that pass is not repeated.
// '?nocache=1' (or { memo:false }) always repeats it. The first pass runs the digests concurrently.
const VERIFIED_KEY = 'pz-web-packs-verified-index';
const memoAllowed = () => typeof location === 'undefined' || new URLSearchParams(location.search).get('nocache') !== '1';
const readMemo = () => { try { return localStorage.getItem(VERIFIED_KEY); } catch { return null; } };
const writeMemo = value => { try { localStorage.setItem(VERIFIED_KEY, value); } catch { /* sin localStorage: se verifica siempre */ } };

/** Cache immutable local pack blobs and preload verified files into synchronous memory. */
export async function preloadPacks(vfs, indexUrl = '/packs/index.json', { memo = memoAllowed(), cachesImpl = globalThis.caches,
  storage = globalThis.navigator?.storage, cleanup = memo, cacheMaxBytes = CACHE_MAX_BYTES, onProgress = () => {} } = {}) {
  const response = await fetch(indexUrl, { cache:'no-cache' });
  if (!response.ok) throw new Error(`Pack index HTTP ${response.status}`);
  const indexBytes = await response.arrayBuffer();
  const indexHash = await digest(indexBytes);
  const index = JSON.parse(new TextDecoder().decode(indexBytes));
  const skipFileDigests = memo && readMemo() === indexHash;
  const stats = globalThis.pzPackStats = { indexHash, fileDigests: skipFileDigests ? 'memo' : 'computed', t0: performance.now(), ms: 0,
    sources: { network:0, cache:0, opfs:0 }, corrupt:0, opfsWrites:0, opfsErrors:0, removed:0 };
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
  const cache = await cachesImpl.open(CACHE_NAME);
  const needsOpfs = index.packs.some(pack => pack.bytes > cacheMaxBytes);
  const opfs = needsOpfs ? await opfsDirectory(storage) : null;
  const opfsWrites = [];
  const pending = [];
  // Round 80 (front H): several paths may alias one pack range (a clip listed in media/anims_X/Bob and
  // read from media/anims/bob lowercase fallback): it is verified once and counted once, as the VFS does for its views.
  const verifiedRanges = new Map();
  // Pantalla de carga: bytes leidos (red o Cache API) sobre el total que declara el indice.
  const progress = { phase:'download', loaded:0, total:index.packs.reduce((sum, pack) => sum + pack.bytes, 0) };
  onProgress({ ...progress });
  const inFlight = { set:new Set(), bytes:0 };
  const valid = async (bytes, pack) => bytes.byteLength === pack.bytes && await digest(bytes) === pack.sha256;
  // Ronda 88: los packs se descargaban (o leian de la Cache API), se hasheaban y se guardaban uno tras otro. Son
  // independientes: todos a la vez. Cada pack sigue verificandose completo (tamano + sha256) antes de montarse.
  // Un pack guardado (Cache API u OPFS) que no verifica se borra y se baja una vez de la red; solo lo que se bajo
  // se guarda (antes se reescribia en la Cache API en cada arranque).
  const loadPack = async pack => {
    if (!/^[a-f0-9]{64}\.bin$/.test(pack.name) || pack.name !== `${pack.sha256}.bin`)
      throw new Error('Invalid pack name');
    const url = new URL(pack.name, base);
    if (url.origin !== location.origin) throw new Error('Packs must be local and same-origin');
    const large = pack.bytes > cacheMaxBytes;
    let bytes = null;
    if (!large) {
      const cached = await cache.match(url);
      if (cached) {
        bytes = await cached.arrayBuffer();
        if (await valid(bytes, pack)) { stats.sources.cache++; progress.loaded += pack.bytes; onProgress({ ...progress }); return bytes; }
        stats.corrupt++; await cache.delete(url);
      }
    } else if (opfs) {
      bytes = await opfsRead(opfs, pack.name, pack.bytes);
      if (bytes) {
        if (await valid(bytes, pack)) { stats.sources.opfs++; progress.loaded += pack.bytes; onProgress({ ...progress }); return bytes; }
        stats.corrupt++; await opfs.removeEntry(pack.name).catch(() => {});
      }
    }
    const downloaded = await fetch(url);
    if (!downloaded.ok) throw new Error(`Pack HTTP ${downloaded.status}`);
    bytes = await readWithProgress(downloaded, pack.bytes, n => { progress.loaded += n; onProgress({ ...progress }); });
    if (!await valid(bytes, pack)) throw new Error(`Pack integrity mismatch: ${pack.name}`);
    stats.sources.network++;
    if (!large) {
      await cache.put(url, new Response(bytes, { headers:{ 'Content-Type':'application/octet-stream' } }));
    } else if (opfs) {
      // In the background: the game does not need the copy on disk to start. The view stays read-only.
      opfsWrites.push(opfsWrite(opfs, pack.name, bytes).then(() => { stats.opfsWrites++; },
        error => { stats.opfsErrors++; stats.lastOpfsError = String(error); }));
    } else {
      (globalThis.pzPackCacheSkipped ??= []).push({ name: pack.name, bytes: pack.bytes });
    }
    return bytes;
  };
  const packBytes = await Promise.all(index.packs.map(loadPack));
  progress.phase = skipFileDigests ? 'mount' : 'verify';
  onProgress({ ...progress });
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
  // Packs of older indexes (every repack changes their content-addressed names) would otherwise pile up forever:
  // a 321 MB stale copy per repack. Only entries absent from this verified index are removed, in the background.
  if (cleanup) stats.cleanup = removeStalePacks(cache, opfs, new Set(index.packs.map(pack => pack.name)), stats)
    .catch(error => { stats.lastCleanupError = String(error); });
  stats.opfsDone = Promise.all(opfsWrites);
  stats.ms = performance.now() - stats.t0;
  return { files:pending.length, bytes:total, fileDigests:stats.fileDigests };
}

async function removeStalePacks(cache, opfs, current, stats) {
  for (const request of await cache.keys()) {
    const name = new URL(request.url).pathname.split('/').pop();
    if (/^[a-f0-9]{64}\.bin$/.test(name) && !current.has(name)) { await cache.delete(request); stats.removed++; }
  }
  if (!opfs?.keys) return;
  for await (const name of opfs.keys()) {
    if (!current.has(name)) { await opfs.removeEntry(name).catch(() => {}); stats.removed++; }
  }
}
