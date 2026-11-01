// Round 84: public (tunnel) mode of the dev server. Synthetic requests only; the key below is a test value.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { once } from 'node:events';
import { createDevServer } from '../../dev-server/server.mjs';
import { createPublicGate, COOKIE } from '../../dev-server/public-mode.mjs';

const KEY = 'test-key-0123456789abcdef', HOST = 'quiet-lake-1234.trycloudflare.com';
let temporary, server, port;
before(async () => {
  temporary = await mkdtemp(path.join(tmpdir(), 'pz-public-test-'));
  await mkdir(path.join(temporary, 'web'));
  await writeFile(path.join(temporary, 'web/play.html'), 'play page');
  await writeFile(path.join(temporary, 'web/boot.wasm'), Buffer.from([0, 97, 115, 109]));
  server = await createDevServer({ webRoot: path.join(temporary, 'web'), pzHome: undefined, publicKey: KEY });
  server.listen(0, '127.0.0.1'); await once(server, 'listening'); port = server.address().port;
});
after(async () => { await new Promise(r => server.close(r)); await rm(temporary, { recursive: true }); });

const request = (pathname, headers = {}) => new Promise((resolve, reject) => {
  const req = http.request({ host: '127.0.0.1', port, path: pathname, headers: { Host: `127.0.0.1:${port}`, ...headers } }, res => {
    let body = ''; res.on('data', d => body += d); res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
  });
  req.on('error', reject); req.end();
});
const tunnel = (extra = {}) => ({ Host: HOST, 'cf-connecting-ip': '203.0.113.9', ...extra });

test('local requests are unchanged and keep COOP/COEP', async () => {
  const r = await request('/play.html');
  assert.equal(r.status, 200); assert.equal(r.body, 'play page');
  assert.equal(r.headers['cross-origin-opener-policy'], 'same-origin'); assert.equal(r.headers['cross-origin-embedder-policy'], 'require-corp');
});

test('a public request without key or cookie is 403', async () => {
  assert.equal((await request('/play.html', tunnel())).status, 403);
  assert.equal((await request('/play.html?k=wrong-key-0123456789abcdef', tunnel())).status, 403);
  assert.equal((await request('/play.html', tunnel({ Cookie: `${COOKIE}=nope` }))).status, 403);
  assert.equal((await request('/play.html', { 'cf-connecting-ip': '203.0.113.9' })).status, 403, 'cf header with a local Host is still public');
  assert.equal((await request('/play.html', { Host: 'evil.example.com' })).status, 403, 'unknown host');
});

test('the right key becomes an HttpOnly cookie and redirects without k', async () => {
  const r = await request('/play.html?x=1&k=' + KEY, tunnel());
  assert.equal(r.status, 302); assert.equal(r.headers.location, '/play.html?x=1');
  const cookie = r.headers['set-cookie'][0];
  assert.match(cookie, new RegExp(`^${COOKIE}=`)); for (const flag of ['HttpOnly', 'Secure', 'SameSite=Lax']) assert.ok(cookie.includes(flag), flag);
  assert.equal(r.headers['cross-origin-opener-policy'], 'same-origin');
  const ok = await request('/play.html', tunnel({ Cookie: cookie.split(';')[0], 'sec-fetch-site': 'same-origin' }));
  assert.equal(ok.status, 200); assert.equal(ok.body, 'play page'); assert.equal(ok.headers['cross-origin-embedder-policy'], 'require-corp');
});

test('a valid cookie does not open cross-site subresources', async () => {
  const cookie = `${COOKIE}=${KEY}`;
  assert.equal((await request('/boot.wasm', tunnel({ Cookie: cookie, 'sec-fetch-site': 'cross-site', 'sec-fetch-mode': 'no-cors' }))).status, 403);
  assert.equal((await request('/play.html', tunnel({ Cookie: cookie, 'sec-fetch-site': 'cross-site', 'sec-fetch-mode': 'navigate' }))).status, 200);
});

test('websocket upgrades: 403 without the cookie or with a foreign Origin, local rules untouched', async () => {
  const upgrade = headers => request('/ws', { Connection: 'Upgrade', Upgrade: 'websocket', 'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': 'dGhlIHNhbXBsZSBub25jZQ==', ...headers });
  assert.equal((await upgrade(tunnel({ Origin: `https://${HOST}` }))).status, 403);
  assert.equal((await upgrade(tunnel({ Origin: 'https://other.trycloudflare.com', Cookie: `${COOKIE}=${KEY}` }))).status, 403);
  assert.equal((await upgrade({ Origin: 'http://evil.example.com' })).status, 403);
});

test('gate unit: verdicts and a missing/short key', () => {
  const gate = createPublicGate({ key: KEY });
  const req = (h) => ({ headers: h, url: '/' });
  assert.equal(gate.wsAllowed(req({ host: '127.0.0.1:8098' })), null);
  assert.equal(gate.wsAllowed(req({ host: HOST, origin: `https://${HOST}`, cookie: `a=b; ${COOKIE}=${KEY}` })), true);
  assert.equal(gate.wsAllowed(req({ host: HOST, origin: `https://${HOST}`, cookie: `${COOKIE}=x` })), false);
  assert.equal(gate.wsAllowed(req({ host: HOST, cookie: `${COOKIE}=${KEY}` })), false, 'Origin is required');
  const off = createPublicGate({ key: 'short' });
  assert.equal(off.enabled, false); assert.equal(off.wsAllowed(req({ host: HOST, origin: `https://${HOST}`, cookie: `${COOKIE}=short` })), false);
});
