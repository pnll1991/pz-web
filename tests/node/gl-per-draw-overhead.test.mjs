// Ronda 81 (frente 3): estado por draw sin trabajo repetido. Contexto WebGL2 sintético, sin bytes del juego.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

function make() {
  const calls = [];
  const rec = (n) => (...a) => { calls.push([n, ...a.map((x) => (x && x.__loc) || x)]); };
  const context = {
    calls, NO_ERROR: 0, getParameter: () => 0, getError: () => 0, getSupportedExtensions: () => [],
    getUniformLocation: (p, name) => { calls.push(['getUniformLocation', name]); return { __loc: name }; },
    uniform1i: rec('uniform1i'), uniform1f: rec('uniform1f'), uniformMatrix4fv: rec('uniformMatrix4fv'), uniformMatrix3fv: rec('uniformMatrix3fv'),
    useProgram: rec('useProgram'),
  };
  const gl = new GLBridge({ width: 8, height: 8, addEventListener: () => {}, getContext: () => context });
  return { gl, calls };
}
const entry = (gl, handle) => { const e = { program: { __p: handle }, linked: true, alphaTest: true, handle }; gl.programs.set(handle, e); return e; };

test('applyAlphaTest: locations are looked up once and an unchanged state is not uploaded again', () => {
  const { gl, calls } = make(); const e = entry(gl, 3);
  gl.applyAlphaTest(e); gl.applyAlphaTest(e); gl.applyAlphaTest(e);
  assert.equal(calls.filter((c) => c[0] === 'getUniformLocation').length, 2);
  assert.equal(calls.filter((c) => c[0] === 'uniform1i').length, 1);
  assert.equal(calls.filter((c) => c[0] === 'uniform1f').length, 1);
  gl.alphaTestState.ref = 0.25; gl.applyAlphaTest(e);
  assert.equal(calls.filter((c) => c[0] === 'uniform1f').length, 2);
  // relink: the program's uniforms are reset, so the state is uploaded again
  e.alphaLocations = undefined; e.alphaApplied = undefined;
  gl.applyAlphaTest(e);
  assert.equal(calls.filter((c) => c[0] === 'uniform1i').length, 3); // initial, ref change, after relink
});

test('the call log keeps the last 160 calls in order with recycled entries', () => {
  const { gl } = make();
  for (let i = 0; i < 500; i++) gl.call('useProgram', null);
  assert.equal(gl.callLog.length, 160);
  assert.equal(gl.callLog[159].seq, gl.callSeq);
  assert.equal(gl.callLog[0].seq, gl.callSeq - 159);
  assert.ok(gl.callLog.every((e, i) => i === 0 || e.seq === gl.callLog[i - 1].seq + 1));
});

test('matrix uploads reuse one scratch array and still send the right values', () => {
  const { gl, calls } = make(); const e = entry(gl, 4); gl.currentProgram = e;
  gl.matrixStacks.MODELVIEW[0] = Array.from({ length: 16 }, (_, i) => i + 1);
  gl.matrixSerial++; gl.applyMatrices();
  assert.ok(gl.matrixScratch instanceof Float32Array);
  assert.equal(calls.filter((c) => c[0] === 'uniformMatrix4fv').length, 3);
});
