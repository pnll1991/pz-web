import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

/**
 * Synthetic WebGL2 context for the state entrypoints the game's GLState wrapper classes and
 * zombie.core.Core drive (alpha test, colour/stencil/depth state, texture units, FBOs).
 * Own fixture, no game bytes; every constant is the number WebGL2 reports in Chrome.
 */
function fakeContext() {
  const calls = [];
  const values = {
    NO_ERROR: 0, FRAMEBUFFER: 36160, READ_FRAMEBUFFER: 36008, DRAW_FRAMEBUFFER: 36009,
    RENDERBUFFER: 36161, COLOR_ATTACHMENT0: 36064, DEPTH_ATTACHMENT: 36096,
    STENCIL_ATTACHMENT: 36128, DEPTH_STENCIL_ATTACHMENT: 33306, FRAMEBUFFER_COMPLETE: 36053,
    RGBA8: 32856, DEPTH_COMPONENT24: 33190, DEPTH24_STENCIL8: 35056,
    NEVER: 512, LESS: 513, EQUAL: 514, LEQUAL: 515, GREATER: 516, NOTEQUAL: 517, GEQUAL: 518, ALWAYS: 519,
    KEEP: 7680, ZERO: 0, REPLACE: 7681, INCR: 7682, DECR: 7683, INVERT: 5386,
    FUNC_ADD: 32774, MIN: 32775, MAX: 32776, FUNC_SUBTRACT: 32778, FUNC_REVERSE_SUBTRACT: 32779,
    TEXTURE0: 33984, TEXTURE1: 33985, TEXTURE_2D: 3553, MAX_COMBINED_TEXTURE_IMAGE_UNITS: 34930,
    VERTEX_SHADER: 35632, FRAGMENT_SHADER: 35633, LINK_STATUS: 35714, COMPILE_STATUS: 35721,
  };
  let id = 0;
  const uniforms = new Map(); // uniform name -> location object
  const context = {
    calls, values, uniforms,
    colorMask: (...a) => calls.push(['colorMask', ...a]),
    stencilFunc: (...a) => calls.push(['stencilFunc', ...a]),
    stencilOp: (...a) => calls.push(['stencilOp', ...a]),
    depthRange: (...a) => calls.push(['depthRange', ...a]),
    clearDepthf: (...a) => calls.push(['clearDepthf', ...a]),
    blendEquation: (...a) => calls.push(['blendEquation', ...a]),
    activeTexture: undefined, // the bridge calls it through call(), so it must exist as a method
    getParameter: (pname) => (pname === 34930 ? 2 : 0),
    createFramebuffer: () => ({ __fbo: ++id }), deleteFramebuffer: (f) => calls.push(['deleteFramebuffer', f]),
    bindFramebuffer: (...a) => calls.push(['bindFramebuffer', ...a]),
    checkFramebufferStatus: () => 36053,
    createRenderbuffer: () => ({ __rbo: ++id }), deleteRenderbuffer: (r) => calls.push(['deleteRenderbuffer', r]),
    bindRenderbuffer: (...a) => calls.push(['bindRenderbuffer', ...a]),
    renderbufferStorage: (...a) => calls.push(['renderbufferStorage', ...a]),
    framebufferTexture2D: (...a) => calls.push(['framebufferTexture2D', ...a]),
    framebufferRenderbuffer: (...a) => calls.push(['framebufferRenderbuffer', ...a]),
    createTexture: () => ({ __tex: ++id }), deleteTexture: () => {}, bindTexture: (...a) => calls.push(['bindTexture', ...a]),
    createShader: (type) => ({ __shader: ++id, type }), shaderSource: () => {}, compileShader: () => {},
    getShaderParameter: () => true, getShaderInfoLog: () => '', deleteShader: () => {},
    createProgram: () => ({ __program: ++id }), attachShader: (...a) => calls.push(['attachShader', ...a]),
    linkProgram: () => {}, bindAttribLocation: (...a) => calls.push(["bindAttribLocation", ...a]), getProgramParameter: () => true, getProgramInfoLog: () => '',
    deleteProgram: () => {}, useProgram: (p) => calls.push(['useProgram', p]),
    getUniformLocation: (program, name) => (name === 'pzAlphaTestFunc' || name === 'pzAlphaTestRef'
      ? { __loc: name } : null),
    uniform1i: (loc, v) => calls.push(['uniform1i', loc && loc.__loc, v]),
    uniform1f: (loc, v) => calls.push(['uniform1f', loc && loc.__loc, v]),
    getAttribLocation: () => -1, // no program reads gl_Color (pzColor) unless a test says so
    getError: () => 0, getSupportedExtensions: () => [],
  };
  context.activeTexture = (unit) => calls.push(['activeTexture', unit]);
  Object.assign(context, values);
  return context;
}
function bridge(context) {
  const canvas = { width: 8, height: 8, addEventListener: () => {}, getContext: () => context };
  return new GLBridge(canvas);
}

test('glAlphaFunc validates the function and drives the program uniforms', () => {
  const gl = bridge(fakeContext());
  gl.alphaTest(516, 0.5); // GL_GREATER, as zombie.core.Core does
  assert.deepEqual({ ...gl.alphaTestState }, { enabled: false, func: 516, ref: 0.5, gaps: 0, uniformGaps: 0 });
  assert.throws(() => gl.alphaTest(9999, 0.5), /unsupported alpha-test function/);
  assert.throws(() => gl.alphaTest(516, Number.NaN), /alpha reference must be finite/);
  gl.alphaTest(519, 2.0); // reference clamps to [0,1] like desktop GL
  assert.equal(gl.alphaTestState.ref, 1);
  const fragment = gl.createShader(35633);
  gl.shaderSourceJoined(fragment, [
    new TextEncoder().encode('#version 120\nvoid main(){gl_FragColor=vec4(1.0,1.0,1.0,1.0);}').length
  ], new TextEncoder().encode('#version 120\nvoid main(){gl_FragColor=vec4(1.0,1.0,1.0,1.0);}'));
  assert.equal(gl.shaders.get(fragment).alphaTest, true);
  const vertex = gl.createShader(35632);
  gl.shaderSourceJoined(vertex, [
    new TextEncoder().encode('#version 120\nvoid main(){gl_Position=vec4(0.0);}').length
  ], new TextEncoder().encode('#version 120\nvoid main(){gl_Position=vec4(0.0);}'));
  const program = gl.createProgram();
  gl.attachShader(program, vertex); gl.attachShader(program, fragment);
  assert.equal(gl.programs.get(program).alphaTest, true);
  gl.linkProgram(program);
  gl.useProgram(program);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'uniform1i'), [['uniform1i', 'pzAlphaTestFunc', -1]]);
  gl.enable(0x0bc0); // GL_ALPHA_TEST -> applies the recorded func to the current program
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'uniform1i').slice(-1),
    [['uniform1i', 'pzAlphaTestFunc', 519]]);
  assert.deepEqual(gl.capabilityState().alphaTest, { enabled: true, func: 519, ref: 1, gaps: 0, uniformGaps: 0 });
  gl.disable(0x0bc0);
  assert.deepEqual(gl.capabilityState().alphaTest, { enabled: false, func: 519, ref: 1, gaps: 0, uniformGaps: 0 });
});

test('a program without the injection is counted as a gap, never silently dropped', () => {
  const gl = bridge(fakeContext());
  gl.alphaTest(515, 0.1);
  gl.enable(0x0bc0);
  const vertex = gl.createShader(35632);
  const vsrc = new TextEncoder().encode('#version 300 es\nvoid main(){}');
  gl.shaderSourceJoined(vertex, [vsrc.length], vsrc);
  const program = gl.createProgram();
  gl.attachShader(program, vertex);
  gl.linkProgram(program);
  gl.useProgram(program);
  assert.equal(gl.capabilityState().alphaTest.gaps, 1);
});

test('stencil, colour mask, depth range and blend equation reach WebGL2 with validated tokens', () => {
  const gl = bridge(fakeContext());
  gl.colorMask(1, 0, 1, 1);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'colorMask'), [['colorMask', true, false, true, true]]);
  gl.stencilFunc(516, 1, 0xff);
  gl.stencilOp(7680, 7681, 7683);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'stencilFunc'), [['stencilFunc', 516, 1, 255]]);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'stencilOp'), [['stencilOp', 7680, 7681, 7683]]);
  gl.depthRange(0.0, 1.0);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'depthRange'), [['depthRange', 0, 1]]);
  gl.clearDepth(1.0);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'clearDepthf'), [['clearDepthf', 1]]);
  gl.blendEquation(32774); // GL_FUNC_ADD
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'blendEquation'), [['blendEquation', 32774]]);
  assert.throws(() => gl.stencilFunc(0x1234, 0, 1), /no constant for GL token/);
  assert.throws(() => gl.stencilOp(7680, 0x1234, 7680), /no constant for GL token/);
  assert.throws(() => gl.blendEquation(999999), /no constant for GL token/);
  assert.throws(() => gl.depthRange(Number.POSITIVE_INFINITY, 1), /needs finite values/);
});

test('glActiveTexture switches texture units and each unit keeps its own bound texture', () => {
  const gl = bridge(fakeContext());
  const a = gl.genTextures(1)[0];
  const b = gl.genTextures(1)[0];
  gl.bindTexture(3553, a);
  gl.activeTexture(33985); // GL_TEXTURE1
  gl.bindTexture(3553, b);
  assert.equal(gl.activeUnit, 1);
  assert.equal(gl.activeBoundTexture(), b);
  gl.activeTexture(33984);
  assert.equal(gl.activeBoundTexture(), a);
  assert.throws(() => gl.activeTexture(3553), /not a GL_TEXTUREn token/);
  assert.throws(() => gl.activeTexture(33986), /no texture unit 2/); // fake context exposes TEXTURE0/1
});

test('framebuffers and renderbuffers are real WebGL2 objects with validated targets', () => {
  const gl = bridge(fakeContext());
  const fbo = gl.genFramebuffers(2);
  assert.deepEqual([...fbo], [1, 2]);
  assert.equal(gl.isFramebuffer(1), true);
  gl.bindFramebuffer(36160, 1);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'bindFramebuffer'), [['bindFramebuffer', 36160, { __fbo: 1 }]]);
  assert.equal(gl.checkFramebufferStatus(36160), 36053);
  const tex = gl.genTextures(1)[0];
  gl.framebufferTexture2D(36160, 36064, 3553, tex, 0);
  // Chrome/SwiftShader rejects the attach with "5 arguments required, but only 4 present" when the
  // WebGLTexture object is missing (measured in diag162), so the emitted call must carry it.
  const attach = gl.context.calls.filter((c) => c[0] === 'framebufferTexture2D');
  assert.equal(attach.length, 1);
  assert.deepEqual(attach[0].slice(1, 4), [36160, 36064, 3553]);
  assert.equal(attach[0][4], gl.textures.get(tex).texture,
    'the attach must carry the WebGLTexture object owned by the GL handle');
  assert.equal(attach[0][5], 0);
  const rbo = gl.genRenderbuffers(1)[0];
  gl.bindRenderbuffer(36161, rbo);
  gl.renderbufferStorage(36161, 35056, 4, 4); // GL_DEPTH24_STENCIL8
  gl.framebufferRenderbuffer(36160, 33306, 36161, rbo);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'framebufferRenderbuffer'),
    [['framebufferRenderbuffer', 36160, 33306, 36161, { __rbo: 4 }]]);
  gl.deleteFramebuffers(fbo);
  assert.equal(gl.isFramebuffer(1), false);
  gl.deleteRenderbuffers(new Int32Array([rbo]));
  assert.equal(gl.isRenderbuffer(rbo), false);
  assert.throws(() => gl.bindFramebuffer(36160, 99), /does not exist/);
  assert.throws(() => gl.bindRenderbuffer(36160, rbo), /needs GL_RENDERBUFFER/);
  assert.throws(() => gl.renderbufferStorage(36161, 0x1234, 4, 4), /no renderbuffer internalformat/);
  assert.throws(() => gl.framebufferTexture2D(36160, 36064, 3553, 99, 0), /does not exist/);
});

/** Extra WebGL2 methods the attribute-stack restore path calls (viewport/scissor/enable/etc.). */
function attribContext() {
  const context = fakeContext();
  for (const name of ['viewport', 'scissor', 'enable', 'disable', 'depthFunc', 'depthMask', 'lineWidth',
    'cullFace', 'frontFace', 'blendFunc', 'blendFuncSeparate', 'clearStencil', 'stencilMask',
    'clearColor', 'pixelStorei']) context[name] = (...a) => context.calls.push([name, ...a]);
  for (const name of ['enableVertexAttribArray', 'disableVertexAttribArray'])
    context[name] = (...a) => context.calls.push([name, ...a]);
  context.BLEND = 3042; // the real WebGL2 constant for GL_BLEND (0x0be2)
  const base = context.getParameter;
  context.getParameter = (pname) => (pname === 3381 || pname === 3387 ? 64 : base(pname));
  return context;
}

test('glPushAttrib/GL_VIEWPORT_BIT snapshots viewport and depth range and glPopAttrib restores them', () => {
  const gl = bridge(attribContext());
  gl.viewport(0, 0, 64, 32);
  gl.depthRange(0, 1);
  gl.pushAttrib(2048); // GL_VIEWPORT_BIT, the mask zombie.core.Core and the render paths push
  assert.equal(gl.attribState().depth, 1);
  assert.deepEqual(gl.attribState().top, ['VIEWPORT']);
  gl.viewport(0, 0, 128, 64);
  gl.depthRange(0.25, 0.75);
  assert.deepEqual(gl.attribState().gaps, {});
  gl.popAttrib();
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'viewport').slice(-1), [['viewport', 0, 0, 64, 32]]);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'depthRange').slice(-1), [['depthRange', 0, 1]]);
  assert.equal(gl.attribState().depth, 0);
});

test('glPushAttrib(GL_ENABLE_BIT|GL_COLOR_BUFFER_BIT) restores capabilities and blend state', () => {
  const gl = bridge(attribContext());
  gl.call('blendFunc', 1, 0);
  gl.enable(0x0be2); // GL_BLEND
  gl.pushAttrib(24576); // GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT, the mask zombie.core.Core#initGL pushes
  assert.deepEqual(gl.attribState().top.sort(), ['COLOR_BUFFER', 'ENABLE']);
  gl.disable(0x0be2);
  gl.call('blendFunc', 0, 1);
  gl.popAttrib();
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'blendFunc').slice(-1), [['blendFunc', 1, 0]]);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'enable').slice(-1), [['enable', 0x0be2]]);
  assert.equal(gl.capabilityState().applied.be2.enabled, true);
});

test('attribute bits this backend cannot restore are counted, and an empty stack queues GL_STACK_UNDERFLOW', () => {
  const gl = bridge(attribContext());
  gl.pushAttrib(262144); // GL_TEXTURE_BIT: the desktop texture environment does not exist in WebGL2
  assert.deepEqual(gl.attribState().gaps, { GL_TEXTURE_BIT: 1 });
  gl.pushAttrib(4096);   // GL_TRANSFORM_BIT: WebGL2 has no matrix stack
  assert.deepEqual(gl.attribState().gaps, { GL_TEXTURE_BIT: 1, GL_TRANSFORM_BIT: 1 });
  gl.popAttrib();
  gl.popAttrib();
  // Desktop GL never raises here: the pop is ignored and GL_STACK_UNDERFLOW (0x0504) is queued.
  assert.equal(gl.getError(), 0);
  assert.doesNotThrow(() => gl.popAttrib());
  assert.doesNotThrow(() => gl.popClientAttrib());
  assert.equal(gl.attribState().depth, 0);
  assert.equal(gl.attribState().underflow, 1);
  assert.equal(gl.clientAttribState().underflow, 1);
  assert.equal(gl.getError(), 0x0504);
  assert.equal(gl.getError(), 0x0504);
  assert.equal(gl.getError(), 0);
});

test('the client attribute stack restores pixelStorei and enabled vertex attrib arrays', () => {
  const gl = bridge(attribContext());
  gl.call('pixelStorei', 0x0c01, 4); // GL_PACK_ALIGNMENT
  gl.enableAttrib(0);
  gl.pushClientAttrib(3); // GL_CLIENT_PIXEL_STORE_BIT | GL_CLIENT_VERTEX_ARRAY_BIT
  gl.call('pixelStorei', 0x0c01, 1);
  gl.disableAttrib(0);
  gl.popClientAttrib();
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'pixelStorei').slice(-1), [['pixelStorei', 0x0c01, 4]]);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'enableVertexAttribArray').slice(-1), [['enableVertexAttribArray', 0]]);
  assert.equal(gl.clientAttribState().depth, 0);
});

test('the stack depth and limit are answered from this backend state, and overflow queues GL_STACK_OVERFLOW', () => {
  const gl = bridge(attribContext());
  assert.deepEqual(gl.intParameter(0x0bb0, 0), 0); // GL_ATTRIB_STACK_DEPTH
  assert.deepEqual(gl.intParameter(0x0d35, 0), 64); // GL_MAX_ATTRIB_STACK_DEPTH
  for (let i = 0; i < 64; i++) gl.pushAttrib(2048);
  assert.equal(gl.attribState().depth, 64);
  assert.deepEqual(gl.intParameter(0x0bb0, 0), 64);
  gl.viewport(0, 0, 32, 32);
  // Desktop GL ignores the push and queues GL_STACK_OVERFLOW (0x0503); nothing is thrown.
  assert.doesNotThrow(() => gl.pushAttrib(2048));
  assert.equal(gl.attribState().depth, 64);
  assert.equal(gl.attribState().overflow, 1);
  assert.equal(gl.getError(), 0x0503);
  assert.equal(gl.getError(), 0);
  for (let i = 0; i < 64; i++) gl.pushClientAttrib(1);
  assert.doesNotThrow(() => gl.pushClientAttrib(1));
  assert.deepEqual(gl.clientAttribState(), { depth: 64, limit: 64, overflow: 1, underflow: 0 });
  assert.equal(gl.getError(), 0x0503);
  // The ignored push left no snapshot behind: the matching pop restores the 64th push, not 32x32.
  gl.popAttrib();
  assert.equal(gl.attribState().depth, 63);
  assert.equal(gl.getError(), 0);
  assert.ok(gl.hasEntrypoint('glPushAttrib') && gl.hasEntrypoint('glPopAttrib'));
});

/** Program whose vertex shader reads gl_Color; the fake linker places pzColor at location 3. */
function colorContext() {
  const context = attribContext();
  Object.assign(context, {
    getAttribLocation: (_p, name) => (name === 'pzColor' ? 3 : -1),
    vertexAttrib4f: (...a) => context.calls.push(['vertexAttrib4f', ...a]),
    drawArrays: (...a) => context.calls.push(['drawArrays', ...a]),
  });
  return context;
}
function colorProgram(gl) {
  const src = new TextEncoder().encode('#version 120\nvoid main(){gl_Position=gl_Vertex*gl_Color.a;}');
  const vertex = gl.createShader(35632);
  gl.shaderSourceJoined(vertex, [src.length], src);
  const program = gl.createProgram();
  gl.attachShader(program, vertex);
  gl.linkProgram(program);
  return program;
}

// Round 81: ItemModelRenderer.DoRender / IsoObjectModelDrawer.renderModel call glColor4f/glColor3f.
test('glColor is the pzColor constant while its array is disabled, and is not applied when enabled', () => {
  const context = colorContext();
  const gl = bridge(context);
  const constants = () => context.calls.filter((c) => c[0] === 'vertexAttrib4f');
  gl.color(0.5, 0.25, 0.125, 0.75); // no program yet: recorded only
  assert.deepEqual(constants(), []);
  const program = colorProgram(gl);
  gl.useProgram(program);           // the current colour follows the program that reads gl_Color
  assert.deepEqual(constants(), [['vertexAttrib4f', 3, 0.5, 0.25, 0.125, 0.75]]);
  gl.color(1, 0, 0);                // glColor3f: alpha 1
  assert.deepEqual(constants().at(-1), ['vertexAttrib4f', 3, 1, 0, 0, 1]);
  gl.drawArrays(4, 0, 3);           // unchanged colour: no redundant upload before the draw
  assert.equal(constants().length, 2);
  gl.call('vertexAttrib4f', 3, 0, 0, 0, 0); // the game overwrote the constant itself
  gl.drawArrays(4, 0, 3);           // so the draw re-applies the current colour
  assert.deepEqual(constants().at(-1), ['vertexAttrib4f', 3, 1, 0, 0, 1]);
  const before = constants().length;
  gl.enableAttrib(3);               // colour array enabled: the array wins, the constant is not set
  gl.color(0, 1, 0, 1);
  gl.drawArrays(4, 0, 3);
  assert.equal(constants().length, before);
  assert.ok(gl.colorState().arrayEnabled >= 2);
  gl.disableAttrib(3);              // disabled again: the next draw feeds the current colour
  gl.drawArrays(4, 0, 3);
  assert.deepEqual(constants().at(-1), ['vertexAttrib4f', 3, 0, 1, 0, 1]);
  gl.colorUnsignedBytes(255, 0, 51); // glColor3ub
  assert.deepEqual(constants().at(-1), ['vertexAttrib4f', 3, 1, 0, 0.2, 1]);
  assert.ok(gl.hasEntrypoint('glColor4f') && gl.hasEntrypoint('glColor3f'));
});

test('a program without gl_Color keeps the current colour without uploading it', () => {
  const context = colorContext();
  context.getAttribLocation = () => -1;
  const gl = bridge(context);
  gl.useProgram(colorProgram(gl));
  gl.color(0.2, 0.4, 0.6, 0.8);
  gl.drawArrays(4, 0, 3);
  assert.deepEqual(context.calls.filter((c) => c[0] === 'vertexAttrib4f'), []);
  assert.deepEqual(gl.colorState().current, [0.2, 0.4, 0.6, 0.8]);
  assert.ok(gl.colorState().noAttribute >= 1);
});

test('glPushAttrib(GL_CURRENT_BIT) snapshots the current colour and glPopAttrib restores it', () => {
  const context = colorContext();
  const gl = bridge(context);
  gl.useProgram(colorProgram(gl));
  gl.color(0.1, 0.2, 0.3, 0.4);
  gl.pushAttrib(1); // GL_CURRENT_BIT
  assert.deepEqual(gl.attribState().top, ['CURRENT']);
  assert.deepEqual(gl.attribState().gaps, {}, 'the current colour is real bridge state, not a gap');
  gl.color(1, 1, 1, 1);
  gl.popAttrib();
  assert.deepEqual(gl.colorState().current, [0.1, 0.2, 0.3, 0.4]);
  assert.deepEqual(context.calls.filter((c) => c[0] === 'vertexAttrib4f').at(-1), ['vertexAttrib4f', 3, 0.1, 0.2, 0.3, 0.4]);
  gl.pushAttrib(2048); // GL_VIEWPORT_BIT does not cover the colour
  gl.color(0.9, 0.9, 0.9, 0.9);
  gl.popAttrib();
  assert.deepEqual(gl.colorState().current, [0.9, 0.9, 0.9, 0.9]);
});

test('glBlitFramebuffer is forwarded to WebGL2 and invalid masks/filters queue the desktop error', () => {
  const context = fakeContext();
  Object.assign(context, { COLOR_BUFFER_BIT: 0x4000, DEPTH_BUFFER_BIT: 0x100, STENCIL_BUFFER_BIT: 0x400,
    blitFramebuffer: (...a) => context.calls.push(['blitFramebuffer', ...a]) });
  const gl = bridge(context);
  const blits = () => context.calls.filter((c) => c[0] === 'blitFramebuffer');
  gl.blitFramebuffer(0, 0, 64, 32, 0, 0, 128, 64, 0x4000, 0x2601); // COLOR, GL_LINEAR
  assert.deepEqual(blits(), [['blitFramebuffer', 0, 0, 64, 32, 0, 0, 128, 64, 0x4000, 0x2601]]);
  gl.blitFramebuffer(0, 0, 8, 8, 0, 0, 8, 8, 0x4500, 0x2600);       // COLOR|DEPTH|STENCIL, GL_NEAREST
  assert.deepEqual(blits().at(-1), ['blitFramebuffer', 0, 0, 8, 8, 0, 0, 8, 8, 0x4500, 0x2600]);
  assert.equal(gl.getError(), 0);
  gl.blitFramebuffer(0, 0, 8, 8, 0, 0, 8, 8, 0x4001, 0x2600);       // unknown bit
  gl.blitFramebuffer(0, 0, 8, 8, 0, 0, 8, 8, 0x4000, 0x1234);       // bad filter
  gl.blitFramebuffer(0, 0, 8, 8, 0, 0, 8, 8, 0x0100, 0x2601);       // LINEAR with depth
  assert.equal(blits().length, 2, 'rejected blits never reach WebGL2');
  assert.equal(gl.getError(), 0x0501);
  assert.equal(gl.getError(), 0x0500);
  assert.equal(gl.getError(), 0x0502);
  assert.equal(gl.getError(), 0);
  assert.ok(gl.hasEntrypoint('glBlitFramebuffer'));
});

// Round 79: ELEMENT_ARRAY_BUFFER is VAO state; drawElements must see the binding of the bound VAO.
test('element buffer binding follows the bound vertex array', () => {
  const context = fakeContext();
  // Real WebGL2 semantics: each VAO (null = default) owns its ELEMENT_ARRAY_BUFFER binding.
  const owned = new Map(); let vao = null; const baseGet = context.getParameter;
  Object.assign(context, { ARRAY_BUFFER: 34962, ELEMENT_ARRAY_BUFFER: 34963,
    createBuffer: () => ({ __buf: 1 }), createVertexArray: () => ({ __vao: Math.random() }),
    bindBuffer: (target, b) => { if (target === 34963) owned.set(vao, b); },
    bindVertexArray: (v) => { vao = v; },
    getParameter: (pname) => (pname === 0x8895 ? owned.get(vao) ?? null : baseGet(pname)),
    drawElements: (...a) => context.calls.push(['drawElements', ...a]) });
  const gl = bridge(context);
  const [vaoA, vaoB] = gl.genVertexArrays(2), [ebo] = gl.genBuffers(1);
  gl.bindVertexArray(vaoA); gl.bindBuffer(34963, ebo);
  gl.bindVertexArray(vaoB); assert.equal(gl.boundElements, null);
  assert.throws(() => gl.drawElements(4, 6, 0x1403, 0), /needs a bound ELEMENT_ARRAY_BUFFER/);
  gl.bindVertexArray(vaoA); assert.notEqual(gl.boundElements, null);
  gl.drawElements(4, 6, 0x1403, 156);
  assert.deepEqual(context.calls.at(-1), ['drawElements', 4, 6, 0x1403, 156]);
});

// Round 79: GL_QUADS is converted to triangles (a,b,c)(a,c,d) in a scratch element buffer.
test('GL_QUADS draws become indexed triangles and legacy modes raise', () => {
  const context = fakeContext();
  let bound = null; const uploads = [];
  Object.assign(context, { ARRAY_BUFFER: 34962, ELEMENT_ARRAY_BUFFER: 34963, TRIANGLES: 4, STREAM_DRAW: 35040,
    createBuffer: () => ({ __scratch: 1 }), bindBuffer: (t, b) => { bound = b; },
    bufferData: (t, data) => uploads.push([...data]),
    getParameter: (p) => (p === 0x8895 ? bound : 0),
    drawElements: (...a) => context.calls.push(['drawElements', ...a]) });
  const gl = bridge(context);
  gl.drawArrays(7, 4, 8);
  assert.deepEqual(uploads.at(-1), [4, 5, 6, 4, 6, 7, 8, 9, 10, 8, 10, 11]);
  assert.deepEqual(context.calls.at(-1), ['drawElements', 4, 12, 0x1405, 0]);
  assert.equal(bound, null, 'previous element binding restored');
  assert.throws(() => gl.drawArrays(8, 0, 4), /no primitive mode 0x8/);
});

// Round 79: TextureFBO.releaseTexture detaches with texture 0.
test('glFramebufferTexture2D with texture 0 detaches the attachment', () => {
  const context = fakeContext();
  context.framebufferTexture2D = (...a) => context.calls.push(['framebufferTexture2D', ...a]);
  const gl = bridge(context);
  gl.framebufferTexture2D(36160, 36064, 3553, 0, 0);
  assert.deepEqual(context.calls.at(-1), ['framebufferTexture2D', 36160, 36064, 3553, null, 0]);
});
