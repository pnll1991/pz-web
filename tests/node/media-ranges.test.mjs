// Ronda 89 (frente B): /media-ranges del dev server (lotes del warm-list en una respuesta) y la precarga por lotes del cliente.
// Servidor en proceso en 127.0.0.1:0 con una copia sintética de PZ_HOME; sin el juego.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm, symlink, realpath } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import http from 'node:http';
import { createDevServer } from '../../dev-server/server.mjs';
import { prefetchMedia } from '../../port/runtime/prefetch.mjs';
import { parseIndices } from '../../dev-server/media-ranges.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';

const KEY = 'test-key-0123456789abcdef', HOST = 'quiet-lake-1234.trycloudflare.com';
let temporary, server, origin, port, publicServer, publicPort;
const bytes = n => Uint8Array.from({ length: n }, (_, i) => (i * 7 + n) & 0xff);
const warm = [
  ['/media/lua/a.bin?r=0-8', 'bytes=0-7'], ['/media/lua/a.bin?r=8-16', 'bytes=8-15'], ['/media/lua/b.txt?r=0-4', 'bytes=0-3'],
  ['/media-bundle?dir=maps%2Fm&ext=lotheader', ''], ['/media/lua/a.bin?r=2000-2008', 'bytes=2000-2007'],
  ['/media/../secret.txt?r=0-4', 'bytes=0-3'], ['/media/lua/link.bin?r=0-4', 'bytes=0-3'], ['/media/lua/missing.bin?r=0-4', 'bytes=0-3'],
];
before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-ranges-'));
  await mkdir(path.join(temporary, 'web')); await mkdir(path.join(temporary, 'packs'));
  await mkdir(path.join(temporary, 'game/media/lua'), { recursive: true }); await mkdir(path.join(temporary, 'game/media/maps/m'), { recursive: true });
  await writeFile(path.join(temporary, 'web/index.html'), 'x'); await writeFile(path.join(temporary, 'game/ProjectZomboid64.json'), '{}');
  await writeFile(path.join(temporary, 'game/media/lua/a.bin'), bytes(64)); await writeFile(path.join(temporary, 'game/media/lua/b.txt'), 'hello');
  await writeFile(path.join(temporary, 'game/media/maps/m/1_1.lotheader'), bytes(5)); await writeFile(path.join(temporary, 'game/media/maps/m/2_1.lotheader'), bytes(3));
  await writeFile(path.join(temporary, 'secret.txt'), 'secret!!'); // fuera de media/
  await symlink(path.join(temporary, 'secret.txt'), path.join(temporary, 'game/media/lua/link.bin'));
  await writeFile(path.join(temporary, 'warm.json'), JSON.stringify(warm));
  const make = async publicKey => {
    const s = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: path.join(temporary, 'game'), packsRoot: path.join(temporary, 'packs'),
      warmFile: path.join(temporary, 'warm.json'), publicKey });
    s.listen(0, '127.0.0.1'); await once(s, 'listening'); return s;
  };
  server = await make(''); port = server.address().port; origin = `http://127.0.0.1:${port}`;
  publicServer = await make(KEY); publicPort = publicServer.address().port;
});
after(async () => { for (const s of [server, publicServer]) await new Promise(r => s.close(r)); await rm(temporary, { recursive: true }); });

const parse = async response => {
  const buffer = await response.arrayBuffer(), headerLength = new DataView(buffer).getUint32(0, true);
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 4, headerLength)));
  return { header, body: new Uint8Array(buffer, 4 + headerLength) };
};

test('parseIndices solo acepta listas de enteros acotadas', () => {
  assert.deepEqual(parseIndices('0,5,12'), [0, 5, 12]);
  for (const bad of [null, '', '-1', '1,,2', '1.5', 'a', '1,2,', '1234567', Array.from({ length: 513 }, (_, i) => i).join(',')]) assert.equal(parseIndices(bad), null, String(bad));
});

test('/media-ranges devuelve los mismos bytes que GET /media con Range y que /media-bundle', async () => {
  const response = await fetch(`${origin}/media-ranges?i=0,1,2,3,4`);
  assert.equal(response.status, 200);
  const { header, body } = await parse(response);
  assert.equal(header.served, 5);
  assert.deepEqual(header.entries.map(([u, r]) => [u, r]), warm.slice(0, 5));
  let at = 0;
  for (const [k, [url, range, size]] of header.entries.entries()) {
    const real = url.startsWith('/media-bundle') ? new Uint8Array(await (await fetch(origin + url)).arrayBuffer())
      : range && size >= 0 ? new Uint8Array(await (await fetch(origin + url.split('?')[0], { headers: { Range: range } })).arrayBuffer()) : null;
    if (k === 4) { assert.equal(size, -1, 'rango fuera del archivo'); continue; }
    assert.deepEqual(body.subarray(at, at + size), real, url); at += size;
  }
});

test('/media-ranges rechaza rutas fuera de media/ y entradas inexistentes sin tocar nada', async () => {
  const { header, body } = await parse(await fetch(`${origin}/media-ranges?i=5,6,7,99`));
  assert.deepEqual(header.entries.map(e => e[2]), [-1, -1, -1, -1]);
  assert.equal(body.length, 0);
  assert.equal((await fetch(`${origin}/media-ranges`)).status, 400);
  assert.equal((await fetch(`${origin}/media-ranges?i=../../etc/passwd`)).status, 400);
});

test('/media-ranges corta por bytes y avisa cuántas resolvió', async () => {
  const { createWarmList } = await import('../../dev-server/warm-list.mjs');
  const { buildRangesResponse } = await import('../../dev-server/media-ranges.mjs');
  const out = await buildRangesResponse({ mediaRoot: await realpath(path.join(temporary, 'game/media')), bundle: async () => Buffer.alloc(0),
    entries: createWarmList({ file: path.join(temporary, 'warm.json') }).list(), indices: [0, 1, 2], maxBytes: 8 });
  assert.equal(out.served, 1, 'el primer rango ya llega al tope');
});

test('en modo público /media-ranges exige la misma clave que el resto', async () => {
  const get = headers => new Promise((resolve, reject) => http.get({ host: '127.0.0.1', port: publicPort, path: '/media-ranges?i=0',
    headers: { Host: HOST, 'cf-connecting-ip': '203.0.113.9', ...headers } }, res => { res.resume(); res.on('end', () => resolve(res.statusCode)); }).on('error', reject));
  assert.equal(await get({}), 403);
});

test('la precarga por lotes llena vfs.prefetched con las claves url|Range, calcula stats y reencola lo cortado', async () => {
  const served = [];
  const fetchImpl = async (u, opts) => {
    served.push(u);
    if (u === '/warm-media.json') return { ok: true, json: async () => warm.slice(0, 5) };
    const r = await fetch(origin + u, opts); return { status: r.status, arrayBuffer: () => r.arrayBuffer() };
  };
  const vfs = new VirtualFileSystem(), recorded = [];
  vfs.prefetched.set(`${warm[0][0]}|${warm[0][1]}`, new Uint8Array(8));
  const stats = await prefetchMedia(vfs, { fetchImpl, onFetched: (u, r, b) => recorded.push([u, r, b.length]) });
  assert.deepEqual([stats.requested, stats.cached, stats.stored, stats.failed, stats.mode], [5, 1, 3, 1, 'batch']);
  assert.equal(stats.batches, 1); assert.equal(stats.requests, 2, 'lista + un lote');
  assert.deepEqual(served.filter(u => u.startsWith('/media-ranges')), ['/media-ranges?i=1,2,3,4']);
  assert.deepEqual([...vfs.prefetched.get(`${warm[2][0]}|${warm[2][1]}`)], [...Buffer.from('hell')]);
  assert.equal(vfs.prefetched.get(`${warm[1][0]}|${warm[1][1]}`).buffer.byteLength, 8, 'cada entrada es una copia, no una vista del lote');
  assert.equal(recorded.length, 3);
});

test('si /media-ranges no existe, la precarga cae al camino de un fetch por rango', async () => {
  const fetchImpl = async (u, opts = {}) => {
    if (u === '/warm-media.json') return { ok: true, json: async () => warm.slice(0, 3) };
    if (u.startsWith('/media-ranges')) return { status: 404, arrayBuffer: async () => new ArrayBuffer(0) };
    const r = await fetch(origin + u.split('?')[0], opts); return { status: r.status, arrayBuffer: () => r.arrayBuffer() };
  };
  const vfs = new VirtualFileSystem();
  const stats = await prefetchMedia(vfs, { fetchImpl });
  assert.deepEqual([stats.stored, stats.failed, stats.mode], [3, 0, 'mixed']);
  assert.equal(vfs.prefetched.size, 3);
});

test('el lotheader precargado por lotes lo usa vfs.bundled (el buffer no es una vista desplazada)', async () => {
  const fetchImpl = async (u, opts) => {
    if (u === '/warm-media.json') return { ok: true, json: async () => warm.slice(0, 4) };
    const r = await fetch(origin + u, opts); return { status: r.status, arrayBuffer: () => r.arrayBuffer() };
  };
  const vfs = new VirtualFileSystem(); vfs.attachRemote([['maps/m/1_1.lotheader', 5], ['maps/m/2_1.lotheader', 3]]);
  await prefetchMedia(vfs, { fetchImpl });
  assert.ok(vfs.prefetched.has(`${warm[3][0]}|`));
  globalThis.XMLHttpRequest = class { open() { throw new Error('network must not be used'); } };
  try { assert.deepEqual([...vfs.bundled('/media/maps/m/1_1.lotheader')], [...bytes(5)]); } finally { delete globalThis.XMLHttpRequest; }
});
