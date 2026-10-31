import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

/**
 * Round 80 (front F): fixed-function matrix stacks, immediate mode, instancing, glIsEnabled,
 * framebuffer readback and the queries the game reads back in game. Synthetic WebGL2 context, own
 * fixture, no game bytes; every constant is the number WebGL2 (and `javap -constants` on the
 * pinned LWJGL) reports.
 */
function fakeContext() {
  const calls = [];
  const params = new Map([[34930, 2], [0x0d05, 4], [0x0d02, 0], [0x0d03, 0], [0x0d04, 0], [0x8b9b, 0x1908], [0x8b9a, 0x1401],
    [0x0cf5, 4], [0x80c9, 770], [0x0b92, 519]]);
  const enabled = new Set();
  let id = 0;
  const context = {
    calls, params,
    NO_ERROR: 0, ARRAY_BUFFER: 34962, ELEMENT_ARRAY_BUFFER: 34963, STREAM_DRAW: 35040, FLOAT: 5126,
    TEXTURE_2D: 3553, TEXTURE_WRAP_R: 32882, CLAMP_TO_EDGE: 33071, REPEAT: 10497, LINK_STATUS: 35714,
    SCISSOR_TEST: 3089, BLEND: 3042, DEPTH_TEST: 2929, CULL_FACE: 2884, STENCIL_TEST: 2960,
    UNPACK_ALIGNMENT: 3317, PACK_ALIGNMENT: 3333, BLEND_SRC_RGB: 32969, STENCIL_FUNC: 2962,
    VERTEX_SHADER: 35632, FRAGMENT_SHADER: 35633, COMPILE_STATUS: 35721,
    TEXTURE0: 33984, TEXTURE1: 33985, MAX_COMBINED_TEXTURE_IMAGE_UNITS: 34930,
    FRAMEBUFFER: 36160, READ_FRAMEBUFFER: 36008, DRAW_FRAMEBUFFER: 36009,
    getParameter: (p) => (p === 0x8895 ? context.boundElements ?? null : params.get(p) ?? 0),
    getError: () => 0, getSupportedExtensions: () => [],
    createShader: (type) => ({ __shader: ++id, type }), shaderSource: () => {}, compileShader: () => {},
    getShaderParameter: () => true, getShaderInfoLog: () => '', deleteShader: () => {},
    createProgram: () => ({ __program: ++id }), attachShader: () => {}, linkProgram: () => {},
    bindAttribLocation: (...a) => calls.push(['bindAttribLocation', ...a.slice(1)]),
    getProgramParameter: () => true, getProgramInfoLog: () => '', deleteProgram: () => {},
    useProgram: (p) => calls.push(['useProgram', p]),
    // The fake linker honours bindAttribLocation like WebGL2: pzVertex 15, pzColor 14.
    getAttribLocation: (_p, name) => ({ pzVertex: 15, pzColor: 14, pzMultiTexCoord0: 2 })[name] ?? -1,
    getUniformLocation: (_p, name) => (context.uniformNames.has(name) ? { __loc: name } : null),
    uniformNames: new Set(),
    uniform1i: () => {}, uniform1f: () => {},
    uniformMatrix4fv: (loc, t, v) => calls.push(['uniformMatrix4fv', loc.__loc, t, Array.from(v)]),
    uniformMatrix3fv: (loc, t, v) => calls.push(['uniformMatrix3fv', loc.__loc, t, Array.from(v)]),
    vertexAttrib4f: (...a) => calls.push(['vertexAttrib4f', ...a]),
    createVertexArray: () => ({ __vao: ++id }), deleteVertexArray: () => {},
    bindVertexArray: (v) => calls.push(['bindVertexArray', v]),
    createBuffer: () => ({ __buffer: ++id }), deleteBuffer: () => {},
    bindBuffer: (t, b) => { calls.push(['bindBuffer', t, b]); if (t === 34963) context.boundElements = b; },
    bufferData: (t, d, u) => calls.push(['bufferData', t, Array.from(d), u]),
    enableVertexAttribArray: (i) => calls.push(['enableVertexAttribArray', i]),
    disableVertexAttribArray: (i) => calls.push(['disableVertexAttribArray', i]),
    vertexAttribPointer: (...a) => calls.push(['vertexAttribPointer', ...a]),
    vertexAttribDivisor: (...a) => calls.push(['vertexAttribDivisor', ...a]),
    drawArrays: (...a) => calls.push(['drawArrays', ...a]),
    drawArraysInstanced: (...a) => calls.push(['drawArraysInstanced', ...a]),
    drawElementsInstanced: (...a) => calls.push(['drawElementsInstanced', ...a]),
    enable: (c) => enabled.add(c), disable: (c) => enabled.delete(c), isEnabled: (c) => enabled.has(c),
    readBuffer: (m) => calls.push(['readBuffer', m]),
    readPixels: (...a) => { calls.push(['readPixels', ...a.slice(0, 6)]); a[6].fill(7); },
    createTexture: () => ({ __tex: ++id }), deleteTexture: () => {}, bindTexture: () => {},
    texParameteri: (...a) => calls.push(['texParameteri', ...a]),
    createFramebuffer: () => ({ __fbo: ++id }), deleteFramebuffer: () => {}, bindFramebuffer: () => {},
    activeTexture: () => {},
  };
  return context;
}
function bridge(context = fakeContext()) {
  const canvas = { width: 8, height: 8, addEventListener: () => {}, getContext: () => context };
  return new GLBridge(canvas);
}
function program(gl, source) {
  const src = new TextEncoder().encode(source);
  const vertex = gl.createShader(35632);
  gl.shaderSourceJoined(vertex, [src.length], src);
  const handle = gl.createProgram();
  gl.attachShader(handle, vertex);
  gl.linkProgram(handle);
  return handle;
}
const close = (actual, expected) => {
  assert.equal(actual.length, expected.length);
  for (let i = 0; i < expected.length; i++) assert.ok(Math.abs(actual[i] - expected[i]) < 1e-6, `[${i}] ${actual[i]} != ${expected[i]}`);
};

test('matrix stacks: mode, push/pop with desktop stack errors, and the depth/matrix queries', () => {
  const gl = bridge();
  assert.equal(gl.intParameter(0x0ba0, 0), 0x1700); // GL_MATRIX_MODE starts at GL_MODELVIEW
  gl.matrixMode(0x1701);                             // GL_PROJECTION, as ModelManager.RenderParticles
  gl.pushMatrix(); gl.loadIdentity();
  assert.equal(gl.intParameter(0x0ba4, 0), 2);        // GL_PROJECTION_STACK_DEPTH
  assert.equal(gl.intParameter(0x0ba3, 0), 1);        // GL_MODELVIEW_STACK_DEPTH
  gl.ortho(0, 4, 0, 2, -1, 1);
  close(gl.floatParameters(0x0ba7), [0.5, 0, 0, 0, 0, 1, 0, 0, 0, 0, -1, 0, -1, -1, 0, 1]);
  gl.popMatrix();
  close(gl.floatParameters(0x0ba7), [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]);
  gl.popMatrix();                                     // underflow: ignored, GL_STACK_UNDERFLOW queued
  assert.equal(gl.getError(), 0x0504);
  for (let i = 1; i < GLBridge.MATRIX_LIMITS.PROJECTION; i++) gl.pushMatrix();
  gl.pushMatrix();                                    // overflow: ignored, GL_STACK_OVERFLOW queued
  assert.equal(gl.getError(), 0x0503);
  assert.equal(gl.intParameter(0x0ba4, 0), GLBridge.MATRIX_LIMITS.PROJECTION);
  assert.equal(gl.intParameter(0x0d38, 0), 32);       // GL_MAX_PROJECTION_STACK_DEPTH
  gl.matrixMode(0x1800);                              // GL_COLOR without ARB_imaging: GL_INVALID_ENUM
  assert.equal(gl.getError(), 0x0500);
  assert.equal(gl.intParameter(0x0ba0, 0), 0x1701);
  gl.ortho(1, 1, 0, 1, 0, 1);                         // degenerate box: GL_INVALID_VALUE
  assert.equal(gl.getError(), 0x0501);
});

test('glTranslate/glRotate/glScale/glMultMatrixf post-multiply the current matrix like desktop GL', () => {
  const gl = bridge();
  gl.translate(1, 2, 3);
  gl.rotate(90, 0, 0, 1);
  gl.scale(2, 2, 2);
  // T * Rz(90) * S, column-major
  close(gl.floatParameters(0x0ba6), [0, 2, 0, 0, -2, 0, 0, 0, 0, 0, 2, 0, 1, 2, 3, 1]);
  gl.rotate(45, 0, 0, 0);                             // zero axis: left unchanged (Mesa)
  close(gl.floatParameters(0x0ba6), [0, 2, 0, 0, -2, 0, 0, 0, 0, 0, 2, 0, 1, 2, 3, 1]);
  gl.loadIdentity();
  const bytes = new Uint8Array(64), view = new DataView(bytes.buffer);
  for (let k = 0; k < 16; k++) view.setFloat32(k * 4, k === 12 ? 5 : (k % 5 === 0 ? 1 : 0), true); // translate x=5
  gl.loadMatrixBytes(bytes, false);
  gl.loadMatrixBytes(bytes, true);                    // glMultMatrixf: translate twice
  assert.equal(gl.floatParameters(0x0ba6)[12], 10);
});

test('the top matrices feed gl_ModelViewMatrix & co. of translated programs once per change', () => {
  const context = fakeContext();
  context.uniformNames = new Set(['pzModelViewMatrix', 'pzProjectionMatrix', 'pzModelViewProjectionMatrix', 'pzNormalMatrix']);
  const gl = bridge(context);
  const handle = program(gl, '#version 120\nvoid main(){gl_Position=gl_ProjectionMatrix*gl_ModelViewMatrix*gl_Vertex+gl_ModelViewProjectionMatrix*gl_Vertex+vec4(gl_NormalMatrix*gl_Normal,0.0);}');
  gl.useProgram(handle);
  gl.translate(1, 0, 0);
  gl.matrixMode(0x1701); gl.scale(2, 2, 2);
  gl.drawArrays(4, 0, 3);
  const uploads = () => context.calls.filter((c) => c[0].startsWith('uniformMatrix'));
  const byName = Object.fromEntries(uploads().map((c) => [c[1], c[3]]));
  close(byName.pzModelViewMatrix, [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1]);
  close(byName.pzProjectionMatrix, [2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 1]);
  close(byName.pzModelViewProjectionMatrix, [2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 2, 0, 2, 0, 0, 1]);
  close(byName.pzNormalMatrix, [1, 0, 0, 0, 1, 0, 0, 0, 1]);
  assert.equal(uploads().length, 4);
  gl.drawArrays(4, 0, 3);                             // unchanged: no redundant uploads
  assert.equal(uploads().length, 4);
  gl.pushMatrix(); gl.popMatrix();                    // the pop counts as a change (desktop restores the copy)
  gl.drawArrays(4, 0, 3);
  assert.equal(uploads().length, 8);
  assert.equal(gl.matrixState().uploads, 2);
});

test('glPushAttrib(GL_TRANSFORM_BIT) restores the matrix mode; GL_CURRENT_BIT the texcoord and normal', () => {
  const gl = bridge();
  gl.matrixMode(0x1701);
  gl.texCoord(0.25, 0.5); gl.normal(0, 1, 0);
  gl.pushAttrib(4096 | 1);                            // GL_TRANSFORM_BIT | GL_CURRENT_BIT
  assert.deepEqual(gl.attribState().gaps, {});
  gl.matrixMode(0x1700); gl.texCoord(1, 1); gl.normal(1, 0, 0);
  gl.popAttrib();
  assert.equal(gl.intParameter(0x0ba0, 0), 0x1701);
  assert.deepEqual(gl.currentTexCoord, [0.25, 0.5, 0, 1]);
  assert.deepEqual(gl.currentNormal, [0, 1, 0]);
});

test('glBegin(GL_QUADS)/glEnd streams per-vertex colour and texcoord into a scratch VAO and restores the bindings', () => {
  const context = fakeContext();
  const gl = bridge(context);
  const handle = program(gl, '#version 120\nvoid main(){gl_Position=gl_Vertex;gl_FrontColor=gl_Color;gl_TexCoord[0]=gl_MultiTexCoord0;}');
  assert.ok(context.calls.some((c) => c[0] === 'bindAttribLocation' && c[1] === 14 && c[2] === 'pzColor'));
  gl.useProgram(handle);
  const vao = gl.genVertexArrays(1)[0];
  gl.bindVertexArray(vao);
  const buffer = gl.genBuffers(1)[0];
  gl.bindBuffer(34962, buffer);
  context.calls.length = 0;
  // DeadBodyAtlas.toBodyAtlas fallback pattern: glBegin(GL_QUADS), glTexCoord2f + glVertex2i x4.
  gl.begin(7);
  gl.color(1, 0, 0, 1);
  gl.texCoord(0, 0); gl.vertex(0, 0);
  gl.texCoord(1, 0); gl.vertex(8, 0);
  gl.matrixMode(0x1701);                              // not allowed inside glBegin: GL_INVALID_OPERATION
  gl.texCoord(1, 1); gl.vertex(8, 8);
  gl.texCoord(0, 1); gl.vertex(0, 8);
  gl.end();
  assert.equal(gl.getError(), 0x0502);
  assert.equal(gl.intParameter(0x0ba0, 0), 0x1700);
  assert.deepEqual(context.calls.filter((c) => c[0] === 'vertexAttrib4f'), [], 'no constant colour inside glBegin');
  const data = context.calls.find((c) => c[0] === 'bufferData')[2];
  assert.equal(data.length, 6 * 15);                  // two triangles (a,b,c)(a,c,d)
  const vertex = (i) => data.slice(i * 15, i * 15 + 15);
  assert.deepEqual(vertex(0), [0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1]);
  assert.deepEqual(vertex(4).slice(0, 2), [8, 8]);
  assert.deepEqual(vertex(5).slice(0, 2), [0, 8]);
  assert.deepEqual(vertex(5).slice(8, 10), [0, 1]);
  const pointers = context.calls.filter((c) => c[0] === 'vertexAttribPointer').map((c) => [c[1], c[2], c[5], c[6]]);
  assert.deepEqual(pointers, [[15, 4, 60, 0], [14, 4, 60, 16], [2, 4, 60, 32]]);
  assert.deepEqual(context.calls.find((c) => c[0] === 'drawArrays').slice(1), [4, 0, 6]);
  const vaos = context.calls.filter((c) => c[0] === 'bindVertexArray').map((c) => c[1]);
  assert.equal(vaos.at(-1), gl.vertexArrays.get(vao), 'the game VAO is bound again');
  const arrays = context.calls.filter((c) => c[0] === 'bindBuffer' && c[1] === 34962).map((c) => c[2]);
  assert.equal(arrays.at(-1), gl.buffers.get(buffer).buffer, 'the game ARRAY_BUFFER is bound again');
  assert.equal(gl.matrixState().immediateDraws, 1);
  // GL_POLYGON / GL_QUAD_STRIP become the equivalent WebGL2 modes.
  gl.begin(9); for (let i = 0; i < 5; i++) gl.vertex(i, i); gl.end();
  assert.deepEqual(context.calls.filter((c) => c[0] === 'drawArrays').at(-1).slice(1), [6, 0, 5]);
  gl.begin(8); for (let i = 0; i < 5; i++) gl.vertex(i, i); gl.end();
  assert.deepEqual(context.calls.filter((c) => c[0] === 'drawArrays').at(-1).slice(1), [5, 0, 4]);
});

test('immediate mode refuses what desktop GL would not do and raises without a program', () => {
  const gl = bridge();
  gl.end();                                           // glEnd without glBegin: GL_INVALID_OPERATION
  assert.equal(gl.getError(), 0x0502);
  gl.begin(12);                                       // not a primitive: GL_INVALID_ENUM
  assert.equal(gl.getError(), 0x0500);
  assert.throws(() => gl.vertex(0, 0), /outside glBegin/);
  gl.begin(4); gl.vertex(0, 0); gl.vertex(1, 0); gl.vertex(0, 1);
  assert.throws(() => gl.end(), /no fixed-function pipeline/);
  assert.equal(gl.immediate, null);
  assert.ok(gl.hasEntrypoint('glBegin') && gl.hasEntrypoint('glMatrixMode') && gl.hasEntrypoint('glReadPixels'));
});

test('enabled arrays follow the bound VAO, and GL_COLOR_ARRAY drives pzColor', () => {
  const context = fakeContext();
  const gl = bridge(context);
  gl.useProgram(program(gl, '#version 120\nvoid main(){gl_Position=gl_Vertex*gl_Color.a;}'));
  const [a, b] = gl.genVertexArrays(2);
  gl.bindVertexArray(a);
  gl.clientState(0x8076, true);                       // GL_COLOR_ARRAY on in VAO a
  assert.ok(context.calls.some((c) => c[0] === 'enableVertexAttribArray' && c[1] === 14));
  gl.color(0, 1, 0, 1);
  const constants = () => context.calls.filter((c) => c[0] === 'vertexAttrib4f');
  const before = constants().length;
  gl.drawArrays(4, 0, 3);
  assert.equal(constants().length, before, 'the colour array wins in VAO a');
  gl.bindVertexArray(b);                              // VAO b never enabled it: gl_Color is the current colour
  gl.drawArrays(4, 0, 3);
  assert.deepEqual(constants().at(-1), ['vertexAttrib4f', 14, 0, 1, 0, 1]);
  gl.bindVertexArray(a);
  assert.equal(gl.attribs.get(14)?.enabled, true);
  gl.clientState(0x8076, false);
  assert.equal(gl.isEnabled(0x8076), false);
  gl.bindBuffer(34962, gl.genBuffers(1)[0]);
  gl.vertexAttribPointer(5, 4, 0x1406, false, 16, 0); // a pointer alone does not enable the array
  assert.equal(gl.attribs.get(5).enabled, false);
});

test('instancing goes to WebGL2 core and the divisor is VAO state', () => {
  const context = fakeContext();
  const gl = bridge(context);
  gl.vertexAttribDivisor(1, 1);                       // ParticlesFire.updateParticleParams
  gl.drawArraysInstanced(5, 0, 4, 100);               // Particles.getGeometryFire: TRIANGLE_STRIP, 4 vertices
  assert.deepEqual(context.calls.filter((c) => c[0].startsWith('vertexAttribDivisor') || c[0] === 'drawArraysInstanced'),
    [['vertexAttribDivisor', 1, 1], ['drawArraysInstanced', 5, 0, 4, 100]]);
  assert.equal(gl.attribs.get(1).divisor, 1);
  assert.throws(() => gl.drawArraysInstanced(7, 0, 4, 2), /no instanced primitive mode 0x7/);
  assert.throws(() => gl.drawElementsInstanced(4, 6, 0x1405, 0, 2), /needs a bound ELEMENT_ARRAY_BUFFER/);
  gl.bindBuffer(34963, gl.genBuffers(1)[0]);
  gl.drawElementsInstanced(4, 6, 0x1405, 8, 3);
  assert.deepEqual(context.calls.at(-1), ['drawElementsInstanced', 4, 6, 0x1405, 8, 3]);
});

test('glIsEnabled answers WebGL2 caps from the context and desktop-only caps from the recorded state', () => {
  const gl = bridge();
  gl.capability(0x0c11, true);                        // GL_SCISSOR_TEST
  assert.equal(gl.isEnabled(0x0c11), true);
  assert.equal(gl.isEnabled(0x0be2), false);          // GL_BLEND
  assert.equal(gl.isEnabled(0x0de1), false);          // GL_TEXTURE_2D, never enabled yet
  gl.capability(0x0de1, true);
  assert.equal(gl.isEnabled(0x0de1), true);
  assert.equal(gl.isEnabled(0x0bd0), true);           // GL_DITHER starts enabled on desktop
  assert.throws(() => gl.isEnabled(0x4000), /no capability for GL token 0x4000/); // GL_LIGHT0
});

test('savefile thumbnail readback: glReadBuffer(GL_BACK) and glReadPixels sized from PACK_* state', () => {
  const context = fakeContext();
  const gl = bridge(context);
  gl.readBuffer(1029);                                // Core.TakeScreenshot via SavefileThumbnail
  assert.deepEqual(context.calls.at(-1), ['readBuffer', 1029]);
  assert.throws(() => gl.readBuffer(1028), /no read buffer for GL token 0x404/); // GL_FRONT
  assert.equal(gl.readPixelsBytes(256, 256, 6408, 5121), 256 * 256 * 4);
  context.params.set(0x0d05, 8);                      // PACK_ALIGNMENT 8 pads each RGB row
  assert.throws(() => gl.readPixelsBytes(3, 2, 6407, 5121), /cannot read pixels as format 0x1907/);
  context.params.set(0x8b9b, 6407);                  // ... unless it is the implementation read format
  assert.equal(gl.readPixelsBytes(3, 2, 6407, 5121), 16 + 9);
  context.params.set(0x0d05, 1);
  const out = new Uint8Array(16);
  gl.readPixels(0, 0, 2, 2, 6408, 5121, out);
  assert.deepEqual(context.calls.at(-1), ['readPixels', 0, 0, 2, 2, 6408, 5121]);
  assert.ok(out.every((v) => v === 7));
});

test('in-game read-backs: active texture, framebuffer and VAO bindings, version, scissor box, pack state', () => {
  const context = fakeContext();
  const gl = bridge(context);
  assert.equal(gl.intParameter(0x84e0, 0), 0x84c0);   // GL_ACTIVE_TEXTURE (SpriteRenderer debugBoundTexture)
  gl.activeTexture(0x84c1);
  assert.equal(gl.intParameter(0x84e0, 0), 0x84c1);
  const [fbo] = gl.genFramebuffers(1);
  gl.bindFramebuffer(36160, fbo);
  assert.equal(gl.intParameter(0x8ca6, 0), fbo);      // GL_FRAMEBUFFER_BINDING (renderToImposterCard)
  assert.equal(gl.intParameter(0x8caa, 0), fbo);      // GL_READ_FRAMEBUFFER_BINDING (renderCard)
  gl.bindFramebuffer(36009, 0);                       // DRAW only
  assert.equal(gl.intParameter(0x8ca6, 0), 0);
  assert.equal(gl.intParameter(0x8caa, 0), fbo);
  gl.deleteFramebuffers([fbo]);
  assert.equal(gl.intParameter(0x8caa, 0), 0);
  const [vao] = gl.genVertexArrays(1);
  gl.bindVertexArray(vao);
  assert.equal(gl.intParameter(0x85b5, 0), vao);      // GL_VERTEX_ARRAY_BINDING
  assert.equal(gl.intParameter(0x821b, 0), 3);        // GL_MAJOR_VERSION
  assert.equal(gl.intParameter(0x821c, 0), 3);        // GL_MINOR_VERSION
  assert.deepEqual(gl.intParameters(0x0c10), [0, 0, 8, 8]); // GL_SCISSOR_BOX (3088)
  assert.equal(gl.intParameter(0x0d05, 0), 4);        // GL_PACK_ALIGNMENT (lwjglx PixelStoreState)
  assert.equal(gl.intParameter(0x80c9, 0), 770);      // GL_BLEND_SRC_RGB (ImGuiImplGl3)
  assert.equal(gl.intParameter(0x0b92, 0), 519);      // GL_STENCIL_FUNC (renderToImposterCard)
});

test('GL_TEXTURE_WRAP_R is a WebGL2 texture parameter (RenderList$ArrayTexture.Init)', () => {
  const context = fakeContext();
  const gl = bridge(context);
  gl.texParameteri(0x0de1, 0x8072, 33071);
  assert.deepEqual(context.calls.at(-1), ['texParameteri', 3553, 32882, 33071]);
  assert.throws(() => gl.texParameteri(0x0de1, 0x8072, 10496), /no texture parameter value for GL token 0x2900/); // GL_CLAMP
});
