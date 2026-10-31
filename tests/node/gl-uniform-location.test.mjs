// Round 80 (front J): desktop glUniform* with location -1 is a silent no-op; an unknown location
// records GL_INVALID_OPERATION instead of throwing. Synthetic WebGL2 context, no game bytes.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

function fakeContext() {
  const calls = [];
  const setter = (name) => (loc, ...a) => calls.push([name, loc && loc.__loc, ...a]);
  const context = {
    calls, NO_ERROR: 0,
    getParameter: () => 0, getError: () => 0, getSupportedExtensions: () => [],
    getUniformLocation: (program, name) => (name === 'Zoom' ? { __loc: name } : null),
    uniform1f: setter('uniform1f'), uniform3f: setter('uniform3f'), uniform1i: setter('uniform1i'),
    uniform4fv: setter('uniform4fv'), uniformMatrix4fv: setter('uniformMatrix4fv'),
  };
  return context;
}
function bridge() {
  const context = fakeContext();
  const gl = new GLBridge({ width: 8, height: 8, addEventListener: () => {}, getContext: () => context });
  gl.programs.set(7, { program: { __program: 7 } });
  return gl;
}

test('location -1 is ignored by every uniform setter without an error', () => {
  const gl = bridge();
  assert.equal(gl.uniformLocation(7, 'TimeOfDay'), -1);
  gl.call('uniform1f', gl.locationOf(-1, 'uniform1f'), 0.5);
  gl.call('uniform3f', gl.locationOf(-1, 'uniform3f'), 1, 2, 3);
  gl.uniformfv('uniform4fv', -1, 1, new Float32Array(4));
  gl.uniformMatrix(4, -1, 1, true, new Float32Array(16));
  assert.deepEqual(gl.context.calls.map((c) => c[1]), [null, null, null, null]);
  assert.equal(gl.getError(), 0);
});

test('an unknown location records GL_INVALID_OPERATION once and has no effect', () => {
  const gl = bridge();
  const zoom = gl.uniformLocation(7, 'Zoom');
  assert.ok(zoom > 0);
  gl.call('uniform1f', gl.locationOf(zoom, 'uniform1f'), 2);
  gl.call('uniform1f', gl.locationOf(12345, 'uniform1f'), 3);
  gl.call('uniform1i', gl.locationOf(0, 'uniform1i'), 3);
  assert.deepEqual(gl.context.calls, [['uniform1f', 'Zoom', 2], ['uniform1f', null, 3], ['uniform1i', null, 3]]);
  assert.equal(gl.getError(), 0x0502);
  assert.equal(gl.getError(), 0x0502);
  assert.equal(gl.getError(), 0);
});
