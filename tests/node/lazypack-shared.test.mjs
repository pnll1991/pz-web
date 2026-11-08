// Ronda 92: modo compartido de los packs perezosos (lazypack.mjs): tramos en un SharedArrayBuffer que llena un Worker, una lectura que espera al
// tramo en vuelo en vez de pedirlo otra vez, y el hint de lectura que ordena el relleno. Datos sintéticos (bytes al azar), sin nada del juego.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { Worker } from 'node:worker_threads';
import { LazyFile, fillShared, sharedLayout, EMPTY, FETCHING, READY, MAIN } from '../../port/runtime/lazypack.mjs';

const sha = bytes => createHash('sha256').update(bytes).digest('hex');
// Archivo: 10 bytes de cabecera + 4 tramos de datos.
const head = new Uint8Array(randomBytes(10)), datas = [700, 300, 500, 200].map(n => new Uint8Array(randomBytes(n)));
const whole = new Uint8Array(Buffer.concat([head, ...datas]));
const parts = [[0, 10, null]]; { let at = 10; for (const d of datas) { parts.push([at, d.length, sha(d)]); at += d.length; } }
const URL_ = 'http://127.0.0.1/packs/x.bin';

/** fetch falso que sirve `whole` por Range y anota el orden de los pedidos. */
const server = ({ delay = 0, gate = null } = {}) => {
  const log = [];
  const fetchImpl = async (url, options) => {
    const [, a, b] = /bytes=(\d+)-(\d+)/.exec(options.headers.Range); log.push([Number(a), String(url).includes('fill=1')]);
    if (gate) await gate(Number(a)); if (delay) await new Promise(r => setTimeout(r, delay));
    return new Response(whole.slice(Number(a), Number(b) + 1), { status: 206 });
  };
  return { fetchImpl, log };
};
const make = (extra = {}) => {
  const syncs = [];
  const file = new LazyFile({ length: whole.length, parts, head, url: URL_, shared: true,
    syncGet: (url, start, end) => { syncs.push(start); return whole.slice(start, end); }, ...extra });
  return { file, syncs };
};
const job = file => ({ url: file.url, starts: [...file.starts], lens: [...file.lens], sums: file.sums, data: file.data.buffer, state: file.state.buffer });

test('sharedLayout: los tramos de datos van seguidos; las cabeceras no ocupan lugar', () => {
  const { dataAt, total } = sharedLayout(Float64Array.from([10, 700, 300]), [null, 'a', 'b']);
  assert.deepEqual([...dataAt], [0, 0, 700]); assert.equal(total, 1000);
});

test('fillShared llena todo empezando por el hint, y las lecturas ya no piden nada', async () => {
  const { file, syncs } = make(); const { fetchImpl, log } = server();
  Atomics.store(file.state, file.state.length - 1, 3); // el juego acaba de leer la parte 2: lo que sigue es la 3
  const posted = [];
  const result = await fillShared({ ...job(file), concurrency: 1, fetchImpl, post: m => posted.push(m.chunk) });
  assert.equal(result.filled, 4); assert.equal(result.left, 0); assert.equal(result.failed, 0);
  assert.deepEqual(log.map(([at]) => file.partAt(at)), [3, 4, 1, 2], 'primero desde el hint, después vuelve al principio');
  assert.ok(log.every(([, fill]) => fill), 'pedidos marcados ?fill=1');
  assert.deepEqual(posted, [3, 4, 1, 2]);
  assert.deepEqual(file.readAll(), whole); assert.deepEqual(syncs, []);
  assert.deepEqual(file.missing(), []);
});

test('una lectura de un tramo que el Worker está bajando lo espera (otro hilo lo termina) y no hace XHR', async () => {
  const { file, syncs } = make();
  Atomics.store(file.state, 1, FETCHING); // el "Worker" lo tiene en vuelo
  const helper = new Worker(`
    const { workerData: { data, state, at, bytes } } = require('node:worker_threads');
    setTimeout(() => { const s = new Int32Array(state); Atomics.store(s, 1, 5); new Uint8Array(data).set(bytes, at); Atomics.store(s, 1, 2); }, 150);`,
  { eval: true, workerData: { data: file.data.buffer, state: file.state.buffer, at: 0, bytes: datas[0] } });
  await new Promise(r => helper.once('online', r));
  const out = new Uint8Array(datas[0].length);
  file.read(10, out); // espera activa: bloquea este hilo hasta que el otro marque READY
  await helper.terminate();
  assert.deepEqual(out, datas[0]); assert.deepEqual(syncs, []);
  assert.equal(file.stats.waited, 1); assert.ok(file.stats.waitMs >= 100, `esperó ${file.stats.waitMs} ms`); assert.equal(file.stats.sync, 0);
});

test('si el Worker no termina a tiempo, la lectura se queda con el tramo (MAIN) y el Worker descarta su copia', async () => {
  const { file, syncs } = make({ waitMs: 30 });
  let release; const gate = new Promise(r => { release = r; });
  const { fetchImpl } = server({ gate: at => at === 10 ? gate : null });
  const filling = fillShared({ ...job(file), concurrency: 1, fetchImpl });
  await new Promise(r => setTimeout(r, 20)); // el relleno ya tomó la parte 1 y está colgado
  assert.equal(Atomics.load(file.state, 1), FETCHING);
  const out = new Uint8Array(5); file.read(10, out);
  assert.deepEqual(out, datas[0].subarray(0, 5)); assert.deepEqual(syncs, [10]);
  assert.equal(file.stats.waitTimeouts, 1); assert.equal(Atomics.load(file.state, 1), READY);
  release();
  const result = await filling;
  assert.equal(result.stolen, 1); assert.equal(result.filled, 3); assert.deepEqual(file.readAll(), whole);
});

test('put() y fetchChunk no pisan un tramo en vuelo del Worker', async () => {
  const { file } = make();
  Atomics.store(file.state, 2, FETCHING);
  assert.equal(file.put(2, datas[1], 'restored'), false);
  assert.equal(file.put(3, datas[2], 'restored'), true); assert.equal(file.stats.restored, 1);
  assert.equal(file.has(3), true); assert.equal(file.has(2), false);
  assert.equal(file.put(3, datas[2]), false, 'una vez');
});

test('una lectura con XHR que falla deja el tramo libre otra vez', () => {
  const file = new LazyFile({ length: whole.length, parts, head, url: URL_, shared: true, syncGet: () => { throw new Error('EIO: red'); } });
  assert.throws(() => file.read(10, new Uint8Array(4)), /EIO/);
  assert.equal(Atomics.load(file.state, 1), EMPTY);
});

/** "Worker" en el mismo hilo con la interfaz de un Worker del navegador. */
const fakeWorker = (fetchImpl, { fail = false } = {}) => {
  const w = { terminated: false, terminate() { this.terminated = true; } };
  w.postMessage = message => queueMicrotask(async () => {
    if (fail) { Atomics.store(new Int32Array(message.state), 1, FETCHING); w.onerror(new Error('module load failed')); return; }
    const result = await fillShared({ ...message, fetchImpl, post: m => w.onmessage({ data: m }) });
    w.onmessage({ data: { done: true, ...result } });
  });
  return w;
};

test('fill() con worker: cuenta lo que trajo, avisa a la caché, y mientras corre la precarga no pide tramos aparte', async () => {
  const fetched = []; const { file } = make({ onFetch: (i, bytes) => fetched.push([i, bytes.length]) });
  const { fetchImpl, log } = server({ delay: 5 }); const worker = fakeWorker(fetchImpl);
  const filling = file.fill({ worker, concurrency: 2 });
  assert.equal(file.workerActive, true);
  assert.equal(await file.fetchChunk(1, { fetchImpl }), false, 'la precarga no duplica lo que trae el Worker');
  const result = await filling;
  assert.equal(result.filled, 4); assert.equal(file.stats.filled, 4); assert.equal(file.stats.loaded, 4);
  assert.equal(file.workerActive, false); assert.equal(worker.terminated, true);
  assert.equal(log.length, 4); assert.deepEqual(fetched.map(([i]) => i).sort(), [1, 2, 3, 4]);
  assert.deepEqual(file.readAll(), whole);
});

test('si el Worker se cae, sus tramos en vuelo vuelven a estar libres y fill() en este hilo los trae', async () => {
  const { file, syncs } = make(); const { fetchImpl } = server();
  const result = await file.fill({ worker: fakeWorker(fetchImpl, { fail: true }) });
  assert.match(result.error, /module load failed/);
  assert.equal(Atomics.load(file.state, 1), EMPTY); assert.equal(file.workerActive, false);
  const again = await file.fill({ fetchImpl, concurrency: 2 });
  assert.equal(again.filled, 4); assert.deepEqual(file.readAll(), whole); assert.deepEqual(syncs, []);
});

test('sin modo compartido todo sigue como antes', async () => {
  const syncs = [];
  const file = new LazyFile({ length: whole.length, parts, head, url: URL_, syncGet: (u, s, e) => { syncs.push(s); return whole.slice(s, e); } });
  assert.equal(file.shared, false); assert.equal(file.state, undefined);
  assert.deepEqual(file.readAll(), whole); assert.equal(syncs.length, 4); assert.equal(file.stats.sync, 4);
  assert.equal(Atomics.load !== undefined && MAIN, 3);
});
