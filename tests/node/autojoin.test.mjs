// Round 83: direct-mode helpers (identity per browser, generated Lua header, console watcher, click loop). Synthetic data only.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { playerIdentity, buildLua, watchConsole, clickUntilIngame, installAutoJoin } from '../../port/runtime/autojoin.mjs';

const memoryStorage = () => { const m = new Map(); return { getItem: k => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }; };
const seeded = (seed = 1) => n => Uint8Array.from({ length: n }, () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) >> 8 & 255);

test('identity is created once per browser and reused', () => {
  const storage = memoryStorage();
  const a = playerIdentity(storage, seeded(1)), b = playerIdentity(storage, seeded(99));
  assert.deepEqual(a, b);
  assert.match(a.user, /^p[a-z0-9]{6}$/); assert.match(a.pass, /^[0-9a-f]{24}$/);
  assert.notDeepEqual(playerIdentity(memoryStorage(), seeded(7)), a);
});

test('a corrupted identity is replaced', () => {
  const storage = memoryStorage(); storage.setItem('pzweb.play.v1', '{"user":"x y","pass":1}');
  assert.match(playerIdentity(storage, seeded(3)).user, /^p[a-z0-9]{6}$/);
});

test('generated Lua header escapes quotes and backslashes', () => {
  const lua = buildLua('-- body', { user: 'u"1', pass: 'a\\b', host: '127.0.0.1', port: 16261 });
  assert.match(lua, /^PZWEB_AUTOJOIN = \{ user = "u\\"1", pass = "a\\\\b", host = "127.0.0.1", port = 16261, name = "pzweb", acceptTos = true \}\n-- body$/);
  assert.match(buildLua('', { user: 'u', pass: 'p', acceptTos: false }), /acceptTos = false/);
});

test('installAutoJoin mounts the generated script under media/lua/client', async () => {
  const mounted = {};
  const vfs = { mount: (p, d) => { mounted[p] = new TextDecoder().decode(d); } };
  const auto = await installAutoJoin(vfs, '?tos=1&port=16270', { storage: memoryStorage(),
    fetchImpl: async () => ({ ok: true, text: async () => '-- template' }) });
  const text = mounted['/media/lua/client/zzz_pzweb_play.lua'];
  assert.match(text, /acceptTos = false/); assert.match(text, /port = 16270/); assert.ok(text.endsWith('-- template'));
  assert.equal(auto.state, 'installed');
});

test('console watcher records script states and the loading line, and still logs', () => {
  const seen = [], consoleObj = { log: (...a) => seen.push(a[0]), info() {}, warn() {}, error() {} };
  const auto = { state: '', log: [] }, states = [];
  watchConsole(auto, { consoleObj, onState: s => states.push(s) });
  consoleObj.log('LOG  : Lua f:0> PZWEB-STATE:connect-1');
  consoleObj.log('game loading took 12 seconds');
  assert.deepEqual(states, ['connect-1', 'loaded']); assert.equal(auto.state, 'loaded'); assert.equal(seen.length, 2);
});

test('click loop presses and releases the left button until the world starts', () => {
  const events = [], timers = [], auto = { state: 'loaded' };
  const input = { buttons: new Set(), emit: e => events.push(e) };
  clickUntilIngame(auto, input, { width: 800, height: 600 }, { setTimeoutFn: f => timers.push(f) });
  assert.ok(input.buttons.has(0) && input.latched.has(0), 'button state is set while held');
  assert.deepEqual(events.map(e => e.type + (e.action ?? '')), ['cursor', 'button1']);
  timers.shift()(); assert.equal(events.at(-1).action, 0); assert.ok(!input.buttons.has(0));
  auto.state = 'ingame'; timers.shift()(); const n = events.length; assert.equal(events.length, n);
});
