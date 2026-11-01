// Round 82: typed text reaches the game from a non-editable canvas (keydown of printable keys), without duplicates once
// beforeinput (IME / composed text) is seen. Synthetic events, no game bytes.
import { test } from 'node:test';
import assert from 'node:assert/strict';
globalThis.window ??= new EventTarget();
globalThis.document ??= new EventTarget();
const { CanvasInput } = await import('../../port/runtime/input.mjs');

function setup() {
  const canvas = new EventTarget(); canvas.focus = () => {}; canvas.setPointerCapture = () => {};
  canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width: 1280, height: 720 }); canvas.width = 1280; canvas.height = 720;
  const input = new CanvasInput(canvas);
  const fire = (type, props) => { const e = new Event(type, { cancelable: true }); Object.assign(e, props); canvas.dispatchEvent(e); };
  const texts = () => input.events.filter(e => e.type === 'text').map(e => e.text);
  return { input, fire, texts };
}

test('printable keydown becomes a text event; control keys and shortcuts do not', () => {
  const { fire, texts } = setup();
  fire('keydown', { code: 'KeyP', key: 'p' }); fire('keyup', { code: 'KeyP', key: 'p' });
  fire('keydown', { code: 'Period', key: '.' });
  fire('keydown', { code: 'Enter', key: 'Enter' });
  fire('keydown', { code: 'KeyA', key: 'a', ctrlKey: true });
  assert.deepEqual(texts(), ['p', '.']);
});

test('once beforeinput fires it is the only text source', () => {
  const { fire, texts } = setup();
  fire('beforeinput', { data: 'é' });
  fire('keydown', { code: 'KeyE', key: 'e' });
  assert.deepEqual(texts(), ['é']);
});
