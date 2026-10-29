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

/** Preloaded synchronous reads, writable /Zomboid, atomic IndexedDB snapshots. */
export class VirtualFileSystem {
  constructor({ name = 'pz-web-saves-v1', maxBytes = 256 * 1024 * 1024 } = {}) {
    this.name = name; this.maxBytes = maxBytes;
    this.files = new Map(); this.database = null; this.pending = Promise.resolve();
    this.directories = new Set(['/', '/media', '/Zomboid']);
    this.metadata = new Map();
    this.listeners = new Set();
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
  get byteLength() { return [...this.files.values()].reduce((n, bytes) => n + bytes.byteLength, 0); }
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
  exists(path) { path = normalizePath(path); return this.files.has(path) || this.directories.has(path); }
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
    return [...this.directories, ...this.files.keys()].filter(p => p !== path && parent(p) === path)
      .map(p => p.slice(p.lastIndexOf('/') + 1)).sort();
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
    if (!this.files.has(path)) { this.noteMissing(path); throw new Error(`ENOENT: ${path}`); }
    return this.files.get(path).slice();
  }
  readInto(path, position, target) {
    path = normalizePath(path);
    const bytes = this.files.get(path);
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
    const snapshot = [...this.files].filter(([key]) => writable(key)).map(([key, data]) => [key, data.slice()]);
    const metadata = [...this.metadata].filter(([key]) => writable(key)).map(([key, value]) => [key, { ...value }]);
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
  async close() { await this.pending; this.database?.close(); this.database = null; }
}
