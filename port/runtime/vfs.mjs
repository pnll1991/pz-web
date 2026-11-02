export function normalizePath(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.includes('\0') || value.includes('\\'))
    throw new Error('VFS requires an absolute POSIX path');
  const parts = [];
  for (const piece of value.split('/')) {
    if (!piece || piece === '.') continue;
    if (piece === '..') {
      if (!parts.length) throw new Error('Path escapes VFS root');
      parts.pop();
    } else parts.push(piece);
  }
  return '/' + parts.join('/');
}

const writable = path => path.startsWith('/Zomboid/');
const parent = path => path.slice(0, path.lastIndexOf('/')) || '/';
// The only /Zomboid paths a pack may own. The game writes everything else under /Zomboid
// (saves, logs, options backup), so those must never come from an immutable blob.
export const READ_ONLY_MOUNTABLE = new Set(['/Zomboid/options.ini']);
const requestValue = request => new Promise((resolve, reject) => {
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});
const transactionDone = transaction => new Promise((resolve, reject) => {
  transaction.oncomplete = resolve;
  transaction.onabort = () => reject(transaction.error || new Error('IndexedDB transaction aborted'));
  transaction.onerror = () => {}; // onabort is authoritative; never resolve failed transactions.
});

// Round 29: the main fiber parks inside DebugOptions#initMessaging(), whose last observed game
// call was ZomboidFileSystem#getMessagingDirSub(). The XML writer then goes through java.io into
// our own VFS, so /Zomboid mutations are traced here with the same ring the Java transformer
// uses. Mounted /media writes are skipped so the boot preload cannot flood the ring.
const ringTrace = (label) => {
  const t = globalThis.pzGameTrace || (globalThis.pzGameTrace = []);
  t.push({ label, time: performance.now() });
  if (t.length > 400) t.splice(0, t.length - 400);
};

// Round 79: parent -> child names, kept in step with every file/directory mutation, so children()
// no longer scans the whole tree (with the on-demand media manifest that is ~60k paths per call).
class ChildIndex {
  constructor(owner) { this.owner = owner; this.map = new Map(); }
  link(path) {
    if (path === '/') return;
    const dir = parent(path); let set = this.map.get(dir);
    if (!set) this.map.set(dir, set = new Set());
    set.add(path.slice(path.lastIndexOf('/') + 1));
  }
  unlink(path) {
    const o = this.owner;
    if (path === '/' || o.files.has(path) || o.directories.has(path) || o.remote?.has(path)) return;
    this.map.get(parent(path))?.delete(path.slice(path.lastIndexOf('/') + 1));
  }
}
class IndexedMap extends Map {
  constructor(index) { super(); this.index = index; }
  set(key, value) { const fresh = !this.has(key); super.set(key, value); if (fresh) this.index.link(key); return this; }
  delete(key) { const removed = super.delete(key); if (removed) this.index.unlink(key); return removed; }
}
class IndexedSet extends Set {
  constructor(index, values) { super(); this.index = index; for (const v of values) this.add(v); }
  add(value) { const fresh = !this.has(value); super.add(value); if (fresh) this.index.link(value); return this; }
  delete(value) { const removed = super.delete(value); if (removed) this.index.unlink(value); return removed; }
}

/** Preloaded synchronous reads, writable /Zomboid, atomic IndexedDB snapshots. */
export class VirtualFileSystem {
  // Round 52: raised from 256 MB. Verified in the pinned bytecode (javap -p -c zombie.core.Core:
  // getOptionTexture2x() is `iconst_1; ireturn`) that Core.tileScale is always 2, so GameWindow.enter()
  // always mounts the 2x tile packs and media/texturepacks/Tiles2x.pack alone is 321 MB. The pack
  // files are mounted as views over the fetched blob (mountReadOnlyView), so the budget counts the
  // blob bytes once instead of blob + copies.
  constructor({ name = 'pz-web-saves-v1', maxBytes = 640 * 1024 * 1024 } = {}) {
    this.name = name; this.maxBytes = maxBytes;
    this.childIndex = new ChildIndex(this);
    this.files = new IndexedMap(this.childIndex); this.database = null; this.pending = Promise.resolve();
    this.directories = new IndexedSet(this.childIndex, ['/', '/media', '/Zomboid']);
    this.metadata = new Map();
    this.readOnlyMounted = new Set();
    // Round 80 (front H): several pack paths may be views over the SAME blob range (a clip listed in
    // media/anims_X/Bob and read from its media/anims/bob lowercase fallback). Resident memory is that
    // range once, so the budget counts it once: sharedViewBytes = bytes of the duplicate views.
    this.viewKeys = new Map(); this.viewRefs = new Map(); this.viewBuffers = new WeakMap();
    this.sharedViewBytes = 0; this.nextViewBuffer = 0;
    this.listeners = new Set();
    // Round 79: read-only files of PZ_HOME/media that are not preloaded from a pack. Their bytes are
    // read on demand with synchronous Range requests (the game's java.io is synchronous) and kept in
    // a bounded block cache. Paths and sizes come from the dev server's /media-manifest.json.
    this.remote = new Map(); this.remoteBase = '/media/';
    // Ronda 90: con el dev server que lo anuncia (/media-manifest.json `slices: 1`) los tramos se piden como `?r=a-b` SIN cabecera
    // Range: Chrome manda `Accept-Encoding: identity` en todo pedido con Range y así el servidor puede mandarlos con br/gzip.
    this.remoteSlices = false;
    // Ronda 89: archivos de pack perezosos (port/runtime/lazypack.mjs): se registran también en `remote` (tamaño, existencia) pero se leen por tramos.
    this.lazy = new Map();
    this.blockCache = new Map(); this.blockCacheBytes = 0; this.blockCacheMax = 384 * 1024 * 1024;
    this.remoteStats = { requests:0, bytes:0, ms:0 };
    // Round 81 (front 2): the world load opens ~4000 tiny media/maps/<map>/*.lotheader files, one synchronous
    // XHR each. The first read of one fetches the whole directory's lotheaders in a single response
    // (dev-server /media-bundle: same bytes) and the rest are served from memory. Off with bundleEnabled=false.
    this.bundleEnabled = true; this.bundles = new Map(); this.bundleDirs = new Set();
    this.bundleStats = { requests:0, files:0, bytes:0, ms:0, hits:0, mismatches:0, failed:0 };
    // Round 84: responses fetched ahead (port/runtime/prefetch.mjs), keyed `${url}|${Range}`; each is used once, so memory is returned.
    this.prefetched = new Map(); this.prefetchHits = 0;
    // Bytes que se acaban de traer de la red (url, Range, bytes): port/runtime/mediacache.mjs los guarda para la recarga
    // siguiente. Un error del callback nunca afecta la lectura.
    this.onRemoteFetch = null;
  }
  noteRemoteFetch(url, range, bytes) {
    if (!this.onRemoteFetch) return;
    try { this.onRemoteFetch(url, range, bytes); } catch { /* la caché es opcional */ }
  }
  takePrefetched(url, range = '') {
    const key = `${url}|${range}`, bytes = this.prefetched.get(key);
    if (bytes) { this.prefetched.delete(key); this.prefetchHits++; }
    return bytes || null;
  }
  /** Bytes of a bundled remote file, or null (not bundled / unavailable). Never throws. */
  bundled(path) {
    if (!this.bundleEnabled || !path.endsWith('.lotheader')) return null;
    const hit = this.bundles.get(path);
    if (hit) { this.bundleStats.hits++; return hit; }
    const dir = path.slice(0, path.lastIndexOf('/'));
    if (this.bundleDirs.has(dir)) return null;
    this.bundleDirs.add(dir);
    const t0 = performance.now(), stats = this.bundleStats;
    try {
      const url = `${this.remoteBase.replace(/media\/$/, '')}media-bundle?dir=${encodeURIComponent(dir.slice('/media/'.length))}&ext=lotheader`;
      let bytes = this.takePrefetched(url);
      if (!bytes) {
        const xhr = new XMLHttpRequest();
        xhr.open('GET', url, false);
        xhr.overrideMimeType('text/plain; charset=x-user-defined');
        xhr.setRequestHeader('Cache-Control', 'no-store');
        xhr.send();
        if (xhr.status !== 200) throw new Error(`HTTP ${xhr.status}`);
        const text = xhr.responseText; bytes = new Uint8Array(text.length);
        for (let i = 0; i < bytes.length; i++) bytes[i] = text.charCodeAt(i) & 0xff;
        this.noteRemoteFetch(url, '', bytes);
      }
      const headerLength = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(0, true);
      const listed = JSON.parse(new TextDecoder().decode(bytes.subarray(4, 4 + headerLength)));
      let offset = 4 + headerLength;
      for (const [name, size] of listed) {
        const file = `${dir}/${name}`;
        // Only what the manifest lists, with exactly the listed size; anything else keeps the per-file path.
        if (this.remote.get(file) === size && offset + size <= bytes.length) this.bundles.set(file, bytes.subarray(offset, offset + size));
        else stats.mismatches++;
        offset += size;
      }
      stats.requests++; stats.files += this.bundles.size; stats.bytes += bytes.length;
    } catch (error) { stats.failed++; stats.lastError = String(error); }
    stats.ms += performance.now() - t0;
    const found = this.bundles.get(path);
    if (found) stats.hits++;
    return found ?? null;
  }
  /** Round 79: register the on-demand media files. Pack-mounted files keep priority. */
  attachRemote(entries, base = '/media/') {
    this.remoteBase = base;
    for (const [relative, size] of entries) {
      const path = normalizePath('/media/' + relative);
      if (this.files.has(path) || this.lazy.has(path) || this.directories.has(path) || !Number.isSafeInteger(size) || size < 0) continue;
      this.remote.set(path, size); this.childIndex.link(path);
      for (let dir = parent(path); !this.directories.has(dir); dir = parent(dir)) {
        this.directories.add(dir);
        this.metadata.set(dir, { directory:true, modified:0, readOnly:true });
      }
    }
    return this.remote.size;
  }
  isRemote(path) { return !this.files.has(path) && this.remote.has(path); }
  /** 0 missing, 1 file, 2 directory (the java.io shim's contract). */
  kind(path) { return this.directories.has(path) ? 2 : (this.files.has(path) || this.remote.has(path) ? 1 : 0); }
  size(path) { const b = this.files.get(path); return b ? b.length : (this.remote.get(path) ?? 0); }
  /** Synchronous Range GET of [start, end) of `url` (bytes exactly as the server holds them: never a content-encoded body). */
  syncRange(url, start, end, label = url) {
    const xhr = new XMLHttpRequest();
    // One URL per range (the dev server ignores the query): Chrome otherwise reuses a cached partial
    // entry, revalidates it with If-Range and the server answers 200 with the whole file.
    xhr.open('GET', url, false);
    xhr.overrideMimeType('text/plain; charset=x-user-defined');
    xhr.setRequestHeader('Range', `bytes=${start}-${end - 1}`);
    // Bypass the HTTP cache: a cached partial entry makes Chrome revalidate with If-Range and the
    // server then answers 200 with the whole file (seen for a 165 MB zip). Blocks are cached here.
    xhr.setRequestHeader('Cache-Control', 'no-store');
    xhr.send();
    if (xhr.status !== 206 && !(xhr.status === 200 && start === 0)) throw new Error(`EIO: ${label} HTTP ${xhr.status}`);
    const text = xhr.responseText, n = Math.min(text.length, end - start);
    if (n !== end - start) throw new Error(`EIO: short read ${label} ${n}/${end - start}`);
    const out = new Uint8Array(n);
    for (let i = 0; i < n; i++) out[i] = text.charCodeAt(i) & 0xff;
    return out;
  }
  /** Ronda 90: GET síncrono de un tramo `?r=a-b` sin Range (el navegador descomprime br/gzip); `length` bytes exactos o EIO. */
  syncSlice(url, length, label = url) {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', url, false);
    xhr.overrideMimeType('text/plain; charset=x-user-defined');
    xhr.setRequestHeader('Cache-Control', 'no-store');
    xhr.send();
    if (xhr.status !== 200) throw new Error(`EIO: ${label} HTTP ${xhr.status}`);
    const text = xhr.responseText;
    if (text.length !== length) throw new Error(`EIO: slice length ${label} ${text.length}/${length}`);
    const out = new Uint8Array(length);
    for (let i = 0; i < length; i++) out[i] = text.charCodeAt(i) & 0xff;
    return out;
  }
  /** Synchronous Range GET of [start, end) of a remote media file. */
  fetchRange(path, start, end) {
    const t0 = performance.now();
    const url = this.remoteBase + path.slice('/media/'.length).split('/').map(encodeURIComponent).join('/') + `?r=${start}-${end}`;
    const ahead = this.takePrefetched(url, `bytes=${start}-${end - 1}`);
    if (ahead && ahead.length === end - start) { const s = this.remoteStats; s.requests++; s.bytes += ahead.length; return ahead; }
    const out = this.remoteSlices ? this.syncSlice(url, end - start, path) : this.syncRange(url, start, end, path);
    const s = this.remoteStats; s.requests++; s.bytes += out.length; s.ms += performance.now() - t0;
    this.noteRemoteFetch(url, `bytes=${start}-${end - 1}`, out);
    return out;
  }
  remoteBlock(path, index) {
    const BLOCK = 1 << 20, key = path + '#' + index;
    let block = this.blockCache.get(key);
    if (block) { this.blockCache.delete(key); this.blockCache.set(key, block); return block; }
    const size = this.remote.get(path), start = index * BLOCK;
    block = this.fetchRange(path, start, Math.min(size, start + BLOCK));
    this.blockCache.set(key, block); this.blockCacheBytes += block.length;
    for (const [k, b] of this.blockCache) {
      if (this.blockCacheBytes <= this.blockCacheMax) break;
      this.blockCache.delete(k); this.blockCacheBytes -= b.length;
    }
    return block;
  }
  readRemoteInto(path, position, target) {
    const lazy = this.lazy.get(path);
    if (lazy) return lazy.read(position, target);
    const BLOCK = 1 << 20, size = this.remote.get(path);
    const whole = this.bundled(path);
    if (whole) {
      const n = Math.min(target.length, Math.max(0, size - position));
      target.set(whole.subarray(position, position + n)); return n;
    }
    const count = Math.min(target.length, Math.max(0, size - position));
    // Large sequential reads skip the cache: one request straight into the caller's buffer.
    if (count > 4 * BLOCK) { target.set(this.fetchRange(path, position, position + count)); return count; }
    let done = 0;
    while (done < count) {
      const at = position + done, block = this.remoteBlock(path, Math.floor(at / BLOCK)), offset = at % BLOCK;
      const n = Math.min(count - done, block.length - offset);
      target.set(block.subarray(offset, offset + n), done); done += n;
    }
    return count;
  }
  readRemoteFile(path) {
    const lazy = this.lazy.get(path);
    if (lazy) return lazy.readAll();
    const whole = this.bundled(path);
    if (whole) return whole.slice();
    const size = this.remote.get(path), out = new Uint8Array(size), CHUNK = 16 << 20;
    for (let at = 0; at < size; at += CHUNK) out.set(this.fetchRange(path, at, Math.min(size, at + CHUNK)), at);
    return out;
  }
  onChange(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
  changed(path, operation) { for (const listener of [...this.listeners]) listener(path, operation); }
  async open() {
    const request = indexedDB.open(this.name, 2);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains('files')) request.result.createObjectStore('files');
      if (!request.result.objectStoreNames.contains('metadata')) request.result.createObjectStore('metadata');
    };
    this.database = await requestValue(request);
    this.database.onversionchange = () => this.database.close();
    const transaction = this.database.transaction(['files', 'metadata'], 'readonly');
    const done = transactionDone(transaction);
    const store = transaction.objectStore('files');
    const meta = transaction.objectStore('metadata');
    const [keys, values, metaKeys, metaValues] = await Promise.all([
      requestValue(store.getAllKeys()), requestValue(store.getAll()), requestValue(meta.getAllKeys()), requestValue(meta.getAll())]);
    await done;
    let size = this.byteLength;
    for (let i = 0; i < keys.length; i++) {
      const key = normalizePath(keys[i]);
      if (!writable(key)) throw new Error('Invalid persisted VFS path');
      size += values[i].byteLength;
      if (size > this.maxBytes) throw new Error('Persisted VFS exceeds memory budget');
      this.ensureParents(key);
      this.files.set(key, new Uint8Array(values[i]));
    }
    for (let i = 0; i < metaKeys.length; i++) {
      const key = normalizePath(metaKeys[i]), value = metaValues[i];
      if (!writable(key)) throw new Error('Invalid persisted metadata path');
      if (value.directory) { this.ensureParents(key); this.directories.add(key); }
      this.metadata.set(key, value);
    }
    return this;
  }
  get byteLength() {
    let total = 0;
    for (const bytes of this.files.values()) total += bytes.byteLength;
    return total - this.sharedViewBytes;
  }
  viewKey(blob, offset, length) {
    let id = this.viewBuffers.get(blob.buffer);
    if (id === undefined) this.viewBuffers.set(blob.buffer, id = this.nextViewBuffer++);
    return `${id}:${blob.byteOffset + offset}:${length}`;
  }
  releaseView(path) {
    const key = this.viewKeys.get(path);
    if (key === undefined) return;
    this.viewKeys.delete(path);
    const ref = this.viewRefs.get(key);
    if (--ref.count === 0) this.viewRefs.delete(key); else this.sharedViewBytes -= ref.length;
  }
  ensureParents(path) {
    let current = parent(path);
    const missing = [];
    while (!this.directories.has(current)) {
      if (this.files.has(current)) throw new Error(`ENOTDIR: ${current}`);
      missing.push(current); current = parent(current);
    }
    for (const directory of missing.reverse()) {
      this.directories.add(directory);
      this.metadata.set(directory, { directory:true, modified:Date.now(), readOnly:!writable(directory) });
      this.changed(directory, 'create');
    }
  }
  store(path, data) {
    if (!(data instanceof Uint8Array)) throw new TypeError('VFS data must be Uint8Array');
    const size = this.byteLength - (this.files.get(path)?.byteLength || 0) + data.byteLength;
    if (size > this.maxBytes) throw new Error('VFS memory budget exceeded');
    if (this.directories.has(path)) throw new Error(`EISDIR: ${path}`);
    const existed = this.files.has(path);
    this.ensureParents(path);
    this.releaseView(path);
    this.files.set(path, data.slice());
    this.metadata.set(path, { directory:false, modified:Date.now(), readOnly:!writable(path) });
    this.changed(path, existed ? 'modify' : 'create');
    if (writable(path)) ringTrace(`vfs#store(${existed ? 'modify' : 'create'}) ${path} bytes=${data.byteLength}`);
  }
  mount(path, data) {
    path = normalizePath(path);
    if (!path.startsWith('/media/')) throw new Error('Read-only resources must be under /media');
    this.store(path, data);
  }
  /** Round 52: a game-owned file under /Zomboid that must never be writable.
   *
   * `writable()` above treats every /Zomboid path as user-writable, which is correct for saves
   * and logs, but the game's own `Core.loadOptions()` ends with `saveOptions()` writing the
   * whole option list back to /Zomboid/options.ini. If that file came from the immutable pack
   * and stayed writable, the pack's texture2x=false would be silently replaced by whatever the
   * in-memory defaults are, and the next reload would take the 2x tile path again. So the pack
   * entry is marked readOnly here: `canWrite()`/`writeFile()`/`resize()`/`writeAt()` then fail
   * with EROFS (the Java layer turns that into an honest IOException), and flush() skips it so
   * a game asset never lands in the save snapshot. */
  mountReadOnly(path, data) {
    path = normalizePath(path);
    if (path.startsWith('/media/')) throw new Error('Use mount() for /media resources');
    if (!path.startsWith('/Zomboid/')) throw new Error(`Only /Zomboid files may be mounted read-only: ${path}`);
    this.store(path, data);
    this.metadata.set(path, { directory:false, modified:Date.now(), readOnly:true });
    this.readOnlyMounted.add(path);
    ringTrace(`vfs#mountReadOnly ${path} bytes=${data.byteLength}`);
  }
  /** Read-only mount without copying: the file is a subarray view over an already resident blob.
   * The whole blob stays alive because the view references it, so a 321 MB pack costs its own
   * bytes once instead of blob + slice (mount()/store() always slice). Writes still fail with
   * EROFS and flush() skips it, so a view is only ever valid for immutable pack content. */
  mountView(path, blob, offset, length) {
    path = normalizePath(path);
    if (!(blob instanceof Uint8Array)) throw new TypeError('Pack blob must be Uint8Array');
    if (!Number.isSafeInteger(offset) || !Number.isSafeInteger(length) || offset < 0 || length < 0
        || offset + length > blob.byteLength) throw new Error(`Invalid view range: ${path}`);
    if (!path.startsWith('/media/') && !READ_ONLY_MOUNTABLE.has(path))
      throw new Error(`Pack views are only allowed for /media or the read-only /Zomboid files: ${path}`);
    const key = this.viewKey(blob, offset, length);
    const shared = this.viewRefs.has(key) && this.viewKeys.get(path) !== key;
    if (this.byteLength + (shared ? 0 : length) > this.maxBytes) throw new Error('VFS memory budget exceeded');
    if (this.directories.has(path)) throw new Error(`EISDIR: ${path}`);
    const existed = this.files.has(path);
    this.ensureParents(path);
    this.releaseView(path);
    const ref = this.viewRefs.get(key);
    if (ref) { ref.count++; this.sharedViewBytes += length; } else this.viewRefs.set(key, { count:1, length });
    this.viewKeys.set(path, key);
    this.files.set(path, blob.subarray(offset, offset + length));
    this.metadata.set(path, { directory:false, modified:Date.now(), readOnly:true });
    this.readOnlyMounted.add(path);
    this.changed(path, existed ? 'modify' : 'create');
    ringTrace(`vfs#mountView ${path} bytes=${length}`);
  }
  /** Ronda 89: monta un archivo de /media que se lee por tramos (LazyFile). Solo lectura; cuenta como remoto para el resto de la VFS. */
  mountLazy(path, lazyFile) {
    path = normalizePath(path);
    if (!path.startsWith('/media/')) throw new Error(`Lazy files are only allowed under /media: ${path}`);
    if (this.directories.has(path) || this.files.has(path)) throw new Error(`Cannot mount lazy file over ${path}`);
    this.ensureParents(path);
    this.lazy.set(path, lazyFile); this.remote.set(path, lazyFile.length); this.childIndex.link(path);
    this.metadata.set(path, { directory:false, modified:Date.now(), readOnly:true });
    this.readOnlyMounted.add(path);
    ringTrace(`vfs#mountLazy ${path} bytes=${lazyFile.length}`);
  }
  isReadOnlyMounted(path) { return this.readOnlyMounted.has(normalizePath(path)); }
  exists(path) { path = normalizePath(path); return this.files.has(path) || this.directories.has(path) || this.remote.has(path); }
  isDirectory(path) { return this.directories.has(normalizePath(path)); }
  canWrite(path) {
    path = normalizePath(path);
    return (path === '/Zomboid' || writable(path)) && this.exists(path) && !this.metadata.get(path)?.readOnly;
  }
  mkdir(path) {
    path = normalizePath(path);
    if (this.exists(path) || !writable(path) || !this.canWrite(parent(path)) || !this.isDirectory(parent(path))) return false;
    this.directories.add(path);
    this.metadata.set(path, { directory:true, modified:Date.now(), readOnly:false });
    this.changed(path, 'create');
    ringTrace(`vfs#mkdir ${path}`);
    return true;
  }
  createFile(path) {
    path = normalizePath(path);
    if (this.files.has(path)) return false;
    if (this.exists(path) || !this.isDirectory(parent(path)) || !this.canWrite(parent(path))) throw new Error(`Cannot create: ${path}`);
    this.writeFile(path, new Uint8Array()); return true;
  }
  children(path) {
    path = normalizePath(path);
    if (!this.isDirectory(path)) return null;
    return [...(this.childIndex.map.get(path) ?? [])].sort();
  }
  lastModified(path) { return this.metadata.get(normalizePath(path))?.modified ?? 0; }
  setAttribute(path, key, value) {
    path = normalizePath(path);
    if (!writable(path) || !this.exists(path)) return false;
    this.metadata.set(path, { ...this.metadata.get(path), directory:this.isDirectory(path), [key]:value }); return true;
  }
  /** Honest observability for the reads the game cannot satisfy: java.io.FileNotFoundException in
   * the game always comes from here, and DebugType.Shader is off in the pinned build, so without
   * this record there is no way to know which path IndieFileLoader actually tried. Recording only;
   * the ENOENT still propagates unchanged. */
  noteMissing(path) {
    const runtime = globalThis.pzRuntime ?? (globalThis.pzRuntime = {});
    const list = runtime.missingPaths ?? (runtime.missingPaths = []);
    const seen = runtime.missingPathCounts ?? (runtime.missingPathCounts = {});
    seen[path] = (seen[path] ?? 0) + 1;
    if (!list.includes(path) && list.length < 200) list.push(path);
  }
  readFile(path) {
    path = normalizePath(path);
    if (!this.files.has(path)) {
      if (this.remote.has(path)) return this.readRemoteFile(path);
      this.noteMissing(path); throw new Error(`ENOENT: ${path}`);
    }
    return this.files.get(path).slice();
  }
  readInto(path, position, target) {
    path = normalizePath(path);
    const bytes = this.files.get(path);
    if (!bytes && this.remote.has(path)) {
      if (!Number.isSafeInteger(position) || position < 0) throw new Error('Invalid file offset');
      return this.readRemoteInto(path, position, target);
    }
    if (!bytes) { this.noteMissing(path); throw new Error(`ENOENT: ${path}`); }
    if (!Number.isSafeInteger(position) || position < 0) throw new Error('Invalid file offset');
    const count = Math.min(target.length, Math.max(0, bytes.length - position));
    target.set(bytes.subarray(position, position + count)); return count;
  }
  resize(path, length) {
    path = normalizePath(path);
    const previous = this.files.get(path);
    if (!previous) throw new Error(`ENOENT: ${path}`);
    if (!Number.isSafeInteger(length) || length < 0 || this.byteLength - previous.length + length > this.maxBytes)
      throw new Error('Invalid file size or VFS memory budget exceeded');
    if (!this.canWrite(path)) throw new Error(`EROFS: ${path}`);
    const bytes = new Uint8Array(length); bytes.set(previous.subarray(0, length));
    this.writeFile(path, bytes);
  }
  writeAt(path, position, data) {
    path = normalizePath(path);
    const previous = this.files.get(path);
    if (!previous) throw new Error(`ENOENT: ${path}`);
    if (!Number.isSafeInteger(position) || position < 0) throw new Error('Invalid file offset');
    const size = Math.max(previous.length, position + data.length);
    if (this.byteLength - previous.length + size > this.maxBytes) throw new Error('VFS memory budget exceeded');
    if (!this.canWrite(path)) throw new Error(`EROFS: ${path}`);
    const bytes = new Uint8Array(size); bytes.set(previous); bytes.set(data, position);
    this.writeFile(path, bytes);
  }
  writeFile(path, data) {
    path = normalizePath(path);
    if (!writable(path) || this.metadata.get(path)?.readOnly) throw new Error(`EROFS: ${path}`);
    this.store(path, data);
  }
  remove(path) {
    path = normalizePath(path);
    if (!writable(path)) throw new Error(`EROFS: ${path}`);
    if (this.isDirectory(path)) {
      if (this.children(path).length) throw new Error(`ENOTEMPTY: ${path}`);
      this.directories.delete(path);
    } else if (!this.files.delete(path)) throw new Error(`ENOENT: ${path}`);
    this.metadata.delete(path);
    this.changed(path, 'delete');
  }
  rename(from, to) {
    from = normalizePath(from); to = normalizePath(to);
    if (!writable(from) || !writable(to)) throw new Error('EROFS: rename requires /Zomboid paths');
    if (!this.exists(from)) throw new Error(`ENOENT: ${from}`);
    if (from === to) return;
    if (!this.isDirectory(parent(to)) || !this.canWrite(parent(to)) || to.startsWith(from + '/')) throw new Error('Invalid rename destination');
    if (this.exists(to)) {
      if (this.isDirectory(from) !== this.isDirectory(to)) throw new Error('Rename type mismatch');
      this.remove(to);
    }
    const moves = [...this.directories, ...this.files.keys()].filter(p => p === from || p.startsWith(from + '/'));
    for (const old of moves) {
      const destination = to + old.slice(from.length);
      if (this.directories.delete(old)) this.directories.add(destination);
      else { this.files.set(destination, this.files.get(old)); this.files.delete(old); }
      if (this.metadata.has(old)) { this.metadata.set(destination, this.metadata.get(old)); this.metadata.delete(old); }
      this.changed(old, 'delete'); this.changed(destination, 'create');
    }
  }
  list(prefix = '/') {
    prefix = normalizePath(prefix);
    return [...this.files.keys()].filter(path => prefix === '/' || path.startsWith(prefix + '/')).sort();
  }
  flush() {
    if (!this.database) return Promise.reject(new Error('VFS must be opened before flush'));
    ringTrace(`vfs#flush:entered pendingFiles=${[...this.files.keys()].filter(writable).length}`);
    const persistent = (key) => writable(key) && !this.readOnlyMounted.has(key);
    const snapshot = [...this.files].filter(([key]) => persistent(key)).map(([key, data]) => [key, data.slice()]);
    const metadata = [...this.metadata].filter(([key]) => persistent(key)).map(([key, value]) => [key, { ...value }]);
    // Serialize flushes, but let the caller retry after an aborted transaction.
    const operation = this.pending.catch(() => {}).then(async () => {
      const transaction = this.database.transaction(['files', 'metadata'], 'readwrite', { durability:'strict' });
      const done = transactionDone(transaction);
      const store = transaction.objectStore('files');
      store.clear();
      for (const [key, bytes] of snapshot) store.put(bytes, key);
      const meta = transaction.objectStore('metadata'); meta.clear();
      for (const [key, value] of metadata) meta.put(value, key);
      await done;
    });
    this.pending = operation;
    return operation;
  }
  /** Round 80: incremental persistence of the given /Zomboid paths in one IndexedDB transaction.
   * The state of every path is captured synchronously (a file's bytes, a directory, or its absence)
   * and queued behind any full flush(), so the database always reflects some real VFS state. */
  flushChanges(paths) {
    if (!this.database) return Promise.reject(new Error('VFS must be opened before flush'));
    const entries = [];
    for (const key of new Set(paths)) {
      if (!writable(key) || this.readOnlyMounted.has(key)) continue;
      const bytes = this.files.get(key), meta = this.metadata.get(key);
      entries.push([key, bytes ? bytes.slice() : null, (bytes || this.directories.has(key)) && meta ? { ...meta } : null]);
    }
    if (!entries.length) return this.pending;
    const operation = this.pending.catch(() => {}).then(async () => {
      const transaction = this.database.transaction(['files', 'metadata'], 'readwrite', { durability:'strict' });
      const done = transactionDone(transaction);
      const store = transaction.objectStore('files'), meta = transaction.objectStore('metadata');
      for (const [key, bytes, value] of entries) {
        if (bytes) store.put(bytes, key); else store.delete(key);
        if (value) meta.put(value, key); else meta.delete(key);
      }
      await done;
    });
    this.pending = operation;
    return operation;
  }
  /** Round 80: the game's java.io writes (saves, presets, latestSave.ini) only reached IndexedDB when
   * SQLite or FileChannel.force() happened to call flush(). Persist every /Zomboid change once the VFS
   * has been quiet for `quietMs` (at most `maxDelayMs` after the first pending change). */
  autoFlush({ quietMs = 2000, maxDelayMs = 15000 } = {}) {
    const stats = this.autoFlushStats = { batches:0, paths:0, errors:0, lastError:null, lastMs:0 };
    let dirty = new Set(), timer = null, first = 0;
    const run = () => {
      const paths = [...dirty]; dirty = new Set(); timer = null; first = 0;
      const t0 = performance.now();
      this.flushChanges(paths).then(() => { stats.batches++; stats.paths += paths.length; stats.lastMs = performance.now() - t0; },
        error => { stats.errors++; stats.lastError = String(error); for (const p of paths) dirty.add(p); console.warn('VFS autoFlush:', error); });
    };
    const stop = this.onChange(path => {
      if (!writable(path) || this.readOnlyMounted.has(path)) return;
      dirty.add(path);
      const now = Date.now(); if (!first) first = now;
      clearTimeout(timer);
      timer = setTimeout(run, Math.max(0, Math.min(quietMs, first + maxDelayMs - now)));
    });
    return () => { stop(); clearTimeout(timer); if (dirty.size) run(); };
  }
  async close() { await this.pending; this.database?.close(); this.database = null; }
}
