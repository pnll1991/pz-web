// Memo persistente de la traducción GLSL de escritorio -> GLSL ES (glsl.mjs#translateShader). Cada carga preprocesaba,
// parseaba, transformaba y regeneraba los mismos shaders. translateShader es una función pura de (fuente, etapa) y su
// resultado son solo strings, arrays y objetos planos, así que se puede guardar.
//
// - Java pide la traducción de forma síncrona (glShaderSource): load() trae TODO lo guardado a un Map antes de
//   arrancar el juego y translate() consulta ese Map. Lo que no esté se traduce como siempre y se guarda en IndexedDB
//   en segundo plano.
// - Versión = SHA-256 de glsl.mjs y de los archivos del parser servidos. Si cambia el traductor o el parser, se vacía
//   el almacén antes de usarlo (igual que texcache.mjs con el build).
// - Cada acierto devuelve una copia (structuredClone): los llamadores guardan y extienden los arrays del resultado.
// - Los errores de traducción no se guardan: se vuelven a lanzar en cada carga, como antes.
// ?glslcache=0 o ?nocache=1 la apagan; ?glslcache=verify traduce siempre y compara con lo guardado (devuelve lo nuevo).
import { translateShader } from './glsl.mjs';

const DB = 'pz-web-glsl-v1', STORE = 'translations', META = 'meta';
// Todo lo que decide la salida de translateShader: el traductor y el parser que importa (copy_web_vendor.py).
export const TRANSLATOR_FILES = ['/runtime/glsl.mjs', ...['ast/ast-types.js', 'ast/ast.js', 'ast/index.js', 'ast/visit.js',
  'error.js', 'index.js', 'parser/generator.js', 'parser/grammar.js', 'parser/index.js', 'parser/parser.js', 'parser/scope.js',
  'parser/utils.js', 'preprocessor/generator.js', 'preprocessor/index.js', 'preprocessor/preprocessor-node.js',
  'preprocessor/preprocessor-parser.js', 'preprocessor/preprocessor.js'].map(file => `/vendor/glsl-parser/${file}`)];
const promised = request => new Promise((resolve, reject) => {
  request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
});
const hex = buffer => [...new Uint8Array(buffer)].map(v => v.toString(16).padStart(2, '0')).join('');
const keyOf = (source, stage) => `${stage}\0${source}`;

/** SHA-256 de los archivos del traductor, en orden. Un archivo que falta hace fallar la versión (caché apagada). */
export async function translatorVersion(fetchImpl = globalThis.fetch.bind(globalThis), files = TRANSLATOR_FILES) {
  const parts = await Promise.all(files.map(async file => {
    const response = await fetchImpl(file);
    if (!response.ok) throw new Error(`glslcache: ${file} HTTP ${response.status}`);
    return `${file}\0${hex(await crypto.subtle.digest('SHA-256', await response.arrayBuffer()))}`;
  }));
  return `glsl:${hex(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(parts.join('\n'))))}`;
}

export class ShaderCache {
  /** mode: 0 apagada, 1 activa, 2 verificación. */
  constructor({ mode = 1, translate = translateShader, indexedDBImpl = globalThis.indexedDB } = {}) {
    this.mode = indexedDBImpl ? mode : 0; this.translateImpl = translate; this.idb = indexedDBImpl;
    this.db = null; this.version = ''; this.entries = new Map(); this.writes = Promise.resolve();
    this.stats = { loaded:0, loadMs:0, hit:0, miss:0, stored:0, writeErrors:0, translateMs:0, hitMs:0,
      verifyOk:0, verifyBad:0, cleared:0, lastError:null };
  }

  static fromLocation(search, options = {}) {
    const q = new URLSearchParams(search), v = q.get('glslcache');
    return new ShaderCache({ ...options, mode: q.get('nocache') === '1' || v === '0' ? 0 : v === 'verify' ? 2 : 1 });
  }

  /** Abre el almacén con esta versión y trae todas las traducciones guardadas a memoria. */
  async load(version) {
    if (this.mode === 0) return this;
    if (!version) throw new Error('glslcache: falta la versión del traductor');
    const t0 = performance.now();
    this.version = version;
    const request = this.idb.open(DB, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
      if (!db.objectStoreNames.contains(META)) db.createObjectStore(META);
    };
    this.db = await promised(request);
    const tx = this.db.transaction([META, STORE], 'readwrite');
    const previous = await promised(tx.objectStore(META).get('version'));
    if (previous !== version) {
      tx.objectStore(STORE).clear(); tx.objectStore(META).put(version, 'version');
      this.stats.cleared = previous == null ? 0 : 1;
    } else {
      const store = tx.objectStore(STORE);
      const [keys, values] = await Promise.all([promised(store.getAllKeys()), promised(store.getAll())]);
      keys.forEach((key, i) => this.entries.set(key, values[i]));
    }
    await new Promise((resolve, reject) => { tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error); });
    this.stats.loaded = this.entries.size; this.stats.loadMs = Math.round(performance.now() - t0);
    return this;
  }

  /** Mismo contrato que translateShader(source, stage). Síncrono. */
  translate(source, stage) {
    if (this.mode === 0) return this.translateImpl(source, stage);
    const key = keyOf(source, stage), saved = this.entries.get(key);
    if (saved && this.mode === 1) {
      const t0 = performance.now(), copy = structuredClone(saved);
      this.stats.hit++; this.stats.hitMs += performance.now() - t0;
      return copy;
    }
    const t0 = performance.now();
    const result = this.translateImpl(source, stage);
    this.stats.translateMs += performance.now() - t0; this.stats.miss++;
    if (saved) {
      // Verificación: lo guardado tiene que ser idéntico a lo que se acaba de traducir.
      if (JSON.stringify(saved) === JSON.stringify(result)) this.stats.verifyOk++;
      else { this.stats.verifyBad++; this.stats.lastError = `verify: ${stage} difiere`; }
      return result;
    }
    if (this.db && this.mode === 1) this.store(key, structuredClone(result));
    return result;
  }

  store(key, value) {
    this.entries.set(key, value);
    this.writes = this.writes.then(async () => {
      try {
        const tx = this.db.transaction(STORE, 'readwrite');
        tx.objectStore(STORE).put(value, key);
        await new Promise((resolve, reject) => { tx.oncomplete = resolve; tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error); });
        this.stats.stored++;
      } catch (error) { this.stats.writeErrors++; this.stats.lastError = String(error); }
    });
  }

  flush() { return this.writes; }
}
