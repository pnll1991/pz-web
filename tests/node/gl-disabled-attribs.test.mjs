// Round 80 (front K): an active attribute whose array is disabled reads the constant generic value
// (desktop and WebGL2 alike); the bridge changes nothing and only counts those draws per
// program/attribute in `disabledAttribDraws`. Pattern: the game's .txt mesh layout (attribute =
// VertexFormat element index: 0 vertex, 1 normal, 2 tangent never enabled, 3 uv, 4 weights,
// 5 indices) drawn with a shader that binds boneWeights=2, boneIndices=3, uv=4.
// Own synthetic WebGL2 context and shaders.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

const C = { COMPILE_STATUS: 35713, LINK_STATUS: 35714, VERTEX_SHADER: 35633, FRAGMENT_SHADER: 35632,
  CURRENT_PROGRAM: 35725, ACTIVE_ATTRIBUTES: 35721, ARRAY_BUFFER: 34962, FLOAT: 5126, TRIANGLES: 4, NO_ERROR: 0 };
const LOCATIONS = { vertex: 0, normal: 1, weights: 2, indices: 3, uv: 4 };

function fakeContext() {
  const calls = [];
  let current = null;
  return {
    calls, ...C,
    getError: () => 0,
    getParameter: (p) => (p === C.CURRENT_PROGRAM ? current : null),
    createShader: (type) => ({ type }), shaderSource: () => {}, compileShader: () => {},
    getShaderParameter: (s, p) => (p === C.COMPILE_STATUS ? true : s.type), getShaderInfoLog: () => '',
    createProgram: () => ({}), attachShader: () => {}, bindAttribLocation: () => {},
    linkProgram: () => calls.push(['linkProgram']),
    getProgramParameter: (_p, p) => (p === C.LINK_STATUS ? true : p === C.ACTIVE_ATTRIBUTES ? 5 : 0),
    getProgramInfoLog: () => '',
    getActiveAttrib: (_p, i) => ({ name: Object.keys(LOCATIONS)[i] }),
    getAttribLocation: (_p, name) => LOCATIONS[name] ?? -1,
    getUniformLocation: () => null,
    useProgram: (p) => { current = p; },
    createBuffer: () => ({}), bindBuffer: () => {},
    enableVertexAttribArray: () => {}, disableVertexAttribArray: () => {}, vertexAttribPointer: () => {},
    drawArrays: () => calls.push(['drawArrays']),
  };
}

test('draws that leave an active attribute without its array are counted, nothing else changes', () => {
  const context = fakeContext();
  const gl = new GLBridge({ width: 8, height: 8, addEventListener: () => {}, getContext: () => context });
  const source = (h, text) => { const b = new TextEncoder().encode(text); gl.shaderSource(h, [0], [b.length], b, 0); };
  const vs = gl.createShader(C.VERTEX_SHADER), fs = gl.createShader(C.FRAGMENT_SHADER);
  source(vs, '#version 330\nlayout (location = 0) in vec4 vertex;\nlayout (location = 1) in vec4 normal;\n' +
    'layout (location = 2) in vec4 weights;\nlayout (location = 3) in vec4 indices;\nlayout (location = 4) in vec2 uv;\n' +
    'out vec2 t;\nvoid main(){t=uv*weights.x*indices.x;gl_Position=vertex+normal;}');
  source(fs, '#version 330\nin vec2 t;\nout vec4 colour;\nvoid main(){colour=vec4(t,0.0,1.0);}');
  gl.compileShader(vs); gl.compileShader(fs);
  const program = gl.createProgram();
  gl.attachShader(program, vs); gl.attachShader(program, fs);
  gl.linkProgram(program);
  gl.useProgram(program);
  const [buffer] = gl.genBuffers(1);
  gl.bindBuffer(C.ARRAY_BUFFER, buffer);
  // The .txt layout: element index = attribute index, the tangent (2) is never enabled.
  for (const [index, size, offset] of [[0, 3, 0], [1, 3, 12], [3, 2, 36], [4, 4, 44], [5, 4, 60]]) {
    gl.vertexAttribPointer(index, size, C.FLOAT, false, 76, offset);
    gl.enableAttrib(index);
  }
  gl.drawArrays(C.TRIANGLES, 0, 3);
  gl.drawArrays(C.TRIANGLES, 0, 3);
  assert.deepEqual(gl.disabledAttribDraws, { [`${program}:weights@2`]: 2 });
  assert.equal(context.calls.filter(c => c[0] === 'drawArrays').length, 2, 'the draws still go to WebGL2');
  gl.enableAttrib(2);
  gl.drawArrays(C.TRIANGLES, 0, 3);
  assert.deepEqual(gl.disabledAttribDraws, { [`${program}:weights@2`]: 2 });
});
