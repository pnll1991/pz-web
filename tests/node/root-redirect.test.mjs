// La raíz lleva al juego: / y /index.html → /play.html (302 en el dev server, con la query intacta; index.html de respaldo
// conserva search y hash). Clave de prueba sintética; ninguna clave real.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import http from 'node:http';
import vm from 'node:vm';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { createDevServer } from '../../dev-server/server.mjs';
import { COOKIE } from '../../dev-server/public-mode.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const KEY = 'test-key-0123456789abcdef', HOST = 'quiet-lake-1234.trycloudflare.com';
let temporary, server, port;
before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-root-redirect-'));
  await mkdir(path.join(temporary, 'web'));
  await writeFile(path.join(temporary, 'web/index.html'), 'old index');
  await writeFile(path.join(temporary, 'web/play.html'), 'play page');
  await writeFile(path.join(temporary, 'web/lab.html'), 'lab page');
  server = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: undefined, publicKey: KEY, warmFile: path.join(temporary, 'warm.json') });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); port = server.address().port;
});
after(async () => { await new Promise(r => server.close(r)); await rm(temporary, { recursive: true }); });

const request = (pathname, headers = {}, method = 'GET') => new Promise((resolve, reject) => {
  const req = http.request({ host: '127.0.0.1', port, path: pathname, method, headers: { Host: `127.0.0.1:${port}`, ...headers } }, res => {
    let body = ''; res.on('data', d => body += d); res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
  });
  req.on('error', reject); req.end();
});
const tunnel = (extra = {}) => ({ Host: HOST, 'cf-connecting-ip': '203.0.113.9', ...extra });

test('GET / and /index.html redirect to /play.html, with and without a query', async () => {
  for (const [from, to] of [['/', '/play.html'], ['/index.html', '/play.html'], ['/?k=abc&x=1', '/play.html?k=abc&x=1'],
    ['/index.html?build=release&tos=1', '/play.html?build=release&tos=1'], ['/?a=%20b&c', '/play.html?a=%20b&c'], ['/?', '/play.html?']]) {
    const r = await request(from);
    assert.equal(r.status, 302, from); assert.equal(r.headers.location, to, from); assert.equal(r.body, '');
    assert.equal(r.headers['cross-origin-opener-policy'], 'same-origin'); assert.equal(r.headers['cache-control'], 'no-store');
  }
});

test('HEAD redirects too; other methods stay read-only', async () => {
  const r = await request('/?k=abc', {}, 'HEAD');
  assert.equal(r.status, 302); assert.equal(r.headers.location, '/play.html?k=abc');
  assert.equal((await request('/', {}, 'POST')).status, 405);
});

test('other paths are untouched and the target does not redirect (no loop)', async () => {
  const play = await request('/play.html?k=abc');
  assert.equal(play.status, 200); assert.equal(play.body, 'play page');
  assert.equal((await request('/lab.html')).body, 'lab page');
  assert.equal((await request('/index.htm')).status, 404);
  assert.equal((await request('/sub/')).status, 404);
});

test('public requests: no key → 403 (never a redirect); right key or cookie → redirect keeping the query', async () => {
  for (const p of ['/', '/index.html', `/?k=wrong-key-0123456789abcdef`]) {
    const r = await request(p, tunnel());
    assert.equal(r.status, 403, p); assert.equal(r.headers.location, undefined, p);
  }
  const withKey = await request(`/?k=${KEY}`, tunnel());
  assert.equal(withKey.status, 302); assert.equal(withKey.headers.location, `/play.html?k=${KEY}`);
  assert.match(withKey.headers['set-cookie'][0], new RegExp(`^${COOKIE}=`));
  const withCookie = await request('/?tos=1', tunnel({ Cookie: `${COOKIE}=${KEY}` }));
  assert.equal(withCookie.status, 302); assert.equal(withCookie.headers.location, '/play.html?tos=1');
  assert.equal(withCookie.headers['set-cookie'], undefined);
  assert.equal(withCookie.body.includes(KEY), false);
});

test('port/web/index.html (copied verbatim by scripts/sync_web.sh) redirects keeping search and hash', async () => {
  const html = await readFile(path.join(repo, 'port/web/index.html'), 'utf8');
  const sync = await readFile(path.join(repo, 'scripts/sync_web.sh'), 'utf8');
  assert.match(sync, /for f in port\/web\/\*; do .*sync_file "\$f" "\$out\/\$\(basename -- "\$f"\)"/);
  assert.match(html, /<meta http-equiv="refresh" content="0; url=\/play\.html">/);
  const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');
  const replaced = [];
  vm.runInNewContext(script, { location: { search: '?k=abc&x=1', hash: '#frag', replace: url => replaced.push(url) } });
  assert.deepEqual(replaced, ['/play.html?k=abc&x=1#frag']);
  const lab = await readFile(path.join(repo, 'port/web/lab.html'), 'utf8');
  assert.match(lab, /id="status"/, 'the platform lab page still exists for the browser specs');
});
