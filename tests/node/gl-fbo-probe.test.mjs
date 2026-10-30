import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GLBridge } from '../../port/runtime/gl.mjs';

/**
 * Synthetic WebGL2 context for the framebuffer-object probe (port/runtime/gl.mjs#framebufferProbe).
 * Own fixture, no game bytes; the constants are the numbers WebGL2 reports in Chrome. The context
 * answers checkFramebufferStatus and readPixels from a controllable "implementation" so the test can
 * prove the probe reports only what WebGL2 actually said.
 */
function fakeContext({ status = 36053, pixel = [128, 64, 192, 255], queueError = null } = {}) {
  const calls = [];
  const values = {
    NO_ERROR: 0, FRAMEBUFFER: 36160, READ_FRAMEBUFFER: 36008, DRAW_FRAMEBUFFER: 36009,
    RENDERBUFFER: 36161, COLOR_ATTACHMENT0: 36064, DEPTH_STENCIL_ATTACHMENT: 33306,
    FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT: 36055, FRAMEBUFFER_COMPLETE: 36053,
    DEPTH24_STENCIL8: 35056, RGBA8: 32856, RGBA: 6408, UNSIGNED_BYTE: 5121,
    NEAREST: 9728, LINEAR: 9729, CLAMP_TO_EDGE: 33071, TEXTURE_2D: 3553,
    TEXTURE_MIN_FILTER: 10241, TEXTURE_MAG_FILTER: 10240, TEXTURE_WRAP_S: 10242, TEXTURE_WRAP_T: 10243,
    TEXTURE_BINDING_2D: 32873, DRAW_FRAMEBUFFER_BINDING: 36006, READ_FRAMEBUFFER_BINDING: 36010,
    COLOR_BUFFER_BIT: 16384, INVALID_VALUE: 1281,
  };
  let id = 0;
  const queue = queueError === null ? [] : [queueError];
  const context = {
    calls, values, uploads: [],
    getParameter: (pname) => (pname === 32873 || pname === 36006 || pname === 36010) ? null : 0,
    createTexture: () => { const o = { __tex: ++id }; calls.push(['createTexture', o]); return o; },
    deleteTexture: (t) => calls.push(['deleteTexture', t]),
    bindTexture: (...a) => calls.push(['bindTexture', ...a]),
    texParameteri: (...a) => calls.push(['texParameteri', ...a]),
    texImage2D: (...a) => calls.push(['texImage2D', ...a]),
    createFramebuffer: () => { const o = { __fbo: ++id }; calls.push(['createFramebuffer', o]); return o; },
    deleteFramebuffer: (f) => calls.push(['deleteFramebuffer', f]),
    bindFramebuffer: (...a) => calls.push(['bindFramebuffer', ...a]),
    checkFramebufferStatus: (...a) => { calls.push(['checkFramebufferStatus', ...a]); return status; },
    createRenderbuffer: () => { const o = { __rbo: ++id }; calls.push(['createRenderbuffer', o]); return o; },
    deleteRenderbuffer: (r) => calls.push(['deleteRenderbuffer', r]),
    bindRenderbuffer: (...a) => calls.push(['bindRenderbuffer', ...a]),
    renderbufferStorage: (...a) => calls.push(['renderbufferStorage', ...a]),
    framebufferTexture2D: (...a) => calls.push(['framebufferTexture2D', ...a]),
    framebufferRenderbuffer: (...a) => calls.push(['framebufferRenderbuffer', ...a]),
    viewport: (...a) => calls.push(['viewport', ...a]),
    clearColor: (...a) => calls.push(['clearColor', ...a]),
    clear: (...a) => calls.push(['clear', ...a]),
    readPixels: (x, y, w, h, format, type, out) => {
      calls.push(['readPixels', x, y, w, h]);
      out.set(pixel);
    },
    getError: () => (queue.length === 0 ? 0 : queue.shift()),
    getSupportedExtensions: () => [],
  };
  Object.assign(context, values);
  return context;
}
function bridge(context) {
  const canvas = { width: 8, height: 8, addEventListener: () => {}, getContext: () => context };
  return new GLBridge(canvas);
}

test('the FBO probe runs a real attachment sequence and reports FRAMEBUFFER_COMPLETE', () => {
  const gl = bridge(fakeContext());
  const report = gl.framebufferProbe();
  assert.equal(report.complete, true);
  assert.equal(report.status, 36053);
  assert.equal(report.statusName, 'FRAMEBUFFER_COMPLETE');
  assert.equal(report.rendered, true);
  assert.deepEqual(report.readback, [128, 64, 192, 255]);
  assert.deepEqual(report.errors, []);
  const names = gl.context.calls.map((c) => c[0]);
  for (const required of ['createTexture', 'bindTexture', 'texImage2D', 'createFramebuffer',
    'framebufferTexture2D', 'createRenderbuffer', 'renderbufferStorage', 'framebufferRenderbuffer',
    'checkFramebufferStatus', 'clearColor', 'clear', 'readPixels', 'deleteFramebuffer',
    'deleteRenderbuffer', 'deleteTexture']) {
    assert.ok(names.includes(required), `the probe must actually call ${required}`);
  }
  // The renderbuffer is the depth+stencil storage the game's TextureFBODepth uses.
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'renderbufferStorage'),
    [['renderbufferStorage', 36161, 35056, 64, 64]]);
  const renderbuffer = gl.context.calls.find((c) => c[0] === 'createRenderbuffer')[1];
  const texture = gl.context.calls.find((c) => c[0] === 'createTexture')[1];
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'framebufferRenderbuffer'),
    [['framebufferRenderbuffer', 36160, 33306, 36161, renderbuffer]]);
  assert.deepEqual(gl.context.calls.filter((c) => c[0] === 'framebufferTexture2D'),
    [['framebufferTexture2D', 36160, 36064, 3553, texture, 0]]);
});

test('the probe reports an incomplete framebuffer as unsupported', () => {
  const gl = bridge(fakeContext({ status: 36055 }));
  const report = gl.framebufferProbe();
  assert.equal(report.complete, false);
  assert.equal(report.statusName, 'FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT');
  assert.equal(report.rendered, false);
  assert.equal(report.readback, null);
  // An incomplete FBO is never cleared through: that would pretend the draw works.
  assert.equal(gl.context.calls.some((c) => c[0] === 'clear'), false);
  // The probe still releases everything it created.
  assert.equal(gl.context.calls.filter((c) => c[0] === 'deleteTexture').length, 1);
  assert.equal(gl.context.calls.filter((c) => c[0] === 'deleteFramebuffer').length, 1);
  assert.equal(gl.context.calls.filter((c) => c[0] === 'deleteRenderbuffer').length, 1);
});

test('a clear that does not render makes the probe false', () => {
  const gl = bridge(fakeContext({ pixel: [0, 0, 0, 0] }));
  const report = gl.framebufferProbe();
  assert.equal(report.complete, false);
  assert.equal(report.rendered, false);
  assert.deepEqual(report.readback, [0, 0, 0, 0]);
});

test('the probe records its WebGL2 errors and leaves the game queue clean', () => {
  const gl = bridge(fakeContext({ queueError: 1281 }));
  const report = gl.framebufferProbe();
  assert.equal(report.errors.length, 1);
  assert.match(report.errors[0], /INVALID_VALUE/);
  assert.deepEqual(gl.pendingErrors, []);
  // glGetError still answers the game's own queue: nothing was stolen and nothing was left behind.
  assert.equal(gl.getError(), gl.context.NO_ERROR);
});

test('the probe result is cached and exposed as JSON for the diagnostics', () => {
  const gl = bridge(fakeContext());
  const first = gl.framebufferProbe();
  const second = gl.framebufferProbe();
  assert.equal(first, second);
  assert.equal(gl.fboProbeRuns, 1);
  const parsed = JSON.parse(JSON.stringify(first));
  assert.equal(parsed.complete, true);
  assert.equal(gl.calls.checkFramebufferStatus, 2); // once per attachment stage, once total afterwards
});
