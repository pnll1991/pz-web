// Round 81b: glClear on the default framebuffer with a smaller-than-canvas viewport is counted
// (symptom of a skipped render-to-texture bind). Synthetic WebGL2 context, no game bytes.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

function bridge() {
  const context = {
    NO_ERROR: 0, COLOR_BUFFER_BIT: 16384, DEPTH_BUFFER_BIT: 256, STENCIL_BUFFER_BIT: 1024,
    drawingBufferWidth: 64, drawingBufferHeight: 64,
    getParameter: () => 0, getError: () => 0, getSupportedExtensions: () => [],
    clear() {}, viewport() {},
  };
  return new GLBridge({ width: 64, height: 64, addEventListener: () => {}, getContext: () => context });
}

test('full-size clear is not counted, small-viewport clear on screen is', () => {
  const gl = bridge();
  gl.clear(16384);
  assert.equal(gl.screenSmallClears, 0);
  gl.viewportBox = [0, 0, 32, 32];
  gl.clear(16384);
  assert.equal(gl.screenSmallClears, 1);
  assert.deepEqual(gl.screenSmallClearInfo.viewport, [0, 0, 32, 32]);
});

test('small-viewport clear on an FBO is not counted', () => {
  const gl = bridge();
  gl.viewportBox = [0, 0, 32, 32];
  gl.drawFramebufferHandle = 5;
  gl.clear(16384);
  assert.equal(gl.screenSmallClears, 0);
});
