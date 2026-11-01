// Round 80 (front P): pzRuntime.gl.paletteProbe summary of the MatrixPalette upload (diagnostic only).
import test from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge, paletteSummary } from '../../port/runtime/gl.mjs';

test('palette summary reports determinant, scale, translation, identity/zero and NaN counts', () => {
  const data = new Float32Array(16 * 4);
  data.set([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1], 0); // identity
  data.set([2, 0, 0, 0, 0, 3, 0, 0, 0, 0, 4, 0, 5, 6, 7, 1], 16); // scale 2,3,4 + translation
  data.set([1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1], 32); // flattened (det 0)
  data[48] = NaN; data[49] = Infinity; // all-zero otherwise
  const s = paletteSummary(data, 4, 2);
  assert.equal(s.count, 4);
  assert.equal(s.identity, 1);
  assert.equal(s.nan, 1);
  assert.equal(s.inf, 1);
  assert.equal(s.zero, 0);
  assert.equal(s.first.length, 2);
  assert.equal(s.first[0].det, 1);
  assert.equal(s.first[1].det, 24);
  assert.deepEqual(s.first[1].scale, [2, 3, 4]);
  assert.deepEqual(s.first[1].translation, [5, 6, 7]);
  assert.equal(s.detMin, 0);
  assert.equal(s.detMax, 24);
});

test('a non-uniform rotation still has |det| = product of scales', () => {
  const c = Math.cos(0.3), n = Math.sin(0.3);
  const m = new Float32Array([c, n, 0, 0, -n, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]);
  const s = paletteSummary(m, 1);
  assert.ok(Math.abs(s.first[0].det - 1) < 1e-4);
  assert.equal(s.zero, 0);
});

test('the bridge records the last MatrixPalette upload only with the probe flag on', () => {
  const context = {
    NO_ERROR: 0, getParameter: () => 0, getError: () => 0, getSupportedExtensions: () => [],
    getUniformLocation: (program, name) => ({ __loc: name }), uniformMatrix4fv: () => {},
  };
  const gl = new GLBridge({ width: 8, height: 8, addEventListener: () => {}, getContext: () => context });
  gl.programs.set(7, { program: { __program: 7 } });
  const palette = gl.uniformLocation(7, 'MatrixPalette');
  const mvp = gl.uniformLocation(7, 'ModelViewProjection');
  // Row-major translation (transpose=true, as Shader.setMatrixPalette uploads it).
  const rowMajor = new Float32Array([1, 0, 0, 5, 0, 1, 0, 6, 0, 0, 1, 7, 0, 0, 0, 1]);
  delete globalThis.__pzPlayerProbeFlag; globalThis.PZ_PLAYER_PROBE = false;
  gl.uniformMatrix(4, palette, 1, true, rowMajor);
  assert.equal(gl.paletteProbe, undefined);
  delete globalThis.__pzPlayerProbeFlag; globalThis.PZ_PLAYER_PROBE = true;
  gl.uniformMatrix(4, mvp, 1, true, rowMajor);
  assert.equal(gl.paletteProbe, undefined);
  gl.uniformMatrix(4, palette, 1, true, rowMajor);
  gl.uniformMatrix(4, palette, 1, true, rowMajor);
  assert.equal(gl.paletteProbe.uploads, 2);
  assert.equal(gl.paletteProbe.uniform, 'MatrixPalette');
  assert.deepEqual(gl.paletteProbe.first[0].translation, [5, 6, 7]);
  delete globalThis.PZ_PLAYER_PROBE; delete globalThis.__pzPlayerProbeFlag;
});
