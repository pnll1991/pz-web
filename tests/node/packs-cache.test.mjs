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
