// Ronda 87: el registro del service worker es opt-in y ?sw=0 lo da de baja. Fixtures sintéticas, sin navegador.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { setupServiceWorker, keepPacks } from '../../port/runtime/sw-register.mjs';

const fakeNav = () => {
  const log = { registered: [], unregistered: 0, messages: [] };
  const reg = { active: { postMessage: m => log.messages.push(m) }, unregister: async () => { log.unregistered++; } };
  return { log, nav: { serviceWorker: { register: async (url, opt) => { log.registered.push([url, opt]); return reg; }, ready: Promise.resolve(reg),
    getRegistrations: async () => [reg], controller: { postMessage: m => log.messages.push(m) } } } };
};

test('sin ?sw no hace nada', async () => {
  const { nav, log } = fakeNav();
  assert.deepEqual(await setupServiceWorker('?x=1', nav, async () => { throw new Error('no debe pedir nada'); }), { state: 'off' });
  assert.equal(log.registered.length, 0);
});

test('?sw=1 registra /sw.js con la versión del manifest de media', async () => {
  const { nav, log } = fakeNav();
  const r = await setupServiceWorker('?sw=1', nav, async () => new Response('{"a":1}'));
  assert.equal(r.state, 'registered');
  assert.match(r.media, /^[0-9a-f]{16}$/);
  assert.equal(log.registered[0][0], `/sw.js?m=${r.media}`);
  assert.equal(log.registered[0][1].scope, '/');
});

test('?sw=0 da de baja el registro y pide vaciar las cachés', async () => {
  const { nav, log } = fakeNav();
  assert.deepEqual(await setupServiceWorker('?sw=0', nav), { state: 'unregistered' });
  assert.equal(log.unregistered, 1);
  assert.deepEqual(log.messages, [{ type: 'clear' }]);
});

test('sin soporte de service worker queda apagado y keepPacks no falla', async () => {
  assert.deepEqual(await setupServiceWorker('?sw=1', {}), { state: 'off' });
  keepPacks(['a.bin'], {});
});
