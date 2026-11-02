// Ronda 90: /media comprimido. Tramos `?r=a-b` sin Range (br/gzip según Accept-Encoding, Vary, tipos que no se comprimen, ETag/304,
// invalidación por mtime), Range siempre sin comprimir, bundles de lotheader comprimidos, warm-list y el lado cliente (vfs, prefetch).
// Archivos sintéticos propios.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtemp, mkdir, writeFile, utimes, rm } from 'node:fs/promises';
import { brotliDecompressSync, gunzipSync } from 'node:zlib';
import { tmpdir } from 'node:os';
import { randomBytes } from 'node:crypto';
import path from 'node:path';
import { once } from 'node:events';
import { createDevServer, parseSlice } from '../../dev-server/server.mjs';
import { createCompressor } from '../../dev-server/compress.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';
import { prefetchMedia, sliceEntry } from '../../port/runtime/prefetch.mjs';

let temporary, origin, server;
const lua = Buffer.from('local function hola() return "mundo" end\n'.repeat(400));
const noise = randomBytes(8000);
const raw = (url, headers = {}) => new Promise((resolve, reject) => http.get(url, { headers }, res => {
  const chunks = []; res.on('data', c => chunks.push(c)); res.on('end', () => resolve({ res, body: Buffer.concat(chunks) }));
}).on('error', reject));
const warmList = async () => JSON.parse((await raw(`${origin}/warm-media.json`, { 'Accept-Encoding': 'identity' })).body.toString());

before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-media-compress-'));
  for (const d of ['web', 'packs', 'game/media/lua', 'game/media/textures', 'game/media/maps/M']) await mkdir(path.join(temporary, d), { recursive: true });
  await writeFile(path.join(temporary, 'game/ProjectZomboid64.json'), '{}');
  await writeFile(path.join(temporary, 'game/media/lua/a.lua'), lua);
  await writeFile(path.join(temporary, 'game/media/textures/t.png'), lua); // comprimible de contenido, pero png no se intenta
  await writeFile(path.join(temporary, 'game/media/lua/ruido.bin'), noise); // no baja: se manda tal cual
  for (let i = 0; i < 3; i++) await writeFile(path.join(temporary, `game/media/maps/M/${i}_0.lotheader`), Buffer.alloc(3000, 65 + i));
  server = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: path.join(temporary, 'game'), packsRoot: path.join(temporary, 'packs'), warmFile: path.join(temporary, 'warm.json') });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); origin = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { server.close(); await rm(temporary, { recursive: true, force: true }); });

test('parseSlice: fin exclusivo, dentro del archivo, sin r = undefined', () => {
  assert.equal(parseSlice(null, 10), undefined);
  assert.deepEqual(parseSlice('0-10', 10), { start: 0, end: 10 });
  assert.deepEqual(parseSlice('3-4', 10), { start: 3, end: 4 });
  for (const bad of ['4-4', '5-3', '0-11', 'a-b', '-3', '1-']) assert.equal(parseSlice(bad, 10), null, bad);
});

test('el manifest anuncia slices: 1 sin cambiar la versión de la caché de media', async () => {
  const m = JSON.parse((await raw(`${origin}/media-manifest.json`, { 'Accept-Encoding': 'identity' })).body);
  assert.equal(m.slices, 1); assert.match(m.version, /^media:[0-9a-f]{64}$/);
});

test('tramo sin Range: br, si no gzip, si no identidad; exactamente esos bytes, Vary y ETag por codificación', async () => {
  const url = `${origin}/media/lua/a.lua?r=100-12100`, want = lua.subarray(100, 12100);
  const br = await raw(url, { 'Accept-Encoding': 'gzip, deflate, br' });
  assert.equal(br.res.statusCode, 200); assert.equal(br.res.headers['content-encoding'], 'br'); assert.equal(br.res.headers.vary, 'Accept-Encoding');
  assert.deepEqual(brotliDecompressSync(br.body), want); assert.ok(br.body.length < want.length / 5);
  assert.equal(br.res.headers['x-pz-slice'], `100-12100/${lua.length}`); assert.equal(br.res.headers['content-range'], undefined);
  const gz = await raw(url, { 'Accept-Encoding': 'gzip' });
  assert.equal(gz.res.headers['content-encoding'], 'gzip'); assert.deepEqual(gunzipSync(gz.body), want);
  const id = await raw(url, { 'Accept-Encoding': 'identity' });
  assert.equal(id.res.headers['content-encoding'], undefined); assert.deepEqual(id.body, want);
  assert.equal(new Set([br.res.headers.etag, gz.res.headers.etag, id.res.headers.etag]).size, 3);
  const other = await raw(`${origin}/media/lua/a.lua?r=0-12000`, { 'Accept-Encoding': 'br' });
  assert.notEqual(other.res.headers.etag, br.res.headers.etag, 'el ETag depende del tramo');
  assert.equal((await raw(url, { 'Accept-Encoding': 'br', 'If-None-Match': br.res.headers.etag })).res.statusCode, 304);
  assert.equal((await raw(url, { 'Accept-Encoding': 'br', 'If-None-Match': gz.res.headers.etag })).res.statusCode, 200);
  const head = await new Promise(r => http.request(url, { method: 'HEAD', headers: { 'Accept-Encoding': 'br' } }, r).end());
  assert.equal(head.headers['content-encoding'], 'br'); assert.equal(Number(head.headers['content-length']), br.body.length);
});

test('tipos ya comprimidos (png) no se intentan y lo que no baja 10 % va sin comprimir', async () => {
  const png = await raw(`${origin}/media/textures/t.png?r=0-5000`, { 'Accept-Encoding': 'br' });
  assert.equal(png.res.statusCode, 200); assert.equal(png.res.headers['content-encoding'], undefined); assert.equal(png.res.headers.vary, undefined);
  assert.deepEqual(png.body, lua.subarray(0, 5000));
  for (let i = 0; i < 2; i++) { // la 2.ª vez sale de la caché ("no vale la pena")
    const n = await raw(`${origin}/media/lua/ruido.bin?r=0-8000`, { 'Accept-Encoding': 'br' });
    assert.equal(n.res.headers['content-encoding'], undefined); assert.deepEqual(n.body, noise);
  }
  const tiny = await raw(`${origin}/media/lua/a.lua?r=0-100`, { 'Accept-Encoding': 'br' });
  assert.equal(tiny.res.headers['content-encoding'], undefined, 'bajo 1 KiB no se comprime'); assert.deepEqual(tiny.body, lua.subarray(0, 100));
});

test('con Range todo sigue igual (206, sin comprimir) aunque el navegador acepte br; ?r= se ignora', async () => {
  const r = await raw(`${origin}/media/lua/a.lua?r=0-10`, { 'Accept-Encoding': 'br, gzip', Range: 'bytes=2000-5999' });
  assert.equal(r.res.statusCode, 206); assert.equal(r.res.headers['content-encoding'], undefined);
  assert.equal(r.res.headers['content-range'], `bytes 2000-5999/${lua.length}`); assert.deepEqual(r.body, lua.subarray(2000, 6000));
  const whole = await raw(`${origin}/media/lua/a.lua`, { 'Accept-Encoding': 'br' });
  assert.equal(whole.res.statusCode, 200); assert.equal(whole.res.headers['content-encoding'], undefined, 'el archivo entero sin r sigue como antes');
});

test('tramo inválido o fuera del archivo: 416', async () => {
  for (const r of ['5-5', `0-${lua.length + 1}`, 'x']) assert.equal((await raw(`${origin}/media/lua/a.lua?r=${r}`)).res.statusCode, 416, r);
});

test('si el archivo cambia (mtime/tamaño) el tramo y el ETag se recalculan', async () => {
  const file = path.join(temporary, 'game/media/lua/b.lua'), url = `${origin}/media/lua/b.lua?r=0-3000`;
  await writeFile(file, Buffer.alloc(4000, 'a')); const past = new Date(Date.now() - 60000); await utimes(file, past, past);
  const first = await raw(url, { 'Accept-Encoding': 'br' });
  assert.deepEqual(brotliDecompressSync(first.body), Buffer.alloc(3000, 'a'));
  await writeFile(file, Buffer.alloc(4000, 'b'));
  const second = await raw(url, { 'Accept-Encoding': 'br', 'If-None-Match': first.res.headers.etag });
  assert.equal(second.res.statusCode, 200); assert.notEqual(second.res.headers.etag, first.res.headers.etag);
  assert.deepEqual(brotliDecompressSync(second.body), Buffer.alloc(3000, 'b'));
});

test('el bundle de lotheader sale comprimido y con los mismos bytes', async () => {
  const url = `${origin}/media-bundle?dir=maps/M&ext=lotheader`;
  const br = await raw(url, { 'Accept-Encoding': 'br' }), id = await raw(url, { 'Accept-Encoding': 'identity' });
  assert.equal(br.res.headers['content-encoding'], 'br'); assert.equal(br.res.headers.vary, 'Accept-Encoding');
  assert.deepEqual(brotliDecompressSync(br.body), id.body); assert.ok(br.body.length < id.body.length / 5);
  assert.equal(id.res.headers['content-encoding'], undefined); assert.equal(id.res.headers['cache-control'], 'no-store');
});

test('el warm-list anota el tramo sin Range con el Range equivalente (misma entrada que un cliente viejo)', async () => {
  await raw(`${origin}/media/lua/a.lua?r=7-1031`, { 'Accept-Encoding': 'br' });
  await raw(`${origin}/media/lua/a.lua?r=2000-3000`, { Range: 'bytes=2000-2999' });
  const warm = await warmList();
  assert.ok(warm.some(([u, r]) => u === '/media/lua/a.lua?r=7-1031' && r === 'bytes=7-1030'));
  assert.equal(warm.filter(([u]) => u === '/media/lua/a.lua?r=2000-3000').length, 1);
  assert.ok(warm.every(([u, r]) => !u.startsWith('/media/') || r), 'ningún /media sin rango');
});

test('compresor: pedidos simultáneos comparten una compresión y minSaving descarta lo que baja poco', async () => {
  const c = createCompressor({ minSaving: 0.5 }); let reads = 0;
  const body = async () => { reads++; return Buffer.alloc(5000, 'q'); };
  const [a, b] = await Promise.all([c.get('k', 'br', body, 5000), c.get('k', 'br', body, 5000)]);
  assert.equal(reads, 1); assert.equal(a, b); assert.ok(a.length < 100);
  assert.equal(await c.get('n', 'gzip', noise), null); assert.equal(c.stats().skipped, 1);
  const capped = createCompressor({ maxEntries: 2 });
  for (const k of ['1', '2', '3']) await capped.get(k, 'gzip', Buffer.alloc(2000, k));
  assert.equal(capped.stats().entries, 2);
});

test('cliente: con remoteSlices el VFS pide ?r=a-b sin Range y exige 200 con el largo exacto', () => {
  const data = new Uint8Array(3 * (1 << 20) + 5).map((_, i) => (i * 7) & 0xff), seen = [];
  globalThis.XMLHttpRequest = class {
    open(method, url) { this.url = url; } overrideMimeType() {} setRequestHeader(k, v) { (this.headers ??= {})[k] = v; }
    send() {
      seen.push([this.url, this.headers]);
      const [, a, b] = /\?r=(\d+)-(\d+)/.exec(this.url).map(Number), part = data.subarray(a, b);
      this.status = 200; let s = ''; for (let i = 0; i < part.length; i += 8192) s += String.fromCharCode(...part.subarray(i, i + 8192)); this.responseText = s;
    }
  };
  try {
    const vfs = new VirtualFileSystem(); vfs.remoteSlices = true;
    vfs.attachRemote([['maps/x.lotpack', data.length]]);
    const target = new Uint8Array(10);
    assert.equal(vfs.readInto('/media/maps/x.lotpack', (1 << 20) - 4, target), 10);
    assert.deepEqual([...target], [...data.subarray((1 << 20) - 4, (1 << 20) + 6)]);
    assert.ok(seen.every(([, h]) => !h.Range && h['Cache-Control'] === 'no-store'));
    assert.deepEqual(vfs.readFile('/media/maps/x.lotpack'), data);
    globalThis.XMLHttpRequest.prototype.send = function () { this.status = 200; this.responseText = 'abc'; };
    const fresh = new VirtualFileSystem(); fresh.remoteSlices = true; fresh.attachRemote([['y.bin', 10]]);
    assert.throws(() => fresh.readFile('/media/y.bin'), /EIO: slice length/);
  } finally { delete globalThis.XMLHttpRequest; }
});

test('cliente: prefetch pide sin Range las entradas /media de tramo cuando el VFS usa slices; la clave no cambia', async () => {
  assert.equal(sliceEntry('/media/a.bin?r=0-8', 'bytes=0-7'), true);
  assert.equal(sliceEntry('/media/a.bin?r=0-8', 'bytes=0-6'), false);
  assert.equal(sliceEntry('/packs/x.bin?r=0-8', 'bytes=0-7'), false);
  assert.equal(sliceEntry('/media-bundle?dir=maps&ext=lotheader', ''), false);
  const data = Uint8Array.from({ length: 8 }, (_, i) => i + 1), calls = [];
  const fetchImpl = async (u, opts = {}) => {
    if (u === '/warm-media.json') return { ok: true, json: async () => [['/media/lua/a.bin?r=0-8', 'bytes=0-7'], ['/media/lua/a.bin?r=0-4', 'bytes=0-3']] };
    calls.push([u, opts.headers]);
    return u.endsWith('r=0-8') ? { status: 200, arrayBuffer: async () => data.buffer.slice(0) } : { status: 200, arrayBuffer: async () => new ArrayBuffer(2) };
  };
  const vfs = new VirtualFileSystem(); vfs.remoteSlices = true; vfs.attachRemote([['lua/a.bin', 8]]);
  const stats = await prefetchMedia(vfs, { fetchImpl, concurrency: 1, batch: false });
  assert.deepEqual([stats.stored, stats.failed], [1, 1], 'un tramo corto es un fallo');
  assert.ok(calls.every(([, h]) => !h.Range));
  assert.ok(vfs.prefetched.has('/media/lua/a.bin?r=0-8|bytes=0-7'));
});
