import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

/**
 * Round 80 (front I): glBufferData/glBufferSubData keep a CPU view of the contents so that
 * glMapBuffer (port.shim.GL15: BUFFER_SIZE + getBufferSubData of the whole buffer) and
 * glGetBufferSubData answer without a WebGL2 getBufferSubData (a synchronous GPU->CPU readback).
 * Own synthetic WebGL2 context: it stores real bytes per buffer and counts every readback.
 */
function fakeContext() {
  const calls = [];
  const store = new Map(); // WebGLBuffer -> Uint8Array
  const bound = { 34962: null, 34963: null };
  let id = 0;
  const context = {
    calls, store,
    ARRAY_BUFFER: 34962, ELEMENT_ARRAY_BUFFER: 34963, BUFFER_SIZE: 34660, DYNAMIC_DRAW: 35048,
    STREAM_DRAW: 35040, TRIANGLES: 4, NO_ERROR: 0,
    createBuffer: () => ({ __buf: ++id }), deleteBuffer: () => {},
    bindBuffer: (t, b) => { bound[t] = b; calls.push(['bindBuffer', t, b]); },
    bufferData: (t, v) => {
      calls.push(['bufferData', t]);
      store.set(bound[t], typeof v === 'number' ? new Uint8Array(v) : new Uint8Array(v.buffer, v.byteOffset, v.byteLength).slice());
    },
    bufferSubData: (t, o, d) => { calls.push(['bufferSubData', t, o]); store.get(bound[t]).set(d, o); },
    getBufferSubData: (t, o, d) => { calls.push(['getBufferSubData', t, o, d.length]); d.set(store.get(bound[t]).subarray(o, o + d.length)); },
    getBufferParameter: (t) => { calls.push(['getBufferParameter', t]); return store.get(bound[t])?.length ?? 0; },
    drawElements: (...a) => calls.push(['drawElements', ...a]),
    getParameter: (p) => (p === 0x8895 ? bound[34963] : 0),
    getError: () => { calls.push(['getError']); return 0; }, getSupportedExtensions: () => [],
  };
  return context;
}
function bridge(context) {
  const canvas = { width: 8, height: 8, addEventListener: () => {}, getContext: () => context };
  return new GLBridge(canvas);
}
const named = (context, name) => context.calls.filter((c) => c[0] === name);

/** What port.shim.GL15.nglMapBuffer does: BUFFER_SIZE, then read the whole buffer into the block. */
function mapBuffer(gl, target) {
  const size = gl.bufferParameter(target, 0x8764)[0];
  const block = new Uint8Array(size);
  gl.getBufferSubData(target, 0, block);
  return block;
}

test('bufferData does not read the GPU back by default (no ring, no getError, no BUFFER_SIZE query)', () => {
  const context = fakeContext(), gl = bridge(context);
  const [vbo] = gl.genBuffers(1);
  gl.bindBuffer(34962, vbo);
  for (let i = 0; i < 50; i++) gl.bufferData(34962, 65536, null);
  gl.bufferData(34962, 4, new Uint8Array([1, 2, 3, 4]));
  assert.equal(named(context, 'getBufferSubData').length, 0);
  assert.equal(named(context, 'getBufferParameter').length, 0);
  assert.equal(named(context, 'getError').length, 0);
  assert.equal(gl.bufferDataLog, undefined);
  assert.deepEqual(gl.bufferParameter(34962, 0x8764), [4]);
  assert.equal(named(context, 'getBufferParameter').length, 0);
});

test('orphan + glMapBuffer + unmap (SpriteRenderer ring buffer) never calls getBufferSubData', () => {
  const context = fakeContext(), gl = bridge(context);
  const [vbo] = gl.genBuffers(1);
  gl.bindBuffer(34962, vbo);
  for (let frame = 0; frame < 3; frame++) {
    gl.bufferData(34962, 16, null);         // glBufferData(target, size, usage): orphan
    const block = mapBuffer(gl, 34962);
    assert.deepEqual([...block], new Array(16).fill(0)); // WebGL2 zero-fills new storage
    block[0] = frame + 1; block[15] = 0x80 + frame;
    gl.bufferSubData(34962, 0, block);       // glUnmapBuffer writes the whole block back
    assert.deepEqual([...context.store.get(gl.buffers.get(vbo).buffer)], [...block]);
  }
  assert.equal(named(context, 'getBufferSubData').length, 0);
  // Mapping again without orphaning answers the last written contents from the shadow.
  const again = mapBuffer(gl, 34962);
  assert.equal(again[0], 3); assert.equal(again[15], 0x82);
  assert.equal(named(context, 'getBufferSubData').length, 0);
});

test('glGetBufferSubData returns the real contents; unknown contents are read back once, then shadowed', () => {
  const context = fakeContext(), gl = bridge(context);
  const [a, b] = gl.genBuffers(2);
  gl.bindBuffer(34962, a);
  gl.bufferData(34962, 8, new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8]));
  gl.bufferSubData(34962, 2, new Uint8Array([9, 9]));
  const out = new Uint8Array(4);
  gl.getBufferSubData(34962, 1, out);
  assert.deepEqual([...out], [2, 9, 9, 5]);
  assert.equal(named(context, 'getBufferSubData').length, 1); // one real readback: contents were GPU-only
  gl.bufferSubData(34962, 0, new Uint16Array([0x0b0a]));     // typed views of other widths are bytes too
  gl.bufferData(34962, 3, new Uint8Array([7, 7, 7]));        // tracked: the shadow follows bufferData
  gl.bufferSubData(34962, 1, new Uint8Array([4]));
  const out2 = new Uint8Array(3);
  gl.getBufferSubData(34962, 0, out2);
  assert.deepEqual([...out2], [7, 4, 7]);
  gl.bufferData(34962, 4, null); gl.bufferSubData(34962, 3, new Uint8Array([5]));
  const out3 = new Uint8Array(4); gl.getBufferSubData(34962, 0, out3);
  assert.deepEqual([...out3], [0, 0, 0, 5]);
  assert.equal(named(context, 'getBufferSubData').length, 1);
  // Shadow is per buffer: another binding is untouched and still has no copy.
  gl.bindBuffer(34962, b);
  gl.bufferData(34962, 2, new Uint8Array([3, 1]));
  assert.equal(gl.buffers.get(b).shadow, null);
  assert.equal(gl.buffers.get(b).store, undefined);
  // Out of range is left to WebGL2, which owns the INVALID_VALUE.
  gl.getBufferSubData(34962, 1, new Uint8Array(4));
  assert.equal(named(context, 'getBufferSubData').at(-1)[3], 4);
});

test('GL_QUADS through a bound element buffer reads indices from the CPU view', () => {
  const context = fakeContext(), gl = bridge(context);
  gl.applyColor = () => {}; gl.applyMatrices = () => {};
  const [ebo] = gl.genBuffers(1);
  gl.bindBuffer(34963, ebo);
  gl.bufferData(34963, 8, null);
  gl.getBufferSubData(34963, 0, new Uint8Array(8)); // the game maps it once: tracked from here
  gl.bufferSubData(34963, 0, new Uint16Array([0, 1, 2, 3]));
  gl.drawElements(7, 4, 0x1403, 0); // GL_QUADS, GL_UNSIGNED_SHORT
  assert.equal(named(context, 'getBufferSubData').length, 0);
  const tri = context.calls.filter((c) => c[0] === 'bufferData').at(-1);
  assert.ok(tri);
  assert.deepEqual(named(context, 'drawElements').at(-1).slice(1, 3), [4, 6]);
});

test('the diagnostic ring stays available on request', () => {
  globalThis.PZ_GL_BUFFER_DIAG = true;
  try {
    const context = fakeContext(), gl = bridge(context);
    const [vbo] = gl.genBuffers(1);
    gl.bindBuffer(34962, vbo);
    gl.bufferData(34962, 32, null);
    assert.equal(gl.bufferDataLog.length, 1);
    assert.equal(gl.bufferDataLog[0].stored, 32);
  } finally { delete globalThis.PZ_GL_BUFFER_DIAG; }
});
