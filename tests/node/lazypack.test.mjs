// Packs perezosos (ronda 89): el tool que parte un .pack (scripts/lazy-packs.mjs), la lectura por tramos (lazypack.mjs), la integración con
// preloadPacks/VFS y la caché de tramos. Todo con un .pack sintético propio (formato PZPK generado acá, sin bytes del juego).
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, randomBytes } from 'node:crypto';
import { mkdtempSync, writeFileSync, readFileSync, rmSync, openSync, closeSync, fstatSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { sha256Hex } from '../../port/runtime/sha256.mjs';
import { LazyFile, ChunkStore, claimPrefetch } from '../../port/runtime/lazypack.mjs';
import { preloadPacks } from '../../port/runtime/packs.mjs';
import { prefetchMedia } from '../../port/runtime/prefetch.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';
import { parsePzpk, makeLazy } from '../../scripts/lazy-packs.mjs';
import { FakeCaches } from './fake-storage.mjs';

const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const int = n => { const b = Buffer.alloc(4); b.writeInt32LE(n); return b; };
const str = s => Buffer.concat([int(Buffer.byteLength(s)), Buffer.from(s)]);
/** .pack PZPK sintético: páginas con entradas de rectángulos y un "PNG" (bytes al azar) cada una. */
const makePzpk = pages => Buffer.concat([Buffer.from('PZPK'), int(1), int(pages.length), ...pages.flatMap(({ name, entries, png }) => [
  str(name), int(entries.length), int(0), ...entries.flatMap(e => [str(e), Buffer.alloc(32, 7)]), int(png.length), png])]);
const pngs = [randomBytes(3000), randomBytes(5000), randomBytes(1)];
const pack = makePzpk(pngs.map((png, i) => ({ name: `Page${i}`, entries: [`tile_${i}_a`, `tile_${i}_b`], png })));

const withDir = fn => async () => { const dir = mkdtempSync(path.join(tmpdir(), 'pzlazy-')); try { await fn(dir); } finally { rmSync(dir, { recursive: true, force: true }); } };
const writeBlob = (dir, bytes) => { const name = `${sha(bytes)}.bin`; writeFileSync(path.join(dir, name), bytes); return { name, sha256: sha(bytes), bytes: bytes.length }; };
const writeIndex = (dir, extra = {}) => {
  const blob = writeBlob(dir, pack);
  const index = { schema_version: 1, packs: [blob], files: [{ path: '/media/texturepacks/T.pack', pack: blob.name, offset: 0, length: pack.length, sha256: blob.sha256 }], ...extra };
  writeFileSync(path.join(dir, 'index.json'), JSON.stringify(index)); return { index, blob };
};

test('sha256 síncrono coincide con node:crypto', () => {
  for (const n of [0, 1, 55, 56, 63, 64, 65, 119, 120, 4096, 100003]) { const b = new Uint8Array(randomBytes(n)); assert.equal(sha256Hex(b), sha(b), `n=${n}`); }
  const big = new Uint8Array(randomBytes(300)).subarray(7, 200);
  assert.equal(sha256Hex(big), sha(big), 'vista con byteOffset');
});

test('parsePzpk recorre las páginas sin leer los PNG y cierra en el fin del archivo', withDir(dir => {
  const file = path.join(dir, 'a.pack'); writeFileSync(file, pack);
  const fd = openSync(file, 'r');
  try {
    const { pages, end } = parsePzpk(fd, fstatSync(fd).size);
    assert.equal(end, pack.length);
    assert.deepEqual(pages.map(p => [p.name, p.entries, p.pngLength]), [['Page0', 2, 3000], ['Page1', 2, 5000], ['Page2', 2, 1]]);
    assert.deepEqual(pack.subarray(pages[1].pngStart, pages[1].pngStart + 5000), pngs[1]);
  } finally { closeSync(fd); }
  writeFileSync(file, Buffer.concat([Buffer.from('NOPE'), pack.subarray(4)]));
  const bad = openSync(file, 'r');
  try { assert.throws(() => parsePzpk(bad, pack.length), /firma/); } finally { closeSync(bad); }
}));

test('makeLazy: cabeceras en un blob chico, un tramo con sha256 por PNG, reconstruye el archivo y es idempotente', withDir(async dir => {
  const { blob } = writeIndex(dir);
  const report = await makeLazy(dir, { minBytes: 100 });
  assert.match(report[0], /^lazy /);
  const index = JSON.parse(readFileSync(path.join(dir, 'index.json'), 'utf8'));
  assert.equal(index.packs.length, 2);
  assert.equal(index.packs[0].lazy, true);
  const { head, parts } = index.files[0].lazy;
  const headBytes = readFileSync(path.join(dir, head));
  assert.equal(`${sha(headBytes)}.bin`, head);
  assert.equal(parts.filter(p => p[2] !== null).length, 3);
  // las partes cubren [0, length) y, juntas, reconstruyen el original
  let cursor = 0, headAt = 0; const out = [];
  for (const [at, length, sum] of parts) {
    assert.equal(at, cursor); cursor += length;
    const bytes = sum === null ? headBytes.subarray(headAt, headAt += length) : pack.subarray(at, at + length);
    if (sum !== null) assert.equal(sha(bytes), sum);
    out.push(bytes);
  }
  assert.equal(cursor, pack.length); assert.deepEqual(Buffer.concat(out), pack);
  assert.ok(existsSync(path.join(dir, 'index.json.before-lazy')));
  assert.deepEqual(await makeLazy(dir, { minBytes: 100 }), [], 'segunda pasada: nada que hacer');
  assert.equal(blob.name, index.packs[0].name, 'el blob original no cambia');
}));

test('makeLazy salta lo que no es un .pack entero', withDir(async dir => {
  const bytes = randomBytes(5000), blob = writeBlob(dir, bytes);
  writeFileSync(path.join(dir, 'index.json'), JSON.stringify({ schema_version: 1, packs: [blob], files: [{ path: '/media/lua/x.lua', pack: blob.name, offset: 0, length: 100, sha256: sha(bytes.subarray(0, 100)) }] }));
  const report = await makeLazy(dir, { minBytes: 100 });
  assert.match(report[0], /^salto/);
  assert.equal(JSON.parse(readFileSync(path.join(dir, 'index.json'), 'utf8')).packs[0].lazy, undefined);
}));

// --- LazyFile / VFS / preloadPacks ---
const lazySetup = async dir => {
  writeIndex(dir); await makeLazy(dir, { minBytes: 100 });
  const index = JSON.parse(readFileSync(path.join(dir, 'index.json'), 'utf8'));
  return { index, indexBytes: readFileSync(path.join(dir, 'index.json')) };
};
let savedFetch, requests;
const serve = dir => async (url, options = {}) => {
  const name = String(url).split('/').pop().split('?')[0]; requests.push([name, options.headers?.Range ?? '', String(url).includes('fill=1')]);
  const file = path.join(dir, name);
  if (!existsSync(file)) return new Response('', { status: 404 });
  const bytes = readFileSync(file), range = /^bytes=(\d+)-(\d+)$/.exec(options.headers?.Range ?? '');
  return range ? new Response(bytes.subarray(+range[1], +range[2] + 1), { status: 206 }) : new Response(bytes);
};
before(() => { savedFetch = globalThis.fetch; globalThis.location = new URL('http://127.0.0.1:8090/game.html'); });
after(() => { globalThis.fetch = savedFetch; delete globalThis.location; delete globalThis.pzPackStats; delete globalThis.pzLazyFiles; delete globalThis.pzLazyStore; });

const bootLazy = async (dir, caches, options = {}) => {
  requests = []; globalThis.fetch = serve(dir);
  const vfs = new VirtualFileSystem(); const syncs = [];
  vfs.syncRange = (url, start, end) => { const name = String(url).split('/').pop().split('?')[0]; syncs.push([name, start, end, String(url)]); return new Uint8Array(readFileSync(path.join(dir, name)).subarray(start, end)); };
  await preloadPacks(vfs, '/packs/index.json', { memo: false, cleanup: false, cachesImpl: caches, storage: null, cacheMaxBytes: 1 << 20, chunkCache: true, ...options });
  return { vfs, syncs, stats: globalThis.pzPackStats };
};

test('preloadPacks monta el archivo perezoso sin bajar el blob; la lectura trae solo el tramo necesario, verificado', withDir(async dir => {
  const { index } = await lazySetup(dir); const caches = new FakeCaches();
  const { vfs, syncs, stats } = await bootLazy(dir, caches);
  const requested = requests.map(r => r[0]);
  assert.ok(!requested.includes(index.packs[0].name), 'el blob grande no se baja entero');
  assert.equal(vfs.size('/media/texturepacks/T.pack'), pack.length);
  assert.equal(vfs.kind('/media/texturepacks/T.pack'), 1);
  assert.equal(stats.lazy.chunks, 3);
  // el juego lee la cabecera de la 1.ª página (en el blob chico): sin red
  const header = new Uint8Array(20); assert.equal(vfs.readInto('/media/texturepacks/T.pack', 0, header), 20);
  assert.deepEqual(Buffer.from(header), pack.subarray(0, 20)); assert.equal(syncs.length, 0);
  // leer un trozo del 2.º PNG pide solo ese tramo (Range) una vez
  const page1 = pack.indexOf(pngs[1]); const part = new Uint8Array(100);
  assert.equal(vfs.readInto('/media/texturepacks/T.pack', page1 + 10, part), 100);
  assert.deepEqual(Buffer.from(part), pngs[1].subarray(10, 110));
  vfs.readInto('/media/texturepacks/T.pack', page1 + 500, part);
  assert.equal(syncs.length, 1, 'el tramo quedó en memoria'); assert.deepEqual(syncs[0].slice(1, 3), [page1, page1 + 5000]);
  assert.match(syncs[0][3], new RegExp(`\\.bin\\?r=${page1}-${page1 + 5000}$`), 'una URL por tramo: si no, Chrome revalida con If-Range y el servidor contesta 200 con el pack entero');
  // un read que cruza partes
  const all = vfs.readFile('/media/texturepacks/T.pack'); assert.deepEqual(Buffer.from(all), pack);
  assert.throws(() => vfs.writeFile?.('/media/texturepacks/T.pack', new Uint8Array(1)), /EROFS|read|only/i);
}));

test('un tramo corrupto no llega al juego', withDir(async dir => {
  const { index } = await lazySetup(dir); const { vfs } = await bootLazy(dir, new FakeCaches());
  vfs.syncRange = () => new Uint8Array(3000).fill(1); // mismo largo, otros bytes
  assert.throws(() => vfs.readInto('/media/texturepacks/T.pack', pack.indexOf(pngs[0]), new Uint8Array(10)), /integrity/);
  assert.ok(index);
}));

test('la caché de tramos: la 2.ª carga restaura lo usado sin pedirlo a la red; lo ajeno al índice se borra', withDir(async dir => {
  await lazySetup(dir); const caches = new FakeCaches();
  const first = await bootLazy(dir, caches);
  first.vfs.readFile('/media/texturepacks/T.pack'); // usa los 3 tramos
  await globalThis.pzLazyStore.flush();
  const store = await caches.open('pz-web-chunks-v1');
  assert.equal(store.map.size, 3);
  store.map.set(ChunkStore.key('f'.repeat(64)), new Uint8Array(4)); // entrada de un índice viejo
  const second = await bootLazy(dir, caches);
  assert.equal(second.stats.lazyStore.restored, 3); assert.equal(second.stats.lazyStore.removed, 1);
  assert.deepEqual(Buffer.from(second.vfs.readFile('/media/texturepacks/T.pack')), pack);
  assert.equal(second.syncs.length, 0, 'todo salió de la caché');
  // una entrada corrupta se descarta y el tramo se pide otra vez
  const key = [...store.map.keys()].find(k => k.includes(sha(pngs[0]))); store.map.set(key, new Uint8Array(3000));
  const third = await bootLazy(dir, caches);
  assert.equal(third.stats.lazyStore.corrupt, 1);
  third.vfs.readFile('/media/texturepacks/T.pack'); assert.equal(third.syncs.length, 1);
}));

test('lazy:false baja el pack entero y lo monta como siempre', withDir(async dir => {
  const { index } = await lazySetup(dir);
  const { vfs, syncs } = await bootLazy(dir, new FakeCaches(), { lazy: false });
  assert.ok(requests.some(r => r[0] === index.packs[0].name && r[1] === ''));
  assert.deepEqual(Buffer.from(vfs.readFile('/media/texturepacks/T.pack')), pack); assert.equal(syncs.length, 0);
}));

test('la precarga trae los tramos del warm-list como tramos verificados, y fill() trae el resto', withDir(async dir => {
  const { index } = await lazySetup(dir); const { vfs } = await bootLazy(dir, new FakeCaches());
  const file = globalThis.pzLazyFiles['/media/texturepacks/T.pack'];
  const url = `/packs/${index.packs[0].name}`, ranges = file.missing().map(i => file.rangeOf(i));
  const warm = [[`${url}?r=0-0`, ranges[1]], ['/media/lua/x.lua', 'bytes=0-9']];
  const base = globalThis.fetch;
  globalThis.fetch = async (u, o) => String(u) === '/warm-media.json' ? new Response(JSON.stringify(warm)) : base(u, o);
  const stats = await prefetchMedia(vfs, { fetchImpl: globalThis.fetch, onEntry: claimPrefetch([file]) });
  assert.equal(stats.lazy, 1); assert.equal(stats.lazyBytes, 5000); assert.equal(file.stats.loaded, 1);
  assert.equal(vfs.prefetched.size, 0, 'el tramo no pasa por vfs.prefetched (y /media/lua/x.lua da 404 en este servidor falso)');
  const left = file.missing().length, result = await file.fill({ concurrency: 2 });
  assert.equal(result.filled, left); assert.deepEqual(file.missing(), []);
  assert.ok(requests.slice(-left).every(r => r[2]), 'el relleno va marcado ?fill=1 para que el servidor no lo anote en el warm-list');
  assert.deepEqual(Buffer.from(file.readAll()), pack);
}));

test('LazyFile valida que las partes cubran el archivo', () => {
  assert.throws(() => new LazyFile({ length: 10, parts: [[0, 5, null]], head: new Uint8Array(5), url: 'x', syncGet: () => null }), /cover/);
  assert.throws(() => new LazyFile({ length: 5, parts: [[0, 5, null]], head: new Uint8Array(4), url: 'x', syncGet: () => null }), /head/);
});
