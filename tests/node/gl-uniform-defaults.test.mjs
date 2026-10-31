// Round 80: desktop GLSL `uniform int useTexture = 1;` is set at every successful link. GLSL ES 3.00
// forbids the initializer (glsl.mjs strips it into uniformDefaults); the bridge must upload it after
// glLinkProgram for the game's own programs, or chunkShader/default draw untextured white quads
// (zombie.core.DefaultShader only uploads useTexture when it changes from the assumed 1).
// Own synthetic WebGL2 context; constants are the numbers Chrome's WebGL2 reports.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

const CONST = { COMPILE_STATUS: 35713, LINK_STATUS: 35714, VERTEX_SHADER: 35633, FRAGMENT_SHADER: 35632,
  CURRENT_PROGRAM: 35725, NO_ERROR: 0 };

function fakeContext({ linkOk = true } = {}) {
  const calls = [];
  let current = null, nextShader = 1;
  const context = {
    calls, ...CONST,
    getError: () => 0,
    getParameter: (p) => (p === CONST.CURRENT_PROGRAM ? current : null),
    createShader: (type) => ({ shader: nextShader++, type }),
    shaderSource: () => {}, compileShader: () => {},
    getShaderParameter: (s, p) => (p === CONST.COMPILE_STATUS ? true : s.type),
    getShaderInfoLog: () => '', deleteShader: () => {},
    createProgram: () => ({ program: 1 }),
    attachShader: () => {}, detachShader: () => {}, bindAttribLocation: () => {},
    linkProgram: () => calls.push(['linkProgram']),
    getProgramParameter: (_p, p) => (p === CONST.LINK_STATUS ? linkOk : 0),
    getProgramInfoLog: () => '',
    useProgram: (p) => { current = p; calls.push(['useProgram', p]); },
    getUniformLocation: (_p, name) => (['useTexture', 'chunkDepth', 'pz_id_texture'].includes(name) ? { name } : null),
    uniform1i: (l, v) => calls.push(['uniform1i', l.name, v]),
    uniform1f: (l, v) => calls.push(['uniform1f', l.name, v]),
  };
  return context;
}
const bridge = (context) => new GLBridge({ width: 8, height: 8, addEventListener: () => {}, getContext: () => context });

const VERT = '#version 120\nattribute vec4 p;\nvoid main(){gl_Position=p;}';
const FRAG = '#version 120\nuniform sampler2D DIFFUSE;\nuniform int useTexture = 1;\nuniform float chunkDepth = 0.0;\n' +
  'void main(){vec4 c=vec4(1.0); if(useTexture==1) c=texture2D(DIFFUSE,vec2(0.5)); gl_FragColor=c;}';

function source(gl, handle, text) {
  const bytes = new TextEncoder().encode(text);
  gl.shaderSource(handle, [0], [bytes.length], bytes, 0);
}
function linked(context, frag = FRAG) {
  const gl = bridge(context);
  const vs = gl.createShader(CONST.VERTEX_SHADER), fs = gl.createShader(CONST.FRAGMENT_SHADER);
  source(gl, vs, VERT); gl.compileShader(vs);
  source(gl, fs, frag); gl.compileShader(fs);
  const program = gl.createProgram();
  gl.attachShader(program, vs); gl.attachShader(program, fs);
  return { gl, program };
}

test('uniform initializers are uploaded after a successful link, keeping the current program', () => {
  const context = fakeContext();
  const { gl, program } = linked(context);
  const previous = { program: 'game' };
  context.useProgram(previous); context.calls.length = 0;
  gl.linkProgram(program);
  const after = context.calls.slice(context.calls.findIndex(c => c[0] === 'linkProgram') + 1);
  assert.deepEqual(after.filter(c => c[0].startsWith('uniform')), [['uniform1i', 'useTexture', 1], ['uniform1f', 'chunkDepth', 0]]);
  assert.equal(context.getParameter(CONST.CURRENT_PROGRAM), previous, 'current program restored');
});

test('a failed link uploads nothing', () => {
  const context = fakeContext({ linkOk: false });
  const { gl, program } = linked(context);
  context.calls.length = 0;
  gl.linkProgram(program);
  assert.equal(context.calls.filter(c => c[0].startsWith('uniform')).length, 0);
});

test('a renamed reserved identifier is set under its WebGL2 name', () => {
  const context = fakeContext();
  const { gl, program } = linked(context, '#version 120\nuniform float texture = 0.5;\nvoid main(){gl_FragColor=vec4(texture);}');
  context.calls.length = 0;
  gl.linkProgram(program);
  assert.deepEqual(context.calls.filter(c => c[0].startsWith('uniform')), [['uniform1f', 'pz_id_texture', 0.5]]);
});
