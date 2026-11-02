// Caché persistente de /media (port/runtime/mediacache.mjs): lo que la VFS trae de la red en una carga se sirve sin red
// en la siguiente, se invalida con la versión del manifest y nunca rompe una lectura.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { MediaCache, mediaCacheVersion } from '../../port/runtime/mediacache.mjs';
import { prefetchMedia } from '../../port/runtime/prefetch.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';
import { FakeCaches } from './fake-storage.mjs';

const data = Uint8Array.from({ length: 300 }, (_, i) => (i * 13) & 0xff);
// XHR síncrono falso sobre `data`, como el de remote-media.test.mjs; cuenta los pedidos a la red.
function installXhr(seen) {
  globalThis.XMLHttpRequest = class {
    open(method, url) { this.url = url; } overrideMimeType() {} setRequestHeader(k, v) { if (k === 'Range') this.range = v; }
    send() {
      seen.push([this.url, this.range]);
      const [, a, b] = /bytes=(\d+)-(\d+)/.exec(this.range).map(Number);
      this.status = 206; this.responseText = String.fromCharCode(...data.subarray(a, b + 1));
    }
  };
}
const remoteVfs = () => { const vfs = new VirtualFileSystem(); vfs.attachRemote([['lua/a.lua', data.length]]); return vfs; };

test('una lectura de red se guarda y en la carga siguiente se sirve sin red', async () => {
  const caches = new FakeCaches(), seen = [];
  installXhr(seen);
  try {
    const first = await new MediaCache({ cachesImpl: caches }).open('media:v1');
    const vfs = remoteVfs(); vfs.onRemoteFetch = (url, range, bytes) => first.record(url, range, bytes);
    assert.deepEqual([...vfs.readFile('/media/lua/a.lua')], [...data]);
    assert.equal(seen.length, 1);
    await first.flush();
    assert.deepEqual([first.stats.stored, first.stats.storedBytes], [1, data.length]);

    const second = await new MediaCache({ cachesImpl: caches }).open('media:v1');
    const reloaded = remoteVfs();
    const stats = await second.restore(reloaded);
    assert.deepEqual([stats.restored, stats.restoredBytes], [1, data.length]);
    reloaded.onRemoteFetch = (url, range, bytes) => second.record(url, range, bytes);
    assert.deepEqual([...reloaded.readFile('/media/lua/a.lua')], [...data]);
    assert.equal(seen.length, 1, 'la segunda carga no sale a la red');
    assert.equal(reloaded.prefetchHits, 1);
  } finally { delete globalThis.XMLHttpRequest; }
});

test('otra versión del manifest borra lo guardado antes de usarlo', async () => {
  const caches = new FakeCaches();
  const a = await new MediaCache({ cachesImpl: caches }).open('media:v1');
  a.record('/media/x?r=0-3', 'bytes=0-2', new Uint8Array([1, 2, 3])); await a.flush();
  const b = await new MediaCache({ cachesImpl: caches }).open('media:v2');
  assert.equal(b.stats.cleared, 1);
  const vfs = new VirtualFileSystem();
  assert.equal((await b.restore(vfs)).restored, 0);
  assert.equal(vfs.prefetched.size, 0);
  const c = await new MediaCache({ cachesImpl: caches }).open('media:v2');
  assert.equal(c.stats.cleared, 0, 'misma versión: no se borra');
});

test('límites: sin duplicados, tope de bytes, no pisa lo ya precargado y el callback no rompe lecturas', async () => {
  const caches = new FakeCaches();
  const cache = await new MediaCache({ cachesImpl: caches, maxBytes: 8 }).open('v');
  assert.equal(cache.record('/u1', 'r', new Uint8Array(5)), true);
  assert.equal(cache.record('/u1', 'r', new Uint8Array(5)), false, 'misma clave');
  assert.equal(cache.record('/u2', 'r', new Uint8Array(5)), false, 'supera maxBytes');
  assert.equal(cache.stats.dropped, 1);
  await cache.flush();
  const vfs = new VirtualFileSystem(); vfs.prefetched.set('/u1|r', new Uint8Array([7]));
  await (await new MediaCache({ cachesImpl: caches }).open('v')).restore(vfs);
  assert.deepEqual([...vfs.prefetched.get('/u1|r')], [7]);

  const seen = []; installXhr(seen);
  try {
    const broken = remoteVfs(); broken.onRemoteFetch = () => { throw new Error('cuota llena'); };
    assert.deepEqual([...broken.readFile('/media/lua/a.lua')], [...data]);
  } finally { delete globalThis.XMLHttpRequest; }
  assert.equal(new MediaCache({ cachesImpl: undefined }).mode, 0, 'sin Cache API queda apagada');
  assert.equal(MediaCache.fromLocation('?nocache=1', { cachesImpl: caches }).mode, 0);
  assert.equal(MediaCache.fromLocation('?mediacache=0', { cachesImpl: caches }).mode, 0);
  assert.equal(MediaCache.fromLocation('', { cachesImpl: caches }).mode, 1);
});

test('la precarga no baja lo restaurado y entrega lo bajado a la caché', async () => {
  const fetched = [], recorded = [];
  const fetchImpl = async (url, opts = {}) => {
    if (url === '/warm-media.json') return { ok: true, json: async () => [['/media/a?r=0-2', 'bytes=0-1'], ['/media/b?r=0-2', 'bytes=0-1']] };
    fetched.push(url); return { status: 206, arrayBuffer: async () => new Uint8Array([4, 5]).buffer };
  };
  const vfs = new VirtualFileSystem(); vfs.prefetched.set('/media/a?r=0-2|bytes=0-1', new Uint8Array([1, 2]));
  const stats = await prefetchMedia(vfs, { fetchImpl, batch: false, onFetched: (...args) => recorded.push(args) });
  assert.deepEqual(fetched, ['/media/b?r=0-2']);
  assert.deepEqual([stats.cached, stats.stored], [1, 1]);
  assert.deepEqual(recorded.map(([url, range]) => [url, range]), [['/media/b?r=0-2', 'bytes=0-1']]);
});

test('versión: la del manifest si viene; si no, el sha256 de sus bytes', async () => {
  const bytes = new TextEncoder().encode('{"schema_version":1,"files":[]}');
  assert.equal(await mediaCacheVersion(bytes, { version: 'media:abc' }), 'media:abc');
  assert.match(await mediaCacheVersion(bytes, {}), /^sha256:[a-f0-9]{64}$/);
});
