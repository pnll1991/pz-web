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

/** Cache immutable local pack blobs and preload verified files into synchronous memory. */
export async function preloadPacks(vfs, indexUrl = '/packs/index.json') {
  const response = await fetch(indexUrl, { cache:'no-cache' });
  if (!response.ok) throw new Error(`Pack index HTTP ${response.status}`);
  const index = await response.json();
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
  for (const pack of index.packs) {
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
    // One view over the whole downloaded blob; every mounted file is a subarray of it, so the
    // blob's bytes are resident exactly once (a copy per file would double a 321 MB pack).
    const blob = new Uint8Array(bytes);
    if (pack.bytes <= CACHE_MAX_BYTES) {
      await cache.put(url, new Response(bytes, { headers:{ 'Content-Type':'application/octet-stream' } }));
    } else {
      (globalThis.pzPackCacheSkipped ??= []).push({ name: pack.name, bytes: pack.bytes });
    }
    for (const file of index.files.filter(file => file.pack === pack.name)) {
      if (!Number.isSafeInteger(file.offset) || !Number.isSafeInteger(file.length) || file.offset < 0 || file.length < 0 || file.offset + file.length > blob.byteLength)
        throw new Error('Invalid file bounds in pack');
      const range = `${pack.name}:${file.offset}:${file.length}`;
      if (!verifiedRanges.has(range))
        verifiedRanges.set(range, await digest(new Uint8Array(bytes, file.offset, file.length)));
      if (verifiedRanges.get(range) !== file.sha256) throw new Error(`File integrity mismatch: ${file.path}`);
      pending.push([file.path, blob, file.offset, file.length, range]);
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
  return { files:pending.length, bytes:total };
}
