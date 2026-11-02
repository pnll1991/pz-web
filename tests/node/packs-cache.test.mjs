// Packs: los chicos en la Cache API, los grandes en OPFS; en la recarga no se baja nada, lo corrupto se vuelve a bajar
// y los packs de índices viejos se borran. Packs sintéticos propios (bytes generados acá).
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { preloadPacks } from '../../port/runtime/packs.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';
import { FakeCaches, FakeDirectory, fakeStorage } from './fake-storage.mjs';

const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const small = new TextEncoder().encode('print("hola")\n');
const large = Uint8Array.from({ length: 4096 }, (_, i) => (i * 31) & 0xff);
const pack = bytes => ({ name: `${sha(bytes)}.bin`, sha256: sha(bytes), bytes: bytes.length });
const index = {
  schema_version: 1, packs: [pack(small), pack(large)],
  files: [
    { path: '/media/lua/a.lua', pack: pack(small).name, offset: 0, length: small.length, sha256: sha(small) },
    { path: '/media/texturepacks/T.pack', pack: pack(large).name, offset: 0, length: large.length, sha256: sha(large) },
  ],
};
const indexBytes = new TextEncoder().encode(JSON.stringify(index));
let fetched, savedFetch;
before(() => {
  savedFetch = globalThis.fetch;
  globalThis.location = new URL('http://127.0.0.1:8090/game.html');
  globalThis.fetch = async url => {
    const name = String(url).split('/').pop();
    fetched.push(name);
    const body = name === 'index.json' ? indexBytes : name === pack(small).name ? small : name === pack(large).name ? large : null;
    return body ? new Response(body.slice()) : new Response('', { status: 404 });
  };
});
after(() => { globalThis.fetch = savedFetch; delete globalThis.location; delete globalThis.pzPackStats; delete globalThis.pzPackCacheSkipped; });

const boot = async (caches, dir, options = {}) => {
  fetched = [];
  const vfs = new VirtualFileSystem();
  await preloadPacks(vfs, '/packs/index.json', { memo: false, cleanup: true, cachesImpl: caches, storage: dir && fakeStorage(dir), cacheMaxBytes: 1024, ...options });
  const stats = globalThis.pzPackStats;
  await stats.opfsDone; await stats.cleanup;
  return { vfs, stats };
};

test('primera carga baja todo; la recarga no baja ningún pack', async () => {
  const caches = new FakeCaches(), dir = new FakeDirectory();
  const first = await boot(caches, dir);
  assert.deepEqual(first.stats.sources, { network: 2, cache: 0, opfs: 0 });
  assert.equal(dir.writes, 1, 'el pack grande va a OPFS');
  assert.equal((await caches.open('pz-web-packs-v1')).map.size, 1, 'el chico va a la Cache API');
  const second = await boot(caches, dir);
  assert.deepEqual(fetched, ['index.json']);
  assert.deepEqual(second.stats.sources, { network: 0, cache: 1, opfs: 1 });
  assert.equal(dir.writes, 1, 'lo leído de la caché no se reescribe');
  assert.deepEqual([...second.vfs.readFile('/media/texturepacks/T.pack')], [...large]);
  assert.equal(new TextDecoder().decode(second.vfs.readFile('/media/lua/a.lua')), 'print("hola")\n');
});

test('un pack guardado corrupto se borra y se vuelve a bajar', async () => {
  const caches = new FakeCaches(), dir = new FakeDirectory();
  await boot(caches, dir);
  dir.files.get(pack(large).name)[0] ^= 0xff;
  const again = await boot(caches, dir);
  assert.equal(again.stats.corrupt, 1);
  assert.deepEqual(again.stats.sources, { network: 1, cache: 1, opfs: 0 });
  assert.deepEqual([...dir.files.get(pack(large).name)], [...large], 'se reemplaza por la copia verificada');
});

test('se borran los packs que ya no están en el índice; sin OPFS el grande se baja siempre', async () => {
  const caches = new FakeCaches(), dir = new FakeDirectory();
  const stale = `${'0'.repeat(64)}.bin`;
  dir.files.set(stale, new Uint8Array(3));
  await (await caches.open('pz-web-packs-v1')).put(`http://127.0.0.1:8090/packs/${'1'.repeat(64)}.bin`, new Response('x'));
  const { stats } = await boot(caches, dir);
  assert.equal(stats.removed, 2);
  assert.equal(dir.files.has(stale), false);
  const noOpfs = await boot(new FakeCaches(), null);
  assert.deepEqual(noOpfs.stats.sources, { network: 2, cache: 0, opfs: 0 });
  assert.ok(globalThis.pzPackCacheSkipped.some(entry => entry.name === pack(large).name));
});

// Ronda 89 (frente D): un pack leido de la cache local no repite el sha256 completo.
const spyDigest = async run => {
  const subtle = globalThis.crypto.subtle, original = subtle.digest;
  const sizes = [];
  subtle.digest = function (algo, data) { sizes.push(data.byteLength); return original.call(this, algo, data); };
  try { await run(); } finally { subtle.digest = original; }
  return sizes;
};
// Con memo, la pasada por archivo (mismos bytes que el pack en estas fixtures) se saltea y solo queda el digest del pack.
const withMemo = async run => {
  const store = new Map();
  globalThis.localStorage = { getItem: k => store.get(k) ?? null, setItem: (k, v) => store.set(k, v) };
  try { return await run(store); } finally { delete globalThis.localStorage; }
};
const packDigests = sizes => sizes.filter(n => n === small.length || n === large.length).length;

test('un pack de la cache o de OPFS solo se comprueba por tamano; el digest se anota como salteado', async () => {
  const caches = new FakeCaches(), dir = new FakeDirectory();
  await boot(caches, dir);
  let second, sizes;
  await withMemo(async () => {
    await boot(caches, dir, { memo: true }); // guarda el memo del indice
    sizes = await spyDigest(async () => { second = await boot(caches, dir, { memo: true }); });
  });
  assert.deepEqual(second.stats.sources, { network: 0, cache: 1, opfs: 1 });
  assert.equal(second.stats.fullDigest, false);
  assert.deepEqual([...second.stats.digestSkipped].sort(), [pack(small).name, pack(large).name].sort());
  assert.equal(second.stats.digestMs, 0);
  assert.equal(packDigests(sizes), 0, 'con el indice en memo no se hashea ningun pack ni archivo');
});

test('fullDigest y ?packverify=1 vuelven a verificar el pack completo', async () => {
  const caches = new FakeCaches(), dir = new FakeDirectory();
  await boot(caches, dir);
  await withMemo(async () => {
    await boot(caches, dir, { memo: true });
    const forced = await spyDigest(async () => { await boot(caches, dir, { memo: true, fullDigest: true }); });
    assert.equal(packDigests(forced), 2);
  });
  assert.deepEqual(globalThis.pzPackStats.digestSkipped, []);
  globalThis.location = new URL('http://127.0.0.1:8090/game.html?packverify=1');
  try {
    const viaUrl = await withMemo(async () => { await boot(caches, dir, { memo: true }); return spyDigest(async () => { await boot(caches, dir, { memo: true }); }); });
    assert.equal(packDigests(viaUrl), 2);
    assert.equal(globalThis.pzPackStats.fullDigest, true);
    // memo no se pasa: ?nocache=1 tambien fuerza la verificacion completa.
    globalThis.location = new URL('http://127.0.0.1:8090/game.html?nocache=1');
    await boot(caches, dir, { memo: true });
    assert.equal(globalThis.pzPackStats.fullDigest, true);
  } finally { globalThis.location = new URL('http://127.0.0.1:8090/game.html'); }
});

test('lo bajado de la red se verifica completo siempre (tambien sin fullDigest)', async () => {
  const sizes = await withMemo(async store => {
    await boot(new FakeCaches(), new FakeDirectory(), { memo: true }); // deja el memo puesto
    return spyDigest(async () => { await boot(new FakeCaches(), new FakeDirectory(), { memo: true }); });
  });
  assert.equal(packDigests(sizes), 2);
  assert.deepEqual(globalThis.pzPackStats.digestSkipped, []);
  assert.ok(globalThis.pzPackStats.digestMs >= 0);
});

test('tamano distinto en la cache: se rechaza y se vuelve a bajar', async () => {
  const caches = new FakeCaches(), dir = new FakeDirectory();
  await boot(caches, dir);
  await (await caches.open('pz-web-packs-v1')).put(`http://127.0.0.1:8090/packs/${pack(small).name}`, new Response(small.slice(1)));
  dir.files.set(pack(large).name, large.slice(1));
  const again = await boot(caches, dir);
  assert.deepEqual(again.stats.sources, { network: 2, cache: 0, opfs: 0 });
  assert.deepEqual(again.stats.digestSkipped, []);
});

test('pack salteado con un byte corrupto: la pasada por archivo lo detecta, se borra y se reintenta completo', async () => {
  const caches = new FakeCaches(), dir = new FakeDirectory();
  await boot(caches, dir);
  (await caches.open('pz-web-packs-v1')).map.get(`http://127.0.0.1:8090/packs/${pack(small).name}`)[0] ^= 0xff;
  const again = await boot(caches, dir);
  assert.equal(again.stats.fullDigest, true);
  assert.equal(again.stats.corrupt, 1);
  assert.deepEqual(again.stats.sources, { network: 1, cache: 0, opfs: 1 });
  assert.equal(new TextDecoder().decode(again.vfs.readFile('/media/lua/a.lua')), 'print("hola")\n');
});
