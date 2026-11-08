// Ronda 89: packs "perezosos". Un pack enorme cuyo archivo el juego lee por tramos (Tiles2x.pack, 321 MB: cabeceras de 369 páginas, saltando cada
// PNG con skip, y después cada PNG) no se baja entero antes de arrancar. El índice (scripts/lazy-packs.mjs) lo parte en partes contiguas que
// cubren [0, length):
//   - partes de cabecera (sha256 null): bytes que vienen, seguidos, de un blob chico `head` que se baja como un pack normal;
//   - partes de datos [at, length, sha256]: un tramo del blob grande, que se pide con Range.
// Medido (docs/analisis-carga-inicial.md): el juego termina leyendo los 369 PNG durante la carga del mundo, así que NO se ahorran bytes; lo que se
// gana es arrancar sin esperar 321 MB: los tramos se bajan en paralelo mientras arranca el resto (fill) y lo que el juego pida antes se lee con un
// XHR síncrono (java.io es síncrono). Cada tramo se verifica contra su sha256 antes de usarse (el pack sigue siendo contenido direccionado).
//
// Ronda 92 (modo compartido, `shared: true`): el relleno corre en un Worker (lazyfill-worker.mjs) y escribe cada tramo verificado en un
// SharedArrayBuffer; el estado de cada tramo es un Int32 compartido. Así el relleno avanza aunque el hilo principal esté ocupado (antes, mientras el
// juego encadenaba lecturas síncronas, las respuestas del relleno quedaban sin procesar y el juego leía todo lo demás por XHR síncrono), una lectura
// de un tramo que el Worker ya está bajando ESPERA a ese pedido (espera activa con Atomics.load: en el hilo principal no hay Atomics.wait) en vez
// de pedirlo otra vez, y el Worker sigue la posición de lectura del juego (`hint`) para bajar primero lo que viene.
import { sha256Hex } from './sha256.mjs';

const CACHE = 'pz-web-chunks-v1';
const ENTRY = '/__pz-chunks/';
const hex = buffer => [...new Uint8Array(buffer)].map(v => v.toString(16).padStart(2, '0')).join('');
const digest = async bytes => hex(await crypto.subtle.digest('SHA-256', bytes));

/** Estados de un tramo en modo compartido (Int32Array sobre SharedArrayBuffer; la última celda es el hint de lectura + 1). */
export const EMPTY = 0, FETCHING = 1, READY = 2, MAIN = 3, FAILED = 4, WRITING = 5;
const now = () => performance.now();

/** Desplazamiento de cada tramo de datos dentro del buffer compartido (las partes de cabecera no ocupan lugar). */
export function sharedLayout(lens, sums) {
  const dataAt = new Float64Array(lens.length); let total = 0;
  for (let i = 0; i < lens.length; i++) { dataAt[i] = total; if (sums[i] !== null) total += lens[i]; }
  return { dataAt, total };
}

export class LazyFile {
  /**
   * @param {object} o
   * @param {number} o.length   largo total del archivo
   * @param {Array<[number, number, string|null]>} o.parts   [at, length, sha256|null], contiguas y ordenadas
   * @param {Uint8Array} o.head  bytes de las partes de cabecera, en orden
   * @param {string} o.url       URL del blob con los tramos de datos (se pide con Range)
   * @param {(url:string, start:number, end:number) => Uint8Array} o.syncGet  GET síncrono de [start, end) de esa URL (ya trae `?r=`)
   * @param {(url:string, range:string) => Uint8Array|null} [o.takePrefetched]  bytes ya traídos por la precarga (se usan una vez)
   * @param {(index:number, bytes:Uint8Array) => void} [o.onFetch]   cada tramo traído de la red (para guardarlo en la caché persistente)
   * @param {boolean} [o.shared]  ronda 92: tramos en un SharedArrayBuffer que puede llenar un Worker (ver arriba)
   * @param {number} [o.waitMs]   cuánto espera una lectura a un tramo que el Worker está bajando antes de pedirlo por su cuenta
   */
  constructor({ length, parts, head, url, syncGet, takePrefetched = () => null, onFetch = () => {}, shared = false, waitMs = 20000 }) {
    this.length = length; this.url = url; this.head = head;
    this.syncGet = syncGet; this.takePrefetched = takePrefetched; this.onFetch = onFetch; this.waitMs = waitMs;
    this.starts = new Float64Array(parts.length); this.lens = new Float64Array(parts.length);
    this.sums = parts.map(part => part[2]); this.headAt = new Float64Array(parts.length);
    let cursor = 0, headCursor = 0;
    parts.forEach(([at, len, sum], index) => {
      if (at !== cursor || !Number.isSafeInteger(len) || len <= 0) throw new Error('Invalid lazy pack parts');
      this.starts[index] = at; this.lens[index] = len; cursor += len;
      if (sum === null) { this.headAt[index] = headCursor; headCursor += len; }
    });
    if (cursor !== length) throw new Error('Lazy pack parts do not cover the file');
    if (headCursor !== head.length) throw new Error('Lazy pack head size mismatch');
    this.chunks = new Map(); // índice de parte -> bytes ya verificados (modo normal)
    this.touched = new Set(); // tramos que el juego leyó (venga de donde venga el dato): dice cuánto del archivo se necesita de verdad
    this.stats = { chunks: parts.filter(part => part[2] !== null).length, loaded: 0, loadedBytes: 0, sync: 0, syncBytes: 0, syncMs: 0,
      prefetched: 0, restored: 0, rejected: 0, filled: 0, totalBytes: parts.reduce((n, part) => n + (part[2] === null ? 0 : part[1]), 0),
      waited: 0, waitMs: 0, waitTimeouts: 0 };
    this.shared = shared;
    if (shared) {
      const { dataAt, total } = sharedLayout(this.lens, this.sums);
      this.dataAt = dataAt; this.data = new Uint8Array(new SharedArrayBuffer(total));
      this.state = new Int32Array(new SharedArrayBuffer(4 * (parts.length + 1)));
      for (let i = 0; i < parts.length; i++) if (!this.isChunk(i)) this.state[i] = READY; // las cabeceras no se bajan
      this.workerActive = false;
    }
  }

  /** Una URL por tramo (el servidor ignora la query): con la misma URL Chrome reutiliza una entrada parcial de su caché, revalida con If-Range
   * y el servidor contesta 200 con el pack entero (321 MB). Mismo motivo que `?r=` en vfs.fetchRange. */
  urlOf(index, background = false) { return `${this.url}?r=${this.starts[index]}-${this.starts[index] + this.lens[index]}${background ? '&fill=1' : ''}`; }
  rangeOf(index) { return `bytes=${this.starts[index]}-${this.starts[index] + this.lens[index] - 1}`; }
  /** Índice de la parte que contiene `position`. */
  partAt(position) {
    let low = 0, high = this.starts.length - 1;
    while (low < high) { const mid = (low + high + 1) >> 1; if (this.starts[mid] <= position) low = mid; else high = mid - 1; }
    return low;
  }
  isChunk(index) { return this.sums[index] !== null; }
  has(index) { return this.shared ? this.isChunk(index) && Atomics.load(this.state, index) === READY : this.chunks.has(index); }
  view(index) { return this.data.subarray(this.dataAt[index], this.dataAt[index] + this.lens[index]); }
  /** Guarda un tramo ya verificado (por la precarga asíncrona, la caché o el relleno). */
  put(index, bytes, source = null) {
    if (!this.isChunk(index) || bytes.length !== this.lens[index]) return false;
    if (this.shared) {
      // Solo un tramo que nadie está bajando: si el Worker lo tiene en vuelo, él lo termina.
      if (Atomics.compareExchange(this.state, index, EMPTY, MAIN) !== EMPTY && Atomics.compareExchange(this.state, index, FAILED, MAIN) !== FAILED) return false;
      this.data.set(bytes, this.dataAt[index]); Atomics.store(this.state, index, READY);
    } else {
      if (this.chunks.has(index)) return false;
      this.chunks.set(index, bytes);
    }
    this.stats.loaded++; this.stats.loadedBytes += bytes.length;
    if (source) this.stats[source]++;
    return true;
  }
  chunk(index) {
    this.touched.add(index);
    if (this.shared) {
      Atomics.store(this.state, this.state.length - 1, index + 1); // el Worker baja primero lo que sigue a esto
      if (this.claimForRead(index)) return this.view(index);
    } else {
      const bytes = this.chunks.get(index);
      if (bytes) return bytes;
    }
    const range = this.rangeOf(index), expected = this.sums[index], len = this.lens[index];
    const ahead = this.takePrefetched(this.urlOf(index), range);
    if (ahead && ahead.length === len && sha256Hex(ahead) === expected) return this.keepSync(index, ahead, 'prefetched');
    if (ahead) this.stats.rejected++;
    const t0 = now();
    let bytes;
    try {
      bytes = this.syncGet(this.urlOf(index), this.starts[index], this.starts[index] + len);
      if (bytes.length !== len || sha256Hex(bytes) !== expected) throw new Error(`EIO: lazy pack chunk integrity mismatch at ${this.starts[index]}`);
    } catch (error) { if (this.shared) Atomics.store(this.state, index, EMPTY); throw error; }
    this.stats.sync++; this.stats.syncBytes += len; this.stats.syncMs += now() - t0;
    this.stats.syncFirstS ??= Math.round(t0 / 100) / 10; this.stats.syncLastS = Math.round(t0 / 100) / 10;
    return this.keepSync(index, bytes, null);
  }
  /** Modo compartido, antes de una lectura: true si el tramo está listo (esperando al Worker si lo tiene en vuelo); si no, lo deja reservado
   * para el hilo principal (MAIN) y devuelve false: lo que sigue es pedirlo con XHR síncrono. */
  claimForRead(index) {
    const state = this.state; let t0 = 0, deadline = 0;
    for (;;) {
      const s = Atomics.load(state, index);
      if (s === READY) { if (t0) { this.stats.waited++; this.stats.waitMs += now() - t0; } return true; }
      if (s === EMPTY || s === FAILED) { if (Atomics.compareExchange(state, index, s, MAIN) === s) return false; continue; }
      if (s === MAIN) return false; // solo pasa si una lectura anterior falló a medias: se vuelve a pedir
      // FETCHING o WRITING: el Worker lo está trayendo; esperarlo cuesta lo que le falta, pedirlo de nuevo costaría una ida y vuelta entera.
      if (!t0) { t0 = now(); deadline = t0 + this.waitMs; }
      if (s === FETCHING && now() > deadline && Atomics.compareExchange(state, index, FETCHING, MAIN) === FETCHING) {
        this.stats.waitTimeouts++; this.stats.waitMs += now() - t0; return false; // el Worker descarta su copia al ver MAIN
      }
    }
  }
  /** Deja en memoria un tramo traído (y verificado) en el hilo principal durante una lectura. */
  keepSync(index, bytes, source) {
    if (this.shared) { this.data.set(bytes, this.dataAt[index]); Atomics.store(this.state, index, READY); bytes = this.view(index); }
    else this.chunks.set(index, bytes);
    this.stats.loaded++; this.stats.loadedBytes += bytes.length;
    if (source) this.stats[source]++;
    this.notify(index, bytes);
    return bytes;
  }
  notify(index, bytes) { try { this.onFetch(index, bytes); } catch { /* la caché es opcional */ } }

  /** Copia a `target` desde `position`; devuelve los bytes copiados (0 en o después del fin). */
  read(position, target) {
    const count = Math.min(target.length, Math.max(0, this.length - position));
    let done = 0;
    while (done < count) {
      const at = position + done, index = this.partAt(at), offset = at - this.starts[index];
      const n = Math.min(count - done, this.lens[index] - offset);
      const source = this.sums[index] === null ? this.head.subarray(this.headAt[index] + offset, this.headAt[index] + offset + n)
        : this.chunk(index).subarray(offset, offset + n);
      target.set(source, done); done += n;
    }
    return count;
  }
  get touchedBytes() { let n = 0; for (const i of this.touched) n += this.lens[i]; return n; }
  readAll() { const out = new Uint8Array(this.length); this.read(0, out); return out; }

  /** Índices de los tramos de datos que faltan, en orden del archivo. */
  missing() { const out = []; for (let i = 0; i < this.sums.length; i++) if (this.isChunk(i) && !this.has(i)) out.push(i); return out; }

  /** Trae (async, con Range) y verifica el tramo `index`; lo deja en memoria. Devuelve false si ya estaba o falló. */
  async fetchChunk(index, { fetchImpl = globalThis.fetch.bind(globalThis), background = false } = {}) {
    // Con el Worker andando, él trae todo (y una lectura sabe esperarlo); un pedido más desde acá sería el mismo tramo dos veces.
    if (this.has(index) || this.workerActive) return false;
    // `?fill=1`: el relleno en segundo plano pide TODO; el dev server no lo anota en el warm-list (si no, la precarga de un jugador nuevo bajaría todo el pack).
    const range = this.rangeOf(index), response = await fetchImpl(this.urlOf(index, background), { headers: { Range: range }, cache: 'no-store' });
    if (response.status !== 206) throw new Error(`Lazy chunk HTTP ${response.status}`);
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.length !== this.lens[index] || await digest(bytes) !== this.sums[index]) throw new Error('Lazy chunk integrity mismatch');
    if (this.put(index, bytes, 'filled')) this.notify(index, this.shared ? this.view(index) : bytes);
    return true;
  }

  /** Relleno en segundo plano de lo que falta. En modo compartido con `worker` (un Worker de lazyfill-worker.mjs, o algo con la misma
   * interfaz postMessage/onmessage) lo hace el Worker; si no, acá, con poca concurrencia. `shouldStop()` corta el relleno de acá. */
  async fill({ concurrency = 2, shouldStop = () => false, fetchImpl, pauseMs = 0, worker = null } = {}) {
    if (this.shared && worker) return this.fillInWorker(worker, concurrency);
    const todo = this.missing(); let next = 0, done = 0, failed = 0;
    const loop = async () => {
      while (next < todo.length && !shouldStop()) {
        try { if (await this.fetchChunk(todo[next++], { fetchImpl, background: true })) done++; } catch { failed++; }
        if (pauseMs) await new Promise(resolve => setTimeout(resolve, pauseMs));
      }
    };
    await Promise.all(Array.from({ length: concurrency }, loop));
    return { filled: done, failed, left: todo.length - next };
  }

  fillInWorker(worker, concurrency) {
    this.workerActive = true; this.stats.worker = true;
    const release = () => { // un Worker caído no deja tramos "en vuelo" para siempre: vuelven a estar libres para una lectura
      this.workerActive = false;
      for (let i = 0; i < this.sums.length; i++) Atomics.compareExchange(this.state, i, FETCHING, EMPTY);
    };
    return new Promise(resolve => {
      worker.onmessage = ({ data }) => {
        if (Number.isInteger(data?.chunk)) {
          const index = data.chunk; this.stats.loaded++; this.stats.loadedBytes += this.lens[index]; this.stats.filled++;
          this.notify(index, this.view(index));
        } else if (data?.done) {
          release(); worker.terminate?.();
          const { done, ...result } = data;
          Object.assign(this.stats, { workerFetchMsMin: result.fetchMsMin, workerFetchMsAvg: result.fetchMsAvg, workerStolen: result.stolen });
          resolve(result);
        }
      };
      worker.onerror = error => { release(); worker.terminate?.(); resolve({ error: String(error?.message ?? error) }); };
      worker.postMessage({ url: this.url, starts: [...this.starts], lens: [...this.lens], sums: this.sums, data: this.data.buffer, state: this.state.buffer, concurrency });
    });
  }
}

/** El relleno del Worker (lazyfill-worker.mjs), aparte para probarlo en Node. Toma tramos libres (EMPTY -> FETCHING) empezando por el hint que
 * deja la última lectura del juego, los trae con Range, los verifica con WebCrypto y los escribe en el buffer compartido (FETCHING -> WRITING ->
 * READY). Si el hilo principal se quedó con uno (lo reservó para una lectura: MAIN) se descarta la copia. `post({ chunk: i })` por cada tramo. */
export async function fillShared({ url, starts, lens, sums, data, state, concurrency = 4, fetchImpl = globalThis.fetch.bind(globalThis), post = () => {} }) {
  const bytesView = new Uint8Array(data), states = new Int32Array(state), n = sums.length, hintAt = states.length - 1;
  const { dataAt } = sharedLayout(lens, sums);
  const result = { filled: 0, failed: 0, stolen: 0, left: 0, fetchMsMin: null, fetchMsAvg: null };
  let fetchMsSum = 0;
  const pick = () => {
    const hint = Math.min(Math.max(Atomics.load(states, hintAt), 0), n);
    for (let k = 0; k < n; k++) {
      const i = (hint + k) % n;
      if (Atomics.load(states, i) === EMPTY && Atomics.compareExchange(states, i, EMPTY, FETCHING) === EMPTY) return i;
    }
    return -1;
  };
  const one = async () => {
    for (let i = pick(); i >= 0; i = pick()) {
      try {
        const t0 = now();
        const response = await fetchImpl(`${url}?r=${starts[i]}-${starts[i] + lens[i]}&fill=1`, { headers: { Range: `bytes=${starts[i]}-${starts[i] + lens[i] - 1}` }, cache: 'no-store' });
        if (response.status !== 206) throw new Error(`Lazy chunk HTTP ${response.status}`);
        const bytes = new Uint8Array(await response.arrayBuffer());
        if (bytes.length !== lens[i] || await digest(bytes) !== sums[i]) throw new Error('Lazy chunk integrity mismatch');
        const ms = now() - t0; fetchMsSum += ms; result.fetchMsMin = Math.round(Math.min(result.fetchMsMin ?? Infinity, ms));
        if (Atomics.compareExchange(states, i, FETCHING, WRITING) !== FETCHING) { result.stolen++; continue; }
        bytesView.set(bytes, dataAt[i]); Atomics.store(states, i, READY);
        result.filled++; post({ chunk: i });
      } catch { if (Atomics.compareExchange(states, i, FETCHING, FAILED) === FETCHING) result.failed++; }
    }
  };
  await Promise.all(Array.from({ length: Math.max(1, concurrency) }, one));
  for (let i = 0; i < n; i++) if (sums[i] !== null && Atomics.load(states, i) !== READY) result.left++;
  result.fetchMsAvg = result.filled ? Math.round(fetchMsSum / result.filled) : null;
  return result;
}


/** Caché persistente de los tramos (Cache API), con clave = sha256 del tramo: contenido direccionado, no necesita versión. */
export class ChunkStore {
  constructor({ cachesImpl = globalThis.caches, maxBytes = 512 << 20 } = {}) {
    this.cachesImpl = cachesImpl; this.cache = null; this.maxBytes = maxBytes; this.known = new Set();
    this.pending = Promise.resolve(); this.stats = { restored: 0, restoredBytes: 0, stored: 0, storedBytes: 0, removed: 0, corrupt: 0, writeErrors: 0, ms: 0 };
  }
  static key(sum) { return new URL(ENTRY + sum, globalThis.location?.href ?? 'http://127.0.0.1/').href; }
  async open() { this.cache = await this.cachesImpl.open(CACHE); return this; }

  /** Mete en cada LazyFile los tramos guardados que su índice conoce (verificados con WebCrypto, fuera del hilo), y borra el resto. */
  async restore(files, { concurrency = 8 } = {}) {
    const t0 = performance.now();
    const wanted = new Map(); // sha256 -> [[file, index]]
    for (const file of files) for (let i = 0; i < file.sums.length; i++) if (file.isChunk(i)) (wanted.get(file.sums[i]) ?? wanted.set(file.sums[i], []).get(file.sums[i])).push([file, i]);
    const requests = await this.cache.keys(); let next = 0, budget = this.maxBytes;
    const worker = async () => {
      while (next < requests.length) {
        const request = requests[next++], sum = new URL(request.url).pathname.split('/').pop();
        if (!wanted.has(sum)) { await this.cache.delete(request); this.stats.removed++; continue; }
        this.known.add(sum);
        try {
          const response = await this.cache.match(request); if (!response) continue;
          const bytes = new Uint8Array(await response.arrayBuffer());
          if (bytes.length > budget) continue;
          if (await digest(bytes) !== sum) { await this.cache.delete(request); this.known.delete(sum); this.stats.corrupt++; continue; }
          budget -= bytes.length;
          for (const [file, index] of wanted.get(sum)) if (file.put(index, bytes, 'restored')) { this.stats.restored++; this.stats.restoredBytes += bytes.length; }
        } catch { /* una entrada ilegible se vuelve a pedir a la red */ }
      }
    };
    await Promise.all(Array.from({ length: concurrency }, worker));
    this.stats.ms = Math.round(performance.now() - t0);
    return this.stats;
  }

  /** Guarda en segundo plano un tramo traído de la red (ya verificado por quien lo trajo). */
  record(file, index, bytes) {
    const sum = file.sums[index];
    if (!this.cache || this.known.has(sum) || this.stats.restoredBytes + this.stats.storedBytes + bytes.length > this.maxBytes) return false;
    this.known.add(sum);
    const copy = bytes.slice();
    this.pending = this.pending.then(async () => {
      try { await this.cache.put(ChunkStore.key(sum), new Response(copy, { headers: { 'Content-Type': 'application/octet-stream' } })); this.stats.stored++; this.stats.storedBytes += copy.length; }
      catch { this.known.delete(sum); this.stats.writeErrors++; }
    });
    return true;
  }
  flush() { return this.pending; }
}

/** Para prefetch.mjs: reconoce las entradas del warm-list que son tramos de estos archivos (`/packs/<hash>.bin` + `bytes=a-b`) y los trae
 * como tramos verificados en vez de dejarlos en vfs.prefetched. Devuelve una promesa con los bytes traídos (0 si ya estaba) o null si no es suyo. */
export function claimPrefetch(files, { fetchImpl } = {}) {
  const byPath = new Map();
  for (const file of files) {
    const ranges = new Map();
    for (let i = 0; i < file.sums.length; i++) if (file.isChunk(i)) ranges.set(file.rangeOf(i), i);
    byPath.set(new URL(file.url, globalThis.location?.href ?? 'http://127.0.0.1/').pathname, { file, ranges });
  }
  return (url, range) => {
    const entry = byPath.get(String(url).split('?')[0]), index = entry?.ranges.get(range);
    if (index === undefined) return null;
    return entry.file.fetchChunk(index, { fetchImpl }).then(fetched => fetched ? entry.file.lens[index] : 0);
  };
}
