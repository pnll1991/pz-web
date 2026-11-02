import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TextureCache, CODEC_VERSION } from '../../port/runtime/texcache.mjs';

// IndexedDB mínimo y propio: una sola base, almacenes como Map. Las transacciones aplican al instante y
// disparan oncomplete en un microtask; las requests resuelven onsuccess en otro (basta para texcache.mjs).
function fakeIndexedDB(initial = {}) {
  const stores = { pages: new Map(Object.entries(initial.pages || {})), meta: new Map(Object.entries(initial.meta || {})) };
  const req = value => { const r = { result: undefined, error: null }; queueMicrotask(() => { r.result = value(); r.onsuccess?.(); }); return r; };
  const store = name => ({
    get: key => req(() => stores[name].get(key)),
    put: (value, key) => req(() => { stores[name].set(key, value); }),
    delete: key => req(() => { stores[name].delete(key); }),
    clear: () => req(() => { stores[name].clear(); }),
    count: () => req(() => stores[name].size),
  });
  const db = {
    objectStoreNames: { contains: () => true },
    transaction() {
      const tx = {}; setTimeout(() => tx.oncomplete?.(), 0);
      tx.objectStore = store; return tx;
    },
  };
  globalThis.indexedDB = { open() { const r = { result: db }; queueMicrotask(() => r.onsuccess?.()); return r; } };
  return stores;
}

test('fromLocation: interruptores y llave por defecto', () => {
  const c = s => TextureCache.fromLocation(s, '');
  assert.deepEqual([c('').mode, c('').keying], [1, 'codec']);
  assert.equal(c('?texcache=0').mode, 0);
  assert.equal(c('?nocache=1').mode, 0);
  assert.deepEqual([c('?texcache=verify').mode, c('?texcache=verify').keying], [2, 'codec']);
  assert.deepEqual([c('?texcache=build').mode, c('?texcache=build').keying], [1, 'build']);
});

test('con llave por codec el build no cuenta: otro build conserva lo guardado y no hace falta id', async () => {
  const stores = fakeIndexedDB({ pages: { k: 1 }, meta: { scheme: `codec:${CODEC_VERSION}` } });
  const cache = new TextureCache({ build: 'etag-nuevo' });
  await cache.open();
  assert.equal(stores.pages.size, 1);
  assert.equal(cache.stats.cleared, 0);
  assert.match(cache.scheme, /^codec:/);
  await new TextureCache({}).open(); // sin build tampoco falla
});

test('subir la versión del códec vacía una vez', async () => {
  const stores = fakeIndexedDB({ pages: { k: 1 }, meta: { scheme: 'codec:1' } });
  await new TextureCache({ codec: 2 }).open();
  assert.equal(stores.pages.size, 0);
  assert.equal(stores.meta.get('scheme'), 'codec:2');
});

test('base del esquema viejo (meta.build) se vacía una vez y se migra', async () => {
  const stores = fakeIndexedDB({ pages: { viejo: 1 }, meta: { build: 'etag-viejo' } });
  const cache = new TextureCache({});
  await cache.open();
  assert.equal(stores.pages.size, 0);
  assert.equal(cache.stats.cleared, 1);
  assert.equal(stores.meta.has('build'), false);
  assert.equal(stores.meta.get('scheme'), `codec:${CODEC_VERSION}`);
  stores.pages.set('nuevo', 1);
  await new TextureCache({ build: 'otro' }).open(); // segunda apertura: se conserva
  assert.equal(stores.pages.size, 1);
});

test('?texcache=build: llavea por build, exige id y vacía al cambiar de build', async () => {
  const stores = fakeIndexedDB({ pages: { k: 1 }, meta: { scheme: 'build:A' } });
  await assert.rejects(new TextureCache({ keying: 'build' }).open(), /falta el id del build/);
  await new TextureCache({ keying: 'build', build: 'A' }).open();
  assert.equal(stores.pages.size, 1);
  await new TextureCache({ keying: 'build', build: 'B' }).open();
  assert.equal(stores.pages.size, 0);
  assert.equal(stores.meta.get('scheme'), 'build:B');
});

test('lookup: la clave usa la versión del códec, no el build, y cambia con ella', async () => {
  fakeIndexedDB();
  const png = new Uint8Array([1, 2, 3]);
  const a = await new TextureCache({ build: 'A' }).lookup(png, 'm0:f0:c1');
  const b = await new TextureCache({ build: 'B' }).lookup(png, 'm0:f0:c1');
  const c = await new TextureCache({ codec: CODEC_VERSION + 1 }).lookup(png, 'm0:f0:c1');
  const d = await new TextureCache({ keying: 'build', build: 'A' }).lookup(png, 'm0:f0:c1');
  assert.equal(a.key, b.key);
  assert.notEqual(a.key, c.key);
  assert.notEqual(a.key, d.key);
  assert.ok(a.key.startsWith(`c${CODEC_VERSION}|m0:f0:c1|`));
});
