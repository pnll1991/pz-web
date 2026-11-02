import { normalizePath, READ_ONLY_MOUNTABLE } from './vfs.mjs';
import { readWithProgress } from './loading.mjs';
import { LazyFile, ChunkStore } from './lazypack.mjs';

const hex = bytes => [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
const digest = async bytes => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)));

// Round 52: /Zomboid/options.ini is mounted from the pack as a read-only game-owned file.
// Everything else under /Zomboid is user data and must never come from an immutable blob.
const mountablePath = path => path.startsWith('/media/') || READ_ONLY_MOUNTABLE.has(path);
// Caching a 321 MB response in the Cache API risks a quota error on a small profile. Blobs above this size
// go to the Origin Private File System instead (one file per content-addressed pack name, written in the
// background after verification). Without OPFS they are fetched every boot, as before, and listed in
// globalThis.pzPackCacheSkipped. Ronda 89 (frente D): un pack bajado de la red siempre se verifica completo (tamano +
// sha256) antes de guardarse; uno leido de la Cache API u OPFS solo comprueba el tamano (el nombre es su sha256 y
// solo se guarda lo ya verificado). '?packverify=1' o '?nocache=1' vuelven a verificar todo en cada carga.
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
// Ronda 89: los packs perezosos (lazypack.mjs) se montan sin bajar el blob grande; '?lazy=0' (o { lazy:false }) los baja enteros como antes.
const lazyAllowed = () => typeof location === 'undefined' || new URLSearchParams(location.search).get('lazy') !== '0';
const memoAllowed = () => typeof location === 'undefined' || new URLSearchParams(location.search).get('nocache') !== '1';
const urlFlag = name => typeof location !== 'undefined' && new URLSearchParams(location.search).get(name) === '1';
const fullDigestWanted = () => urlFlag('packverify') || urlFlag('nocache');
const readMemo = () => { try { return localStorage.getItem(VERIFIED_KEY); } catch { return null; } };
const writeMemo = value => { try { localStorage.setItem(VERIFIED_KEY, value); } catch { /* sin localStorage: se verifica siempre */ } };

/** Cache immutable local pack blobs and preload verified files into synchronous memory. */
export async function preloadPacks(vfs, indexUrl = '/packs/index.json', options = {}) {
  const { memo = memoAllowed(), cachesImpl = globalThis.caches, storage = globalThis.navigator?.storage, cleanup = memo,
    cacheMaxBytes = CACHE_MAX_BYTES, onProgress = () => {}, lazy = lazyAllowed(), chunkCache = memo,
    fullDigest = fullDigestWanted() } = options;
  const response = await fetch(indexUrl, { cache:'no-cache' });
  if (!response.ok) throw new Error(`Pack index HTTP ${response.status}`);
  const indexBytes = await response.arrayBuffer();
  const indexHash = await digest(indexBytes);
  const index = JSON.parse(new TextDecoder().decode(indexBytes));
  const skipFileDigests = memo && readMemo() === indexHash;
  const stats = globalThis.pzPackStats = { indexHash, fileDigests: skipFileDigests ? 'memo' : 'computed', t0: performance.now(), ms: 0,
    sources: { network:0, cache:0, opfs:0 }, corrupt:0, opfsWrites:0, opfsErrors:0, removed:0,
    fullDigest, digestSkipped:[], digestMs:0 };
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
  // Un pack perezoso solo se baja entero si el modo perezoso está apagado; si no, sus datos se piden por tramos al leerlos.
  const isLazy = pack => lazy && pack.lazy === true;
  const needsOpfs = index.packs.some(pack => !isLazy(pack) && pack.bytes > cacheMaxBytes);
  const opfs = needsOpfs ? await opfsDirectory(storage) : null;
  const opfsWrites = [];
  const pending = [];
  // Round 80 (front H): several paths may alias one pack range (a clip listed in media/anims_X/Bob and
  // read from media/anims/bob lowercase fallback): it is verified once and counted once, as the VFS does for its views.
  const verifiedRanges = new Map();
  // Pantalla de carga: bytes leidos (red o Cache API) sobre el total que declara el indice.
  const progress = { phase:'download', loaded:0, total:index.packs.reduce((sum, pack) => sum + (isLazy(pack) ? 0 : pack.bytes), 0) };
  onProgress({ ...progress });
  const inFlight = { set:new Set(), bytes:0 };
  const timedDigest = async bytes => { const t = performance.now(); try { return await digest(bytes); } finally { stats.digestMs += performance.now() - t; } };
  const valid = async (bytes, pack) => bytes.byteLength === pack.bytes && await timedDigest(bytes) === pack.sha256;
  // Pack leido de la cache local: tamano siempre; sha256 completo solo con fullDigest. Si se saltea queda anotado.
  const validStored = async (bytes, pack, source) => {
    if (fullDigest) return valid(bytes, pack);
    if (bytes.byteLength !== pack.bytes) return false;
    stats.digestSkipped.push(pack.name); skippedFrom.set(pack.name, source);
    return true;
  };
  const skippedFrom = new Map();
  // Ronda 88: los packs se descargaban (o leian de la Cache API), se hasheaban y se guardaban uno tras otro. Son
  // independientes: todos a la vez. Un pack bajado se verifica completo (tamano + sha256) antes de montarse y guardarse; uno de la cache solo por tamano (ver fullDigest).
  // Un pack guardado (Cache API u OPFS) que no verifica se borra y se baja una vez de la red; solo lo que se bajo
  // se guarda (antes se reescribia en la Cache API en cada arranque).
  const loadPack = async pack => {
    if (!/^[a-f0-9]{64}\.bin$/.test(pack.name) || pack.name !== `${pack.sha256}.bin`)
      throw new Error('Invalid pack name');
    if (isLazy(pack)) return null;
    const url = new URL(pack.name, base);
    if (url.origin !== location.origin) throw new Error('Packs must be local and same-origin');
    const large = pack.bytes > cacheMaxBytes;
    let bytes = null;
    if (!large) {
      const cached = await cache.match(url);
      if (cached) {
        bytes = await cached.arrayBuffer();
        if (await validStored(bytes, pack, 'cache')) { stats.sources.cache++; progress.loaded += pack.bytes; onProgress({ ...progress }); return bytes; }
        stats.corrupt++; await cache.delete(url);
      }
    } else if (opfs) {
      bytes = await opfsRead(opfs, pack.name, pack.bytes);
      if (bytes) {
        if (await validStored(bytes, pack, 'opfs')) { stats.sources.opfs++; progress.loaded += pack.bytes; onProgress({ ...progress }); return bytes; }
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
  const bytesByName = new Map(index.packs.map((pack, i) => [pack.name, packBytes[i]]));
  progress.phase = skipFileDigests ? 'mount' : 'verify';
  onProgress({ ...progress });
  const lazyMounts = []; // [path, LazyFile]
  const lazyStore = lazy && index.files.some(file => file.lazy) ? new ChunkStore({ cachesImpl }) : null;
  for (const [packIndex, pack] of index.packs.entries()) {
    const bytes = packBytes[packIndex];
    const files = index.files.filter(file => file.pack === pack.name);
    if (isLazy(pack)) {
      // Pack perezoso: ni el blob ni sus archivos se verifican enteros aqui; cada tramo se verifica contra su sha256 cuando se trae.
      for (const file of files) lazyMounts.push([file.path, buildLazyFile(file, pack, bytesByName, base, vfs, lazyStore)]);
      continue;
    }
    // One view over the whole downloaded blob; every mounted file is a subarray of it, so the
    // blob's bytes are resident exactly once (a copy per file would double a 321 MB pack).
    const blob = new Uint8Array(bytes);
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
      if (await verifiedRanges.get(range) !== file.sha256) {
        // Pack salteado de la cache con un archivo corrupto: se borra y se reintenta una vez con verificacion completa.
        if (skippedFrom.has(pack.name) && !fullDigest) {
          await Promise.allSettled([...verifiedRanges.values()]);
          const url = new URL(pack.name, base);
          if (skippedFrom.get(pack.name) === 'cache') await cache.delete(url); else await opfs?.removeEntry(pack.name).catch(() => {});
          const retried = await preloadPacks(vfs, indexUrl, { ...options, fullDigest:true });
          globalThis.pzPackStats.corrupt++; globalThis.pzPackStats.retriedFullDigest = true;
          return retried;
        }
        throw new Error(`File integrity mismatch: ${file.path}`);
      }
    }
  }
  if (pending.length + lazyMounts.length !== index.files.length || new Set([...pending, ...lazyMounts].map(([path]) => path)).size !== index.files.length)
    throw new Error('Missing or duplicate pack entries');
  // Validate all paths and total memory before mutating the VFS.
  for (const [path] of [...pending, ...lazyMounts]) if (!mountablePath(path) || path.split('/').some(p => p === '..' || p === '.'))
    throw new Error('Invalid mounted resource path');
  // Views, not copies: the blob stays alive because every mounted file is a subarray of it, so a
  // 321 MB pack (media/texturepacks/Tiles2x.pack, which the game always asks for) costs its own
  // bytes once instead of blob + per-file slices.
  const counted = new Set();
  const total = pending.reduce((n, [, , , length, range]) => counted.has(range) ? n : (counted.add(range), n + length), 0)
    + lazyMounts.reduce((n, [, file]) => n + file.length, 0); // el peor caso de un archivo perezoso: leido entero
  if (total > vfs.maxBytes) throw new Error('Pack files exceed VFS memory budget');
  for (const [path, blob, offset, length] of pending) vfs.mountView(path, blob, offset, length);
  if (lazyMounts.length) {
    // Lo guardado de una carga anterior se trae a memoria ahora (verificado por WebCrypto): la lectura de la VFS es sincrona y la Cache API no.
    if (lazyStore && chunkCache) {
      try { await lazyStore.open(); stats.lazyStore = await lazyStore.restore(lazyMounts.map(([, file]) => file)); }
      catch (error) { stats.lazyStoreError = String(error); lazyStore.cache = null; }
    }
    for (const [path, file] of lazyMounts) vfs.mountLazy(path, file);
    globalThis.pzLazyFiles = Object.fromEntries(lazyMounts); globalThis.pzLazyStore = lazyStore;
    stats.lazy = { files: lazyMounts.length, chunks: lazyMounts.reduce((n, [, file]) => n + file.stats.chunks, 0), bytes: lazyMounts.reduce((n, [, file]) => n + file.stats.totalBytes, 0) };
  }
  if (!skipFileDigests && memo) writeMemo(indexHash);
  // Packs of older indexes (every repack changes their content-addressed names) would otherwise pile up forever:
  // a 321 MB stale copy per repack. Only entries absent from this verified index are removed, in the background.
  if (cleanup) stats.cleanup = removeStalePacks(cache, opfs, new Set(index.packs.map(pack => pack.name)), stats)
    .catch(error => { stats.lastCleanupError = String(error); });
  stats.opfsDone = Promise.all(opfsWrites);
  stats.ms = performance.now() - stats.t0;
  return { files:pending.length + lazyMounts.length, bytes:total, fileDigests:stats.fileDigests };
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

// Un archivo del índice con `lazy: { head, parts }` -> LazyFile. `head` es el nombre de un blob chico (ya bajado y verificado) con los bytes de
// cabecera; `parts` son [at, length, sha256|null] contiguas. El blob grande (`pack`) solo se pide con Range, por tramos.
function buildLazyFile(file, pack, bytesByName, base, vfs, store) {
  const lazy = file.lazy;
  if (!lazy || typeof lazy.head !== 'string' || !Array.isArray(lazy.parts) || file.offset !== 0 || file.length !== pack.bytes)
    throw new Error(`Invalid lazy pack entry: ${file.path}`);
  const headBytes = bytesByName.get(lazy.head);
  if (!headBytes) throw new Error(`Lazy pack head missing: ${lazy.head}`);
  for (const part of lazy.parts) if (!Array.isArray(part) || part.length !== 3 || (part[2] !== null && !/^[a-f0-9]{64}$/.test(part[2])))
    throw new Error(`Invalid lazy part: ${file.path}`);
  const url = new URL(pack.name, base);
  if (url.origin !== location.origin) throw new Error('Packs must be local and same-origin');
  let lazyFile;
  lazyFile = new LazyFile({ length: file.length, parts: lazy.parts, head: new Uint8Array(headBytes), url: url.href,
    syncGet: (target, start, end) => vfs.syncRange(target, start, end, file.path),
    takePrefetched: (target, range) => vfs.takePrefetched(target, range),
    onFetch: (chunk, bytes) => store?.record(lazyFile, chunk, bytes) });
  return lazyFile;
}
