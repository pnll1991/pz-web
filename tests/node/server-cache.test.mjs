// Cabeceras de caché del dev server: manifest de media con versión y ETag fuerte (304 en la recarga), packs con
// nombre por hash inmutables, y el resto sigue con no-cache.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, utimes, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { createDevServer } from '../../dev-server/server.mjs';

let temporary, origin;
const servers = [];
const start = async () => {
  const server = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: path.join(temporary, 'game'),
    packsRoot: path.join(temporary, 'packs'), warmFile: path.join(temporary, 'warm.json') });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); servers.push(server);
  return `http://127.0.0.1:${server.address().port}`;
};
const hash = '0123456789abcdef'.repeat(4);
before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-server-cache-'));
  await mkdir(path.join(temporary, 'web'));
  await mkdir(path.join(temporary, 'game/media/lua'), { recursive: true });
  await mkdir(path.join(temporary, 'packs'));
  await writeFile(path.join(temporary, 'web/index.html'), 'synthetic');
  await writeFile(path.join(temporary, 'game/ProjectZomboid64.json'), '{}');
  await writeFile(path.join(temporary, 'game/media/lua/b.lua'), 'b');
  await writeFile(path.join(temporary, 'game/media/lua/a.lua'), 'aa');
  await writeFile(path.join(temporary, 'packs/index.json'), '{}');
  await writeFile(path.join(temporary, `packs/${hash}.bin`), 'pack');
  origin = await start();
});
after(async () => {
  for (const server of servers) await new Promise(resolve => server.close(resolve));
  await rm(temporary, { recursive: true });
});

test('manifest: archivos ordenados, versión estable y 304 con su ETag', async () => {
  const first = await fetch(origin + '/media-manifest.json');
  const etag = first.headers.get('etag'), listed = await first.json();
  assert.match(etag, /^"[a-f0-9]{64}"$/);
  assert.match(listed.version, /^media:[a-f0-9]{64}$/);
  assert.deepEqual(listed.files, [['lua/a.lua', 2], ['lua/b.lua', 1]]);
  const again = await fetch(origin + '/media-manifest.json', { headers: { 'If-None-Match': etag } });
  assert.equal(again.status, 304);
  // Mismo contenido, otro mtime (otra copia de PZ_HOME): otra versión para la caché del navegador.
  await utimes(path.join(temporary, 'game/media/lua/a.lua'), new Date(2020, 0, 1), new Date(2020, 0, 1));
  const other = await (await fetch(await start() + '/media-manifest.json')).json();
  assert.notEqual(other.version, listed.version);
  assert.deepEqual(other.files, listed.files);
});

test('packs con nombre por hash son inmutables; el índice y la media no', async () => {
  const blob = await fetch(origin + `/packs/${hash}.bin`);
  assert.equal(blob.headers.get('cache-control'), 'public, max-age=31536000, immutable');
  assert.equal(await blob.text(), 'pack');
  assert.equal((await fetch(origin + '/packs/index.json')).headers.get('cache-control'), 'no-cache');
  assert.equal((await fetch(origin + '/media/lua/a.lua')).headers.get('cache-control'), 'no-cache');
});

test('wasm y packs: con <archivo>.br al lado y Accept-Encoding br sale el .br; con Range o sin br, el original', async () => {
  const body = 'x'.repeat(5000);
  await writeFile(path.join(temporary, 'web/a.wasm'), body);
  await writeFile(path.join(temporary, 'web/a.wasm.br'), 'comprimido'); // contenido sintético: sólo se verifican las cabeceras y los bytes servidos
  const raw = (url, headers) => new Promise((resolve, reject) => import('node:http').then(({ default: http }) =>
    http.get(url, { headers }, res => { const chunks = []; res.on('data', c => chunks.push(c)); res.on('end', () => resolve({ res, body: Buffer.concat(chunks).toString() })); }).on('error', reject)));
  const withBr = await raw(origin + '/a.wasm', { 'Accept-Encoding': 'br, gzip' });
  assert.equal(withBr.res.headers['content-encoding'], 'br');
  assert.equal(withBr.res.headers['vary'], 'Accept-Encoding');
  assert.equal(withBr.body, 'comprimido');
  assert.match(withBr.res.headers['etag'], /-br"$/);
  const plain = await raw(origin + '/a.wasm', { 'Accept-Encoding': 'gzip' });
  assert.equal(plain.res.headers['content-encoding'], undefined);
  assert.equal(plain.body, body);
  const ranged = await raw(origin + '/a.wasm', { 'Accept-Encoding': 'br', Range: 'bytes=0-9' });
  assert.equal(ranged.res.statusCode, 206);
  assert.equal(ranged.res.headers['content-encoding'], undefined);
  assert.equal(ranged.body, 'xxxxxxxxxx');
  const again = await raw(origin + '/a.wasm', { 'Accept-Encoding': 'br', 'If-None-Match': withBr.res.headers['etag'] });
  assert.equal(again.res.statusCode, 304);
  // un .br más grande que el original no se usa
  await writeFile(path.join(temporary, 'web/b.wasm'), 'ab'); await writeFile(path.join(temporary, 'web/b.wasm.br'), 'mucho mas grande');
  assert.equal((await raw(origin + '/b.wasm', { 'Accept-Encoding': 'br' })).res.headers['content-encoding'], undefined);
});
