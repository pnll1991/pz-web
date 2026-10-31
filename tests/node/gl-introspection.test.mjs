import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

/**
 * Own synthetic WebGL2 context for the shader/program introspection the game reaches through
 * zombie.core.opengl.ShaderProgram (getShaderiv/getProgramiv with desktop tokens, plus
 * glBindAttribLocation/getAttachedShaders/getActiveUniform/getActiveAttrib/getShaderSource).
 * Constants are the numbers Chrome's WebGL2 actually reports.
 */
const CONST = {
  DELETE_STATUS: 35712, COMPILE_STATUS: 35713, LINK_STATUS: 35714, VALIDATE_STATUS: 35715,
  SHADER_TYPE: 35663, ATTACHED_SHADERS: 35717, ACTIVE_UNIFORMS: 35718, ACTIVE_ATTRIBUTES: 35721,
  MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS: 35979, VERTEX_SHADER: 35632, FRAGMENT_SHADER: 35633,
  FLOAT: 5126,
};

function fakeContext() {
  const calls = [];
  const attached = [];
  const shader = { __shader: 1 };
  const program = { __program: 1 };
  const context = {
    calls,
    ...CONST,
    createShader: () => ({ ...shader }),
    shaderSource: (s, src) => calls.push(['shaderSource', src]),
    compileShader: () => calls.push(['compileShader']),
    getShaderParameter: (_s, pname) => (pname === CONST.COMPILE_STATUS ? true
      : pname === CONST.SHADER_TYPE ? CONST.FRAGMENT_SHADER : false),
    getShaderInfoLog: () => '',
    deleteShader: () => {},
    createProgram: () => program,
    attachShader: (p, s) => { attached.push(s); calls.push(['attachShader', s]); },
    detachShader: () => calls.push(['detachShader']),
    linkProgram: () => calls.push(['linkProgram']),
    getProgramParameter: (p, pname) => {
      if (pname === CONST.LINK_STATUS) return true;
      if (pname === CONST.VALIDATE_STATUS) return true;
      if (pname === CONST.ACTIVE_UNIFORMS) return 2;
      if (pname === CONST.ACTIVE_ATTRIBUTES) return 1;
      if (pname === CONST.MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS) return 4;
      return 0;
    },
    getProgramInfoLog: () => '',
    useProgram: () => {},
    validateProgram: () => {},
    deleteProgram: () => {},
    bindAttribLocation: (p, index, name) => calls.push(['bindAttribLocation', index, name]),
    getAttachedShaders: () => attached.slice(),
    getActiveUniform: (_p, i) => (i === 0 ? { name: 'color', size: 1, type: CONST.FLOAT }
      : { name: 'longestUniformName', size: 4, type: CONST.FLOAT }),
    getActiveAttrib: (_p, i) => (i === 0 ? { name: 'position', size: 2, type: CONST.FLOAT } : null),
  };
  context.__attached = attached;
  return context;
}

function bridge(context) {
  const canvas = { width: 8, height: 8, addEventListener: () => {}, getContext: () => context };
  return new GLBridge(canvas);
}
const frag = '#version 120\nvoid main(){gl_FragColor=vec4(1.0);}';
const src = '#version 120\nattribute vec4 position;\nvoid main(){gl_Position=position;}';

test('desktop shader/program parameter tokens answer from bridge state, not missing enums', () => {
  const gl = bridge(fakeContext());
  const shader = gl.createShader(CONST.FRAGMENT_SHADER);
  gl.shaderSourceJoined(shader, [new TextEncoder().encode(frag).length], new TextEncoder().encode(frag));
  gl.compileShader(shader);
  assert.deepEqual(gl.shaderParameter(shader, 0x8b81), [1]);   // COMPILE_STATUS
  assert.deepEqual(gl.shaderParameter(shader, 0x8b4f), [CONST.FRAGMENT_SHADER]); // SHADER_TYPE (recorded)
  assert.deepEqual(gl.shaderParameter(shader, 0x8b84), [0]);   // INFO_LOG_LENGTH: 0 when the log is empty
  assert.ok(gl.shaderParameter(shader, 0x8b88)[0] > 0);        // SHADER_SOURCE_LENGTH includes the NUL
  assert.deepEqual(gl.shaderParameter(shader, 0x8b80), [1]);   // DELETE_STATUS while the handle is live
  assert.throws(() => gl.shaderParameter(shader, 0x8b8d), /Unsupported WebGL2 shader parameter: 0x8b8d/);

  const program = gl.createProgram();
  gl.attachShader(program, shader);
  gl.linkProgram(program);
  assert.deepEqual(gl.programParameter(program, 0x8b82), [1]); // LINK_STATUS
  assert.deepEqual(gl.programParameter(program, 0x8b83), [1]); // VALIDATE_STATUS
  assert.deepEqual(gl.programParameter(program, 0x8b84), [0]); // INFO_LOG_LENGTH
  assert.deepEqual(gl.programParameter(program, 0x8b85), [1]); // ATTACHED_SHADERS from bookkeeping
  assert.deepEqual(gl.programParameter(program, 0x8b86), [2]); // ACTIVE_UNIFORMS from WebGL2
  assert.deepEqual(gl.programParameter(program, 0x8b89), [1]); // ACTIVE_ATTRIBUTES
  assert.deepEqual(gl.programParameter(program, 0x8b87), ['longestUniformName'.length + 1]);
  assert.deepEqual(gl.programParameter(program, 0x8b8a), ['position'.length + 1]);
  assert.throws(() => gl.programParameter(program, 0x8b8c), /Unsupported WebGL2 program parameter: 0x8b8c/);
});

test('bindAttribLocation, attached shaders, active info and shader source reach WebGL2', () => {
  const context = fakeContext();
  const gl = bridge(context);
  const program = gl.createProgram();
  const shader = gl.createShader(CONST.VERTEX_SHADER);
  gl.shaderSourceJoined(shader, [new TextEncoder().encode(src).length], new TextEncoder().encode(src));
  gl.attachShader(program, shader);

  gl.bindAttribLocation(program, 3, 'position');
  assert.deepEqual(context.calls.find(([n]) => n === 'bindAttribLocation'), ['bindAttribLocation', 3, 'position']);

  const attached = gl.attachedShaders(program, 16);
  assert.deepEqual(attached, [shader]);
  assert.deepEqual(gl.attachedShaders(program, 0), []);

  assert.equal(gl.activeName(program, 0, 1), 'color');
  assert.deepEqual(gl.activeSizeType(program, 1, 1), [4, CONST.FLOAT]);
  assert.equal(gl.activeName(program, 0, 0), 'position');
  assert.throws(() => gl.activeName(program, 1, 0), /No active attribute 1/);

  assert.equal(gl.shaderSourceText(shader), gl.shaders.get(shader).source);
  assert.ok(gl.shaderSourceText(shader).includes('in vec4')); // translated to WebGL2 by glsl.mjs
});

/**
 * zombie.iso.PuddlesShader (and every Shader subclass) stores glGetUniformLocation results and
 * guards each setter with `!= -1`. Desktop returns -1 for a missing uniform; WebGL2 returns null.
 * The bridge must hand back -1 (not 0, which the game treats as valid) so the guard works, and it
 * must keep raising for handles it never handed out.
 */
test('uniformLocation answers -1 for missing uniforms like desktop glGetUniformLocation', () => {
  const context = fakeContext();
  context.getUniformLocation = (program, name) => (name === 'WaterGroundTex' ? { __loc: name } : null);
  context.uniform1i = (loc, v) => context.calls.push(['uniform1i', loc && loc.__loc, v]);
  const gl = bridge(context);
  const program = gl.createProgram();

  assert.ok(gl.uniformLocation(program, 'WaterGroundTex') > 0); // handles start at 1, 0 stays "none"
  assert.equal(gl.uniformLocation(program, 'NoSuchUniform'), -1); // the game's `!= -1` guard
  assert.equal(gl.uniformLocation(program, 'AlsoMissing'), -1);

  // A real handle reaches WebGL2 through port.shim.GL20's dispatcher (gl.call + locationOf);
  // -1 is a silent no-op and an unknown handle records GL_INVALID_OPERATION (desktop semantics);
  // neither reaches location 0.
  gl.call('uniform1i', gl.locationOf(gl.uniformLocation(program, 'WaterGroundTex'), 'uniform1i'), 0);
  assert.deepEqual(context.calls.find(([n]) => n === 'uniform1i'), ['uniform1i', 'WaterGroundTex', 0]);
  assert.equal(gl.locationOf(-1, 'uniform1i'), null);
  assert.equal(gl.locationOf(999, 'uniform1i'), null);
  assert.equal(gl.getError(), 0x0502);
  assert.equal(gl.getError(), context.NO_ERROR); // only the 999 call queued an error
});

/**
 * Round 81: glsl.mjs renames desktop identifiers that GLSL ES 3.00 reserves (a `uniform sampler2D
 * texture;` collides with the texture() lookup) to pz_id_*. The game keeps using the desktop name,
 * so the bridge maps it on the way in (locations, bindAttribLocation) and back (active names).
 */
test('uniform/attribute names renamed by the translator keep their desktop names', () => {
  const context = fakeContext();
  context.getUniformLocation = (program, name) => (name === 'pz_id_texture' ? { __loc: name } : null);
  context.getAttribLocation = (program, name) => (name === 'pz_id_input' ? 2 : -1);
  context.getActiveUniform = (_p, i) => (i === 0 ? { name: 'pz_id_texture', size: 1, type: CONST.FLOAT }
    : { name: 'DIFFUSE', size: 1, type: CONST.FLOAT });
  const gl = bridge(context);
  const program = gl.createProgram();
  assert.ok(gl.uniformLocation(program, 'texture') > 0);
  assert.equal(gl.attribLocation(program, 'input'), 2);
  gl.bindAttribLocation(program, 4, 'input');
  assert.deepEqual(context.calls.find(([n]) => n === 'bindAttribLocation'), ['bindAttribLocation', 4, 'pz_id_input']);
  assert.equal(gl.activeName(program, 0, 1), 'texture');
  assert.equal(gl.activeName(program, 1, 1), 'DIFFUSE');
  assert.deepEqual(gl.programParameter(program, 0x8b87), ['DIFFUSE'.length + 1]);
});
