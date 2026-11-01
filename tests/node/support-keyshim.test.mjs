// Round 85: platform check (support.js), the ?k= request shim (keyshim.js) and key stripping in the warm list. Synthetic environments.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { stripKey } from '../../dev-server/warm-list.mjs';

const load = (file, sandbox) => { vm.runInNewContext(readFileSync(new URL(`../../port/web/${file}`, import.meta.url), 'utf8'), sandbox); return sandbox; };
const { pzSupportProblem, GC_PROBE } = load('support.js', { module: { exports: {} }, Uint8Array }).module.exports;
const chrome = { userAgent: 'Mozilla/5.0 (X11; Linux x86_64) Chrome/130', platform: 'Linux x86_64', maxTouchPoints: 0, wasmGC: true, isSecureContext: true, crossOriginIsolated: true };

test('a desktop browser with WasmGC and isolation is fine', () => assert.equal(pzSupportProblem(chrome), null));

test('iPhone, iPad (also as Mac with touch) and Android phones get a clear Spanish message', () => {
  const iphone = pzSupportProblem({ ...chrome, userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) Safari/604.1', platform: 'iPhone', maxTouchPoints: 5 });
  assert.match(iphone, /^Necesitás una PC con Chrome, Edge o Firefox de escritorio/);
  assert.match(pzSupportProblem({ ...chrome, userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1', platform: 'MacIntel', maxTouchPoints: 5 }), /iPad/);
  assert.match(pzSupportProblem({ ...chrome, userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/130 Mobile Safari/537.36' }), /celulares/);
  assert.equal(pzSupportProblem({ ...chrome, userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1', platform: 'MacIntel', maxTouchPoints: 0 }), null, 'a real Mac is not an iPad');
});

test('missing WasmGC, insecure or non-isolated pages are explained', () => {
  assert.match(pzSupportProblem({ ...chrome, wasmGC: false }), /WebAssembly GC/);
  assert.match(pzSupportProblem({ ...chrome, isSecureContext: false }), /https/);
  assert.match(pzSupportProblem({ ...chrome, crossOriginIsolated: false }), /COOP\/COEP/);
});

test('the WasmGC probe is a valid wasm module on this runtime (a GC engine)', () => assert.equal(WebAssembly.validate(GC_PROBE), true));

function shimEnv(search) {
  const calls = { fetch: [], open: [], ws: [] };
  class XHR { open(...a) { calls.open.push(a); } }
  class WS { constructor(...a) { calls.ws.push(a); } }
  class Req { constructor(url, init) { this.url = url; this.init = init; } }
  const env = { location: { search, href: 'https://x.trycloudflare.com/game.html' + search, host: 'x.trycloudflare.com' }, URL, URLSearchParams, Request: Req,
    fetch: (...a) => { calls.fetch.push(a); return Promise.resolve(); }, XMLHttpRequest: XHR, WebSocket: WS };
  env.window = env; load('keyshim.js', env); return { env, calls, XHR };
}

test('keyshim adds the key to this site\'s fetch / XHR / WebSocket, and only to them', () => {
  const { env, calls } = shimEnv('?build=r&k=SECRET');
  env.fetch('/release/game/build-state.json'); env.fetch('https://other.example.com/a'); env.fetch('/media/a?r=0-4');
  assert.deepEqual(calls.fetch.map(a => a[0]), ['https://x.trycloudflare.com/release/game/build-state.json?k=SECRET', 'https://other.example.com/a', 'https://x.trycloudflare.com/media/a?r=0-4&k=SECRET']);
  new env.XMLHttpRequest().open('GET', '/media/b', false);
  assert.equal(calls.open[0][1], 'https://x.trycloudflare.com/media/b?k=SECRET'); assert.equal(calls.open[0][2], false);
  new env.WebSocket('wss://x.trycloudflare.com/ws'); new env.WebSocket('wss://elsewhere.example.com/ws');
  assert.equal(calls.ws[0][0], 'wss://x.trycloudflare.com/ws?k=SECRET'); assert.equal(calls.ws[1][0], 'wss://elsewhere.example.com/ws');
});

test('keyshim does nothing without k in the page URL', () => {
  const { env, calls } = shimEnv('?build=r'); const original = env.fetch;
  env.fetch('/a'); assert.equal(calls.fetch[0][0], '/a');
});

test('the warm list never stores the key', () => {
  assert.equal(stripKey('/media/a.bin?r=0-4&k=SECRET'), '/media/a.bin?r=0-4');
  assert.equal(stripKey('/media-bundle?k=SECRET&dir=maps&ext=lotheader'), '/media-bundle?dir=maps&ext=lotheader');
  assert.equal(stripKey('/media/a.bin?k=SECRET'), '/media/a.bin');
  assert.equal(stripKey('/media/a.bin?r=0-4'), '/media/a.bin?r=0-4');
});
