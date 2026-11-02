// Ronda 91: /media-ranges comprimido. Negociación br/gzip/identity (q-values, q=0, *), Vary, Content-Length del cuerpo codificado,
// bytes descomprimidos idénticos a la respuesta sin comprimir, sin ETag (no-store), lotes de tipos ya comprimidos sin tocar,
// caché por contenido (un archivo que cambia no reusa el lote viejo) y la precarga del cliente sobre la respuesta comprimida.
// Archivos sintéticos propios.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { brotliDecompressSync, gunzipSync } from 'node:zlib';
import { tmpdir } from 'node:os';
import { randomBytes } from 'node:crypto';
import path from 'node:path';
import { once } from 'node:events';
import { createDevServer } from '../../dev-server/server.mjs';
import { extensionOf } from '../../dev-server/media-ranges.mjs';
import { prefetchMedia } from '../../port/runtime/prefetch.mjs';
import { VirtualFileSystem } from '../../port/runtime/vfs.mjs';

let temporary, origin, server;
const lua = Buffer.from('local function hola() return "mundo" end\n'.repeat(800));
const noise = randomBytes(20000);
const warm = [
  ['/media/lua/a.lua?r=0-16000', 'bytes=0-15999'], ['/media/lua/a.lua?r=16000-32000', 'bytes=16000-31999'],
  ['/media-bundle?dir=maps%2FM&ext=lotheader', ''],
  ['/media/textures/t.png?r=0-20000', 'bytes=0-19999'], ['/media/lua/b.lua', ''],
];
const get = (url, headers = {}, method = 'GET') => new Promise((resolve, reject) => http.request(url, { method, headers }, res => {
  const chunks = []; res.on('data', c => chunks.push(c)); res.on('end', () => resolve({ res, body: Buffer.concat(chunks) }));
}).on('error', reject).end());
const decode = ({ res, body }) => res.headers['content-encoding'] === 'br' ? brotliDecompressSync(body)
  : res.headers['content-encoding'] === 'gzip' ? gunzipSync(body) : body;

before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-ranges-compress-'));
  for (const d of ['web', 'packs', 'game/media/lua', 'game/media/textures', 'game/media/maps/M']) await mkdir(path.join(temporary, d), { recursive: true });
  await writeFile(path.join(temporary, 'web/index.html'), 'x'); await writeFile(path.join(temporary, 'game/ProjectZomboid64.json'), '{}');
  await writeFile(path.join(temporary, 'game/media/lua/a.lua'), lua); await writeFile(path.join(temporary, 'game/media/lua/b.lua'), 'tiny');
  await writeFile(path.join(temporary, 'game/media/textures/t.png'), noise);
  for (let i = 0; i < 3; i++) await writeFile(path.join(temporary, `game/media/maps/M/${i}_0.lotheader`), Buffer.alloc(3000, 65 + i));
  await writeFile(path.join(temporary, 'warm.json'), JSON.stringify(warm));
  server = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: path.join(temporary, 'game'), packsRoot: path.join(temporary, 'packs'), warmFile: path.join(temporary, 'warm.json') });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); origin = `http://127.0.0.1:${server.address().port}`;
});
after(async () => { server.close(); await rm(temporary, { recursive: true, force: true }); });

test('extensionOf: extensión de la ruta o el ext del bundle', () => {
  assert.equal(extensionOf('/media/textures/A.PNG?r=0-4'), '.png');
  assert.equal(extensionOf('/media-bundle?dir=maps%2FM&ext=lotheader'), '.lotheader');
  assert.equal(extensionOf('/media/lua/sin'), '');
});

test('br, gzip e identity dan los mismos bytes descomprimidos, con Vary y Content-Length del cuerpo enviado', async () => {
  const plain = await get(`${origin}/media-ranges?i=0,1,2,3,4`, { 'Accept-Encoding': 'identity' });
  assert.equal(plain.res.statusCode, 200);
  assert.equal(plain.res.headers['content-encoding'], undefined);
  assert.equal(Number(plain.res.headers['content-length']), plain.body.length);
  for (const [accept, coding] of [['gzip, deflate, br, zstd', 'br'], ['gzip', 'gzip'], ['br;q=0.5, gzip;q=0.9', 'gzip'], ['*', 'br'], ['identity', undefined], ['', undefined]]) {
    const got = await get(`${origin}/media-ranges?i=0,1,2,3,4`, { 'Accept-Encoding': accept });
    assert.equal(got.res.statusCode, 200, accept);
    assert.equal(got.res.headers['content-encoding'], coding, accept);
    assert.equal(got.res.headers.vary, 'Accept-Encoding', accept);
    assert.equal(Number(got.res.headers['content-length']), got.body.length, accept);
    assert.equal(got.res.headers['cache-control'], 'no-store', accept);
    assert.equal(got.res.headers.etag, undefined, accept); // no-store: sin validador que un caché pueda mezclar entre codings
    assert.ok(decode(got).equals(plain.body), accept);
    if (coding) assert.ok(got.body.length < plain.body.length * 0.9, `${accept}: ${got.body.length} vs ${plain.body.length}`);
  }
});

test('q=0 descarta el coding y *;q=0 / br;q=0,gzip;q=0 caen a identity', async () => {
  for (const [accept, coding] of [['br;q=0, gzip', 'gzip'], ['gzip;q=0, br', 'br'], ['*;q=0', undefined], ['br;q=0, gzip;q=0', undefined], ['*;q=0, identity', undefined]]) {
    const got = await get(`${origin}/media-ranges?i=0,1`, { 'Accept-Encoding': accept });
    assert.equal(got.res.headers['content-encoding'], coding, accept);
    assert.equal(got.res.headers.vary, 'Accept-Encoding', accept);
  }
});

test('HEAD anuncia el mismo Content-Length y Content-Encoding que GET, sin cuerpo', async () => {
  const head = await get(`${origin}/media-ranges?i=0,1,2`, { 'Accept-Encoding': 'br' }, 'HEAD');
  const full = await get(`${origin}/media-ranges?i=0,1,2`, { 'Accept-Encoding': 'br' });
  assert.equal(head.body.length, 0);
  assert.equal(head.res.headers['content-encoding'], 'br');
  assert.equal(head.res.headers['content-length'], full.res.headers['content-length']);
});

test('un lote solo de png (ya comprimido) y un lote chico se mandan sin comprimir', async () => {
  for (const i of ['3', '4']) {
    const got = await get(`${origin}/media-ranges?i=${i}`, { 'Accept-Encoding': 'br, gzip' });
    assert.equal(got.res.headers['content-encoding'], undefined, i);
    assert.equal(got.res.headers.vary, 'Accept-Encoding', i);
    assert.equal(Number(got.res.headers['content-length']), got.body.length, i);
  }
});

test('la caché del lote comprimido sigue al contenido: un archivo cambiado da otro cuerpo', async () => {
  const file = path.join(temporary, 'game/media/lua/c.lua');
  await writeFile(file, Buffer.from('uno '.repeat(2000)));
  warm.push(['/media/lua/c.lua', '']); await writeFile(path.join(temporary, 'warm.json'), JSON.stringify(warm));
  server.close(); // el warm-list se lee al crear el servidor
  server = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: path.join(temporary, 'game'), packsRoot: path.join(temporary, 'packs'), warmFile: path.join(temporary, 'warm.json') });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); origin = `http://127.0.0.1:${server.address().port}`;
  const index = warm.length - 1;
  const first = decode(await get(`${origin}/media-ranges?i=${index}`, { 'Accept-Encoding': 'br' }));
  await writeFile(file, Buffer.from('dos '.repeat(2000)));
  const second = await get(`${origin}/media-ranges?i=${index}`, { 'Accept-Encoding': 'br' });
  assert.equal(second.res.headers['content-encoding'], 'br');
  assert.notDeepEqual(decode(second), first);
  assert.ok(decode(second).includes(Buffer.from('dos dos')));
});

test('prefetchMedia por lotes guarda los mismos bytes con la respuesta comprimida (fetch descomprime)', async () => {
  const vfs = new VirtualFileSystem();
  const fetchImpl = (url, init = {}) => fetch(new URL(url, origin), init);
  const stats = await prefetchMedia(vfs, { fetchImpl, batch: true });
  assert.equal(stats.mode, 'batch'); assert.equal(stats.failed, 0);
  assert.ok(vfs.prefetched.get(`${warm[0][0]}|${warm[0][1]}`).every((b, i) => b === lua[i]));
  assert.ok(Buffer.from(vfs.prefetched.get(`${warm[1][0]}|${warm[1][1]}`)).equals(lua.subarray(16000, 32000)));
  assert.ok(Buffer.from(vfs.prefetched.get(`${warm[3][0]}|${warm[3][1]}`)).equals(noise));
  assert.equal(Buffer.from(vfs.prefetched.get(`${warm[4][0]}|${warm[4][1]}`)).toString(), 'tiny');
});
