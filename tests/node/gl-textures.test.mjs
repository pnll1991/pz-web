import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

/**
 * Synthetic WebGL2 context (own fixture, no game bytes): the constants WebGL2 reports in Chrome and
 * the methods GLBridge's texture path calls. Uploads record what they received so the test can check
 * the bytes actually reach WebGL2.
 */
function fakeContext() {
  const calls = [];
  const values = {
    TEXTURE_2D: 3553, TEXTURE_CUBE_MAP: 34067, TEXTURE_2D_ARRAY: 35866,
    TEXTURE_MAG_FILTER: 10240, TEXTURE_MIN_FILTER: 10241, TEXTURE_WRAP_S: 10242, TEXTURE_WRAP_T: 10243,
    TEXTURE_MIN_LOD: 33082, TEXTURE_MAX_LOD: 33083, TEXTURE_BASE_LEVEL: 33084, TEXTURE_MAX_LEVEL: 33085,
    TEXTURE_COMPARE_MODE: 34892, TEXTURE_COMPARE_FUNC: 34893, TEXTURE_BORDER_COLOR: 4100,
    TEXTURE_BINDING_2D: 32873, READ_FRAMEBUFFER_BINDING: 36010, DRAW_FRAMEBUFFER_BINDING: 36006,
    NEAREST: 9728, LINEAR: 9729, NEAREST_MIPMAP_NEAREST: 9984, LINEAR_MIPMAP_NEAREST: 9985,
    NEAREST_MIPMAP_LINEAR: 9986, LINEAR_MIPMAP_LINEAR: 9987, REPEAT: 10497, CLAMP_TO_EDGE: 33071,
    MIRRORED_REPEAT: 33648, NONE: 0, COMPARE_REF_TO_TEXTURE: 34894, NEVER: 512, LESS: 513, EQUAL: 514,
    LEQUAL: 515, GREATER: 516, NOTEQUAL: 517, GEQUAL: 518, ALWAYS: 519,
    RGBA: 6408, RGB: 6407, RED: 6403, RG: 33319, DEPTH_COMPONENT: 6402,
    BYTE: 5120, UNSIGNED_BYTE: 5121, SHORT: 5122, UNSIGNED_SHORT: 5123, INT: 5124, UNSIGNED_INT: 5125,
    FLOAT: 5126, HALF_FLOAT: 5131,
    RGBA8: 32856, RGB8: 32849, R8: 33321, RG8: 33323, RGBA32F: 34836, RGB32F: 34837, R16F: 33325,
    R32F: 33326, RG32F: 33328, DEPTH_COMPONENT16: 33189, DEPTH_COMPONENT24: 33190,
    DEPTH_COMPONENT32F: 36012, FRAMEBUFFER: 36160, COLOR_ATTACHMENT0: 36064, NO_ERROR: 0,
    READ_FRAMEBUFFER: 36008, MAX_TEXTURE_SIZE: 3379,
  };
  let textureId = 0;
  const context = {
    calls, values, uploads: [],
    createTexture: () => { const t = { __texture: ++textureId }; calls.push(['createTexture', t]); return t; },
    deleteTexture: (t) => calls.push(['deleteTexture', t]),
    bindTexture: (target, t) => calls.push(['bindTexture', target, t]),
    texParameteri: (target, pname, value) => calls.push(['texParameteri', target, pname, value]),
    texImage2D: (target, level, sized, w, h, border, format, type, data) =>
      context.uploads.push({ level, sized, width: w, height: h, format, type, data }),
    texSubImage2D: (target, level, x, y, w, h, format, type, data) =>
      context.uploads.push({ sub: true, level, x, y, width: w, height: h, format, type, data }),
    createFramebuffer: () => ({ __fbo: 1 }),
    bindFramebuffer: () => {}, framebufferTexture2D: (...a) => calls.push(['framebufferTexture2D', ...a]),
    checkFramebufferStatus: () => 0,
    readPixels: (x, y, w, h, format, type, out) => { for (let i = 0; i < out.length; i++) out[i] = i & 0xff; },
    deleteFramebuffer: () => {}, getParameter: () => 0, getError: () => 0,
    getSupportedExtensions: () => [],
  };
  values.FRAMEBUFFER_COMPLETE = 0;
  Object.assign(context, values); // WebGL2 exposes its constants as properties of the context
  return context;
}
function bridge(context) {
  // The synthetic context is what the canvas hands out, so the bridge is built exactly as in the
  // page and its own state (handles, bound texture) is the real code under test.
  const canvas = { width: 8, height: 8, addEventListener: () => {}, getContext: () => context };
  return new GLBridge(canvas);
}

test('texture handles are unique, nonzero and create real WebGL2 objects', () => {
  const gl = bridge(fakeContext());
  const handles = gl.genTextures(3);
  assert.deepEqual([...handles], [1, 2, 3]);
  assert.equal(handles.some((h) => h === 0), false);
  assert.equal(gl.isTexture(1), true);
  assert.equal(gl.isTexture(99), false);
  assert.equal(gl.context.calls.filter((c) => c[0] === 'createTexture').length, 3);
});

test('bind, filter and wrap tokens reach WebGL2; unknown tokens raise', () => {
  const gl = bridge(fakeContext());
  const id = gl.genTextures(1)[0];
  gl.bindTexture(3553, id);
  gl.texParameteri(3553, 10241, 9987); // GL_MIN_FILTER = GL_LINEAR_MIPMAP_LINEAR
  gl.texParameteri(3553, 10242, 33071); // GL_WRAP_S = GL_CLAMP_TO_EDGE
  gl.texParameteri(3553, 33085, 4);      // GL_TEXTURE_MAX_LEVEL is a plain integer
  const params = gl.context.calls.filter((c) => c[0] === 'texParameteri');
  assert.deepEqual(params, [['texParameteri', 3553, 10241, 9987], ['texParameteri', 3553, 10242, 33071],
    ['texParameteri', 3553, 33085, 4]]);
  assert.throws(() => gl.texParameteri(3553, 10241, 0x1234), /no texture parameter value/);
  assert.throws(() => gl.texParameteri(3552, 10241, 9728), /no texture target/); // GL_TEXTURE_1D
  assert.throws(() => gl.bindTexture(0, id), /no texture target/);
});

test('level-0 upload forwards the caller bytes and records the level', () => {
  const gl = bridge(fakeContext());
  const id = gl.genTextures(1)[0];
  gl.bindTexture(3553, id);
  const bytes = new Uint8Array(4 * 2 * 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = i + 1;
  gl.texImage2D(3553, 0, 6408, 2, 2, 0, 6408, 5121, bytes);
  const upload = gl.context.uploads[0];
  assert.equal(upload.sized, 32856); // GL_RGBA + GL_UNSIGNED_BYTE -> RGBA8
  assert.equal(upload.width, 2); assert.equal(upload.height, 2);
  assert.deepEqual([...upload.data], [...bytes]);
  assert.equal(gl.texLevelBytes(0), 16);
  gl.texSubImage2D(3553, 0, 0, 0, 2, 2, 6408, 5121, bytes);
  assert.equal(gl.context.uploads[1].sub, true);
  assert.throws(() => gl.texSubImage2D(3553, 3, 0, 0, 1, 1, 6408, 5121, new Uint8Array(4)),
    /has no level 3/);
});

test('unsized desktop formats map to the storage class; unsupported pairs raise', () => {
  const gl = bridge(fakeContext());
  const id = gl.genTextures(1)[0];
  gl.bindTexture(3553, id);
  gl.texImage2D(3553, 0, 6402, 1, 1, 0, 6402, 5123, new Uint8Array(4)); // DEPTH_COMPONENT + USHORT
  assert.equal(gl.context.uploads[0].sized, 33189); // DEPTH_COMPONENT16
  gl.texImage2D(3553, 0, 32856, 1, 1, 0, 6408, 5121, new Uint8Array(4)); // already-sized RGBA8
  assert.equal(gl.context.uploads[1].sized, 32856);
  assert.throws(() => gl.texImage2D(3553, 0, 6409, 1, 1, 0, 6409, 5121, new Uint8Array(1)),
    /no pixel format/); // GL_LUMINANCE has no WebGL2 format
  assert.throws(() => gl.texImage2D(3553, 0, 32828, 1, 1, 0, 6408, 5121, new Uint8Array(4)),
    /no sized internal format/); // ALPHA8 does not exist in WebGL2
  assert.throws(() => gl.texImage2D(3553, 0, 6408, 1, 1, 1, 6408, 5121, new Uint8Array(4)), /border 0/);
});

test('readback fills the caller buffer and rejects other layouts', () => {
  const gl = bridge(fakeContext());
  const id = gl.genTextures(1)[0];
  gl.bindTexture(3553, id);
  gl.texImage2D(3553, 0, 6408, 2, 2, 0, 6408, 5121, new Uint8Array(16));
  const out = new Uint8Array(16);
  gl.getTexImage(3553, 0, 6408, 5121, out);
  assert.deepEqual([...out], [...new Uint8Array(16).map((_, i) => i & 0xff)]);
  assert.throws(() => gl.getTexImage(3553, 0, 6408, 5121, new Uint8Array(8)), /needs 16/);
  assert.throws(() => gl.getTexImage(3553, 0, 6407, 5121, new Uint8Array(16)), /cannot read a texture back/);
});

test('deleting releases the WebGL2 object and forgets the handle', () => {
  const gl = bridge(fakeContext());
  const handles = gl.genTextures(2);
  gl.bindTexture(3553, handles[0]);
  gl.deleteTextures(handles);
  assert.equal(gl.isTexture(handles[0]), false);
  assert.equal(gl.isTexture(handles[1]), false);
  assert.equal(gl.boundTexture, 0);
  assert.equal(gl.context.calls.filter((c) => c[0] === 'deleteTexture').length, 2);
  assert.throws(() => gl.bindTexture(3553, handles[0]), /does not exist/);
});
