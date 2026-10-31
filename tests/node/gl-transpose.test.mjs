// Round 80: glUniformMatrix*fv with transpose=true is uploaded as the transposed copy (WebGL2 only
// accepts transpose=false); the shader must see the same matrix.
import test from 'node:test';
import assert from 'node:assert/strict';
import { transposeMatrices } from '../../port/runtime/gl.mjs';

test('row-major 2x2 and 3x3 matrices become column-major copies', () => {
  assert.deepEqual([...transposeMatrices(new Float32Array([1, 2, 3, 4]), 2, 1)], [1, 3, 2, 4]);
  const two = transposeMatrices(new Float32Array([1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9]), 3, 2);
  assert.deepEqual([...two], [1, 4, 7, 2, 5, 8, 3, 6, 9, 1, 4, 7, 2, 5, 8, 3, 6, 9]);
});
