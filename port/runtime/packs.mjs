import { normalizePath } from './vfs.mjs';

const hex = bytes => [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
const digest = async bytes => hex(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)));

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
    if (normalizePath(file.path) !== file.path || !file.path.startsWith('/media/'))
      throw new Error('Invalid mounted resource path');
  }
  if (index.packs.reduce((sum, pack) => sum + pack.bytes, 0) > vfs.maxBytes)
    throw new Error('Pack preload exceeds VFS memory budget');
  const base = new URL(indexUrl, location.href);
  const cache = await caches.open('pz-web-packs-v1');
  const pending = [];
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
    await cache.put(url, new Response(bytes, { headers:{ 'Content-Type':'application/octet-stream' } }));
    for (const file of index.files.filter(file => file.pack === pack.name)) {
      if (!Number.isSafeInteger(file.offset) || !Number.isSafeInteger(file.length) || file.offset < 0 || file.length < 0 || file.offset + file.length > bytes.byteLength)
        throw new Error('Invalid file bounds in pack');
      const content = new Uint8Array(bytes.slice(file.offset, file.offset + file.length));
      if (await digest(content) !== file.sha256) throw new Error(`File integrity mismatch: ${file.path}`);
      pending.push([file.path, content]);
    }
  }
  if (pending.length !== index.files.length || new Set(pending.map(([path]) => path)).size !== pending.length)
    throw new Error('Missing or duplicate pack entries');
  // Validate all paths and total memory before mutating the VFS.
  for (const [path] of pending) if (!path.startsWith('/media/') || path.split('/').some(p => p === '..' || p === '.'))
    throw new Error('Invalid mounted resource path');
  const total = pending.reduce((n, [path, bytes]) => n + bytes.byteLength - (vfs.files.get(path)?.byteLength || 0), vfs.byteLength);
  if (total > vfs.maxBytes) throw new Error('Pack files exceed VFS memory budget');
  for (const [path, bytes] of pending) vfs.mount(path, bytes);
  return { files:pending.length, bytes:total };
}
