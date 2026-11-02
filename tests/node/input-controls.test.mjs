// Controls: keyboard, mouse buttons/wheel, focus and pointer lock mapping (CanvasInput + BrowserWindow). Synthetic events, no game bytes.
import { test } from 'node:test';
import assert from 'node:assert/strict';
globalThis.window ??= new EventTarget();
globalThis.document ??= new EventTarget();
const { CanvasInput } = await import('../../port/runtime/input.mjs');
const { BrowserWindow } = await import('../../port/runtime/window.mjs');

function setup() {
  const canvas = new EventTarget();
  canvas.focusCalls = 0; canvas.focus = () => { canvas.focusCalls++; document.activeElement = canvas; };
  canvas.setPointerCapture = () => {}; canvas.clientHeight = 720;
  canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width: 1280, height: 720 }); canvas.width = 1280; canvas.height = 720;
  canvas.style = {}; canvas.lockRequests = 0; canvas.requestPointerLock = () => { canvas.lockRequests++; };
  document.activeElement = null; document.pointerLockElement = null; document.hidden = false;
  document.exitPointerLock = () => {};
  const input = new CanvasInput(canvas);
  const fire = (target, type, props = {}) => {
    const e = new Event(type, { cancelable: true });
    for (const [k, v] of Object.entries(props)) Object.defineProperty(e, k, { value: v, configurable: true });
    target.dispatchEvent(e); return e;
  };
  const win = new BrowserWindow(canvas, {}, input); win.initialized = true; win.created = true;
  const lock = locked => { document.pointerLockElement = locked ? canvas : null; fire(document, 'pointerlockchange'); };
  const done = () => { input.dispose(); win.gamepadController.abort(); };
  return { input, canvas, win, fire, lock, done, last: () => input.events.at(-1) };
}

test('DOM buttons map to GLFW: left 0, right 1, middle 2, back/forward 3/4; the compatibility mouse events are cancelled', () => {
  const { input, canvas, fire, done } = setup();
  for (const [dom, glfw] of [[0, 0], [2, 1], [1, 2], [3, 3], [4, 4]]) {
    fire(canvas, 'pointerdown', { button: dom, pointerId: 1 });
    assert.equal(input.events.at(-1).button, glfw); assert.equal(input.events.at(-1).action, 1);
    fire(canvas, 'pointerup', { button: dom, pointerId: 1 });
    assert.equal(input.events.at(-1).button, glfw); assert.equal(input.events.at(-1).action, 0);
  }
  assert.equal(fire(canvas, 'mousedown', { button: 3 }).defaultPrevented, true);
  assert.equal(fire(canvas, 'mouseup', { button: 4 }).defaultPrevented, true);
  done();
});

test('wheel: down is negative like GLFW, lines/pages are scaled, and the page never scrolls or zooms', () => {
  const { input, canvas, fire, done } = setup();
  const e = fire(canvas, 'wheel', { deltaX: 0, deltaY: 100, deltaMode: 0 });
  assert.equal(e.defaultPrevented, true);
  assert.deepEqual([input.events.at(-1).type, input.events.at(-1).y], ['scroll', -100]);
  fire(canvas, 'wheel', { deltaX: 2, deltaY: -3, deltaMode: 1 });
  assert.deepEqual([input.events.at(-1).x, input.events.at(-1).y], [-32, 48]);
  fire(canvas, 'wheel', { deltaX: 0, deltaY: 1, deltaMode: 2, ctrlKey: true });
  assert.equal(input.events.at(-1).y, -720);
  done();
});

test('keys: browser actions are kept for the game, but F11/F12 and Ctrl/Meta chords stay with the browser', () => {
  const { canvas, fire, done } = setup();
  for (const code of ['Tab', 'Space', 'ArrowDown', 'F5', 'F1', 'AltLeft', 'ContextMenu', 'Backspace'])
    assert.equal(fire(canvas, 'keydown', { code, key: code }).defaultPrevented, true, code);
  for (const code of ['F11', 'F12']) assert.equal(fire(canvas, 'keydown', { code, key: code }).defaultPrevented, false, code);
  assert.equal(fire(canvas, 'keydown', { code: 'KeyW', key: 'w', ctrlKey: true }).defaultPrevented, false);
  assert.equal(fire(canvas, 'keydown', { code: 'KeyL', key: 'l', metaKey: true }).defaultPrevented, false);
  done();
});

test('alt-tab: blur releases every held key and button once; the held key coming back is a new PRESS', () => {
  const { input, canvas, fire, done } = setup();
  fire(canvas, 'keydown', { code: 'KeyW', key: 'w' }); fire(canvas, 'pointerdown', { button: 2, pointerId: 1 });
  fire(window, 'blur');
  assert.equal(input.keys.size, 0); assert.equal(input.buttons.size, 0);
  assert.deepEqual(input.events.slice(-2).map(e => `${e.type}:${e.action}`), ['key:0', 'button:0']);
  fire(canvas, 'keydown', { code: 'KeyW', key: 'w', repeat: true });
  assert.equal(input.events.filter(e => e.type === 'key').at(-1).action, 1);
  done();
});

test('focus comes back to the canvas after alt-tab, tab switch or a click on the HUD, but not from a text field', () => {
  const { canvas, fire, done } = setup();
  fire(window, 'focus'); assert.equal(document.activeElement, canvas);
  document.activeElement = null; document.hidden = true; fire(document, 'visibilitychange'); assert.equal(document.activeElement, null);
  document.hidden = false; fire(document, 'visibilitychange'); assert.equal(document.activeElement, canvas);
  document.activeElement = null;
  fire(document, 'pointerdown', { target: { closest: () => null } }); assert.equal(document.activeElement, canvas);
  const before = canvas.focusCalls; document.activeElement = 'field';
  fire(document, 'pointerdown', { target: { closest: () => ({}) } }); assert.equal(document.activeElement, 'field');
  fire(document, 'pointerdown', { target: canvas }); assert.equal(canvas.focusCalls, before);
  done();
});

test('GLFW_FOCUSED follows the page focus', () => {
  const { win, fire, done } = setup();
  assert.equal(win.attrib(1, 131073), 1);
  fire(window, 'blur'); assert.equal(win.attrib(1, 131073), 0);
  fire(window, 'focus'); assert.equal(win.attrib(1, 131073), 1);
  assert.deepEqual(win.events.slice(-2).map(e => e.type), ['focus_lost', 'focus_gained']);
  done();
});

test('pointer lock: the game asks, the browser answers; Esc releases the lock and the next click on the canvas asks again', () => {
  const { win, canvas, fire, lock, done } = setup();
  win.setInputMode(1, 208897, 212995); // GLFW_CURSOR_DISABLED
  assert.equal(canvas.lockRequests, 1); assert.equal(win.pointerLock, false); // never claims success
  lock(true); assert.equal(win.pointerLock, true); assert.equal(win.pointerLockPending, false);
  fire(canvas, 'pointerdown', { button: 0, pointerId: 1 }); assert.equal(canvas.lockRequests, 1); // already locked: nothing to ask
  lock(false); assert.equal(win.pointerLock, false);
  assert.equal(win.inputModeValue(1, 208897), 212995); // the game still believes the cursor is disabled
  fire(canvas, 'pointerdown', { button: 0, pointerId: 1 }); assert.equal(canvas.lockRequests, 2); // the gesture asks again
  lock(true); assert.equal(win.pointerLock, true);
  win.setInputMode(1, 208897, 212993); // back to the normal cursor: the click must not lock again
  lock(false); fire(canvas, 'pointerdown', { button: 0, pointerId: 1 });
  assert.equal(canvas.lockRequests, 2); assert.equal(canvas.style.cursor, '');
  done();
});

test('pointer lock: a refused request is recorded as an error, not as a lock', () => {
  const { win, canvas, done } = setup();
  canvas.requestPointerLock = () => { throw Object.assign(new Error('not allowed'), { name: 'NotAllowedError' }); };
  win.setInputMode(1, 208897, 212995);
  assert.equal(win.pointerLock, false); assert.match(win.pointerLockError, /NotAllowedError/);
  assert.equal(win.events.at(-2).type, 'pointer_lock_error');
  done();
});

test('pointer lock: the cursor is a virtual one that moves by the deltas; leaving the lock releases held buttons', () => {
  const { input, canvas, fire, lock, done } = setup();
  fire(canvas, 'pointermove', { clientX: 100, clientY: 50, movementX: 0, movementY: 0 });
  assert.deepEqual([input.events.at(-1).x, input.events.at(-1).y], [100, 50]);
  lock(true);
  fire(canvas, 'pointermove', { clientX: 100, clientY: 50, movementX: 30, movementY: -10 }); // clientX/Y frozen by the lock
  input.drain();
  fire(canvas, 'pointermove', { clientX: 100, clientY: 50, movementX: 5, movementY: 5 });
  assert.deepEqual([input.events.at(-1).x, input.events.at(-1).y], [135, 45]);
  fire(canvas, 'pointerdown', { button: 2, buttons: 2, pointerId: 1 }); assert.ok(input.buttons.has(1));
  lock(false);
  assert.equal(input.buttons.size, 0); assert.deepEqual(input.events.at(-1), { type: 'button', button: 1, action: 0 });
  fire(canvas, 'pointermove', { clientX: 7, clientY: 8, movementX: 1, movementY: 1 }); // unlocked: the real position again
  assert.deepEqual([input.events.at(-1).x, input.events.at(-1).y], [7, 8]);
  done();
});
