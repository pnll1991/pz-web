// Servidor de desarrollo: br/gzip precomprimidos para wasm y packs, compresión en memoria del texto (JS, JSON, manifest) y
// Range siempre sobre el original (los tramos de un pack perezoso). Archivos sintéticos propios.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtemp, mkdir, writeFile, utimes, rm } from 'node:fs/promises';
import { brotliDecompressSync, gunzipSync, brotliCompressSync, gzipSync } from 'node:zlib';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { createDevServer, acceptedCodings } from '../../dev-server/server.mjs';

let temporary, origin, server;
const hash = 'ab'.repeat(32);
const text = 'function hola() { return "mundo"; }\n'.repeat(200);
const raw = (url, headers = {}) => new Promise((resolve, reject) => http.get(url, { headers }, res => {
  const chunks = []; res.on('data', c => chunks.push(c)); res.on('end', () => resolve({ res, body: Buffer.concat(chunks) }));
}).on('error', reject));

before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-server-compress-'));
  for (const d of ['web', 'packs', 'game/media/lua']) await mkdir(path.join(temporary, d), { recursive: true });
  await writeFile(path.join(temporary, 'game/ProjectZomboid64.json'), '{}');
  await writeFile(path.join(temporary, 'game/media/lua/a.lua'), text);
  for (let i = 0; i < 60; i++) await writeFile(path.join(temporary, `game/media/lua/archivo_con_nombre_largo_${i}.lua`), 'x'); // manifest de más de 1 KiB
  await writeFile(path.join(temporary, 'web/app.mjs'), text);
  await writeFile(path.join(temporary, 'web/tiny.mjs'), 'x');
  await writeFile(path.join(temporary, 'packs/index.json'), JSON.stringify({ files: Array.from({ length: 300 }, (_, i) => ({ path: `/media/x${i}` })) }));
  const body = Buffer.alloc(20000, 'abc');
  await writeFile(path.join(temporary, `packs/${hash}.bin`), body);
  await writeFile(path.join(temporary, `packs/${hash}.bin.br`), brotliCompressSync(body));
  await writeFile(path.join(temporary, `packs/${hash}.bin.gz`), gzipSync(body));
  const old = new Date(Date.now() - 60000);
  for (const f of [`packs/${hash}.bin`]) await utimes(path.join(temporary, f), old, old);
  server = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: path.join(temporary, 'game'), packsRoot: path.join(temporary, 'packs'), warmFile: path.join(temporary, 'warm.json') });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); origin = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { server.close(); await rm(temporary, { recursive: true, force: true }); });

test('Accept-Encoding: br antes que gzip, q=0 descarta, * vale', () => {
  assert.deepEqual(acceptedCodings('gzip, deflate, br'), ['br', 'gzip']);
  assert.deepEqual(acceptedCodings('gzip;q=1.0, br;q=0.5'), ['gzip', 'br']);
  assert.deepEqual(acceptedCodings('br;q=0, gzip'), ['gzip']);
  assert.deepEqual(acceptedCodings('*'), ['br', 'gzip']);
  assert.deepEqual(acceptedCodings('identity'), []);
  assert.deepEqual(acceptedCodings(undefined), []);
});

test('pack precomprimido: br, si no gzip, si no el original; Vary, ETag por codificación e immutable', async () => {
  const url = `${origin}/packs/${hash}.bin`;
  const br = await raw(url, { 'Accept-Encoding': 'gzip, br' });
  assert.equal(br.res.headers['content-encoding'], 'br'); assert.deepEqual(brotliDecompressSync(br.body), Buffer.alloc(20000, 'abc'));
  assert.match(br.res.headers['cache-control'], /immutable/); assert.equal(br.res.headers.vary, 'Accept-Encoding');
  const gz = await raw(url, { 'Accept-Encoding': 'gzip' });
  assert.equal(gz.res.headers['content-encoding'], 'gzip'); assert.deepEqual(gunzipSync(gz.body), Buffer.alloc(20000, 'abc'));
  const id = await raw(url, { 'Accept-Encoding': 'identity' });
  assert.equal(id.res.headers['content-encoding'], undefined); assert.equal(id.body.length, 20000);
  assert.equal(new Set([br.res.headers.etag, gz.res.headers.etag, id.res.headers.etag]).size, 3, 'un ETag por cuerpo');
  const again = await raw(url, { 'Accept-Encoding': 'gzip, br', 'If-None-Match': br.res.headers.etag });
  assert.equal(again.res.statusCode, 304);
  assert.equal(again.res.headers['content-encoding'], 'br');
});

test('Range sobre un pack sale siempre del original, aunque el navegador acepte br', async () => {
  const r = await raw(`${origin}/packs/${hash}.bin`, { 'Accept-Encoding': 'br, gzip', Range: 'bytes=3-8' });
  assert.equal(r.res.statusCode, 206); assert.equal(r.res.headers['content-encoding'], undefined);
  assert.equal(r.res.headers['content-range'], 'bytes 3-8/20000'); assert.equal(r.body.toString(), 'abcabc');
});

test('un .gz o .br más viejo que el original o que no baja de tamaño no se usa', async () => {
  const other = 'cd'.repeat(32), file = path.join(temporary, `packs/${other}.bin`);
  await writeFile(file, 'pequeño'); await writeFile(`${file}.br`, 'mucho más grande que el original');
  assert.equal((await raw(`${origin}/packs/${other}.bin`, { 'Accept-Encoding': 'br' })).res.headers['content-encoding'], undefined);
  const stale = 'ef'.repeat(32), staleFile = path.join(temporary, `packs/${stale}.bin`);
  await writeFile(staleFile, Buffer.alloc(5000, 'z')); await writeFile(`${staleFile}.gz`, gzipSync(Buffer.alloc(5000, 'z')));
  const past = new Date(Date.now() - 3600000); await utimes(`${staleFile}.gz`, past, past);
  assert.equal((await raw(`${origin}/packs/${stale}.bin`, { 'Accept-Encoding': 'gzip' })).res.headers['content-encoding'], undefined);
});

test('texto en memoria: JS, JSON del índice y manifest salen comprimidos (si lo piden) y con 304 propio; lo chico y /media no', async () => {
  for (const [url, expected] of [['/app.mjs', text], ['/packs/index.json', null]]) {
    const r = await raw(origin + url, { 'Accept-Encoding': 'br, gzip' });
    assert.equal(r.res.headers['content-encoding'], 'br', url);
    const body = brotliDecompressSync(r.body).toString(); if (expected) assert.equal(body, expected);
    assert.ok(r.body.length < body.length / 3, `${url} comprime`);
    assert.equal((await raw(origin + url, { 'Accept-Encoding': 'br', 'If-None-Match': r.res.headers.etag })).res.statusCode, 304);
    assert.equal((await raw(origin + url, { 'Accept-Encoding': 'identity' })).res.headers['content-encoding'], undefined);
  }
  assert.equal((await raw(`${origin}/tiny.mjs`, { 'Accept-Encoding': 'br' })).res.headers['content-encoding'], undefined, 'bajo 1 KiB no vale la pena');
  const media = await raw(`${origin}/media/lua/a.lua`, { 'Accept-Encoding': 'br', Range: 'bytes=0-4' });
  assert.equal(media.res.statusCode, 206); assert.equal(media.res.headers['content-encoding'], undefined);
  const manifest = await raw(`${origin}/media-manifest.json`, { 'Accept-Encoding': 'gzip' });
  assert.equal(manifest.res.headers['content-encoding'], 'gzip');
  const listed = JSON.parse(gunzipSync(manifest.body)); assert.equal(listed.schema_version, 1);
  const plain = await raw(`${origin}/media-manifest.json`, { 'Accept-Encoding': 'identity' });
  assert.notEqual(plain.res.headers.etag, manifest.res.headers.etag);
  assert.equal((await raw(`${origin}/media-manifest.json`, { 'Accept-Encoding': 'gzip', 'If-None-Match': manifest.res.headers.etag })).res.statusCode, 304);
});

test('los Range sobre packs se anotan en el warm-list (los tramos que las sesiones reales piden)', async () => {
  await raw(`${origin}/packs/${hash}.bin`, { Range: 'bytes=10-19' });
  const warm = JSON.parse((await raw(`${origin}/warm-media.json`, { 'Accept-Encoding': 'identity' })).body.toString());
  assert.ok(warm.some(([url, range]) => url === `/packs/${hash}.bin` && range === 'bytes=10-19'));
  await raw(`${origin}/packs/${hash}.bin?fill=1`, { Range: 'bytes=30-39' }); // relleno en segundo plano: no se anota
  const after = JSON.parse((await raw(`${origin}/warm-media.json`, { 'Accept-Encoding': 'identity' })).body.toString());
  assert.ok(!after.some(([, range]) => range === 'bytes=30-39'));
});
