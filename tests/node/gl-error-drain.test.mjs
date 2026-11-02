// Ronda 89 (frente C): getError reales durante la carga. Contexto WebGL2 sintético, sin bytes del juego.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

// Cola pegajosa como la de WebGL2: `fail` hace que la próxima llamada con ese nombre deje un error.
function make(strict = false) {
  const queue = []; const fail = new Map();
  const mk = (n) => () => { if (fail.has(n)) queue.push(fail.get(n)); };
  const context = { NO_ERROR: 0, INVALID_ENUM: 0x500, INVALID_VALUE: 0x501, getParameter: () => 0,
    getSupportedExtensions: () => [], getError: () => queue.shift() ?? 0 };
  for (const n of ['clearColor', 'colorMask', 'depthMask', 'viewport', 'scissor', 'bindTexture', 'texParameteri'])
    context[n] = mk(n);
  globalThis.PZ_GL_ERRORS = strict ? 'strict' : undefined;
  const gl = new GLBridge({ width: 8, height: 8, addEventListener: () => {}, getContext: () => context });
  globalThis.PZ_GL_ERRORS = undefined;
  return { gl, fail, queue };
}
const frame = (gl) => { gl.call('colorMask', true, true, true, true); gl.call('depthMask', true);
  gl.call('clearColor', 0, 0, 0, 1); gl.call('viewport', 0, 0, 8, 8); gl.call('scissor', 0, 0, 8, 8); };

test('llamadas sin condición de error no cuestan getError real; strict vuelve al comportamiento anterior', () => {
  const a = make(); frame(a.gl);
  assert.equal(a.gl.getError(), 0); assert.equal(a.gl.realGetErrors, 0);
  const b = make(true); frame(b.gl);
  assert.equal(b.gl.getError(), 0); assert.equal(b.gl.realGetErrors, 1);
});

test('viewport con tamaño negativo sí se comprueba y su error llega al juego', () => {
  const { gl, fail } = make(); fail.set('viewport', 0x501);
  gl.call('viewport', 0, 0, -1, 8);
  assert.equal(gl.getError(), 0x501); assert.equal(gl.getError(), 0);
  assert.equal(gl.realGetErrors, 2);
});

test('un error de una llamada que sí puede fallar no se pierde aunque lo sigan llamadas sin error', () => {
  const { gl, fail } = make(); fail.set('texParameteri', 0x500);
  gl.call('texParameteri', 3553, 1, 2);
  fail.delete('texParameteri'); frame(gl);
  assert.equal(gl.getError(), 0x500); assert.equal(gl.getError(), 0);
  assert.equal(gl.errorDrains[0].fromCall, 'deferred..texParameteri');
});

test('cuenta de getError reales por ciclo: frame sin error 0, textura 1 por drenaje', () => {
  const { gl } = make();
  for (let i = 0; i < 100; i++) { frame(gl); assert.equal(gl.getError(), 0); }
  assert.equal(gl.realGetErrors, 0);
  for (let i = 0; i < 100; i++) { gl.call('bindTexture', 3553, null); gl.call('texParameteri', 3553, 1, 2); assert.equal(gl.getError(), 0); }
  assert.equal(gl.realGetErrors, 100);
  const s = make(true);
  for (let i = 0; i < 100; i++) { frame(s.gl); s.gl.getError(); }
  assert.equal(s.gl.realGetErrors, 100);
});
