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
const requestValue = request => new Promise((resolve, reject) => {
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});
const transactionDone = transaction => new Promise((resolve, reject) => {
  transaction.oncomplete = resolve;
  transaction.onabort = () => reject(transaction.error || new Error('IndexedDB transaction aborted'));
  transaction.onerror = () => {}; // onabort is authoritative; never resolve failed transactions.
});

/** Preloaded synchronous reads, writable /Zomboid, atomic IndexedDB snapshots. */
export class VirtualFileSystem {
  constructor({ name = 'pz-web-saves-v1', maxBytes = 256 * 1024 * 1024 } = {}) {
    this.name = name; this.maxBytes = maxBytes;
    this.files = new Map(); this.database = null; this.pending = Promise.resolve();
  }
  async open() {
    const request = indexedDB.open(this.name, 1);
    request.onupgradeneeded = () => request.result.createObjectStore('files');
    this.database = await requestValue(request);
    this.database.onversionchange = () => this.database.close();
    const transaction = this.database.transaction('files', 'readonly');
    const done = transactionDone(transaction);
    const store = transaction.objectStore('files');
    const [keys, values] = await Promise.all([requestValue(store.getAllKeys()), requestValue(store.getAll())]);
    await done;
    let size = this.byteLength;
    for (let i = 0; i < keys.length; i++) {
      const key = normalizePath(keys[i]);
      if (!writable(key)) throw new Error('Invalid persisted VFS path');
      size += values[i].byteLength;
      if (size > this.maxBytes) throw new Error('Persisted VFS exceeds memory budget');
      this.files.set(key, new Uint8Array(values[i]));
    }
    return this;
  }
  get byteLength() { return [...this.files.values()].reduce((n, bytes) => n + bytes.byteLength, 0); }
  store(path, data) {
    if (!(data instanceof Uint8Array)) throw new TypeError('VFS data must be Uint8Array');
    const size = this.byteLength - (this.files.get(path)?.byteLength || 0) + data.byteLength;
    if (size > this.maxBytes) throw new Error('VFS memory budget exceeded');
    this.files.set(path, data.slice());
  }
  mount(path, data) {
    path = normalizePath(path);
    if (!path.startsWith('/media/')) throw new Error('Read-only resources must be under /media');
    this.store(path, data);
  }
  exists(path) { return this.files.has(normalizePath(path)); }
  readFile(path) {
    path = normalizePath(path);
    if (!this.files.has(path)) throw new Error(`ENOENT: ${path}`);
    return this.files.get(path).slice();
  }
  writeFile(path, data) {
    path = normalizePath(path);
    if (!writable(path)) throw new Error(`EROFS: ${path}`);
    this.store(path, data);
  }
  remove(path) {
    path = normalizePath(path);
    if (!writable(path)) throw new Error(`EROFS: ${path}`);
    if (!this.files.delete(path)) throw new Error(`ENOENT: ${path}`);
  }
  rename(from, to) {
    from = normalizePath(from); to = normalizePath(to);
    if (!writable(from) || !writable(to)) throw new Error('EROFS: rename requires /Zomboid paths');
    const value = this.files.get(from);
    if (!value) throw new Error(`ENOENT: ${from}`);
    if (from !== to) { this.files.set(to, value); this.files.delete(from); }
  }
  list(prefix = '/') {
    prefix = normalizePath(prefix);
    return [...this.files.keys()].filter(path => prefix === '/' || path.startsWith(prefix + '/')).sort();
  }
  flush() {
    if (!this.database) return Promise.reject(new Error('VFS must be opened before flush'));
    const snapshot = [...this.files].filter(([key]) => writable(key)).map(([key, data]) => [key, data.slice()]);
    // Serialize flushes, but let the caller retry after an aborted transaction.
    const operation = this.pending.catch(() => {}).then(async () => {
      const transaction = this.database.transaction('files', 'readwrite', { durability:'strict' });
      const done = transactionDone(transaction);
      const store = transaction.objectStore('files');
      store.clear();
      for (const [key, bytes] of snapshot) store.put(bytes, key);
      await done;
    });
    this.pending = operation;
    return operation;
  }
  async close() { await this.pending; this.database?.close(); this.database = null; }
}
