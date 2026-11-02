// Round 86b: the right mouse button reaches the game (GLFW button 1) and the browser's context menu is suppressed. Synthetic events, no game bytes.
import { test } from 'node:test';
import assert from 'node:assert/strict';
globalThis.window ??= new EventTarget();
globalThis.document ??= new EventTarget();
const { CanvasInput } = await import('../../port/runtime/input.mjs');

function setup() {
  const canvas = new EventTarget(); canvas.focus = () => {}; canvas.setPointerCapture = () => {};
  canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width: 1280, height: 720 }); canvas.width = 1280; canvas.height = 720;
  const input = new CanvasInput(canvas);
  const fire = (target, type, props = {}) => { const e = new Event(type, { cancelable: true }); Object.assign(e, props); target.dispatchEvent(e); return e; };
  return { input, canvas, fire, buttons: () => input.events.filter(e => e.type === 'button').map(e => `${e.button}:${e.action}`) };
}

test('right button down/up become GLFW button 1 events and stay held in between', () => {
  const { input, canvas, fire, buttons } = setup();
  fire(canvas, 'pointerdown', { button: 2, pointerId: 1 });
  assert.ok(input.buttons.has(1));
  fire(canvas, 'pointerup', { button: 2, pointerId: 1 });
  assert.ok(!input.buttons.has(1));
  assert.deepEqual(buttons(), ['1:1', '1:0']);
});

test('contextmenu is cancelled on the canvas and on the whole document; middle click does not autoscroll', () => {
  const { canvas, fire } = setup();
  assert.equal(fire(document, 'contextmenu').defaultPrevented, true);
  assert.equal(fire(canvas, 'auxclick', { button: 1 }).defaultPrevented, true);
  assert.equal(fire(canvas, 'pointerdown', { button: 1, pointerId: 1 }).defaultPrevented, true);
  assert.equal(fire(canvas, 'pointerdown', { button: 2, pointerId: 1 }).defaultPrevented, true);
});

test('a key tapped between two samples still reads as down once (latch), like buttons', () => {
  const { input, canvas, fire } = setup();
  fire(canvas, 'keydown', { code: 'Space', key: ' ' }); fire(canvas, 'keyup', { code: 'Space', key: ' ' });
  assert.ok(!input.keys.has(32));
  assert.ok(input.latchedKeys.has(32));
});

test('button events survive a full queue and cursor moves are merged', () => {
  const { input, canvas, fire } = setup();
  fire(canvas, 'pointerdown', { button: 2, pointerId: 1 });
  for (let i = 0; i < 2000; i++) fire(canvas, 'pointermove', { clientX: i, clientY: 0, movementX: 1, movementY: 0 });
  fire(canvas, 'pointerup', { button: 2, pointerId: 1 });
  assert.deepEqual(input.events.filter(e => e.type === 'button').map(e => `${e.button}:${e.action}`), ['1:1', '1:0']);
  assert.equal(input.events.filter(e => e.type === 'cursor').length, 1);
});

test('a button the browser stopped reporting is released from event.buttons', () => {
  const { input, canvas, fire } = setup();
  fire(canvas, 'pointerdown', { button: 2, pointerId: 1 });
  fire(canvas, 'pointermove', { clientX: 1, clientY: 1, movementX: 0, movementY: 0, buttons: 0 });
  assert.ok(!input.buttons.has(1));
});

test('numpad keys map to GLFW, modifiers travel with the event, held-before-focus is a PRESS', () => {
  const { input, canvas, fire } = setup();
  fire(canvas, 'keydown', { code: 'NumpadEnter', key: 'Enter', shiftKey: true, repeat: true });
  const e = input.events.at(-1);
  assert.equal(e.key, 335); assert.equal(e.action, 1); assert.equal(e.mods, 1);
  fire(canvas, 'keydown', { code: 'NumpadEnter', key: 'Enter', repeat: true });
  assert.equal(input.events.at(-1).action, 2);
});

test('a left press while the right button is held (a chord: only a pointermove with new event.buttons) reaches the game as button 0', () => {
  const { input, canvas, fire, buttons } = setup();
  fire(canvas, 'pointerdown', { button: 2, buttons: 2, pointerId: 1 });
  fire(canvas, 'pointermove', { buttons: 3, clientX: 10, clientY: 10, movementX: 0, movementY: 0 }); // left joins
  assert.ok(input.buttons.has(0) && input.buttons.has(1));
  assert.ok(input.latched.has(0));
  fire(canvas, 'pointermove', { buttons: 2, clientX: 10, clientY: 10, movementX: 0, movementY: 0 }); // left released, right still held
  assert.ok(!input.buttons.has(0) && input.buttons.has(1));
  fire(canvas, 'pointerup', { button: 2, buttons: 0, pointerId: 1 });
  assert.deepEqual(buttons(), ['1:1', '0:1', '0:0', '1:0']);
});
