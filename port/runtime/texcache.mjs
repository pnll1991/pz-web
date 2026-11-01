// Ronda 81 (frente 2): caché en IndexedDB de las páginas de texturas ya decodificadas por el juego
// (zombie.asset.FileTask_LoadPackImage.call: PNG -> ImageData + máscara + mipmaps). La usa
// port.runtime.PackImageCache (Java); acá sólo se guardan/leen bytes.
//
// Clave = build + mask/flags/formato del códec (lo arma Java) + SHA-256 del PNG exacto de la página.
// El build (ETag de boot.wasm) invalida todo: al abrir con otro build se vacía el almacén, así nunca
// conviven datos de dos compilaciones. ?texcache=1 la activa; ?nocache=1 o ?texcache=0 la apagan (call()
// original puro); ?texcache=verify compara cada acierto con el original y devuelve siempre el original.
const DB = 'pz-web-texcache-v1', STORE = 'pages', META = 'meta';
const hex = buffer => [...new Uint8Array(buffer)].map(v => v.toString(16).padStart(2, '0')).join('');
const promised = request => new Promise((resolve, reject) => {
  request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
});

export class TextureCache {
  /** mode: 0 apagada, 1 activa, 2 verificación. */
  constructor({ mode = 1, build = '', maxPendingBytes = 256 << 20 } = {}) {
    this.mode = mode; this.build = build; this.db = null; this.maxPendingBytes = maxPendingBytes;
    this.pendingBytes = 0; this.writes = Promise.resolve();
    this.stats = { hit:0, miss:0, stored:0, storedBytes:0, readBytes:0, lookupMs:0, dropped:0, writeErrors:0,
      verifyOk:0, verifyBad:0, unparsed:0, uncovered:0, lookupError:0, restoreError:0, cleared:0, lastError:null };
  }

  static fromLocation(search, build) {
    const q = new URLSearchParams(search), v = q.get('texcache');
    // Ronda 81: opt-in (?texcache=1) mientras otros frentes miden arranques en frío con el mismo build.
    const mode = q.get('nocache') === '1' || v === '0' || v === null ? 0 : v === 'verify' ? 2 : 1;
    return new TextureCache({ mode, build });
  }

  async open() {
    if (this.mode === 0) return this;
    if (!this.build) throw new Error('texcache: falta el id del build');
    const request = indexedDB.open(DB, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
      if (!db.objectStoreNames.contains(META)) db.createObjectStore(META);
    };
    this.db = await promised(request);
    const tx = this.db.transaction([META, STORE], 'readwrite');
    const previous = await promised(tx.objectStore(META).get('build'));
    if (previous !== this.build) {
      // Otro build (o primera vez): nada de lo guardado vale; se borra antes de usar la caché.
      tx.objectStore(STORE).clear(); tx.objectStore(META).put(this.build, 'build');
      this.stats.cleared = previous == null ? 0 : 1;
    }
    await new Promise((resolve, reject) => { tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error); });
    return this;
  }

  note(kind) { if (kind in this.stats) this.stats[kind]++; }

  /** png: copia propia (Java ya la sacó de la memoria lineal). Devuelve {key, hit, meta, levels}. */
  async lookup(png, salt) {
    const t0 = performance.now();
    const key = `${this.build}|${salt}|${hex(await crypto.subtle.digest('SHA-256', png))}`;
    const record = this.db ? await promised(this.db.transaction(STORE).objectStore(STORE).get(key)) : undefined;
    this.stats.lookupMs += performance.now() - t0;
    if (!record) { this.stats.miss++; return { key, hit:false }; }
    const meta = new Uint8Array(record.meta), levels = record.levels.map(b => new Uint8Array(b));
    this.stats.readBytes += meta.length + levels.reduce((n, l) => n + l.length, 0);
    return { key, hit:true, meta, levels };
  }

  // Escritura: begin/addLevel copian YA (las vistas apuntan a memoria lineal que puede moverse); commit encola.
  begin(entry, meta, count) {
    if (!this.db || this.mode !== 1) return false;
    entry.pending = { meta:meta.slice().buffer, levels:new Array(count), bytes:meta.length };
    return true;
  }
  addLevel(entry, index, view) {
    const p = entry.pending; if (!p) return;
    if (p.bytes + view.length + this.pendingBytes > this.maxPendingBytes) { p.over = true; return; }
    p.levels[index] = view.slice().buffer; p.bytes += view.length;
  }
  commit(entry) {
    const p = entry.pending; entry.pending = null;
    if (!p) return;
    if (p.over || p.levels.some(l => !l)) { this.stats.dropped++; return; }
    this.pendingBytes += p.bytes;
    const record = { meta:p.meta, levels:p.levels };
    this.writes = this.writes.then(() => new Promise(resolve => {
      const tx = this.db.transaction(STORE, 'readwrite');
      tx.objectStore(STORE).put(record, entry.key);
      tx.oncomplete = () => { this.stats.stored++; this.stats.storedBytes += p.bytes; this.pendingBytes -= p.bytes; resolve(); };
      tx.onerror = tx.onabort = () => { this.stats.writeErrors++; this.stats.lastError = String(tx.error); this.pendingBytes -= p.bytes; resolve(); };
    }));
  }

  sameMeta(entry, meta, count) {
    const a = entry.meta;
    if (!a || a.length !== meta.length || entry.levels.length !== count) return false;
    for (let i = 0; i < a.length; i++) if (a[i] !== meta[i]) return false;
    return true;
  }
  sameLevel(entry, index, view) {
    const a = entry.levels[index];
    if (!a || a.length !== view.length) return false;
    for (let i = 0; i < a.length; i++) if (a[i] !== view[i]) return false;
    return true;
  }

  /** Para pruebas/diagnóstico: entradas y bytes guardados. */
  async usage() {
    if (!this.db) return { entries:0 };
    const entries = await promised(this.db.transaction(STORE).objectStore(STORE).count());
    const estimate = navigator.storage?.estimate ? await navigator.storage.estimate() : null;
    return { entries, usage:estimate?.usage ?? null, quota:estimate?.quota ?? null };
  }
}
