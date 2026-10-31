import { translateShader, webglIdentifier, desktopIdentifier } from './glsl.mjs';

/** Function names that a desktop GLSL unit only prototypes (`void f(void);`) without defining. */
function declaredOnlyFunctions(raw) {
  const text = raw.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  const declared = new Set(), defined = new Set();
  for (const m of text.matchAll(/\b[A-Za-z_]\w*\s+([A-Za-z_]\w*)\s*\([^()]*\)\s*([;{])/g))
    (m[2] === ';' ? declared : defined).add(m[1]);
  for (const name of defined) declared.delete(name);
  return declared;
}
/** True when every error in a compile log is an unresolved call to a function this unit prototypes. */
function onlyUnresolvedPrototypes(log, prototypes) {
  if (!prototypes || prototypes.size === 0) return false;
  const errors = String(log).split('\n').filter(line => /ERROR/.test(line));
  return errors.length > 0 && errors.every(line => {
    const m = /Function (\w+)\(.*\) called by .* is undefined/.exec(line) || /'(\w+)' : no matching overloaded function found/.exec(line);
    return m !== null && prototypes.has(m[1]);
  });
}

/**
 * GL entrypoints this backend implements beyond raw WebGL2. Keep in sync with the mappings in
 * tools/glgen/generate.py and the wrappers in port.shim.GL11: an address is only handed out for
 * a name that can actually be called.
 */
const PORT_PROVIDED = new Set([
  'glClearColor', 'glClear', 'glViewport', 'glGetError', 'glEnable', 'glDisable', 'glScissor',
  'glDepthMask', 'glDepthFunc', 'glCullFace', 'glFrontFace', 'glPixelStorei', 'glLineWidth',
  'glBlendFunc', 'glBlendFuncSeparate', 'glBlendEquation', 'glClearDepthf', 'glClearDepth',
  'glStencilMask', 'glClearStencil', 'glStencilFunc', 'glStencilOp', 'glColorMask',
  'glDepthRange', 'glDepthFunc', 'glAlphaFunc', 'glActiveTexture',
  // Desktop attribute stacks: WebGL2 has none, so this bridge keeps its own snapshots of the state
  // it owns (attribState/clientAttribState report depth and any bit it cannot restore).
  'glPushAttrib', 'glPopAttrib', 'glPushClientAttrib', 'glPopClientAttrib',
  // GL30 framebuffer/renderbuffer natives the game reaches through zombie.core.textures and the
  // VBO/Lighting paths: every one of these has a real WebGL2 implementation below.
  'glGenFramebuffers', 'glDeleteFramebuffers', 'glIsFramebuffer', 'glBindFramebuffer', 'glIsVertexArray',
  // Round 80: GL30 glGenerateMipmap (FBORenderChunk textures) -> WebGL2 generateMipmap.
  'glGenerateMipmap',
  // Round 81: current colour (fed to gl_Color/pzColor while its array is off) and framebuffer blit.
  'glColor3f', 'glColor4f', 'glColor3ub', 'glColor4ub', 'glBlitFramebuffer',
  'glCheckFramebufferStatus', 'glFramebufferTexture2D', 'glFramebufferRenderbuffer',
  'glGenRenderbuffers', 'glDeleteRenderbuffers', 'glIsRenderbuffer', 'glBindRenderbuffer',
  'glRenderbufferStorage',
  'glGetString', 'glGetStringi', 'glGetInteger', 'glGetIntegerv', 'glGetFloat', 'glGetFloatv',
  'glGetBoolean', 'glGetBooleanv',
  // GL15 vertex buffer objects: WebGL2 owns the storage, port.shim.GL15 drives it through the
  // bridge below. Every one of these names has a real implementation in this class.
  'glGenBuffers', 'glCreateBuffer', 'glDeleteBuffers', 'glBindBuffer', 'glIsBuffer', 'glBufferData',
  'glBufferSubData', 'glGetBufferSubData', 'glMapBuffer', 'glUnmapBuffer', 'glGetBufferParameteriv',
  'glGetBufferPointerv', 'glGenQueries', 'glDeleteQueries', 'glIsQuery', 'glBeginQuery', 'glEndQuery',
  'glGetQueryiv', 'glGetQueryObjectiv', 'glGetQueryObjectuiv',
  // GL20 shaders/programs/uniforms and the GL30 VAO natives: port.shim.GL20 drives the bridge
  // methods below, so every name here has a real implementation over WebGL2.
  'glCreateProgram', 'glDeleteProgram', 'glIsProgram', 'glCreateShader', 'glDeleteShader',
  'glIsShader', 'glAttachShader', 'glDetachShader', 'glShaderSource', 'glCompileShader',
  'glLinkProgram', 'glUseProgram', 'glValidateProgram', 'glGetShaderiv', 'glGetProgramiv',
  'glGetShaderInfoLog', 'glGetProgramInfoLog', 'glGetUniformLocation', 'glGetAttribLocation',
  'glUniform1f', 'glUniform2f', 'glUniform3f', 'glUniform4f', 'glUniform1i', 'glUniform2i',
  'glUniform3i', 'glUniform4i', 'glUniform1fv', 'glUniform2fv', 'glUniform3fv', 'glUniform4fv',
  'glUniform1iv', 'glUniform2iv', 'glUniform3iv', 'glUniform4iv', 'glUniformMatrix2fv',
  'glUniformMatrix3fv', 'glUniformMatrix4fv', 'glEnableVertexAttribArray',
  'glDisableVertexAttribArray', 'glVertexAttribPointer', 'glVertexAttrib1f', 'glVertexAttrib2f',
  'glVertexAttrib3f', 'glVertexAttrib4f', 'glGenVertexArrays', 'glDeleteVertexArrays',
  'glBindVertexArray', 'glDrawArrays', 'glDrawElements', 'glDrawRangeElements',
  // Capability names of check_GL20 that WebGL2 does not expose as methods. port.shim.GL20 really
  // implements the ones the game uses; the desktop-only introspection natives raise with their own
  // identity instead of answering (docs/decisions.md), so no value is invented.
  'glIsProgram', 'glIsShader', 'glGetShaderiv', 'glGetProgramiv', 'glGetShaderInfoLog',
  'glGetProgramInfoLog', 'glGetAttachedShaders', 'glGetActiveUniform', 'glGetUniformfv',
  'glGetUniformiv', 'glGetShaderSource', 'glVertexAttrib1s', 'glVertexAttrib1d', 'glVertexAttrib2s',
  'glVertexAttrib2d', 'glVertexAttrib3s', 'glVertexAttrib3d', 'glVertexAttrib4s', 'glVertexAttrib4d',
  'glVertexAttrib4Nub', 'glVertexAttrib1fv', 'glVertexAttrib1sv', 'glVertexAttrib1dv',
  'glVertexAttrib2fv', 'glVertexAttrib2sv', 'glVertexAttrib2dv', 'glVertexAttrib3fv',
  'glVertexAttrib3sv', 'glVertexAttrib3dv', 'glVertexAttrib4fv', 'glVertexAttrib4sv',
  'glVertexAttrib4dv', 'glVertexAttrib4iv', 'glVertexAttrib4bv', 'glVertexAttrib4ubv',
  'glVertexAttrib4usv', 'glVertexAttrib4uiv', 'glVertexAttrib4Nbv', 'glVertexAttrib4Nsv',
  'glVertexAttrib4Niv', 'glVertexAttrib4Nubv', 'glVertexAttrib4Nusv', 'glVertexAttrib4Nuiv',
  'glBindAttribLocation', 'glGetActiveAttrib', 'glGetVertexAttribiv', 'glGetVertexAttribfv',
  'glGetVertexAttribdv', 'glGetVertexAttribPointerv',
  // GL11 texture lifecycle used by zombie.core.textures.TextureID (glGenTextures -> glBindTexture
  // -> glTexParameteri -> glTexImage2D/glTexSubImage2D -> glDeleteTextures). port.shim.GL11 drives
  // the bridge methods below, so every name here has a real implementation over WebGL2.
  'glGenTextures', 'glDeleteTextures', 'glIsTexture', 'glBindTexture', 'glTexParameteri',
  'glTexImage2D', 'glTexSubImage2D', 'glGetTexImage',
  // Round 80 (front F): fixed-function matrix stacks and immediate mode kept by this bridge (WebGL2
  // has neither), instancing, glIsEnabled and the framebuffer readback (see the section below).
  'glMatrixMode', 'glPushMatrix', 'glPopMatrix', 'glLoadIdentity', 'glLoadMatrixf', 'glMultMatrixf',
  'glOrtho', 'glTranslatef', 'glTranslated', 'glRotatef', 'glRotated', 'glScalef', 'glScaled',
  'glBegin', 'glEnd', 'glVertex2f', 'glVertex2i', 'glVertex2d', 'glVertex3f', 'glTexCoord2f', 'glNormal3f',
  'glIsEnabled', 'glReadBuffer', 'glReadPixels',
]);

const EAGER_ERROR_CALLS = new Set(['texImage2D', 'texSubImage2D', 'readPixels', 'compressedTexImage2D']);

/** Round 80: column-major copy of `count` row-major size x size matrices (glUniformMatrix transpose). */
export function transposeMatrices(values, size, count) {
  const out = new Float32Array(count * size * size);
  for (let m = 0; m < count; m++) {
    const base = m * size * size;
    for (let r = 0; r < size; r++) for (let c = 0; c < size; c++) out[base + c * size + r] = values[base + r * size + c];
  }
  return out;
}

/** WebGL2 context and object handles; unsupported entrypoints must throw. */

export class GLBridge {
  constructor(canvas) {
    const context = canvas.getContext('webgl2', { alpha: false, antialias: false, preserveDrawingBuffer: true });
    if (!context) throw new Error('WebGL 2 is required');
    this.context = context;
    this.calls = Object.create(null);
    // Attribute stacks must exist before the first WebGL2 call: call() records state into them.
    this.attribRecords = { pixelStorei: new Map() }; // last value of each restorable state entry
    this.attribStack = [];       // snapshots pushed by glPushAttrib, newest last
    this.clientAttribStack = []; // snapshots pushed by glPushClientAttrib, newest last
    this.attribGaps = {};        // attribute-bit name -> pushes whose desktop state this backend lacks
    this.attribLimit = 64;       // answered as GL_MAX_(CLIENT_)ATTRIB_STACK_DEPTH
    this.viewportBox = [0, 0, canvas.width, canvas.height];
    this.scissorBox = [0, 0, canvas.width, canvas.height];
    this.buffers = new Map(); // GL handle -> {buffer,size,target}
    this.queries = new Map(); // GL handle -> {query,target}
    this.shaders = new Map(); // GL handle -> {shader,type,source,compiled,log}
    this.programs = new Map(); // GL handle -> {program,shaders:Set,log,linked,used}
    // Observability only (never a fake success): every shader compile and program link is
    // recorded here because destroy() removes the maps above. Entries keep the WebGL2 info log
    // so a failing desktop program (e.g. media/shaders/default) can be diagnosed in the browser.
    this.shaderTrail = []; // {kind:'compile'|'link', handle, ok, log, detail}
    this.attribs = new Map(); // attribute index -> {enabled,size,...} recorded by glVertexAttribPointer
    this.locations = new Map(); // location handle -> WebGLUniformLocation (0 means "no location")
    this.boundElements = null; // last ELEMENT_ARRAY_BUFFER handle (drawElements needs it)
    // Desktop *_BINDING queries must report the GL handle the game passed to glBindBuffer, so the
    // handle (not only the WebGL object) is recorded alongside it.
    this.boundArrayHandle = 0;   // GL_ARRAY_BUFFER_BINDING (34964)
    this.boundElementHandle = 0; // GL_ELEMENT_ARRAY_BUFFER_BINDING (34965)
    this.vertexArrays = new Map(); // GL handle -> WebGLVertexArrayObject (GL30 natives)
    this.framebuffers = new Map(); // GL handle -> WebGLFramebuffer (GL30 framebuffer natives)
    this.renderbuffers = new Map(); // GL handle -> WebGLRenderbuffer (GL30 renderbuffer natives)
    this.boundArrays = null; // last ARRAY_BUFFER handle bound by bindBuffer (needed by attrib pointers)
    this.textures = new Map(); // GL handle -> {texture,levels:Map,width,height,internalformat} (GL11)
    // Desktop texture units: glBindTexture binds into the *active* unit, and the level uploads
    // (glTexImage2D/glTexSubImage2D/glGetTexImage) act on the texture bound to that unit.
    this.textureUnits = [null]; // unit index -> bound texture handle (0/null = none)
    this.activeUnit = 0;        // unit index set by glActiveTexture
    this.nextHandle = 1; // 0 is reserved for "none", as in desktop GL
    // Diagnosis of upload failures (observability only). The WebGL2 error queue is sticky: an
    // error raised by an earlier call is reported by the *next* glGetError. callLog keeps the
    // ordered calls and lastErrorDrainSeq marks where the game last drained the queue, so a
    // failing glTexImage2D can be attributed to a real call instead of guessing.
    this.callSeq = 0;
    this.callLog = [];       // [{seq, name}] last 160 WebGL2 calls actually reached
    this.errorDrains = [];   // [{seq, code, fromSeq, fromCall}] last 32 glGetError with an error
    this.lastErrorDrainSeq = 0;
    // Emulated desktop error queue. WebGL2's queue is FIFO and glGetError pops it, so the bridge
    // drains it after every call into this list and answers glGetError from here. Each error is
    // still reported once, in order, and now carries the call that really produced it (the sticky
    // queue otherwise blames whichever call checks first).
    this.pendingErrors = []; // [{seq, name, code}] oldest first
    this.unchecked = null; this.eagerErrors = globalThis.PZ_GL_EAGER_ERRORS === true;
    this.uploadFailCount = 0;   // failed level uploads (glTexImage2D/glTexSubImage2D)
    this.firstUploadFailure = undefined;
    // Result of the real framebuffer-object probe (see framebufferProbe). Undefined until the
    // game's FBO gate asks for it; the gate is what triggers it, never a synthetic default.
    this.fboProbe = undefined;
    this.fboProbeRuns = 0;
    // Desktop glClear masks whose bits WebGL2 cannot clear (accum, line, point, etc). Recorded,
    // never silently ignored: {from, token, unmapped, seq}.
    this.clearMaskGaps = [];
    this.lost = false;
    this.capabilities = new Map(); // desktop capability token -> {enabled, webgl} actually applied
    this.fixedFunctionCaps = new Map(); // desktop-only token -> last requested state, recorded not applied
    // Desktop glAlphaFunc state. WebGL2 has no alpha test, but the fragment shaders this backend
    // generates carry an equivalent discard (see port/runtime/glsl.mjs), so the state is *applied*
    // through the pzAlphaTestFunc/pzAlphaTestRef uniforms of every program that got the injection.
    this.alphaTestState = { enabled: false, func: -1, ref: 0, gaps: 0, uniformGaps: 0 };
    this.currentProgram = null; // program entry set by useProgram (needed to refresh uniforms)
    // Desktop current colour (glColor*, GL_CURRENT_BIT). Desktop GL feeds it to gl_Color whenever the
    // colour array is off; here gl_Color is the pzColor attribute (port/runtime/glsl.mjs), so the
    // colour becomes that attribute's constant value (vertexAttrib4f) when its array is disabled.
    this.currentColor = [1, 1, 1, 1];
    this.appliedColor = null; // {location, r, g, b, a} last constant uploaded by applyColor
    this.colorStats = { sets: 0, applied: 0, arrayEnabled: 0, noAttribute: 0 };
    // Round 80 (front F): the other GL_CURRENT_BIT values, the fixed-function matrix stacks and the
    // glBegin/glEnd batch in progress (see "fixed-function matrix stacks and immediate mode").
    this.currentTexCoord = [0, 0, 0, 1];
    this.currentNormal = [0, 0, 1];
    this.matrixModeName = 'MODELVIEW';
    this.matrixStacks = { MODELVIEW: [GLBridge.identity()], PROJECTION: [GLBridge.identity()], TEXTURE: [[GLBridge.identity()]] };
    this.matrixSerial = 0; // bumped on every matrix change; programs remember what they received
    this.matrixStats = { uploads: 0, overflow: 0, underflow: 0 };
    this.immediate = null; // {mode, data:number[]} between glBegin and glEnd
    // Desktop GL_STACK_OVERFLOW/GL_STACK_UNDERFLOW events on the attribute stacks (diagnostics).
    this.attribStackErrors = { overflow: 0, underflow: 0, clientOverflow: 0, clientUnderflow: 0 };
    // Desktop attribute stack (glPushAttrib/glPopAttrib). WebGL2 has none, so this backend keeps
    // real snapshots of the state it actually owns and restores them (see pushAttrib/popAttrib).
    canvas.addEventListener('webglcontextlost', event => { event.preventDefault(); this.lost = true; });
    canvas.addEventListener('webglcontextrestored', () => {
      // Resource reconstruction is not implemented yet: do not continue with invalid handles.
      this.lost = true;
    });
  }
  call(name, ...args) {
    if (this.lost) throw new Error('WebGL context lost: reload required');
    this.callSeq++;
    this.callLog.push({ seq: this.callSeq, name });
    if (this.callLog.length > 160) this.callLog.shift();
    this.calls[name] = (this.calls[name] || 0) + 1;
    if (name === 'viewport') this.viewportBox = args.slice(0, 4).map(Number);
    if (name === 'scissor') this.scissorBox = args.slice(0, 4).map(Number);
    const method = this.context[name];
    if (typeof method !== 'function') throw new Error(`Unsupported WebGL function: ${name}`);
    // Deferred error drain: gl.getError() is a synchronous GPU-process round trip, and doing it after
    // every call cost ~15 s per 30 s of boot (r69 profile). Calls whose own errors are inspected
    // (EAGER_ERROR_CALLS) and every consumer of the queue flush first, so attribution stays exact for
    // them; other errors are attributed to the last unchecked call ("deferred..name"). Set
    // globalThis.PZ_GL_EAGER_ERRORS = true to restore the per-call drain for debugging.
    const eager = this.eagerErrors || EAGER_ERROR_CALLS.has(name);
    if (eager) this.flushErrors();
    const result = method.apply(this.context, args);
    // A generic-attribute constant set by anyone (the game's glVertexAttrib*f or applyColor itself)
    // invalidates the colour applyColor last uploaded; applyColor re-records it after its own call.
    if (name.charCodeAt(0) === 118 && GLBridge.ATTRIB_CONSTANT_SETTERS.test(name)) this.appliedColor = null;
    this.recordAttrib(name, args);
    if (eager) this.drainQueue(name, this.callSeq); else this.unchecked = { seq: this.callSeq, name };
    return result;
  }
  // WebGL2 generic-attribute constant setters (vertexAttrib1f..4fv, vertexAttribI4i/ui[v]).
  static ATTRIB_CONSTANT_SETTERS = /^vertexAttribI?[1-4]/;
  /**
   * Record a desktop GL error this bridge raises itself (state WebGL2 does not own, such as the
   * attribute stacks): queued in order with the real WebGL2 errors, so glGetError reports it once.
   */
  recordError(name, code) {
    this.flushErrors();
    this.callSeq++;
    this.callLog.push({ seq: this.callSeq, name });
    if (this.callLog.length > 160) this.callLog.shift();
    this.pendingErrors.push({ seq: this.callSeq, name, code });
    if (this.pendingErrors.length > 512) this.pendingErrors.shift();
  }
  /** Move whatever WebGL2 queued for the call just made into the emulated desktop queue. */
  /** Move errors left by unchecked calls into the queue before anyone reads or marks it. */
  flushErrors() {
    const u = this.unchecked; if (u === undefined || u === null) return;
    this.unchecked = null; this.drainQueue(`deferred..${u.name}`, u.seq);
  }
  drainQueue(name, seq) {
    const gl = this.context;
    if (typeof gl.getError !== 'function') return;
    for (;;) {
      const code = gl.getError();
      if (code === gl.NO_ERROR || code === undefined) return;
      this.pendingErrors.push({ seq, name, code });
      if (this.pendingErrors.length > 512) this.pendingErrors.shift();
    }
  }
  /** Errors the emulated queue attributes to one specific call. */
  errorsFor(seq) { this.flushErrors(); return this.pendingErrors.filter((e) => e.seq === seq); }
  /**
   * Error check for calls this bridge makes on its own behalf. glGetError belongs to the game (it
   * pops the emulated queue), so an internal check must not consume it: it looks at what the queue
   * attributes to its own call instead.
   */
  internalErrors(sinceSeq) {
    this.flushErrors();
    return this.pendingErrors.filter((e) => e.seq >= sinceSeq).map((e) =>
      `${this.webglConstantName(e.code) ?? e.code} from ${e.name}#${e.seq}`);
  }
  checkInternal(sinceSeq, what) {
    const errs = this.internalErrors(sinceSeq);
    if (errs.length !== 0) throw new Error(`${what} generated ${errs.join(', ')}`);
  }
  /**
   * Remember the last value of every state entry this bridge can restore, so the desktop
   * attribute stack below has real data to pop. Only calls that actually reached WebGL2 are
   * recorded, which keeps the snapshot faithful to what the context really holds.
   */
  static ATTRIB_CALLS = new Map([
    ['clearColor', 'clearColor'], ['clearStencil', 'clearStencil'], ['stencilMask', 'stencilMask'],
    ['cullFace', 'cullFace'], ['frontFace', 'frontFace'], ['depthFunc', 'depthFunc'],
    ['depthMask', 'depthMask'], ['lineWidth', 'lineWidth'], ['blendFunc', 'blendFunc'],
    ['blendFuncSeparate', 'blendFuncSeparate'], ['blendEquation', 'blendEquation'],
    ['viewport', 'viewport'], ['scissor', 'scissor'], ['colorMask', 'colorMask'],
    ['stencilFunc', 'stencilFunc'], ['stencilOp', 'stencilOp'], ['depthRange', 'depthRange'],
    ['clearDepthf', 'clearDepth'], ['clearDepth', 'clearDepth'],
  ]);
  recordAttrib(name, args) {
    const key = GLBridge.ATTRIB_CALLS.get(name);
    if (key === undefined) {
      if (name === 'pixelStorei') this.attribRecords.pixelStorei.set(Number(args[0]), args.slice(0, 2).map(Number));
      return;
    }
    this.attribRecords[key] = { fn: name, args: Array.from(args) };
  }
  clearColor(...args) { this.call('clearColor', ...args); }
  /* Desktop glClear mask -> WebGL2 clear mask. The desktop bits are COLOR 0x00004000,
   * DEPTH 0x00000100 and STENCIL 0x00000400 (verified with `javap -constants` over the pinned
   * jar); WebGL2 only accepts COLOR 0x1, DEPTH 0x100 and STENCIL 0x200, so a desktop mask raises
   * "WebGL: INVALID_VALUE: clear: invalid mask" - the last console line of round 60, right after
   * UI_Loading_ModelsAnimations. DEPTH happens to be the same number in both systems. A mask with
   * no desktop bit at all is passed through untouched, which keeps the bridge's own WebGL-token
   * calls (the framebuffer probe) working. Bits WebGL2 cannot clear are never dropped silently:
   * they are recorded in clearMaskGaps.
   */
  static CLEAR_BITS = new Map([
    [0x4000, 'COLOR_BUFFER_BIT'], [0x100, 'DEPTH_BUFFER_BIT'], [0x400, 'STENCIL_BUFFER_BIT'],
  ]);
  clearMask(mask) {
    const bits = Number(mask) >>> 0;
    let token = 0;
    let unmapped = bits;
    for (const [bit, name] of GLBridge.CLEAR_BITS) {
      if ((bits & bit) === 0) continue;
      const value = this.context[name];
      if (value === undefined)
        throw new Error(`WebGL2 has no ${name} for glClear mask 0x${bits.toString(16)}`);
      token |= value;
      unmapped &= ~bit;
    }
    if (token === 0) return { token: bits, unmapped: 0 };
    return { token, unmapped };
  }
  clear(...args) {
    const mapped = this.clearMask(args[0]);
    this.call('clear', mapped.token);
    if (mapped.unmapped !== 0) {
      this.clearMaskGaps.push({ from: args[0], token: mapped.token, unmapped: mapped.unmapped,
        seq: this.callSeq });
      if (this.clearMaskGaps.length > 32) this.clearMaskGaps.shift();
    }
  }
  viewport(...args) { this.call('viewport', ...args); }
  /**
   * Desktop glGetError: pops the oldest queued error and returns it, NO_ERROR when the queue is
   * empty. The WebGL2 queue was already drained per call (see drainQueue), so nothing is lost and
   * nothing is reported twice; the drain point also marks the calls that follow it.
   */
  getError() {
    this.callSeq++;
    this.callLog.push({ seq: this.callSeq, name: 'getError' });
    if (this.callLog.length > 160) this.callLog.shift();
    this.calls.getError = (this.calls.getError || 0) + 1;
    this.flushErrors();
    this.lastErrorDrainSeq = this.callSeq;
    const entry = this.pendingErrors.shift();
    if (entry === undefined) return this.context.NO_ERROR;
    const code = entry.code;
    this.errorDrains.push({ seq: this.callSeq, code: this.webglConstantName(code) ?? String(code),
      fromSeq: entry.seq, fromCall: entry.name });
    if (this.errorDrains.length > 32) this.errorDrains.splice(0, this.errorDrains.length - 32);
    return code;
  }
  unsupported(jniName) { throw new Error(`Unsupported native: ${jniName}`); }
  /* --- Desktop capability tokens on glEnable/glDisable ---------------------------------
   * WebGL2 only accepts its own capability constants, so a desktop token has to be translated.
   * A token with no WebGL2 capability is reported with its identity instead of being dropped:
   * silently ignoring it would pretend the fixed-function feature is active (AGENTS.md).
   */
  // Values verified with `javap -constants` over the pinned LWJGL jar: for these capabilities the
  // desktop token and the WebGL2 constant are the same number, so the token is only a lookup key.
  static CAPS = new Map([
    [0x0c11, 'SCISSOR_TEST'], [0x0b44, 'CULL_FACE'], [0x0b71, 'DEPTH_TEST'], [0x0b90, 'STENCIL_TEST'],
    [0x0be2, 'BLEND'], [0x8037, 'POLYGON_OFFSET_FILL'], [0x8c89, 'RASTERIZER_DISCARD'],
    [0x809e, 'SAMPLE_ALPHA_TO_COVERAGE'], [0x80a0, 'SAMPLE_COVERAGE'], [0x80b0, 'SAMPLE_MASK'],
  ]);
  /* Desktop fixed-function capabilities with no WebGL2 state at all. This backend runs the game's
   * shader pipeline (see apiVersion '3.3 (WebGL 2.0)'), where texturing, lighting, alpha test and
   * fog are shader behaviour rather than enable bits. Their requested value is *recorded* and is
   * reported in the Chrome evidence (capabilityState), never reported as applied.
   */
  static FIXED_FUNCTION_CAPS = new Map([
    [0x0de0, 'TEXTURE_1D'], [0x0de1, 'TEXTURE_2D'], [0x806f, 'TEXTURE_3D'], [0x8513, 'TEXTURE_CUBE_MAP'],
    [0x0b60, 'FOG'], [0x0b50, 'LIGHTING'], [0x0b57, 'COLOR_MATERIAL'], [0x0ba1, 'NORMALIZE'],
    [0x0bc0, 'ALPHA_TEST'], [0x0bd0, 'DITHER'], [0x0b20, 'LINE_SMOOTH'], [0x0b10, 'POINT_SMOOTH'],
    [0x0b24, 'LINE_STIPPLE'], [0x0b42, 'POLYGON_STIPPLE'], [0x0bf1, 'LOGIC_OP'], [0x809d, 'MULTISAMPLE'],
  ]);
  capability(cap, on) {
    const gl = this.context;
    const name = GLBridge.CAPS.get(cap);
    const constant = name === undefined ? undefined : gl[name];
    if (GLBridge.FIXED_FUNCTION_CAPS.has(cap)) {
      this.fixedFunctionCaps.set(cap, { enabled: !!on, name: GLBridge.FIXED_FUNCTION_CAPS.get(cap) });
      // GL_ALPHA_TEST is the one recorded capability this backend can really reproduce: the generated
      // fragment shaders carry the equivalent discard, so the enable bit is applied to the program
      // that is current (if it received the injection).
      if (cap === 0x0bc0) {
        this.alphaTestState.enabled = !!on;
        if (this.currentProgram !== null) this.applyAlphaTest(this.currentProgram);
      }
      return;
    }
    if (constant === undefined)
      throw new Error(`WebGL2 has no capability for GL token 0x${cap.toString(16)} (${on ? 'glEnable' : 'glDisable'})`);
    this.call(on ? 'enable' : 'disable', constant);
    this.capabilities.set(cap, { enabled: on, webgl: name });
  }
  enable(cap) { this.capability(cap, true); }
  disable(cap) { this.capability(cap, false); }
  /**
   * Desktop glAlphaFunc(func, ref). Function values come from `javap -constants` on the pinned jar
   * (GL_NEVER 512 .. GL_ALWAYS 519); an unknown function raises with its own identity instead of
   * guessing a comparison. The reference is clamped to [0,1] exactly as desktop GL clamps it.
   */
  alphaTest(func, ref) {
    const fn = Number(func);
    if (!Number.isInteger(fn) || fn < 512 || fn > 519)
      throw new Error(`glAlphaFunc: unsupported alpha-test function ${func} (GL_NEVER 512..GL_ALWAYS 519)`);
    const value = Number(ref);
    if (!Number.isFinite(value)) throw new Error(`glAlphaFunc: alpha reference must be finite, got ${ref}`);
    this.alphaTestState.func = fn;
    this.alphaTestState.ref = Math.min(1, Math.max(0, value));
    if (this.currentProgram !== null) this.applyAlphaTest(this.currentProgram);
  }
  /** Desktop stencil/colour/depth state that WebGL2 owns directly. Tokens are validated against
   * the real WebGL2 constants (values verified with `javap -constants` on the pinned jar and the
   * numbers Chrome exposes); a token WebGL2 lacks raises with its identity.
   */
  webglToken(token, what) {
    const name = this.webglConstantName(Number(token));
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no constant for GL token 0x${(token >>> 0).toString(16)} in ${what}`);
    return this.context[name];
  }
  colorMask(r, g, b, a) {
    const bools = v => (v === 1 || v === true) ? 1 : 0;
    this.call('colorMask', bools(r) !== 0, bools(g) !== 0, bools(b) !== 0, bools(a) !== 0);
  }
  stencilFunc(func, ref, mask) {
    this.call('stencilFunc', this.webglToken(func, 'glStencilFunc'), Number(ref) | 0, Number(mask) >>> 0);
  }
  stencilOp(fail, zfail, zpass) {
    this.call('stencilOp', this.webglToken(fail, 'glStencilOp fail'),
      this.webglToken(zfail, 'glStencilOp zfail'), this.webglToken(zpass, 'glStencilOp zpass'));
  }
  depthRange(near, far) {
    const n = Number(near), f = Number(far);
    if (!Number.isFinite(n) || !Number.isFinite(f))
      throw new Error(`glDepthRange needs finite values, got ${near} and ${far}`);
    this.call('depthRange', Math.min(1, Math.max(0, n)), Math.min(1, Math.max(0, f)));
  }
  clearDepth(depth) {
    const value = Number(depth);
    if (!Number.isFinite(value)) throw new Error(`glClearDepth needs a finite value, got ${depth}`);
    // WebGL2 renamed the setter to clearDepthf; the value is clamped to [0,1] as desktop GL does.
    const name = typeof this.context.clearDepthf === 'function' ? 'clearDepthf' : 'clearDepth';
    this.call(name, Math.min(1, Math.max(0, value)));
  }
  blendEquation(mode) {
    this.call('blendEquation', this.webglToken(mode, 'glBlendEquation'));
  }
  /* --- Desktop attribute stack (glPushAttrib/glPopAttrib) --------------------------------
   * WebGL2 has no attribute stack, so this backend keeps its own snapshots of the state it
   * actually owns (see recordAttrib) and restores them. Bits whose desktop state this backend
   * cannot hold are counted in attribGaps and reported by attribState(): they are never
   * silently dropped (AGENTS.md: no simulated success). Bit values come from `javap -constants`
   * over the pinned jar; the masks the game really pushes were read from bytecode
   * (GL_VIEWPORT_BIT 2048 in 9 call sites, GL_ENABLE_BIT|GL_COLOR_BUFFER_BIT 24576 in 1).
   */
  // Values read with `javap -constants` from org.lwjgl.opengl.GL11 of the pinned jar.
  static ATTRIB_BITS = new Map([
    [1, 'CURRENT'], [2, 'POINT'], [4, 'LINE'], [8, 'POLYGON'], [16, 'POLYGON_STIPPLE'],
    [32, 'PIXEL_MODE'], [64, 'LIGHTING'], [128, 'FOG'], [256, 'DEPTH_BUFFER'],
    [512, 'ACCUM_BUFFER'], [1024, 'STENCIL_BUFFER'], [2048, 'VIEWPORT'], [4096, 'TRANSFORM'],
    [8192, 'ENABLE'], [16384, 'COLOR_BUFFER'], [32768, 'HINT'], [65536, 'EVAL'],
    [131072, 'LIST'], [262144, 'TEXTURE'], [524288, 'SCISSOR'],
  ]);
  // Which restorable entries each attribute bit covers on desktop GL.
  static ATTRIB_STATE = new Map([
    ['LINE', ['lineWidth']],
    ['POINT', null], // desktop glPointSize / point smoothing have no WebGL2 state
    ['POLYGON', ['cullFace', 'frontFace']],
    ['DEPTH_BUFFER', ['depthFunc', 'depthMask', 'clearDepth', 'depthRange']],
    ['STENCIL_BUFFER', ['stencilFunc', 'stencilOp', 'stencilMask', 'clearStencil']],
    ['VIEWPORT', ['viewport', 'depthRange']],
    ['COLOR_BUFFER', ['clearColor', 'colorMask', 'blendFunc', 'blendFuncSeparate', 'blendEquation']],
    ['SCISSOR', ['scissor']],
    // Bits whose state lives in this bridge rather than in a WebGL2 setter: the snapshot captures
    // them through state.caps/state.texture/state.pixelStorei/state.current, so they carry an empty
    // key list instead of being counted as gaps. GL_CURRENT_BIT holds the current colour (glColor*);
    // the other current values it covers (normal, texture coordinates, raster position) have no
    // entrypoint in this backend (their natives raise), so there is no state of theirs to lose.
    ['ENABLE', []], ['PIXEL_MODE', []], ['CURRENT', []],
    // Round 80 (front F): GL_TRANSFORM_BIT is the matrix mode (bridge state, see matrixMode) plus the
    // clip planes, whose natives are not mapped (they raise), so there is no state of theirs to lose.
    // GL_CURRENT_BIT also covers the current texture coordinate and normal (glTexCoord2f/glNormal3f).
    ['TRANSFORM', []],
    // GL_TEXTURE_BIT covers the desktop texture environment (glTexEnvi, env colour/modes), which
    // WebGL2 has no equivalent for, so it is a counted gap. The unit bindings are still snapshotted
    // below because losing them across a push/pop would mis-bind textures.
    ['TEXTURE', null],
    // Bits whose desktop state WebGL2 does not expose at all: lighting, fog, accumulation, the
    // matrix stack, hints, eval/list and polygon stipple. Pushing one is counted in
    // attribGaps so the Chrome evidence shows what this backend cannot reproduce.
    ['POLYGON_STIPPLE', null], ['LIGHTING', null], ['FOG', null],
    ['ACCUM_BUFFER', null], ['HINT', null], ['EVAL', null], ['LIST', null],
  ]);
  // Desktop error codes (`javap -constants` on the pinned GL11): GL_STACK_OVERFLOW/UNDERFLOW.
  static GL_STACK_OVERFLOW = 0x0503;
  static GL_STACK_UNDERFLOW = 0x0504;
  /**
   * Snapshot the state this bridge owns for the requested mask, then push it. A full stack behaves
   * as desktop GL: GL_STACK_OVERFLOW is queued for glGetError and the push is ignored.
   */
  pushAttrib(mask) {
    const bits = Number(mask) >>> 0;
    if (this.attribStack.length >= this.attribLimit) {
      this.attribStackErrors.overflow++;
      this.recordError('glPushAttrib', GLBridge.GL_STACK_OVERFLOW);
      return;
    }
    const names = [];
    for (const [bit, name] of GLBridge.ATTRIB_BITS) if (bits & bit) names.push(name);
    if (bits === 0xffffffff) names.push('ALL');
    const state = {};
    for (const name of names) {
      const keys = GLBridge.ATTRIB_STATE.get(name);
      if (keys === undefined) { this.attribGap(`GL_${name}_BIT`); continue; }
      if (keys === null) { this.attribGap(`GL_${name}_BIT`); continue; }
      for (const key of keys) {
        const rec = this.attribRecords[key];
        if (rec !== undefined) state[key] = { fn: rec.fn, args: rec.args.slice() };
      }
    }
    // ENABLE and TEXTURE also cover state held in this bridge's own maps, not WebGL2 calls.
    if (names.includes('ENABLE')) {
      state.caps = { caps: new Map(this.capabilities), fixed: new Map(this.fixedFunctionCaps), alphaTest: { ...this.alphaTestState } };
      state.scissor = this.scissorBox.slice(); // GL_ENABLE_BIT covers SCISSOR_TEST (WebGL2 has no ALU bits)
    }
    if (names.includes('TEXTURE') || names.includes('ALL')) state.texture = { activeUnit: this.activeUnit, units: this.textureUnits.slice() };
    if (names.includes('PIXEL_MODE')) state.pixelStorei = new Map(this.attribRecords.pixelStorei);
    if (names.includes('VIEWPORT')) state.viewport = this.viewportBox.slice();
    if (names.includes('SCISSOR')) state.scissor = this.scissorBox.slice();
    if (names.includes('CURRENT')) state.current = this.currentColor.slice();
    if (names.includes('CURRENT')) state.currentVertex = { texCoord: this.currentTexCoord.slice(), normal: this.currentNormal.slice() };
    if (names.includes('TRANSFORM')) state.matrixMode = this.matrixModeName;
    this.attribStack.push({ mask: bits, names, state });
  }
  /**
   * Restore the topmost snapshot. An empty stack behaves as desktop GL: GL_STACK_UNDERFLOW is
   * queued for glGetError and the pop is ignored (returns null).
   */
  popAttrib() {
    if (this.attribStack.length === 0) {
      this.attribStackErrors.underflow++;
      this.recordError('glPopAttrib', GLBridge.GL_STACK_UNDERFLOW);
      return null;
    }
    const top = this.attribStack.pop();
    const st = top.state;
    for (const key of Object.keys(st)) {
      const rec = st[key];
      if (rec === undefined || rec.fn === undefined) continue;
      this.call(rec.fn, ...rec.args);
    }
    if (st.caps) {
      this.capabilities = new Map(st.caps.caps);
      this.fixedFunctionCaps = new Map(st.caps.fixed);
      this.alphaTestState = { ...st.caps.alphaTest };
      for (const [token, entry] of this.capabilities) this.capability(token, entry.enabled);
      if (this.currentProgram !== null) this.applyAlphaTest(this.currentProgram);
    }
    if (st.texture) { this.activeUnit = st.texture.activeUnit; this.textureUnits = st.texture.units.slice(); }
    if (st.pixelStorei) { this.attribRecords.pixelStorei = new Map(st.pixelStorei); for (const [, v] of st.pixelStorei) this.call('pixelStorei', v[0], v[1]); }
    if (st.viewport) this.call('viewport', ...st.viewport);
    if (st.scissor) this.call('scissor', ...st.scissor);
    if (st.current) { this.currentColor = st.current.slice(); this.applyColor(); }
    if (st.currentVertex) { this.currentTexCoord = st.currentVertex.texCoord.slice(); this.currentNormal = st.currentVertex.normal.slice(); }
    if (st.matrixMode) this.matrixModeName = st.matrixMode;
    return top.names;
  }
  /* --- Desktop client attribute stack (glPushClientAttrib/glPopClientAttrib) -------------
   * GL_CLIENT_PIXEL_STORE_BIT covers glPixelStorei values this bridge recorded, and
   * GL_CLIENT_VERTEX_ARRAY_BIT covers the enabled vertex attrib arrays this bridge tracks in
   * `attribs`. Both are state this backend really owns, so the snapshot restores them for real.
   */
  pushClientAttrib(mask) {
    const bits = Number(mask) >>> 0;
    if (this.clientAttribStack.length >= this.attribLimit) {
      this.attribStackErrors.clientOverflow++;
      this.recordError('glPushClientAttrib', GLBridge.GL_STACK_OVERFLOW);
      return;
    }
    const state = {};
    if (bits & 1) state.pixelStorei = new Map(this.attribRecords.pixelStorei); // GL_CLIENT_PIXEL_STORE_BIT
    if (bits & 2) state.attribs = new Map([...this.attribs].map(([k, v]) => [k, { ...v }])); // GL_CLIENT_VERTEX_ARRAY_BIT
    if (bits === 0xffffffff) { state.pixelStorei = new Map(this.attribRecords.pixelStorei); state.attribs = new Map([...this.attribs].map(([k, v]) => [k, { ...v }])); }
    this.clientAttribStack.push({ mask: bits, state });
  }
  popClientAttrib() {
    if (this.clientAttribStack.length === 0) {
      this.attribStackErrors.clientUnderflow++;
      this.recordError('glPopClientAttrib', GLBridge.GL_STACK_UNDERFLOW);
      return null;
    }
    const top = this.clientAttribStack.pop();
    if (top.state.pixelStorei) {
      this.attribRecords.pixelStorei = new Map(top.state.pixelStorei);
      for (const [, v] of top.state.pixelStorei) this.call('pixelStorei', v[0], v[1]);
    }
    if (top.state.attribs) {
      const now = new Set([...this.attribs.keys()].filter((k) => this.attribs.get(k).enabled));
      for (const index of now) if (!top.state.attribs.get(index)?.enabled) this.disableAttrib(index);
      for (const [index, entry] of top.state.attribs) if (entry.enabled && !now.has(index)) this.enableAttrib(index);
      this.attribs = new Map([...this.attribs].map(([k, v]) => [k, top.state.attribs.has(k) ? top.state.attribs.get(k) : v]));
    }
    return top.mask;
  }
  clientAttribState() {
    return { depth: this.clientAttribStack.length, limit: this.attribLimit,
      overflow: this.attribStackErrors.clientOverflow, underflow: this.attribStackErrors.clientUnderflow };
  }
  /** Count a bit this backend cannot restore, keeping the name for the Chrome evidence. */
  attribGap(name) { this.attribGaps[name] = (this.attribGaps[name] || 0) + 1; }
  attribState() {
    return { depth: this.attribStack.length, limit: this.attribLimit, gaps: { ...this.attribGaps },
      top: this.attribStack.length ? this.attribStack[this.attribStack.length - 1].names : null,
      overflow: this.attribStackErrors.overflow, underflow: this.attribStackErrors.underflow };
  }
  /** Upload the recorded alpha test to the program that is current, when it carries the injection. */
  applyAlphaTest(entry) {
    if (!entry.alphaTest) {
      // A program whose fragment shader never wrote gl_FragColor got no injection, so the desktop
      // alpha test cannot be reproduced for it. Counted, never silently dropped.
      if (this.alphaTestState.enabled) this.alphaTestState.gaps += 1;
      return;
    }
    const func = this.call('getUniformLocation', entry.program, 'pzAlphaTestFunc');
    const ref = this.call('getUniformLocation', entry.program, 'pzAlphaTestRef');
    if (func === null || ref === null) {
      // The shader compiler dropped the gating uniforms (it may constant-fold the discard). The
      // alpha test then cannot be reproduced for this program: counted and logged, never hidden.
      this.alphaTestState.uniformGaps += 1;
      this.alphaTestState.uniformGapPrograms = (this.alphaTestState.uniformGapPrograms || []).slice(0, 3);
      if (this.alphaTestState.uniformGapPrograms.length < 4)
        this.alphaTestState.uniformGapPrograms.push({ func: func === null, ref: ref === null });
      return;
    }
    this.call('uniform1i', func, this.alphaTestState.enabled ? this.alphaTestState.func : -1);
    this.call('uniform1f', ref, this.alphaTestState.ref);
  }
  /**
   * Desktop glColor4f/glColor3f (alpha 1): sets the current colour. Desktop GL does not clamp the
   * current colour (GL 3.0+ compatibility), so the values are stored as given.
   */
  color(r, g, b, a = 1) {
    this.currentColor = [Number(r), Number(g), Number(b), Number(a)];
    this.colorStats.sets++;
    if (this.immediate === null) this.applyColor(); // inside glBegin/glEnd the colour goes with each vertex
  }
  /** glColor3ub/glColor4ub: unsigned bytes (0..255, already masked by the shim) map to [0,1]. */
  colorUnsignedBytes(r, g, b, a = 255) {
    this.color((r & 0xff) / 255, (g & 0xff) / 255, (b & 0xff) / 255, (a & 0xff) / 255);
  }
  /** Location of gl_Color (pzColor) in a linked program, -1 when its vertex shader has none. */
  colorLocation(entry) {
    if (entry.colorLocation !== undefined) return entry.colorLocation;
    if (!entry.linked) return -1; // an unlinked program would queue INVALID_OPERATION for the game
    const location = Number(this.call('getAttribLocation', entry.program, 'pzColor'));
    entry.colorLocation = Number.isInteger(location) ? location : -1;
    return entry.colorLocation;
  }
  /**
   * Feed the current colour to gl_Color as desktop GL does: when the program in use reads pzColor
   * and its array is disabled, the attribute's constant value is the current colour. With the array
   * enabled the array wins (desktop leaves the current colour unused), so nothing is uploaded.
   * Called on glColor*, glUseProgram, glPopAttrib(GL_CURRENT_BIT) and before every draw.
   */
  applyColor() {
    const entry = this.currentProgram;
    if (entry === null) return;
    const location = this.colorLocation(entry);
    if (location < 0) { this.colorStats.noAttribute++; return; }
    if (this.attribs.get(location)?.enabled) { this.colorStats.arrayEnabled++; return; }
    const [r, g, b, a] = this.currentColor, last = this.appliedColor;
    if (last !== null && last.location === location && last.r === r && last.g === g && last.b === b && last.a === a) return;
    this.call('vertexAttrib4f', location, r, g, b, a);
    this.appliedColor = { location, r, g, b, a };
    this.colorStats.applied++;
  }
  colorState() { return { current: this.currentColor.slice(), applied: this.appliedColor, ...this.colorStats }; }
  /** State the Java/runtime report exposes: applied WebGL2 caps plus recorded desktop-only caps. */
  capabilityState() {
    const applied = {};
    for (const [token, entry] of this.capabilities) applied[token.toString(16)] = entry;
    const recorded = {};
    for (const [token, entry] of this.fixedFunctionCaps) recorded[token.toString(16)] = entry;
    return { applied, fixedFunctionRecorded: recorded, alphaTest: { ...this.alphaTestState } };
  }
  /**
   * WebGL2 exposes no desktop OpenGL version. This backend presents the API version 3.3 with the
   * WebGL 2.0 renderer comment, because WebGL2 has no fixed-function pipeline and the game's
   * modern shader path is the only one that can run (docs/decisions.md).
   */
  apiVersion() { return '3.3 (WebGL 2.0)'; }
  extensions() { return this.context.getSupportedExtensions() || []; }
  /**
   * Honest entrypoint probe for the capability bridge: a GL name exists only when WebGL2 has the
   * method (GL names map to WebGL by lower-casing the first two letters) or when this bridge
   * implements an explicit equivalent in PORT_PROVIDED. Everything else reports address 0.
   */
  hasEntrypoint(name) {
    if (PORT_PROVIDED.has(name)) return true;
    if (!name.startsWith('gl') || name.length < 3) return false;
    return typeof this.context[name[2].toLowerCase() + name.slice(3)] === 'function';
  }
  /* --- Queries the game reads through GL11/GL11C (docs/decisions.md) ---------------
   * Desktop GL tokens are answered from real WebGL2 state where an equivalent exists.
   * A token with no WebGL equivalent raises honestly; nothing is invented. The values
   * that follow from this backend's declared API version (3.3) are stated below, and
   * viewport/scissor come from the state this bridge actually set.
   */
  stringParameter(pname) {
    const gl = this.context;
    switch (pname) {
      case 0x1f00: { // GL_VENDOR
        const ext = gl.getExtension('WEBGL_debug_renderer_info');
        return ext ? String(gl.getParameter(ext.UNMASKED_VENDOR_WEBGL)) : String(gl.getParameter(gl.VENDOR));
      }
      case 0x1f01: { // GL_RENDERER
        const ext = gl.getExtension('WEBGL_debug_renderer_info');
        return ext ? String(gl.getParameter(ext.UNMASKED_RENDERER_WEBGL)) : String(gl.getParameter(gl.RENDERER));
      }
      case 0x1f02: return this.apiVersion();
      case 0x1f03: return String(gl.getParameter(gl.SHADING_LANGUAGE_VERSION));
      case 0x1f05: return this.extensions().join(' '); // desktop: space-separated list
      default: throw new Error(`Unsupported WebGL2 string parameter: 0x${pname.toString(16)}`);
    }
  }
  stringParameterIndexed(pname, index) {
    if (pname !== 0x1f05) throw new Error(`Unsupported WebGL2 indexed string parameter: 0x${pname.toString(16)}`);
    const names = this.extensions();
    if (index < 0 || index >= names.length) throw new Error(`Extension index out of range: ${index}`);
    return names[index];
  }
  // Desktop token -> the WebGL2 constant to query.
  static INT_ANSWERS = new Map([
    [0x0d33, 'MAX_TEXTURE_SIZE'], [0x84e8, 'MAX_CUBE_MAP_TEXTURE_SIZE'],
    [0x8872, 'MAX_TEXTURE_IMAGE_UNITS'], [0x8b4c, 'MAX_VERTEX_TEXTURE_IMAGE_UNITS'],
    [0x8b4d, 'MAX_COMBINED_TEXTURE_IMAGE_UNITS'], [0x8869, 'MAX_VERTEX_ATTRIBS'],
    [0x8dfb, 'MAX_VERTEX_UNIFORM_VECTORS'], [0x8dfc, 'MAX_VARYING_VECTORS'],
    [0x8dfd, 'MAX_FRAGMENT_UNIFORM_VECTORS'], [0x84d8, 'MAX_SAMPLES'],
    [0x80e9, 'MAX_ELEMENTS_INDICES'], [0x8b4a, 'MAX_VERTEX_UNIFORM_COMPONENTS'],
    [0x8587, 'MAX_VERTEX_OUTPUT_COMPONENTS'], [0x88a4, 'MAX_FRAGMENT_INPUT_COMPONENTS'],
    [0x8a3b, 'MAX_VERTEX_UNIFORM_BLOCKS'], [0x8a3d, 'MAX_FRAGMENT_UNIFORM_BLOCKS'],
    [0x8a3e, 'MAX_COMBINED_UNIFORM_BLOCKS'],
    // Round 80 (front F): pixel-store, blend and stencil state the game reads back (lwjglx
    // PixelStoreState, ImGuiImplGl3.backupGlState, ModelSlotRenderData.renderToImposterCard).
    // WebGL2 answers these from the context itself with the same token numbers.
    [0x0cf2, 'UNPACK_ROW_LENGTH'], [0x0cf3, 'UNPACK_SKIP_ROWS'], [0x0cf4, 'UNPACK_SKIP_PIXELS'],
    [0x0cf5, 'UNPACK_ALIGNMENT'], [0x0d02, 'PACK_ROW_LENGTH'], [0x0d03, 'PACK_SKIP_ROWS'],
    [0x0d04, 'PACK_SKIP_PIXELS'], [0x0d05, 'PACK_ALIGNMENT'],
    [0x80c8, 'BLEND_DST_RGB'], [0x80c9, 'BLEND_SRC_RGB'], [0x80ca, 'BLEND_DST_ALPHA'],
    [0x80cb, 'BLEND_SRC_ALPHA'], [0x8009, 'BLEND_EQUATION_RGB'], [0x883d, 'BLEND_EQUATION_ALPHA'],
    [0x0b92, 'STENCIL_FUNC'],
  ]);
  static BOOL_ANSWERS = new Map([
    [0x0c11, 'SCISSOR_TEST'], [0x0b44, 'CULL_FACE'], [0x0b71, 'DEPTH_TEST'], [0x0b90, 'STENCIL_TEST'],
    [0x0be2, 'BLEND'], [0x8037, 'POLYGON_OFFSET_FILL'], [0x8c89, 'RASTERIZER_DISCARD'],
    [0x809e, 'SAMPLE_ALPHA_TO_COVERAGE'], [0x80a0, 'SAMPLE_COVERAGE'],
  ]);
  intParameters(pname) {
    const gl = this.context;
    if (pname === 0x0ba2) return this.viewportBox; // GL_VIEWPORT: what this bridge last set
    if (pname === 0x0c10) return this.scissorBox; // GL_SCISSOR_BOX (3088; was 0x0c36 before round 80)
    if (pname === 0x821b) return [3]; // GL_MAJOR_VERSION (33307), from the declared apiVersion()
    if (pname === 0x821c) return [3]; // GL_MINOR_VERSION (33308), from the declared apiVersion()
    // Round 80 (front F): state this bridge holds itself (javap -constants on the pinned GL11/GL13C/GL30C).
    if (pname === 0x84e0) return [0x84c0 + (this.activeUnit ?? 0)]; // GL_ACTIVE_TEXTURE (34016)
    if (pname === 0x8ca6) return [this.drawFramebufferHandle ?? 0]; // GL_(DRAW_)FRAMEBUFFER_BINDING (36006)
    if (pname === 0x8caa) return [this.readFramebufferHandle ?? 0]; // GL_READ_FRAMEBUFFER_BINDING (36010)
    if (pname === 0x85b5) return [this.currentVao ?? 0]; // GL_VERTEX_ARRAY_BINDING (34229)
    if (pname === 0x0ba0) return [this.matrixModeToken()]; // GL_MATRIX_MODE (2976)
    if (pname === 0x0ba3) return [this.matrixStack('MODELVIEW').length]; // GL_MODELVIEW_STACK_DEPTH
    if (pname === 0x0ba4) return [this.matrixStack('PROJECTION').length]; // GL_PROJECTION_STACK_DEPTH
    if (pname === 0x0ba5) return [this.matrixStack('TEXTURE').length]; // GL_TEXTURE_STACK_DEPTH
    if (pname === 0x0d36) return [GLBridge.MATRIX_LIMITS.MODELVIEW]; // GL_MAX_MODELVIEW_STACK_DEPTH
    if (pname === 0x0d38) return [GLBridge.MATRIX_LIMITS.PROJECTION]; // GL_MAX_PROJECTION_STACK_DEPTH
    if (pname === 0x0d39) return [GLBridge.MATRIX_LIMITS.TEXTURE]; // GL_MAX_TEXTURE_STACK_DEPTH
    if (pname === 0x821d) return [this.extensions().length]; // GL_NUM_EXTENSIONS
    if (pname === 0x0bb0) return [this.attribStack.length]; // GL_ATTRIB_STACK_DEPTH: our own stack
    if (pname === 0x0bb1) return [this.clientAttribStack.length]; // GL_CLIENT_ATTRIB_STACK_DEPTH
    if (pname === 0x0d35) return [this.attribLimit]; // GL_MAX_ATTRIB_STACK_DEPTH
    if (pname === 0x0d3b) return [this.attribLimit]; // GL_MAX_CLIENT_ATTRIB_STACK_DEPTH
    if (pname === 0x9126) return [0]; // GL_CONTEXT_PROFILE_MASK: WebGL2 has no profile bits
    // Desktop binding queries report GL handles; WebGL2 would answer with a JS object, so these come
    // from the bindings this bridge actually applied (bindBuffer/useProgram/bindTexture).
    if (pname === 0x8894) return [this.boundArrayHandle];   // GL_ARRAY_BUFFER_BINDING (34964)
    if (pname === 0x8895) return [this.boundElementHandle]; // GL_ELEMENT_ARRAY_BUFFER_BINDING (34965)
    if (pname === 0x8069) return [this.textureUnits[this.activeUnit] ?? 0]; // GL_TEXTURE_BINDING_2D (32873)
    if (pname === 0x8b8d) return [this.currentProgram ? this.currentProgram.handle : 0]; // GL_CURRENT_PROGRAM (35725)
    const constant = GLBridge.INT_ANSWERS.get(pname);
    if (constant === undefined || gl[constant] === undefined)
      throw new Error(`Unsupported WebGL2 integer parameter: 0x${pname.toString(16)}`);
    return [Number(gl.getParameter(gl[constant]))];
  }
  floatParameters(pname) {
    if (pname === 0x0b51) return [0, 1]; // GL_DEPTH_RANGE: WebGL2 always uses [0,1]
    // Round 80 (front F): the bridge's own matrix stacks (column-major, as desktop returns them).
    if (pname === 0x0ba6) return [...this.matrixTop('MODELVIEW')];  // GL_MODELVIEW_MATRIX (2982)
    if (pname === 0x0ba7) return [...this.matrixTop('PROJECTION')]; // GL_PROJECTION_MATRIX (2983)
    if (pname === 0x0ba8) return [...this.matrixTop('TEXTURE')];    // GL_TEXTURE_MATRIX (2984)
    throw new Error(`Unsupported WebGL2 float parameter: 0x${pname.toString(16)}`);
  }
  boolParameters(pname) {
    const gl = this.context;
    const constant = GLBridge.BOOL_ANSWERS.get(pname);
    if (constant === undefined || gl[constant] === undefined)
      throw new Error(`Unsupported WebGL2 boolean parameter: 0x${pname.toString(16)}`);
    return [gl.getParameter(gl[constant]) ? 1 : 0];
  }
  // Single-value accessors used by the Java wrappers (glGetInteger / glGetFloat / glGetBoolean).
  intParameter(pname, index) {
    const values = this.intParameters(pname);
    if (index < 0 || index >= values.length)
      throw new Error(`Desktop parameter ${pname.toString(16)} returns ${values.length} ints, index ${index} unavailable`);
    return values[index];
  }
  floatParameter(pname, index) {
    const values = this.floatParameters(pname);
    if (index < 0 || index >= values.length)
      throw new Error(`Desktop parameter ${pname.toString(16)} returns ${values.length} floats, index ${index} unavailable`);
    return values[index];
  }
  boolParameter(pname, index) {
    const values = this.boolParameters(pname);
    if (index < 0 || index >= values.length)
      throw new Error(`Desktop parameter ${pname.toString(16)} returns ${values.length} bools, index ${index} unavailable`);
    return values[index];
  }

  /* --- GL15 vertex buffer objects over WebGL2 (docs/decisions.md) --------------------
   * Desktop VBO/query names have no one-to-one WebGL2 entrypoint (deleteBuffers vs deleteBuffer,
   * glGenBuffers returns handles, mapBuffer returns a pointer), so port.shim.GL15 drives these
   * bridge methods. WebGL2 owns the storage: a GL handle never repeats and 0 means "none".
   */
  genBuffers(count) {
    const handles = new Int32Array(count);
    for (let i = 0; i < count; i++) {
      const handle = this.nextHandle++;
      this.buffers.set(handle, { buffer: this.call('createBuffer') });
      handles[i] = handle;
    }
    return handles;
  }
  deleteBuffers(handles) {
    for (let i = 0; i < handles.length; i++) {
      const entry = this.buffers.get(handles[i]);
      if (entry === undefined) continue; // deleting an unknown handle is a no-op, as in desktop GL
      this.buffers.delete(handles[i]);
      this.call('deleteBuffer', entry.buffer);
    }
  }
  bufferEntry(handle, what) {
    const entry = this.buffers.get(handle);
    if (entry === undefined) throw new Error(`Unknown buffer handle for ${what}: ${handle}`);
    return entry;
  }
  bindBuffer(target, handle) {
    const gl = this.context;
    if (target !== gl.ARRAY_BUFFER && target !== gl.ELEMENT_ARRAY_BUFFER)
      throw new Error(`Unsupported WebGL2 buffer target: 0x${target.toString(16)}`);
    if (handle === 0) {
      this.call('bindBuffer', target, null);
      if (target === gl.ARRAY_BUFFER) { this.boundArrays = null; this.boundArrayHandle = 0; }
      else { this.boundElements = null; this.boundElementHandle = 0; }
      return;
    }
    const buffer = this.bufferEntry(handle, 'bindBuffer').buffer;
    this.call('bindBuffer', target, buffer);
    if (target === gl.ARRAY_BUFFER) { this.boundArrays = buffer; this.boundArrayHandle = handle; }
    else { this.boundElements = buffer; this.boundElementHandle = handle; }
  }
  isBuffer(handle) { return this.buffers.has(handle); }
  bufferData(target, size, data) {
    const gl = this.context;
    if (target !== gl.ARRAY_BUFFER && target !== gl.ELEMENT_ARRAY_BUFFER)
      throw new Error(`Unsupported WebGL2 buffer target: 0x${target.toString(16)}`);
    // The desktop usage hint is dropped: WebGL2 has no storage hints.
    const value = data === null ? size : data;
    this.call('bufferData', target, value, gl.DYNAMIC_DRAW);
    // Round 43 diagnostic ring: what the storage allocation really received and what WebGL2 kept.
    const ring = this.bufferDataLog || (this.bufferDataLog = []);
    ring.push({ target, size, dataType: data === null ? 'null' : (data?.constructor?.name ?? typeof data),
      dataLength: data?.length ?? null, valueKind: typeof value, valueLen: value?.length ?? value,
      err: this.errorsFor(this.callSeq).map((e) => this.webglConstantName(e.code) ?? e.code),
      stored: Number(gl.getBufferParameter(target, gl.BUFFER_SIZE)),
      bound: target === gl.ARRAY_BUFFER ? this.boundArrays : this.boundElements });
    if (ring.length > 24) ring.splice(0, ring.length - 24);
  }
  bufferSubData(target, offset, data) {
    const gl = this.context;
    if (target !== gl.ARRAY_BUFFER && target !== gl.ELEMENT_ARRAY_BUFFER)
      throw new Error(`Unsupported WebGL2 buffer target: 0x${target.toString(16)}`);
    this.call('bufferSubData', target, offset, data);
  }
  getBufferSubData(target, offset, data) {
    const gl = this.context;
    if (target !== gl.ARRAY_BUFFER && target !== gl.ELEMENT_ARRAY_BUFFER)
      throw new Error(`Unsupported WebGL2 buffer target: 0x${target.toString(16)}`);
    this.call('getBufferSubData', target, offset, data);
  }
  bufferParameter(target, pname) {
    const gl = this.context;
    if (target !== gl.ARRAY_BUFFER && target !== gl.ELEMENT_ARRAY_BUFFER)
      throw new Error(`Unsupported WebGL2 buffer target: 0x${target.toString(16)}`);
    if (pname === 0x8764) { // BUFFER_SIZE
      const size = Number(gl.getBufferParameter(target, gl.BUFFER_SIZE));
      // Round 43 diagnostic: ring of the sizes the mapped-buffer path really saw (no rebuild needed).
      const ring = this.bufferParamLog || (this.bufferParamLog = []);
      ring.push({ target, pname, size, bound: target === gl.ARRAY_BUFFER ? this.boundArrays : this.boundElements });
      if (ring.length > 24) ring.splice(0, ring.length - 24);
      return [size];
    }
    if (pname === 0x8765) return [0]; // BUFFER_USAGE: WebGL2 exposes no usage query
    throw new Error(`Unsupported WebGL2 buffer parameter: 0x${pname.toString(16)}`);
  }
  bufferPointerParameter(target, pname) {
    throw new Error(`WebGL2 has no buffer pointer parameter: 0x${pname.toString(16)} (target 0x${target.toString(16)})`);
  }
  genQueries(count) {
    const handles = new Int32Array(count);
    for (let i = 0; i < count; i++) {
      const handle = this.nextHandle++;
      this.queries.set(handle, { query: this.call('createQuery') });
      handles[i] = handle;
    }
    return handles;
  }
  deleteQueries(handles) {
    for (let i = 0; i < handles.length; i++) {
      const entry = this.queries.get(handles[i]);
      if (entry === undefined) continue;
      this.queries.delete(handles[i]);
      this.call('deleteQuery', entry.query);
    }
  }
  queryEntry(handle) {
    const entry = this.queries.get(handle);
    if (entry === undefined) throw new Error(`Unknown query handle: ${handle}`);
    return entry;
  }
  isQuery(handle) { return this.queries.has(handle); }
  beginQuery(target, handle) {
    const entry = this.queryEntry(handle);
    entry.target = target;
    this.call('beginQuery', target, entry.query);
  }
  endQuery(target) { this.call('endQuery', target); }
  queryResult(handle, pname) {
    if (pname !== 0x8866) throw new Error(`Unsupported WebGL2 query object parameter: 0x${pname.toString(16)}`); // QUERY_RESULT
    const entry = this.queryEntry(handle);
    return [this.call('getQueryParameter', entry.query, this.context.QUERY_RESULT) ? 1 : 0];
  }
  queryActive(target, pname) {
    if (pname !== 0x8865) throw new Error(`Unsupported WebGL2 active-query parameter: 0x${pname.toString(16)}`); // QUERY_ACTIVE
    return [this.call('getQueryParameter', target, this.context.QUERY_ACTIVE) ? 1 : 0];
  }

  /* --- Texture lifecycle for zombie.core.textures.TextureID (GL11 natives) ----------------
   * Desktop handles are integers owned by this bridge: WebGL2 objects live in `textures` and a
   * handle is handed out from `nextHandle` (0 stays "no texture", as on desktop). Level-0 uploads
   * come from the native arena as a live Uint8Array view (port.shim.GL11), so no pixel data is
   * invented: what the game wrote into its buffer is what WebGL2 receives.
   */
  genTextures(count) {
    const handles = new Int32Array(count);
    for (let i = 0; i < count; i++) {
      const handle = this.nextHandle++;
      this.textures.set(handle, { texture: this.call('createTexture'), levels: new Map(),
        width: 0, height: 0, internalformat: 0 });
      handles[i] = handle;
    }
    return handles;
  }
  /**
   * glReadPixels cannot convert formats: this backend answers the readback the game performs in
   * TextureID.getData (RGBA + UNSIGNED_BYTE into an arena buffer) through an FBO whose attachment
   * is the requested level. Any other format/type combination raises instead of returning bytes
   * whose layout was guessed.
   */
  getTexImage(target, level, format, type, data) {
    const tname = this.textureTargetName(target);
    const entry = this.textureEntry(this.activeBoundTexture(), 'glGetTexImage');
    const size = entry.levels.get(level);
    if (size === undefined) throw new Error(`Texture ${this.activeBoundTexture()} has no level ${level} for glGetTexImage`);
    if (data === undefined) throw new Error('glGetTexImage needs the arena buffer as a byte view');
    const needed = size.width * size.height * 4;
    if (data.length < needed)
      throw new Error(`glGetTexImage buffer is ${data.length} bytes, level ${level} needs ${needed}`);
    const glFormat = this.textureFormat(format, 'glGetTexImage');
    const glType = this.textureType(type, 'glGetTexImage');
    if (glFormat !== this.context.RGBA || glType !== this.context.UNSIGNED_BYTE)
      throw new Error(`WebGL2 cannot read a texture back as format 0x${format.toString(16)} type 0x${type.toString(16)}`);
    const gl = this.context;
    const previousRead = gl.getParameter(gl.READ_FRAMEBUFFER_BINDING);
    const framebuffer = this.call('createFramebuffer');
    try {
      this.call('bindFramebuffer', gl.FRAMEBUFFER, framebuffer);
      // WebGL2 framebufferTexture2D needs target, attachment, textarget, the WebGLTexture object
      // and the level. Measured in Chrome (diag162, round 58): without the texture object WebGL2
      // raises "5 arguments required, but only 4 present", which the game reports as
      // "FBO not compatible with gfx card at this time". The object is the one this GL handle owns.
      const owned = this.textures.get(this.activeBoundTexture());
      if (!owned) throw new Error(`glGetTexImage: texture ${this.activeBoundTexture()} has no WebGL2 object to attach`);
      this.call('framebufferTexture2D', gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, this.context[tname],
        owned.texture, level);
      if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE)
        throw new Error(`Texture ${this.activeBoundTexture()} level ${level} is not readable`);
      this.flushErrors(); const readSeq = this.callSeq;
      this.call('readPixels', 0, 0, size.width, size.height, gl.RGBA, gl.UNSIGNED_BYTE, data);
      this.checkInternal(readSeq + 1, `glGetTexImage readback for texture ${this.activeBoundTexture()} level ${level}`);
    } finally {
      gl.bindFramebuffer(gl.READ_FRAMEBUFFER, previousRead);
      gl.deleteFramebuffer(framebuffer);
    }
  }
  /** Byte count the caller must hand to getTexImage for this level (RGBA + UNSIGNED_BYTE only). */
  texLevelBytes(level) {
    const entry = this.textureEntry(this.activeBoundTexture(), 'glGetTexImage');
    const size = entry.levels.get(level);
    if (size === undefined) throw new Error(`Texture ${this.activeBoundTexture()} has no level ${level} for glGetTexImage`);
    return size.width * size.height * 4;
  }
  deleteTextures(handles) {
    for (let i = 0; i < handles.length; i++) {
      const entry = this.textures.get(handles[i]);
      if (entry === undefined) continue;
      this.textures.delete(handles[i]);
      if (this.lost) continue; // context gone: there is nothing left to release
      this.call('deleteTexture', entry.texture);
      const bound = this.textureUnits.indexOf(handles[i]);
      if (bound >= 0) this.textureUnits[bound] = null;
    }
  }
  isTexture(handle) { return this.textures.has(handle); }
  /** Texture handle bound to the active unit (0 when nothing is bound, as on desktop). */
  activeBoundTexture() { return this.textureUnits[this.activeUnit] ?? 0; }
  /**
   * Desktop glActiveTexture(GL_TEXTUREn). Only units WebGL2 actually exposes are accepted; the
   * unit index is checked against the implementation's MAX_COMBINED_TEXTURE_IMAGE_UNITS so a game
   * asking for a unit this context does not have raises instead of silently binding unit 0.
   */
  activeTexture(unit) {
    const value = Number(unit);
    if (!Number.isInteger(value) || value < 0x84c0)
      throw new Error(`glActiveTexture: 0x${(unit >>> 0).toString(16)} is not a GL_TEXTUREn token (GL_TEXTURE0 is 0x84c0)`);
    const index = value - 0x84c0;
    if (this.context['TEXTURE' + index] === undefined)
      throw new Error(`glActiveTexture: WebGL2 has no texture unit ${index} (GL_TEXTURE${index})`);
    const max = Number(this.context.getParameter(this.context.MAX_COMBINED_TEXTURE_IMAGE_UNITS));
    if (index >= max)
      throw new Error(`glActiveTexture: unit ${index} exceeds MAX_COMBINED_TEXTURE_IMAGE_UNITS=${max}`);
    this.activeUnit = index;
    while (this.textureUnits.length <= index) this.textureUnits.push(null);
  }
  /**
   * Desktop target -> WebGL2 target. WebGL2 has no 1D textures and no proxy targets, so those
   * raise with the token instead of silently binding a 2D texture (AGENTS.md: no simulated success).
   */
  // Desktop texture targets, values read with `javap -constants` from the pinned jar. Only the three
  // targets WebGL2 owns are mapped: TEXTURE_1D (0x0de0), PROXY_TEXTURE_1D/2D (0x8063/0x8064) and
  // TEXTURE_3D (0x806f) have no WebGL2 target, so textureTargetName raises with the token.
  static TEXTURE_TARGETS = new Map([
    [0x0de1, 'TEXTURE_2D'], [0x8513, 'TEXTURE_CUBE_MAP'], [0x8c1a, 'TEXTURE_2D_ARRAY'],
  ]);
  textureTargetName(target) {
    const name = GLBridge.TEXTURE_TARGETS.get(target);
    if (name === undefined)
      throw new Error(`WebGL2 has no texture target for GL token 0x${target.toString(16)}`);
    if (this.context[name] === undefined)
      throw new Error(`WebGL2 does not expose texture target ${name} (GL token 0x${target.toString(16)})`);
    return name;
  }
  textureEntry(handle, what) {
    const entry = this.textures.get(handle);
    if (entry === undefined)
      throw new Error(`Texture ${handle} does not exist for ${what} (this backend raises where desktop GL would return an error)`);
    return entry;
  }
  /** Real WebGL2 state of the texture being uploaded: what the driver sees at upload time. */
  textureParamSnapshot(entry) {
    if (this.lost) return null;
    const gl = this.context;
    const tex = entry?.texture ?? null;
    if (tex === null) return null;
    const previous = gl.getParameter(gl.TEXTURE_BINDING_2D);
    const out = {};
    try {
      gl.bindTexture(gl.TEXTURE_2D, tex);
      for (const pname of ['TEXTURE_MIN_FILTER', 'TEXTURE_MAG_FILTER', 'TEXTURE_WRAP_S', 'TEXTURE_WRAP_T',
        'TEXTURE_MIN_LOD', 'TEXTURE_MAX_LOD', 'TEXTURE_BASE_LEVEL', 'TEXTURE_MAX_LEVEL']) {
        try { out[pname] = gl.getTexParameter(gl.TEXTURE_2D, gl[pname]); }
        catch (e) { out[pname] = 'unreadable'; }
      }
      // getTexParameter (called raw, outside this.call) can queue INVALID_ENUM. Keep that noise in
      // the snapshot instead of throwing it away, so the WebGL2 queue stays consistent.
      out.readErrors = [];
      if (typeof gl.getError === 'function') {
        for (;;) { const c = gl.getError(); if (c === gl.NO_ERROR || c === undefined) break;
          out.readErrors.push(this.webglConstantName(c) ?? c); }
      }
      // WebGL2 validates the UNPACK_* state against srcData, so the unpack parameters the context
      // really holds are part of the failure evidence.
      for (const pname of ['UNPACK_ALIGNMENT', 'UNPACK_ROW_LENGTH', 'UNPACK_IMAGE_HEIGHT',
        'UNPACK_SKIP_PIXELS', 'UNPACK_SKIP_ROWS', 'UNPACK_SKIP_IMAGES', 'UNPACK_FLIP_Y_WEBGL',
        'UNPACK_COLORSPACE_CONVERSION_WEBGL']) {
        try { out[pname] = gl.getParameter(gl[pname]); } catch (e) { out[pname] = 'unreadable'; }
      }
    } finally {
      gl.bindTexture(gl.TEXTURE_2D, previous);
    }
    out.desktopPixelStorei = Object.fromEntries(
      [...(this.attribRecords?.pixelStorei || new Map()).entries()].map(([k, v]) => [String(k), v[1]]));
    return out;
  }
  bindTexture(target, handle) {
    const name = this.textureTargetName(target);
    if (handle === 0) { this.call('bindTexture', this.context[name], null); this.textureUnits[this.activeUnit] = null; return; }
    const entry = this.textureEntry(handle, 'glBindTexture');
    this.call('bindTexture', this.context[name], entry.texture);
    this.textureUnits[this.activeUnit] = handle;
  }
  /**
   * Desktop token -> WebGL2 constant. Values verified twice: `javap -constants` over the pinned
   * jar and the real WebGL2 constants read from Chrome (see docs/decisions.md). A token or value
   * WebGL2 cannot express raises with its identity rather than being mapped to a similar filter.
   */
  static TEX_PARAMS = new Map([
    [0x2800, 'TEXTURE_MAG_FILTER'], [0x2801, 'TEXTURE_MIN_FILTER'], [0x2802, 'TEXTURE_WRAP_S'],
    [0x2803, 'TEXTURE_WRAP_T'], [0x1004, 'TEXTURE_BORDER_COLOR'], [0x813a, 'TEXTURE_MIN_LOD'],
    [0x813b, 'TEXTURE_MAX_LOD'], [0x813c, 'TEXTURE_BASE_LEVEL'], [0x813d, 'TEXTURE_MAX_LEVEL'],
    [0x884d, 'TEXTURE_COMPARE_FUNC'], [0x884c, 'TEXTURE_COMPARE_MODE'],
    [0x8072, 'TEXTURE_WRAP_R'], // Round 80: GL12 token 32882, same number in WebGL2
  ]);
  texParameteri(target, pname, param) {
    const tname = this.textureTargetName(target);
    const pnameName = GLBridge.TEX_PARAMS.get(pname);
    if (pnameName === undefined)
      throw new Error(`WebGL2 has no texture parameter for GL token 0x${pname.toString(16)} (glTexParameteri)`);
    const constant = this.context[pnameName];
    if (constant === undefined)
      throw new Error(`WebGL2 does not expose texture parameter ${pnameName} (0x${pname.toString(16)})`);
    this.call('texParameteri', this.context[tname], constant, this.textureParamValue(pnameName, param));
  }
  /** Filter/wrap values the game passes are desktop tokens; the WebGL2 constant is the same number. */
  textureParamValue(pnameName, value) {
    if (pnameName === 'TEXTURE_BORDER_COLOR')
      throw new Error(`WebGL2 has no texture border colour (GL token 0x${(0x1004).toString(16)})`);
    if (!GLBridge.TEX_PARAM_TOKENS.has(pnameName)) return value; // LOD/base-level params take plain integers
    const token = GLBridge.TEX_VALUES.get(value);
    if (token === undefined)
      throw new Error(`WebGL2 has no texture parameter value for GL token 0x${value.toString(16)} (${pnameName})`);
    const constant = this.context[token];
    if (constant === undefined)
      throw new Error(`WebGL2 does not expose texture parameter value ${token} (0x${value.toString(16)})`);
    return constant;
  }
  /* Desktop filter/wrap/comparison values in decimal, exactly as `javap -constants` reports them and
   * as Chrome's WebGL2 constants read them: for every entry the numbers are identical, so the value
   * is a lookup key onto the WebGL2 constant name (see textureParamValue). */
  static TEX_VALUES = new Map([
    [9728, 'NEAREST'], [9729, 'LINEAR'], [9984, 'NEAREST_MIPMAP_NEAREST'],
    [9985, 'LINEAR_MIPMAP_NEAREST'], [9986, 'NEAREST_MIPMAP_LINEAR'], [9987, 'LINEAR_MIPMAP_LINEAR'],
    [10497, 'REPEAT'], [33071, 'CLAMP_TO_EDGE'], [33648, 'MIRRORED_REPEAT'],
    [0, 'NONE'], [34894, 'COMPARE_REF_TO_TEXTURE'],
    [512, 'NEVER'], [513, 'LESS'], [514, 'EQUAL'], [515, 'LEQUAL'],
    [516, 'GREATER'], [517, 'NOTEQUAL'], [518, 'GEQUAL'], [519, 'ALWAYS'],
  ]);
  /* Parameters whose value is a token. TEXTURE_MIN_LOD/MAX_LOD/BASE_LEVEL/MAX_LEVEL take plain
   * integers, so they are not listed here and the value is passed through unchanged. */
  static TEX_PARAM_TOKENS = new Set(['TEXTURE_MAG_FILTER', 'TEXTURE_MIN_FILTER', 'TEXTURE_WRAP_S',
    'TEXTURE_WRAP_T', 'TEXTURE_WRAP_R', 'TEXTURE_COMPARE_MODE', 'TEXTURE_COMPARE_FUNC']);
  /* --- Level uploads and readback ----------------------------------------------------------
   * WebGL2 requires a *sized* internal format, while the desktop call passes the unsized
   * GL_RGBA/GL_RGB/GL_DEPTH_COMPONENT plus a type. The pair below is what the desktop driver
   * picks for that combination, so the mapping states the implied storage class rather than
   * inventing one (docs/decisions.md). Any pair not listed raises with both tokens.
   */
  static SIZED_INTERNAL_FORMATS = new Map([
    ['6408:5121', 'RGBA8'], ['6408:5123', 'RGBA16F'], ['6408:5126', 'RGBA32F'],
    ['6407:5121', 'RGB8'], ['6407:5126', 'RGB32F'],
    ['6403:5121', 'R8'], ['6403:5123', 'R16F'], ['6403:5126', 'R32F'],
    ['33319:5121', 'RG8'], ['33319:5126', 'RG32F'],
    ['6402:5123', 'DEPTH_COMPONENT16'], ['6402:5125', 'DEPTH_COMPONENT24'],
    ['6402:5126', 'DEPTH_COMPONENT32F'],
  ]);
  // Formats/types WebGL2 accepts, keyed by the desktop token (numbers verified in Chrome).
  static TEX_FORMATS = new Map([[6408, 'RGBA'], [6407, 'RGB'], [6403, 'RED'], [33319, 'RG'],
    [6402, 'DEPTH_COMPONENT']]);
  static TEX_TYPES = new Map([[5121, 'UNSIGNED_BYTE'], [5123, 'UNSIGNED_SHORT'], [5120, 'BYTE'],
    [5122, 'SHORT'], [5124, 'INT'], [5125, 'UNSIGNED_INT'], [5126, 'FLOAT'], [5131, 'HALF_FLOAT']]);
  /** Desktop (internalformat, type) -> WebGL2 sized internal format constant. */
  sizedInternalFormat(internalformat, type) {
    // An unsized desktop format (GL_RGBA, ...) needs the storage class the driver would pick; a
    // token that is already a WebGL2 sized format is passed through unchanged.
    const name = GLBridge.TEX_FORMATS.has(internalformat)
      ? GLBridge.SIZED_INTERNAL_FORMATS.get(`${internalformat}:${type}`)
      : this.webglConstantName(internalformat);
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no sized internal format for internalformat 0x${internalformat.toString(16)} + type 0x${type.toString(16)}`);
    return this.context[name];
  }
  /**
   * A desktop token that is already a WebGL2 constant (sized internal formats pass through).
   * WebGL2 exposes its constants on the prototype, so the value -> name table is built once over
   * the own properties and the prototype chain of the context.
   */
  static CONSTANTS_BY_VALUE = null;
  webglConstantName(token) {
    if (GLBridge.CONSTANTS_BY_VALUE === null) {
      const table = new Map();
      let object = this.context;
      while (object !== null && object !== Object.prototype) {
        for (const name of Object.getOwnPropertyNames(object)) {
          if (!/^[A-Z0-9_]+$/.test(name)) continue;
          const value = object[name];
          if (typeof value === 'number' && !table.has(value)) table.set(value, name);
        }
        object = Object.getPrototypeOf(object);
      }
      GLBridge.CONSTANTS_BY_VALUE = table;
    }
    return GLBridge.CONSTANTS_BY_VALUE.get(token);
  }
  textureFormat(token, what) {
    const name = GLBridge.TEX_FORMATS.get(token);
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no pixel format for GL token 0x${token.toString(16)} (${what})`);
    return this.context[name];
  }
  textureType(token, what) {
    const name = GLBridge.TEX_TYPES.get(token);
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no pixel type for GL token 0x${token.toString(16)} (${what})`);
    return this.context[name];
  }
  /**
   * glTexImage2D over the bound texture. `data` is a live view of the native arena (or null, which
   * allocates the level without initialising it, exactly as desktop GL does with a null pointer).
   * The dimensions are recorded per level so glGetTexImage can read the level back.
   */
  texImage2D(target, level, internalformat, width, height, border, format, type, data) {
    const tname = this.textureTargetName(target);
    if (border !== 0) throw new Error(`WebGL2 texImage2D requires border 0, got ${border}`);
    if (level < 0) throw new Error(`Negative texture level: ${level}`);
    const entry = this.textureEntry(this.activeBoundTexture(), 'glTexImage2D');
    // The queue is sticky, so remember the drain point *before* this call: getError() below moves
    // it forward and would otherwise hide the calls that actually belong to this upload.
    const drainBefore = this.lastErrorDrainSeq;
    this.flushErrors(); const seqBefore = this.callSeq;
    const glFormat = this.textureFormat(format, 'glTexImage2D');
    const glType = this.textureType(type, 'glTexImage2D');
    const sized = this.sizedInternalFormat(internalformat, type);
    this.call('texImage2D', this.context[tname], level, sized, width, height, 0, glFormat, glType, data ?? null);
    // Only the errors WebGL2 queued for *this* call belong to the upload. Popping the queue here
    // would steal the error the game reads back through glGetError in checkGLErrorThrowTexture.
    const uploadErrs = this.errorsFor(seqBefore + 1);
    const err = uploadErrs.length === 0 ? this.context.NO_ERROR : uploadErrs[0].code;
    if (err !== this.context.NO_ERROR) {
      // The GL error code plus every token is the only way to tell OUT_OF_MEMORY apart from
      // INVALID_OPERATION/INVALID_VALUE; a bare "failed" message cannot be diagnosed.
      const code = this.webglConstantName(err) ?? `0x${err.toString(16)}`;
      this.uploadFailCount = (this.uploadFailCount || 0) + 1;
      const sinceDrain = this.callLog.filter((e) => e.seq > drainBefore).map((e) => e.name);
      this.pendingErrorsForGame = (this.pendingErrorsForGame || 0) + uploadErrs.length;
      this.lastUploadFailure = { op: 'texImage2D', error: code, target: tname, level, width, height,
        internalformat, format, type, sized, data: data === null ? 'null' : 'view',
        boundTexture: this.activeBoundTexture(), unit: this.activeUnit,
        sinceDrain: sinceDrain.slice(-40), lastDrainSeq: drainBefore, uploadSeq: seqBefore + 1,
        errorDrains: this.errorDrains.slice(-6),
        texParams: this.textureParamSnapshot(entry) };
      if (this.firstUploadFailure === undefined) this.firstUploadFailure = this.lastUploadFailure;
      throw new Error(`glTexImage2D failed: ${code} for texture ${this.activeBoundTexture()} level ${level} ` +
        `${width}x${height} internalformat 0x${internalformat.toString(16)} format 0x${format.toString(16)} ` +
        `type 0x${type.toString(16)} data=${data === null ? 'null' : 'arena view'}`);
    }
    entry.levels.set(level, { width, height, format, type });
    entry.internalformat = internalformat;
    entry.width = width; entry.height = height;
  }
  texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, data) {
    const tname = this.textureTargetName(target);
    const entry = this.textureEntry(this.activeBoundTexture(), 'glTexSubImage2D');
    if (entry.levels.get(level) === undefined)
      throw new Error(`Texture ${this.activeBoundTexture()} has no level ${level} to update with glTexSubImage2D`);
    this.call('texSubImage2D', this.context[tname], level, xoffset, yoffset, width, height,
      this.textureFormat(format, 'glTexSubImage2D'), this.textureType(type, 'glTexSubImage2D'), data);
    const subSeq = this.callSeq;
    const subErrs = this.errorsFor(subSeq);
    const err = subErrs.length === 0 ? this.context.NO_ERROR : subErrs[0].code;
    if (err !== this.context.NO_ERROR) {
      const code = this.webglConstantName(err) ?? `0x${err.toString(16)}`;
      this.lastUploadFailure = { op: 'texSubImage2D', error: code, target: tname, level, width, height, format, type };
      throw new Error(`glTexSubImage2D failed: ${code} for texture ${this.activeBoundTexture()} level ${level} ` +
        `${xoffset},${yoffset} ${width}x${height} format 0x${format.toString(16)} type 0x${type.toString(16)}`);
    }
  }

  async loadTexture(url) {
    const location = new URL(url, globalThis.location.href);
    if (location.origin !== globalThis.location.origin) throw new Error('Texture must be local');
    const response = await fetch(location);
    if (!response.ok) throw new Error(`Texture HTTP ${response.status}`);
    const bitmap = await createImageBitmap(await response.blob(), { premultiplyAlpha:'none', colorSpaceConversion:'none' });
    const gl = this.context, previous = gl.getParameter(gl.TEXTURE_BINDING_2D);
    const texture = this.call('createTexture');
    try {
      this.call('bindTexture', gl.TEXTURE_2D, texture);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      this.flushErrors(); const seq = this.callSeq;
      this.call('texImage2D', gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, bitmap);
      this.checkInternal(seq + 1, 'Texture upload');
      return { texture, width:bitmap.width, height:bitmap.height };
    } catch (error) { gl.deleteTexture(texture); throw error; }
    finally { gl.bindTexture(gl.TEXTURE_2D, previous); bitmap.close(); }
  }
  readTexture({ texture, width, height }) {
    const gl = this.context;
    const previousRead = gl.getParameter(gl.READ_FRAMEBUFFER_BINDING);
    const previousDraw = gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING);
    const framebuffer = this.call('createFramebuffer');
    try {
      this.call('bindFramebuffer', gl.FRAMEBUFFER, framebuffer);
      this.call('framebufferTexture2D', gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
      if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw new Error('Texture framebuffer incomplete');
      const pixels = new Uint8Array(width * height * 4);
      this.flushErrors(); const seq = this.callSeq;
      this.call('readPixels', 0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
      this.checkInternal(seq + 1, 'Texture readback');
      return pixels;
    } finally {
      gl.bindFramebuffer(gl.READ_FRAMEBUFFER, previousRead);
      gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, previousDraw);
      gl.deleteFramebuffer(framebuffer);
    }
  }
  shader(type, source) {
    const gl = this.context;
    const shader = this.call('createShader', type);
    this.call('shaderSource', shader, source);
    this.call('compileShader', shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(shader);
      gl.deleteShader(shader);
      throw new Error(`Shader compilation failed: ${log}`);
    }
    return shader;
  }
  desktopProgram(vertexSource, fragmentSource) {
    const gl = this.context;
    const vertex = translateShader(vertexSource, 'vertex');
    const fragment = translateShader(fragmentSource, 'fragment');
    let vs, fs, program;
    try {
      vs = this.shader(gl.VERTEX_SHADER, vertex.source);
      fs = this.shader(gl.FRAGMENT_SHADER, fragment.source);
      program = this.call('createProgram');
      this.call('attachShader', program, vs); this.call('attachShader', program, fs); this.call('linkProgram', program);
      if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(`Program link failed: ${gl.getProgramInfoLog(program)}`);
      const defaults = new Map();
      for (const entry of [...vertex.uniformDefaults,...fragment.uniformDefaults]) {
        if (defaults.has(entry.name) && JSON.stringify(defaults.get(entry.name)) !== JSON.stringify(entry))
          throw new Error(`Conflicting uniform defaults: ${entry.name}`);
        defaults.set(entry.name,entry);
      }
      const previous = gl.getParameter(gl.CURRENT_PROGRAM);
      try {
        this.call('useProgram',program);
        for (const entry of defaults.values()) this.uniformDefault(program,entry);
      } finally { this.call('useProgram',previous); }
      return {program,vertex,fragment};
    } catch (error) { if (program) gl.deleteProgram(program); throw error; }
    finally { if (vs) gl.deleteShader(vs); if (fs) gl.deleteShader(fs); }
  }
  uniformDefault(program, {name,type,value}) {
    const gl = this.context, location = gl.getUniformLocation(program,name);
    if (location === null) return;
    if (type === 'float') this.call('uniform1f',location,value);
    else if (type === 'int' || type === 'bool') this.call('uniform1i',location,value);
    else if (['vec2','vec3','vec4'].includes(type)) this.call(`uniform${type.at(-1)}fv`,location,new Float32Array(value));
    else if (['ivec2','ivec3','ivec4','bvec2','bvec3','bvec4'].includes(type)) this.call(`uniform${type.at(-1)}iv`,location,new Int32Array(value));
    else if (['mat2','mat3','mat4'].includes(type)) this.call(`uniformMatrix${type.at(-1)}fv`,location,false,new Float32Array(value));
    else throw new Error(`Unsupported uniform default type: ${type}`);
  }
  /* --- GL20 shaders, programs and uniforms over WebGL2 (docs/decisions.md) ------------
   * The desktop GL20 natives are replaced by port.shim.GL20, which drives these bridge
   * methods. Handles are monotonic and 0 means "none", as in desktop GL. Desktop shader
   * source is GLSL ES 1.00-style; port/runtime/glsl.mjs translates it to GLSL ES 3.00
   * before compilation, so no source is passed to WebGL2 unverified.
   */
  shaderEntry(handle, what) {
    const entry = this.shaders.get(handle);
    if (entry === undefined) throw new Error(`Unknown shader handle for ${what}: ${handle}`);
    return entry;
  }
  programEntry(handle, what) {
    const entry = this.programs.get(handle);
    if (entry === undefined) throw new Error(`Unknown program handle for ${what}: ${handle}`);
    return entry;
  }
  createShader(type) {
    const gl = this.context;
    if (type !== gl.VERTEX_SHADER && type !== gl.FRAGMENT_SHADER)
      throw new Error(`Unsupported WebGL2 shader type: 0x${type.toString(16)}`);
    const handle = this.nextHandle++;
    this.shaders.set(handle, { shader: this.call('createShader', type), type, source: '', compiled: false, log: '' });
    return handle;
  }
  deleteShader(handle) {
    const entry = this.shaders.get(handle);
    if (entry === undefined) return; // deleting an unknown handle is a no-op, as in desktop GL
    this.invalidateMerged(handle);
    this.shaders.delete(handle);
    this.call('deleteShader', entry.shader);
  }
  isShader(handle) { return this.shaders.has(handle); }
  /**
   * Desktop sources arrive as [pointer,length] pairs read from the arena. Each block is
   * UTF-8-decoded here and translated to GLSL ES 3.00 (glsl.mjs); the concatenated result
   * is what WebGL2 compiles, mirroring how the driver concatenates the strings.
   */
  shaderSource(handle, pointers, lengths, bytes, base) {
    const entry = this.shaderEntry(handle, 'shaderSource');
    const stage = entry.type === this.context.VERTEX_SHADER ? 'vertex' : 'fragment';
    const parts = [];
    for (let i = 0; i < pointers.length; i++) {
      // `bytes` is the view that starts at `base`; the pointers are absolute arena addresses.
      const start = pointers[i] - base;
      parts.push(new TextDecoder('utf-8').decode(bytes.subarray(start, start + lengths[i])));
    }
    this.finishShaderSource(handle, stage, parts);
  }
  /**
   * Variant used by port.shim.GL20: the desktop source blocks live in different arena
   * allocations, so Java copies them into one contiguous buffer and sends that view plus the
   * per-part lengths. Nothing is dropped: the parts are concatenated exactly as the driver does.
   */
  shaderSourceJoined(handle, lengths, bytes) {
    const entry = this.shaderEntry(handle, 'shaderSource');
    const stage = entry.type === this.context.VERTEX_SHADER ? 'vertex' : 'fragment';
    const parts = [];
    let offset = 0;
    for (let i = 0; i < lengths.length; i++) {
      parts.push(new TextDecoder('utf-8').decode(bytes.subarray(offset, offset + lengths[i])));
      offset += lengths[i];
    }
    if (offset !== bytes.length)
      throw new Error(`Shader source buffer is ${bytes.length} bytes, the ${lengths.length} parts cover ${offset}`);
    this.finishShaderSource(handle, stage, parts);
  }
  finishShaderSource(handle, stage, parts) {
    const entry = this.shaderEntry(handle, 'shaderSource');
    entry.raw = parts.join('\n');
    entry.stage = stage;
    const translated = translateShader(entry.raw, stage);
    entry.source = translated.source;
    entry.hasMain = translated.hasMain === true;
    entry.globalInits = translated.globalInits ?? [];
    entry.prototypes = declaredOnlyFunctions(entry.raw);
    // Round 80: built-in-named user functions are renamed (glsl.mjs renameBuiltinOverloads); the
    // compile log of a prototype-only unit names the renamed function.
    for (const n of translated.builtinOverloads ?? []) if (entry.prototypes.has(n)) entry.prototypes.add('pz_user_' + n);
    this.invalidateMerged(handle);
    entry.replacements = translated.replacements;
    entry.uniformDefaults = translated.uniformDefaults;
    entry.alphaTest = translated.alphaTestEmulated === true;
    entry.compiled = false;
    entry.log = '';
    this.call('shaderSource', entry.shader, entry.source);
  }
  compileShader(handle) {
    const entry = this.shaderEntry(handle, 'compileShader');
    if (entry.source === '') throw new Error(`Shader ${handle} compiled without source`);
    this.invalidateMerged(handle);
    if (entry.hasMain) {
      this.call('compileShader', entry.shader);
      entry.compiled = this.call('getShaderParameter', entry.shader, this.context.COMPILE_STATUS) ? true : false;
      entry.log = String(this.call('getShaderInfoLog', entry.shader) ?? '');
    } else {
      // Library unit (no main()): GLSL ES rejects it alone, so validate it for real by compiling it
      // with an empty main() appended. The merged per-stage shader built at link is authoritative.
      const probe = this.call('createShader', entry.type);
      try {
        this.call('shaderSource', probe, `${entry.source}\nvoid main(){\n${entry.globalInits.join('\n')}\n}\n`);
        this.call('compileShader', probe);
        entry.compiled = this.call('getShaderParameter', probe, this.context.COMPILE_STATUS) ? true : false;
        entry.log = String(this.call('getShaderInfoLog', probe) ?? '');
      } finally { this.call('deleteShader', probe); }
    }
    entry.linkDeferred = false;
    if (!entry.compiled && onlyUnresolvedPrototypes(entry.log, entry.prototypes)) {
      // Desktop GL resolves functions that are only prototyped in this unit at link time (from
      // another attached unit of the same stage); GLSL ES reports it at compile. Every other error
      // still fails the compile. The link step compiles the merged stage and reports its log.
      entry.compiled = true; entry.linkDeferred = true; entry.log = '';
    }
    if (this.shaderTrail.length < 400)
      this.shaderTrail.push({ kind: 'compile', handle, type: entry.type, ok: entry.compiled,
        log: entry.log, source: entry.compiled ? '' : entry.source.slice(0, 1200) });
  }
  createProgram() {
    const handle = this.nextHandle++;
    this.programs.set(handle, { program: this.call('createProgram'), shaders: new Set(), log: '',
      linked: false, alphaTest: false });
    return handle;
  }
  deleteProgram(handle) {
    const entry = this.programs.get(handle);
    if (entry === undefined) return;
    this.dropMerged(entry);
    this.programs.delete(handle);
    for (const [location, value] of this.locations) if (value.program === entry.program) this.locations.delete(location);
    this.call('deleteProgram', entry.program);
  }
  isProgram(handle) { return this.programs.has(handle); }
  attachShader(programHandle, shaderHandle) {
    const program = this.programEntry(programHandle, 'attachShader');
    const shader = this.shaderEntry(shaderHandle, 'attachShader');
    program.shaders.add(shaderHandle);
    program.linked = false;
    // A fragment shader carrying the injected alpha-test discard makes the program able to honour
    // the recorded glAlphaFunc state; see useProgram/applyAlphaTest.
    if (shader.alphaTest === true) program.alphaTest = true;
    this.dropMerged(program);
  }
  detachShader(programHandle, shaderHandle) {
    const program = this.programEntry(programHandle, 'detachShader');
    const shader = this.shaderEntry(shaderHandle, 'detachShader');
    void shader;
    program.shaders.delete(shaderHandle);
    program.linked = false;
    this.dropMerged(program);
  }
  /** Drops the WebGL attachments/merged shaders built by the last link of this program. */
  dropMerged(program) {
    for (const gl of program.glAttached ?? []) this.call('detachShader', program.program, gl);
    for (const merged of program.merged ?? []) this.call('deleteShader', merged);
    program.glAttached = []; program.merged = [];
  }
  /** A shader unit changed (new source, recompile, delete): every program using it must re-merge. */
  invalidateMerged(shaderHandle) {
    for (const program of this.programs.values())
      if (program.shaders.has(shaderHandle) && (program.merged?.length ?? 0) > 0) { this.dropMerged(program); program.linked = false; }
  }
  /**
   * Desktop GL may attach several shader objects per stage (PZ loads "x.glsl" for each
   * #include "x.h" as an extra unit). WebGL2 takes one shader per stage, so each stage is
   * rebuilt here: the single unit with main() plus the library units, merged from their
   * desktop sources and translated once (alpha test injected only into this merged fragment).
   * Returns an error string, or '' on success.
   */
  buildStages(program) {
    this.dropMerged(program);
    let alphaTest = false;
    program.uniformDefaults = [];
    for (const type of [this.context.VERTEX_SHADER, this.context.FRAGMENT_SHADER]) {
      const units = [...program.shaders].map(h => [h, this.shaders.get(h)]).filter(([, e]) => e && e.type === type);
      if (units.length === 0) continue;
      const mains = units.filter(([, e]) => e.hasMain);
      const name = type === this.context.VERTEX_SHADER ? 'vertex' : 'fragment';
      if (mains.length !== 1)
        return `${name} stage has ${mains.length} units with main() (handles ${units.map(([h]) => h).join(',')}); exactly one is required`;
      let gl = mains[0][1].shader;
      if (units.length > 1 || mains[0][1].linkDeferred) {
        // One #version on top, then library units (their globals must be declared before main()
        // assigns hoisted initializers), then the unit that owns main().
        const versionRe = /^[ \t]*#[ \t]*version\b.*$/m;
        const version = (mains[0][1].raw.match(versionRe) ?? units.map(([, e]) => e.raw.match(versionRe)).find(Boolean) ?? [''])[0];
        const raw = [version, ...units.filter(([, e]) => !e.hasMain).map(([, e]) => e), mains[0][1]]
          .map(e => typeof e === 'string' ? e : e.raw.replace(versionRe, '')).join('\n');
        let translated;
        try { translated = translateShader(raw, name); }
        catch (error) { return `merged ${name} stage failed to translate: ${error.message}`; }
        gl = this.call('createShader', type);
        program.merged.push(gl);
        this.call('shaderSource', gl, translated.source);
        this.call('compileShader', gl);
        if (!this.call('getShaderParameter', gl, this.context.COMPILE_STATUS))
          return `merged ${name} stage (handles ${units.map(([h]) => h).join(',')}) failed to compile: ${this.call('getShaderInfoLog', gl)}`;
        if (translated.alphaTestEmulated) alphaTest = true;
        program.uniformDefaults.push(...translated.uniformDefaults);
      } else {
        if (mains[0][1].alphaTest) alphaTest = true;
        program.uniformDefaults.push(...(mains[0][1].uniformDefaults ?? []));
      }
      this.call('attachShader', program.program, gl);
      program.glAttached.push(gl);
    }
    program.alphaTest = alphaTest;
    return '';
  }
  linkProgram(handle) {
    const program = this.programEntry(handle, 'linkProgram');
    const stageError = this.buildStages(program);
    program.colorLocation = undefined; // relinking can move (or drop) the pzColor attribute
    if (this.currentProgram === program) this.appliedColor = null;
    if (stageError) { program.linked = false; program.log = stageError; }
    else {
      // Round 79: the desktop conventional vertex array (gl_Vertex, translated to pzVertex by
      // glsl.mjs) gets a fixed generic location so glVertexPointer/GL_VERTEX_ARRAY can feed it.
      this.call('bindAttribLocation', program.program, GLBridge.VERTEX_ARRAY_LOCATION, 'pzVertex');
      // Round 80 (front F): gl_Color (pzColor) gets its own fixed location next to it, so GL_COLOR_ARRAY
      // drives it and the linker never places it on an index the game enabled for its own attribute.
      this.call('bindAttribLocation', program.program, GLBridge.COLOR_ARRAY_LOCATION, 'pzColor');
      program.matrixLocations = undefined; program.matrixSerial = -1; program.immediateLocations = undefined;
      this.call('linkProgram', program.program);
      program.linked = this.call('getProgramParameter', program.program, this.context.LINK_STATUS) ? true : false;
      program.log = String(this.call('getProgramInfoLog', program.program) ?? '');
      if (program.linked) this.applyUniformDefaults(program);
    }
    const sources = [];
    for (const h of program.shaders) {
      const s = this.shaders.get(h);
      sources.push({ handle: h, type: s?.type ?? null, compiled: s?.compiled ?? null, log: s?.log ?? '' });
    }
    if (this.shaderTrail.length < 400)
      this.shaderTrail.push({ kind: 'link', handle, ok: program.linked, log: program.log, shaders: sources });
  }
  /**
   * Round 80: desktop GLSL `uniform T x = v;` sets the uniform at every successful link (and the
   * game relies on it: chunkShader/default declare `useTexture = 1` and DefaultShader only uploads
   * it when it changes). GLSL ES 3.00 forbids the initializer, so glsl.mjs strips it into
   * uniformDefaults and they are uploaded here, keeping the program the game has current.
   * Different initializers for the same uniform in two stages fail the link, as on desktop.
   */
  applyUniformDefaults(program) {
    const defaults = new Map();
    for (const entry of program.uniformDefaults ?? []) {
      const name = webglIdentifier(entry.name);
      if (defaults.has(name) && JSON.stringify(defaults.get(name).value) !== JSON.stringify(entry.value)) {
        program.linked = false; program.log = `uniform ${entry.name} has different initializers in its stages`;
        return;
      }
      defaults.set(name, { ...entry, name });
    }
    if (defaults.size === 0) return;
    const previous = this.context.getParameter(this.context.CURRENT_PROGRAM);
    try {
      this.call('useProgram', program.program);
      for (const entry of defaults.values()) this.uniformDefault(program.program, entry);
    } finally { this.call('useProgram', previous); }
  }
  useProgram(handle) {
    if (handle === 0) { this.call('useProgram', null); this.currentProgram = null; return; }
    const entry = this.programEntry(handle, 'useProgram');
    this.call('useProgram', entry.program);
    this.currentProgram = entry;
    entry.handle = handle; // GL_CURRENT_PROGRAM reports the handle the game used
    // Desktop GL keeps alpha-test state globally; WebGL2 can only reproduce it per program, so the
    // recorded state is uploaded to whatever program just became current.
    this.applyAlphaTest(entry);
    // The current colour is global on desktop; the program decides where gl_Color lives.
    this.applyColor();
  }
  validateProgram(handle) {
    const program = this.programEntry(handle, 'validateProgram');
    this.call('validateProgram', program.program);
  }
  /**
   * Desktop shader/program parameter tokens. WebGL2 deliberately drops several GL enums
   * (INFO_LOG_LENGTH, SHADER_SOURCE_LENGTH, ACTIVE_*_MAX_LENGTH, TRANSFORM_FEEDBACK_BUFFER_COUNT),
   * so those are answered from state this bridge already owns or from WebGL2 introspection
   * (getActiveUniform/getActiveAttrib) rather than from a missing constant. Any other token raises
   * with its own name instead of guessing a value.
   */
  shaderParameter(handle, pname) {
    const gl = this.context, entry = this.shaderEntry(handle, 'getShaderiv');
    switch (pname) {
      case 0x8b80: return [1]; // DELETE_STATUS: the handle is live while it is in this map
      case 0x8b81: return [entry.compiled ? 1 : 0]; // this bridge records the compile result
      case 0x8b4f: return [entry.type]; // SHADER_TYPE: recorded at glCreateShader
      case 0x8b84: return [entry.log.length === 0 ? 0 : entry.log.length + 1]; // desktop log length includes NUL
      case 0x8b88: return [entry.source.length === 0 ? 0 : entry.source.length + 1];
      default: break;
    }
    // Tokens WebGL2 does expose go straight to the real WebGL2 parameter.
    const constant = pname === 0x8b89 ? gl.REFERENCE_COUNT
      : pname === 0x8b30 ? gl.SHADER_COMPILER_OPTIONS : undefined;
    if (constant === undefined) throw new Error(`Unsupported WebGL2 shader parameter: 0x${pname.toString(16)}`);
    const value = gl.getShaderParameter(entry.shader, constant);
    return [typeof value === 'boolean' ? (value ? 1 : 0) : Number(value)];
  }
  programParameter(handle, pname) {
    const gl = this.context, entry = this.programEntry(handle, 'getProgramiv');
    switch (pname) {
      case 0x8b80: return [1]; // DELETE_STATUS: live handle
      case 0x8b82: return [entry.linked ? 1 : 0]; // this bridge records the link result
      case 0x8b83: return [Number(gl.getProgramParameter(entry.program, gl.VALIDATE_STATUS)) ? 1 : 0];
      case 0x8b84: return [entry.log.length === 0 ? 0 : entry.log.length + 1];
      case 0x8b85: return [entry.shaders.size]; // ATTACHED_SHADERS from the attach/detach bookkeeping
      case 0x8b86: return [Number(gl.getProgramParameter(entry.program, gl.ACTIVE_UNIFORMS))];
      case 0x8b89: return [Number(gl.getProgramParameter(entry.program, gl.ACTIVE_ATTRIBUTES))];
      case 0x8b87: return [this.maxNameLength(entry, 'uniform')];
      case 0x8b8a: return [this.maxNameLength(entry, 'attribute')];
      case 0x8b88: return [0]; // no program source on desktop either
      default: break;
    }
    const constant = pname === 0x8e20 ? gl.MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS : undefined;
    if (constant === undefined) throw new Error(`Unsupported WebGL2 program parameter: 0x${pname.toString(16)}`);
    return [Number(gl.getProgramParameter(entry.program, constant))];
  }
  /** Desktop ACTIVE_{UNIFORM,ATTRIBUTE}_MAX_LENGTH: longest introspection name plus the NUL,
   * measured from WebGL2's own getActiveUniform/getActiveAttrib (0 when nothing is active). */
  maxNameLength(entry, kind) {
    const gl = this.context;
    const count = Number(gl.getProgramParameter(entry.program,
      kind === 'uniform' ? gl.ACTIVE_UNIFORMS : gl.ACTIVE_ATTRIBUTES));
    let longest = 0;
    for (let i = 0; i < count; i++) {
      const info = kind === 'uniform' ? gl.getActiveUniform(entry.program, i) : gl.getActiveAttrib(entry.program, i);
      // Desktop names: glsl.mjs renames ES-reserved identifiers (texture -> pz_id_texture).
      if (info && desktopIdentifier(info.name).length > longest) longest = desktopIdentifier(info.name).length;
    }
    return longest === 0 ? 0 : longest + 1;
  }
  shaderInfoLog(handle) {
    const entry = this.shaderEntry(handle, 'getShaderInfoLog');
    if (entry.log === '') entry.log = String(this.call('getShaderInfoLog', entry.shader) ?? '');
    return entry.log;
  }
  programInfoLog(handle) {
    const entry = this.programEntry(handle, 'getProgramInfoLog');
    if (entry.log === '') entry.log = String(this.call('getProgramInfoLog', entry.program) ?? '');
    return entry.log;
  }
  /** glBindAttribLocation: WebGL2 exposes it, so the requested name/index reaches the driver. */
  bindAttribLocation(programHandle, index, name) {
    const program = this.programEntry(programHandle, 'bindAttribLocation');
    if (program.linked) program.linked = false; // desktop: binding after linking is ignored
    this.call('bindAttribLocation', program.program, index, webglIdentifier(name));
  }
  /** glGetAttachedShaders: the desktop units the game attached (WebGL2 only sees the per-stage
   * shaders built at link, see buildStages), in attach order. */
  attachedShaders(programHandle, maxCount) {
    const program = this.programEntry(programHandle, 'getAttachedShaders');
    return [...program.shaders].filter(h => this.shaders.has(h)).slice(0, Math.max(0, maxCount));
  }
  /** Name of an active uniform (kind 1) or attribute (kind 0), straight from WebGL2. */
  activeName(programHandle, index, kind) {
    const program = this.programEntry(programHandle, kind ? 'getActiveUniform' : 'getActiveAttrib');
    const info = kind ? this.call('getActiveUniform', program.program, index)
      : this.call('getActiveAttrib', program.program, index);
    if (info === null) throw new Error(`No active ${kind ? 'uniform' : 'attribute'} ${index} in program ${programHandle}`);
    return desktopIdentifier(String(info.name));
  }
  /** [size, GL type] of an active uniform/attribute; WebGL2's type tokens are the GL ones. */
  activeSizeType(programHandle, index, kind) {
    const program = this.programEntry(programHandle, kind ? 'getActiveUniform' : 'getActiveAttrib');
    const info = kind ? this.call('getActiveUniform', program.program, index)
      : this.call('getActiveAttrib', program.program, index);
    if (info === null) throw new Error(`No active ${kind ? 'uniform' : 'attribute'} ${index} in program ${programHandle}`);
    return [Number(info.size), Number(info.type)];
  }
  /** glGetShaderSource: the source actually handed to WebGL2 (translated GLSL). */
  shaderSourceText(handle) {
    return this.shaderEntry(handle, 'getShaderSource').source;
  }
  /**
   * Desktop uniform/attribute names and pointers become WebGL2 locations. A location handle
   * is monotonic and starts at 1, and a missing uniform returns **-1** exactly like desktop
   * `glGetUniformLocation` (the game guards every setter with `!= -1`, e.g. PuddlesShader;
   * returning 0 there made it call `glUniform1i(0,...)` and raise "Unknown uniform location 0").
   */
  uniformLocation(programHandle, name) {
    const program = this.programEntry(programHandle, 'getUniformLocation');
    // Round 81: desktop names that GLSL ES reserves are renamed by glsl.mjs (texture -> pz_id_texture).
    const location = this.call('getUniformLocation', program.program, webglIdentifier(name));
    // Round 80 diagnostics: which names the game asks for and how many resolve.
    const seen = this.uniformNames || (this.uniformNames = {});
    const key = `${name}:${location === null ? 'missing' : 'found'}`;
    seen[key] = (seen[key] || 0) + 1;
    if (location === null) return -1;
    const handle = this.nextHandle++;
    this.locations.set(handle, { location, program: program.program });
    return handle;
  }
  locationOf(handle, what) {
    const entry = this.locations.get(handle);
    if (entry === undefined) throw new Error(`Unknown uniform location ${handle} for ${what}`);
    return entry.location;
  }
  attribLocation(programHandle, name) {
    const program = this.programEntry(programHandle, 'getAttribLocation');
    const index = this.call('getAttribLocation', program.program, webglIdentifier(name));
    return index === null || index === undefined ? -1 : Number(index);
  }
  /**
   * Desktop uniform setters take count + arena pointer; WebGL2 takes an array. The Float32Array
   * / Int32Array views come from the arena block (same mechanism as port.shim.GL15).
   */
  /**
   * Desktop `n*Uniform*Nfv(location,count,pointer)` natives become WebGL2 array setters. The
   * `name` argument is the desktop name (it identifies the component count and is used in the
   * error message), so one dispatcher covers all of them. WebGL2 takes the whole array, so the
   * count is used to slice the arena view exactly as the desktop call would upload `count`
   * elements.
   */
  uniformfv(name, handle, count, values) {
    const size = Number(name.match(/^uniform(\d)fv$/)[1]);
    const total = count * size;
    if (total > values.length) throw new Error(`${name}: count ${count} needs ${total} floats, the view has ${values.length}`);
    this.call(name, this.locationOf(handle, name), values.subarray(0, total));
  }
  uniform1iv(name, handle, count, values) {
    const size = Number(name.match(/^uniform(\d)iv$/)[1]);
    const total = count * size;
    if (total > values.length) throw new Error(`${name}: count ${count} needs ${total} ints, the view has ${values.length}`);
    this.call(name, this.locationOf(handle, name), values.subarray(0, total));
  }
  uniformMatrix(size, handle, count, transpose, values) {
    const total = count * size * size;
    const name = `uniformMatrix${size}fv`;
    if (total > values.length) throw new Error(`${name}: count ${count} needs ${total} floats, the view has ${values.length}`);
    let data = values.subarray(0, total);
    // Round 80: WebGL2 requires transpose=false. Desktop transpose=true means the floats are row-major;
    // uploading their transpose with false gives the shader the same matrix.
    if (transpose) data = transposeMatrices(data, size, count);
    this.call(name, this.locationOf(handle, name), false, data);
  }
  /* --- Vertex attributes -------------------------------------------------------------
   * glEnable/DisableVertexAttribArray track the attribute index; glVertexAttribPointer needs
   * a bound ARRAY_BUFFER because WebGL2 offsets are buffer-relative (the desktop native's
   * pointer argument is an offset in the bound buffer). An offset that is not a whole
   * multiple of the element stride raises rather than silently misreading the buffer.
   */
  enableAttrib(index) {
    if (index < 0) throw new Error(`Attribute index out of range: ${index}`);
    this.call('enableVertexAttribArray', index);
    const entry = this.attribs.get(index) || {};
    entry.enabled = true;
    this.attribs.set(index, entry);
  }
  disableAttrib(index) {
    if (index < 0) throw new Error(`Attribute index out of range: ${index}`);
    this.call('disableVertexAttribArray', index);
    const entry = this.attribs.get(index) || {};
    entry.enabled = false;
    this.attribs.set(index, entry);
  }
  vertexAttribPointer(index, size, type, normalized, stride, offset) {
    const gl = this.context;
    if (index < 0) throw new Error(`Attribute index out of range: ${index}`);
    const webglType = GLBridge.attribType(type);
    const elementBytes = GLBridge.attribElementBytes(webglType);
    if (offset % elementBytes !== 0)
      throw new Error(`Attribute offset ${offset} is not a multiple of the ${elementBytes}-byte element size`);
    if (this.boundArrays === null)
      throw new Error(`glVertexAttribPointer needs a bound ARRAY_BUFFER (attribute ${index}, offset ${offset})`);
    this.call('vertexAttribPointer', index, size, webglType, !!normalized, stride, offset);
    // Desktop/WebGL2 glVertexAttribPointer does not enable the array; keep the tracked enable bit
    // (applyColor relies on it) and the divisor (round 80, front F).
    const previous = this.attribs.get(index);
    this.attribs.set(index, { enabled: previous?.enabled ?? false, size, type: webglType, normalized: !!normalized, stride, offset,
      ...(previous?.divisor !== undefined ? { divisor: previous.divisor } : {}) });
  }
  // WebGL2 reuses the desktop tokens for these data types, so the mapping is the identity
  // restricted to the subset WebGL2 actually accepts.
  static attribType(type) {
    const map = new Map([[0x1400, 0x1400], [0x1401, 0x1401], [0x1402, 0x1402], [0x1403, 0x1403],
      [0x1404, 0x1404], [0x1405, 0x1405], [0x1406, 0x1406]]);
    const value = map.get(type);
    if (value === undefined) throw new Error(`Unsupported WebGL2 attribute type: 0x${type.toString(16)}`);
    return value;
  }
  static attribElementBytes(webglType) {
    if (webglType === 0x1406 || webglType === 0x1404 || webglType === 0x1405) return 4; // FLOAT, INT, UNSIGNED_INT
    if (webglType === 0x1402 || webglType === 0x1403) return 2; // SHORT, UNSIGNED_SHORT
    return 1; // BYTE, UNSIGNED_BYTE
  }
  /* --- GL30 vertex array objects ------------------------------------------------------ */
  genVertexArrays(count) {
    const handles = new Int32Array(count);
    for (let i = 0; i < count; i++) {
      const handle = this.nextHandle++;
      this.vertexArrays.set(handle, this.call('createVertexArray'));
      handles[i] = handle;
    }
    return handles;
  }
  deleteVertexArrays(handles) {
    for (let i = 0; i < handles.length; i++) {
      const vao = this.vertexArrays.get(handles[i]);
      if (vao === undefined) continue;
      this.vertexArrays.delete(handles[i]);
      this.vaoElements?.delete(handles[i]);
      this.vaoAttribs?.delete(handles[i]);
      if (this.currentVao === handles[i]) { this.currentVao = 0; this.attribs = this.vaoAttribs?.get(0) ?? new Map(); this.boundElements = this.vaoElements?.get(0)?.buffer ?? null; this.boundElementHandle = this.vaoElements?.get(0)?.handle ?? 0; }
      this.call('deleteVertexArray', vao);
    }
  }
  isVertexArray(handle) { return this.vertexArrays.has(handle); }
  bindVertexArray(handle) {
    // Round 79: ELEMENT_ARRAY_BUFFER is VAO state in GL3/WebGL2. Keep the tracked binding (used to
    // tell a buffer offset from a client pointer in drawElements) in step with the bound VAO.
    const elements = this.vaoElements ?? (this.vaoElements = new Map());
    elements.set(this.currentVao ?? 0, { buffer: this.boundElements, handle: this.boundElementHandle });
    if (handle === 0) this.call('bindVertexArray', null);
    else {
      const vao = this.vertexArrays.get(handle);
      if (vao === undefined) throw new Error(`Unknown vertex array handle: ${handle}`);
      this.call('bindVertexArray', vao);
    }
    // Round 80 (front F): enabled arrays, pointers and divisors are VAO state too, so the tracked map
    // follows the bound VAO (applyColor reads it to decide whether gl_Color comes from an array).
    const arrays = this.vaoAttribs ?? (this.vaoAttribs = new Map());
    arrays.set(this.currentVao ?? 0, this.attribs);
    this.attribs = arrays.get(handle) ?? new Map();
    this.currentVao = handle;
    const restored = elements.get(handle) ?? { buffer: null, handle: 0 };
    this.boundElements = restored.buffer; this.boundElementHandle = restored.handle;
  }
  /* --- GL30 framebuffers and renderbuffers -------------------------------------------
   * Desktop framebuffer/renderbuffer targets and attachment tokens are numerically identical to
   * their WebGL2 constants (verified with `javap -constants` on the pinned jar and the constants
   * Chrome exposes), so the token is only a lookup key. Anything WebGL2 lacks raises.
   */
  // 0x0d00 READ_FRAMEBUFFER? no: values from the pinned jar - FRAMEBUFFER 36160, READ 36008,
  // DRAW 36009, RENDERBUFFER 36161; attachments COLOR_ATTACHMENT0 36064, DEPTH 36096,
  // STENCIL 36128, DEPTH_STENCIL 33306.
  static FB_TARGETS = new Map([[36160, 'FRAMEBUFFER'], [36008, 'READ_FRAMEBUFFER'], [36009, 'DRAW_FRAMEBUFFER']]);
  static FB_ATTACHMENTS = new Map([[36064, 'COLOR_ATTACHMENT0'], [36096, 'DEPTH_ATTACHMENT'],
    [36128, 'STENCIL_ATTACHMENT'], [33306, 'DEPTH_STENCIL_ATTACHMENT']]);
  fbTarget(target, what) {
    const name = GLBridge.FB_TARGETS.get(Number(target));
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no framebuffer target for GL token 0x${(target >>> 0).toString(16)} (${what})`);
    return this.context[name];
  }
  fbAttachment(attachment, what) {
    const name = GLBridge.FB_ATTACHMENTS.get(Number(attachment));
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no framebuffer attachment for GL token 0x${(attachment >>> 0).toString(16)} (${what})`);
    return this.context[name];
  }
  genFramebuffers(count) {
    const handles = new Int32Array(count);
    for (let i = 0; i < count; i++) {
      const handle = this.nextHandle++;
      this.framebuffers.set(handle, this.call('createFramebuffer'));
      handles[i] = handle;
    }
    return handles;
  }
  deleteFramebuffers(handles) {
    for (let i = 0; i < handles.length; i++) {
      const fbo = this.framebuffers.get(handles[i]);
      if (fbo === undefined) continue;
      this.framebuffers.delete(handles[i]);
      this.call('deleteFramebuffer', fbo);
      // Deleting a bound framebuffer reverts that binding to 0 (desktop and WebGL2 alike).
      if (this.drawFramebufferHandle === handles[i]) this.drawFramebufferHandle = 0;
      if (this.readFramebufferHandle === handles[i]) this.readFramebufferHandle = 0;
    }
  }
  isFramebuffer(handle) { return this.framebuffers.has(handle); }
  /** Round 80: glGenerateMipmap(GL_TEXTURE_2D) on the texture bound to the active unit. */
  generateMipmap(target) {
    if (target !== 0x0DE1) throw new Error(`glGenerateMipmap: only GL_TEXTURE_2D is supported, got ${target}`);
    if (!this.activeBoundTexture()) throw new Error('glGenerateMipmap: no texture bound to the active unit');
    this.call('generateMipmap', this.context.TEXTURE_2D);
  }
  bindFramebuffer(target, handle) {
    const glTarget = this.fbTarget(target, 'glBindFramebuffer');
    const fbo = handle === 0 ? null : this.framebuffers.get(handle);
    if (fbo === undefined)
      throw new Error(`Framebuffer ${handle} does not exist for glBindFramebuffer`);
    this.call('bindFramebuffer', glTarget, fbo);
    // Round 80 (front F): GL_(DRAW|READ)_FRAMEBUFFER_BINDING report the game's handles.
    if (target !== 36008) this.drawFramebufferHandle = handle;
    if (target !== 36009) this.readFramebufferHandle = handle;
  }
  /**
   * GL30 glBlitFramebuffer -> WebGL2 blitFramebuffer between the bound READ/DRAW framebuffers.
   * Mask and filter are checked first, as desktop GL and WebGL2 both require: a bit other than
   * COLOR/DEPTH/STENCIL queues GL_INVALID_VALUE, a filter other than NEAREST/LINEAR queues
   * GL_INVALID_ENUM, and LINEAR with depth or stencil queues GL_INVALID_OPERATION; in each case
   * nothing is drawn. Everything else (incompatible formats, incomplete framebuffers, overlapping
   * rectangles) is left to WebGL2, whose errors reach glGetError through the emulated queue.
   */
  blitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter) {
    const gl = this.context, bits = Number(mask) >>> 0;
    let token = 0, unknown = bits;
    for (const [bit, name] of GLBridge.CLEAR_BITS) {
      if ((bits & bit) === 0) continue;
      if (gl[name] === undefined) throw new Error(`WebGL2 has no ${name} for glBlitFramebuffer mask 0x${bits.toString(16)}`);
      token |= gl[name]; unknown &= ~bit;
    }
    if (unknown !== 0) { this.recordError('glBlitFramebuffer', 0x0501); return; } // GL_INVALID_VALUE
    const f = Number(filter);
    if (f !== 0x2600 && f !== 0x2601) { this.recordError('glBlitFramebuffer', 0x0500); return; } // GL_INVALID_ENUM
    if (f === 0x2601 && (bits & 0x500) !== 0) { this.recordError('glBlitFramebuffer', 0x0502); return; } // GL_INVALID_OPERATION
    // GL_NEAREST/GL_LINEAR are the same numbers in WebGL2.
    this.call('blitFramebuffer', srcX0 | 0, srcY0 | 0, srcX1 | 0, srcY1 | 0, dstX0 | 0, dstY0 | 0, dstX1 | 0, dstY1 | 0, token, f);
    this.blitCount = (this.blitCount || 0) + 1;
  }
  /** Desktop status tokens are identical to WebGL2's, so the WebGL2 answer is returned unchanged. */
  checkFramebufferStatus(target) { return Number(this.call('checkFramebufferStatus', this.fbTarget(target, 'glCheckFramebufferStatus'))); }
  framebufferTexture2D(target, attachment, textarget, texture, level) {
    // Round 79: texture 0 detaches the attachment (TextureFBO.releaseTexture), as in desktop GL.
    if (Number(texture) === 0) {
      this.call('framebufferTexture2D', this.fbTarget(target, 'glFramebufferTexture2D'),
        this.fbAttachment(attachment, 'glFramebufferTexture2D'), this.context.TEXTURE_2D, null, 0);
      return;
    }
    const entry = this.textureEntry(texture, 'glFramebufferTexture2D');
    const tname = this.textureTargetName(textarget);
    // The WebGLTexture object is a required argument: WebGL2 raises
    // "5 arguments required, but only 4 present" without it (measured in Chrome, diag162).
    this.call('framebufferTexture2D', this.fbTarget(target, 'glFramebufferTexture2D'),
      this.fbAttachment(attachment, 'glFramebufferTexture2D'), this.context[tname],
      entry.texture, Number(level));
  }
  framebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer) {
    if (Number(renderbuffertarget) !== 36161)
      throw new Error(`glFramebufferRenderbuffer needs GL_RENDERBUFFER (36161), got 0x${(renderbuffertarget >>> 0).toString(16)}`);
    const rb = Number(renderbuffer) === 0 ? null : this.renderbuffers.get(renderbuffer); // 0 detaches
    if (rb === undefined)
      throw new Error(`Renderbuffer ${renderbuffer} does not exist for glFramebufferRenderbuffer`);
    this.call('framebufferRenderbuffer', this.fbTarget(target, 'glFramebufferRenderbuffer'),
      this.fbAttachment(attachment, 'glFramebufferRenderbuffer'), this.context.RENDERBUFFER, rb);
  }
  genRenderbuffers(count) {
    const handles = new Int32Array(count);
    for (let i = 0; i < count; i++) {
      const handle = this.nextHandle++;
      this.renderbuffers.set(handle, this.call('createRenderbuffer'));
      handles[i] = handle;
    }
    return handles;
  }
  deleteRenderbuffers(handles) {
    for (let i = 0; i < handles.length; i++) {
      const rb = this.renderbuffers.get(handles[i]);
      if (rb === undefined) continue;
      this.renderbuffers.delete(handles[i]);
      this.call('deleteRenderbuffer', rb);
    }
  }
  isRenderbuffer(handle) { return this.renderbuffers.has(handle); }
  bindRenderbuffer(target, handle) {
    if (Number(target) !== 36161)
      throw new Error(`glBindRenderbuffer needs GL_RENDERBUFFER (36161), got 0x${(target >>> 0).toString(16)}`);
    if (handle === 0) { this.call('bindRenderbuffer', this.context.RENDERBUFFER, null); return; }
    const rb = this.renderbuffers.get(handle);
    if (rb === undefined) throw new Error(`Renderbuffer ${handle} does not exist for glBindRenderbuffer`);
    this.call('bindRenderbuffer', this.context.RENDERBUFFER, rb);
  }
  /** The desktop internalformat tokens the game uses are WebGL2 storage constants; others raise. */
  renderbufferStorage(target, internalformat, width, height) {
    if (Number(target) !== 36161)
      throw new Error(`glRenderbufferStorage needs GL_RENDERBUFFER (36161), got 0x${(target >>> 0).toString(16)}`);
    const name = this.webglConstantName(internalformat);
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no renderbuffer internalformat for GL token 0x${(internalformat >>> 0).toString(16)}`);
    this.call('renderbufferStorage', this.context.RENDERBUFFER, this.context[name], Number(width), Number(height));
  }
  /**
   * Real framebuffer-object probe for the game's FBO gate. The desktop capability bits
   * (GLCapabilities.OpenGL30 / GL_ARB_framebuffer_object) are read from a host symbol table that
   * does not exist here, so the gate asks this instead: a WebGL2 texture is created, filled with
   * real pixels, attached to a WebGL2 framebuffer, given a DEPTH24_STENCIL8 renderbuffer exactly
   * as zombie.core.textures.TextureFBODepth does, and the probe finally clears and reads a pixel
   * back through it. `complete` is true only when WebGL2 itself reported FRAMEBUFFER_COMPLETE and
   * the readback matched the clear colour, so an incomplete FBO is never reported as usable.
   * The result is cached (the gate is asked once per FBO) and every WebGL2 error the probe caused
   * is recorded instead of left in the game's error queue.
   */
  framebufferProbe() {
    if (this.fboProbe !== undefined) return this.fboProbe;
    if (this.lost) throw new Error('WebGL context lost: the framebuffer probe needs a live context');
    const gl = this.context;
    this.flushErrors(); const startSeq = this.callSeq;
    const previousDraw = gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING);
    const previousRead = gl.getParameter(gl.READ_FRAMEBUFFER_BINDING);
    const previousTexture = gl.getParameter(gl.TEXTURE_BINDING_2D);
    // Round 64b: the probe's viewport (0,0,64,64) and clear colour (0.5,0.25,0.75) leaked into the
    // game's default framebuffer: the menu frame came out purple outside a black 640x480 area and
    // gl VIEWPORT still read [0,0,64,64]. Save both here and restore them in finally.
    const previousViewport = Array.from(gl.getParameter(gl.VIEWPORT));
    const previousClear = Array.from(gl.getParameter(gl.COLOR_CLEAR_VALUE));
    const report = { complete: false, status: null, statusName: null, steps: [], rendered: false,
      readback: null, errors: [], size: 64 };
    const texture = this.call('createTexture');
    const framebuffer = this.call('createFramebuffer');
    const renderbuffer = this.call('createRenderbuffer');
    try {
      this.call('bindTexture', gl.TEXTURE_2D, texture);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      this.call('texParameteri', gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      const size = report.size;
      const pixels = new Uint8Array(size * size * 4);
      for (let i = 0; i < pixels.length; i += 4) { pixels[i] = 13; pixels[i + 1] = 210; pixels[i + 2] = 90; pixels[i + 3] = 255; }
      this.call('texImage2D', gl.TEXTURE_2D, 0, gl.RGBA8, size, size, 0, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
      this.call('bindFramebuffer', gl.FRAMEBUFFER, framebuffer);
      this.call('framebufferTexture2D', gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
      const colorStatus = Number(this.call('checkFramebufferStatus', gl.FRAMEBUFFER));
      report.steps.push({ step: 'color attachment', status: this.webglConstantName(colorStatus) ?? String(colorStatus) });
      this.call('bindRenderbuffer', gl.RENDERBUFFER, renderbuffer);
      this.call('renderbufferStorage', gl.RENDERBUFFER, gl.DEPTH24_STENCIL8, size, size);
      this.call('bindRenderbuffer', gl.RENDERBUFFER, null);
      this.call('framebufferRenderbuffer', gl.FRAMEBUFFER, gl.DEPTH_STENCIL_ATTACHMENT, gl.RENDERBUFFER, renderbuffer);
      report.status = Number(this.call('checkFramebufferStatus', gl.FRAMEBUFFER));
      report.statusName = this.webglConstantName(report.status) ?? String(report.status);
      report.steps.push({ step: 'depth-stencil attachment', status: report.statusName });
      if (report.status === gl.FRAMEBUFFER_COMPLETE) {
        // "FRAMEBUFFER_COMPLETE" alone is not enough: the FBO has to actually take draws, which is
        // what TextureFBO.initInternal does next. A clear through it and a readback prove that.
        this.call('viewport', 0, 0, size, size);
        this.call('clearColor', 0.5, 0.25, 0.75, 1);
        this.call('clear', gl.COLOR_BUFFER_BIT);
        const read = new Uint8Array(4);
        this.call('readPixels', 0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, read);
        report.readback = [...read];
        // The clear colour was (0.5, 0.25, 0.75, 1). Reading it back as bytes is a float->byte
        // conversion, so an implementation is allowed to land 1 unit away (Chrome/SwiftShader
        // really does: 0.75 -> 191, not 192). Accepting +/-2 per channel still proves the FBO took
        // the draw - a broken or unbound FBO reads back 0,0,0 or stale pixels - without demanding
        // an exact quantization that WebGL2 does not guarantee.
        const expected = [128, 64, 192, 255];
        report.rendered = expected.every((v, i) => Math.abs(read[i] - v) <= 2);
        report.expected = expected;
        report.maxChannelError = Math.max(...expected.map((v, i) => Math.abs(read[i] - v)));
      }
      report.complete = report.status === gl.FRAMEBUFFER_COMPLETE && report.rendered;
    } finally {
      this.call('bindFramebuffer', gl.FRAMEBUFFER, previousDraw ?? null);
      this.call('bindTexture', gl.TEXTURE_2D, previousTexture ?? null);
      this.call('deleteFramebuffer', framebuffer);
      this.call('deleteRenderbuffer', renderbuffer);
      this.call('deleteTexture', texture);
      this.call('bindFramebuffer', gl.READ_FRAMEBUFFER, previousRead ?? null);
      this.call('viewport', ...previousViewport);
      this.call('clearColor', ...previousClear);
      report.errors = this.internalErrors(startSeq + 1);
      // The game reads its own errors through glGetError; errors caused by this probe must not be
      // blamed on the game's next check. They are kept in report.errors, not silently discarded.
      this.pendingErrors = this.pendingErrors.filter((e) => e.seq <= startSeq);
    }
    this.fboProbe = report;
    this.fboProbeRuns = (this.fboProbeRuns || 0) + 1;
    return report;
  }
  /* --- Draw calls (GL11 natives) ------------------------------------------------------ */
  /** Round 79: the element binding WebGL2 really has for the bound VAO (client-side state query). */
  hasElementBuffer() {
    const bound = this.context.getParameter(0x8895) ?? null; // ELEMENT_ARRAY_BUFFER_BINDING
    if ((bound !== null) !== (this.boundElements !== null)) {
      this.elementTrackingMismatches = (this.elementTrackingMismatches || 0) + 1;
      this.boundElements = bound;
    }
    return bound !== null;
  }
  /** Round 79: GL_QUADS (7) does not exist in WebGL2. Each quad (a,b,c,d) becomes the triangles
   * (a,b,c)(a,c,d), drawn from a scratch element buffer; the caller's bindings are restored.
   * QUAD_STRIP/POLYGON and other legacy modes still raise. */
  static checkMode(mode) {
    if (mode >= 0 && mode <= 6) return;
    if (mode === 7) return;
    throw new Error(`WebGL2 has no primitive mode 0x${mode.toString(16)}`);
  }
  drawQuads(quadIndices, webglType) {
    const gl = this.context;
    const n = Math.floor(quadIndices.length / 4) * 4;
    const tris = new (quadIndices.constructor)(n / 4 * 6);
    for (let q = 0, t = 0; q < n; q += 4) {
      tris[t++] = quadIndices[q]; tris[t++] = quadIndices[q + 1]; tris[t++] = quadIndices[q + 2];
      tris[t++] = quadIndices[q]; tris[t++] = quadIndices[q + 2]; tris[t++] = quadIndices[q + 3];
    }
    const previous = gl.getParameter(0x8895);
    const scratch = this.quadIndexBuffer ?? (this.quadIndexBuffer = gl.createBuffer());
    this.call('bindBuffer', gl.ELEMENT_ARRAY_BUFFER, scratch);
    try {
      this.call('bufferData', gl.ELEMENT_ARRAY_BUFFER, tris, gl.STREAM_DRAW);
      this.call('drawElements', gl.TRIANGLES, tris.length, webglType, 0);
    } finally { this.call('bindBuffer', gl.ELEMENT_ARRAY_BUFFER, previous); }
    this.quadDraws = (this.quadDraws || 0) + 1;
  }
  static indexArray(webglType, buffer, byteOffset, count) {
    const C = webglType === 0x1405 ? Uint32Array : webglType === 0x1403 ? Uint16Array : Uint8Array;
    return new C(buffer, byteOffset, count);
  }
  quadsFromBoundElements(count, webglType, offset) {
    const bytes = webglType === 0x1405 ? 4 : webglType === 0x1403 ? 2 : 1;
    const raw = new Uint8Array(count * bytes);
    this.call('getBufferSubData', this.context.ELEMENT_ARRAY_BUFFER, offset, raw);
    this.drawQuads(GLBridge.indexArray(webglType, raw.buffer, 0, count), webglType);
  }
  drawElements(mode, count, type, indices) {
    GLBridge.checkMode(mode);
    this.applyColor(); this.applyMatrices();
    if (mode === 7 && this.hasElementBuffer()) { this.quadsFromBoundElements(count, GLBridge.indexType(type), indices); return; }
    if (!this.hasElementBuffer())
      throw new Error('glDrawElements needs a bound ELEMENT_ARRAY_BUFFER');
    const webglType = GLBridge.indexType(type);
    const elementBytes = webglType === 0x1405 ? 4 : webglType === 0x1403 ? 2 : 1;
    if (indices % elementBytes !== 0)
      throw new Error(`Index offset ${indices} is not a multiple of the ${elementBytes}-byte index size`);
    // As in desktop GL, `indices` is an offset into the bound ELEMENT_ARRAY_BUFFER.
    this.call('drawElements', mode, count, webglType, indices);
  }
  drawRangeElements(mode, start, end, count, type, indices) {
    GLBridge.checkMode(mode);
    this.applyColor(); this.applyMatrices();
    if (mode === 7 && this.hasElementBuffer()) { this.quadsFromBoundElements(count, GLBridge.indexType(type), indices); return; }
    if (!this.hasElementBuffer())
      throw new Error('glDrawRangeElements needs a bound ELEMENT_ARRAY_BUFFER');
    if (end < start) throw new Error(`glDrawRangeElements end ${end} < start ${start}`);
    const webglType = GLBridge.indexType(type);
    const elementBytes = webglType === 0x1405 ? 4 : webglType === 0x1403 ? 2 : 1;
    if (indices % elementBytes !== 0)
      throw new Error(`Index offset ${indices} is not a multiple of the ${elementBytes}-byte index size`);
    this.call('drawRangeElements', mode, start >>> 0, end >>> 0, count, webglType, indices);
  }
  static VERTEX_ARRAY_LOCATION = 15;
  static COLOR_ARRAY_LOCATION = 14; // Round 80 (front F): pzColor (gl_Color)
  /** Round 79: glEnable/DisableClientState. GL_VERTEX_ARRAY (0x8074) drives the pzVertex attribute
   * (gl_Vertex) at VERTEX_ARRAY_LOCATION; the other conventional arrays are recorded only, and
   * their pointer functions are not mapped (they raise). */
  clientState(array, on) {
    if (![0x8074, 0x8075, 0x8076, 0x8078].includes(array))
      throw new Error(`gl${on ? 'Enable' : 'Disable'}ClientState: unsupported array 0x${array.toString(16)}`);
    (this.clientArrays ?? (this.clientArrays = {}))[array.toString(16)] = !!on;
    if (array === 0x8074) { if (on) this.enableAttrib(GLBridge.VERTEX_ARRAY_LOCATION); else this.disableAttrib(GLBridge.VERTEX_ARRAY_LOCATION); }
    // Round 80 (front F): GL_COLOR_ARRAY drives pzColor (gl_Color); disabling it makes gl_Color the
    // current colour again (applyColor), exactly as desktop GL does.
    if (array === 0x8076) { if (on) this.enableAttrib(GLBridge.COLOR_ARRAY_LOCATION); else { this.disableAttrib(GLBridge.COLOR_ARRAY_LOCATION); this.applyColor(); } }
  }
  /** Round 79: glVertexPointer(size, type, stride, offset in the bound ARRAY_BUFFER) -> pzVertex. */
  vertexPointer(size, type, stride, offset) {
    this.vertexAttribPointer(GLBridge.VERTEX_ARRAY_LOCATION, size, type, false, stride, offset);
  }
  /** Round 79: glTexEnvi has no WebGL2 state; recorded per unit (shaders do the combining). */
  texEnvi(target, pname, param) {
    if (target !== 0x2300 && target !== 0x8500 && target !== 0x8861)
      throw new Error(`glTexEnvi: unsupported target 0x${target.toString(16)}`);
    const unit = this.activeUnit ?? 0;
    const env = this.texEnv ?? (this.texEnv = {});
    (env[unit] ?? (env[unit] = {}))[`${target.toString(16)}:${pname.toString(16)}`] = param;
  }
  /** Round 79: desktop client-memory indices (no ELEMENT_ARRAY_BUFFER bound). The bytes are
   * streamed into a scratch element buffer for this one draw and the binding is restored. */
  drawElementsClient(mode, start, end, count, type, data) {
    if (this.hasElementBuffer()) throw new Error('drawElementsClient with an ELEMENT_ARRAY_BUFFER bound');
    const gl = this.context, webglType = GLBridge.indexType(type);
    this.applyColor(); this.applyMatrices();
    const scratch = this.clientIndexBuffer ?? (this.clientIndexBuffer = gl.createBuffer());
    this.call('bindBuffer', gl.ELEMENT_ARRAY_BUFFER, scratch);
    try {
      if (mode === 7) {
        const bytes = webglType === 0x1405 ? 4 : webglType === 0x1403 ? 2 : 1;
        const copy = data.slice(0, count * bytes);
        this.call('bindBuffer', gl.ELEMENT_ARRAY_BUFFER, null);
        this.drawQuads(GLBridge.indexArray(webglType, copy.buffer, 0, count), webglType);
        return;
      }
      this.call('bufferData', gl.ELEMENT_ARRAY_BUFFER, data, gl.STREAM_DRAW);
      if (start >= 0) this.call('drawRangeElements', mode, start >>> 0, end >>> 0, count, webglType, 0);
      else this.call('drawElements', mode, count, webglType, 0);
    } finally { this.call('bindBuffer', gl.ELEMENT_ARRAY_BUFFER, null); }
    this.clientIndexDraws = (this.clientIndexDraws || 0) + 1;
  }
  drawArrays(mode, first, count) {
    GLBridge.checkMode(mode);
    this.applyColor(); this.applyMatrices();
    if (mode === 7) {
      const idx = new Uint32Array(count);
      for (let i = 0; i < count; i++) idx[i] = first + i;
      this.drawQuads(idx, 0x1405);
      return;
    }
    this.call('drawArrays', mode, first, count);
  }
  static indexType(type) {
    const map = new Map([[0x1401, 0x1401], [0x1403, 0x1403], [0x1405, 0x1405]]);
    const value = map.get(type);
    if (value === undefined) throw new Error(`Unsupported WebGL2 index type: 0x${type.toString(16)}`);
    return value;
  }
  /* --- Round 80 (front F): fixed-function matrix stacks and immediate mode -----------------
   * WebGL2 has no matrix stack and no glBegin/glEnd. Desktop GL keeps one stack per matrix mode
   * (MODELVIEW, PROJECTION and one TEXTURE stack per texture unit) and feeds the top matrices to the
   * gl_ModelViewMatrix family of built-ins. port/runtime/glsl.mjs turns those built-ins into the
   * pzModelViewMatrix / pzProjectionMatrix / pzModelViewProjectionMatrix / pzNormalMatrix uniforms,
   * which applyMatrices uploads before every draw of a program that declares them (the game's own
   * GLSL never does: ModelManager.RenderParticles only pushes/loads/pops around its shaders).
   * Matrices are column-major (the glLoadMatrixf/glGetFloatv layout). Stack limits are the values
   * Mesa reports (MAX_MODELVIEW 32, MAX_PROJECTION 32, MAX_TEXTURE 10); an overflow/underflow queues
   * GL_STACK_OVERFLOW/UNDERFLOW and leaves the stack unchanged, as on desktop (docs/decisions.md).
   */
  static MATRIX_MODES = new Map([[0x1700, 'MODELVIEW'], [0x1701, 'PROJECTION'], [0x1702, 'TEXTURE']]);
  static MATRIX_LIMITS = { MODELVIEW: 32, PROJECTION: 32, TEXTURE: 10 };
  static identity() { const m = new Float64Array(16); m[0] = m[5] = m[10] = m[15] = 1; return m; }
  /** a*b for column-major 4x4 matrices (desktop post-multiplies the current matrix). */
  static multiply(a, b) {
    const out = new Float64Array(16);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
      let sum = 0;
      for (let k = 0; k < 4; k++) sum += a[k * 4 + r] * b[c * 4 + k];
      out[c * 4 + r] = sum;
    }
    return out;
  }
  /** gl_NormalMatrix: inverse transpose of the upper-left 3x3 of the modelview (zeros if singular). */
  static normalMatrix(m) {
    const a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], i = m[10];
    const A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
    const det = a * A + b * B + c * C;
    const out = new Float32Array(9);
    if (det === 0 || !Number.isFinite(det)) return out;
    // Inverse transpose = cofactor matrix / det; stored column-major (out[col*3+row]).
    const cof = [[A, B, C], [-(b * i - c * h), a * i - c * g, -(a * h - b * g)], [b * f - c * e, -(a * f - c * d), a * e - b * d]];
    for (let r = 0; r < 3; r++) for (let col = 0; col < 3; col++) out[col * 3 + r] = cof[r][col] / det;
    return out;
  }
  matrixStack(name) {
    if (name !== 'TEXTURE') return this.matrixStacks[name];
    const unit = this.activeUnit ?? 0, units = this.matrixStacks.TEXTURE;
    return units[unit] ?? (units[unit] = [GLBridge.identity()]);
  }
  matrixTop(name = this.matrixModeName) { const stack = this.matrixStack(name); return stack[stack.length - 1]; }
  matrixModeToken() {
    for (const [token, name] of GLBridge.MATRIX_MODES) if (name === this.matrixModeName) return token;
    return 0x1700;
  }
  matrixState() {
    return { mode: this.matrixModeName, modelview: this.matrixStack('MODELVIEW').length,
      projection: this.matrixStack('PROJECTION').length, texture: this.matrixStack('TEXTURE').length,
      serial: this.matrixSerial, ...this.matrixStats, immediateDraws: this.immediateDraws || 0 };
  }
  /** Desktop forbids these calls between glBegin and glEnd: GL_INVALID_OPERATION, call ignored. */
  insideBegin(what) {
    if (this.immediate === null) return false;
    this.recordError(what, 0x0502);
    return true;
  }
  /** glMatrixMode. GL_COLOR needs ARB_imaging, which this context does not advertise, so any other
   * token is GL_INVALID_ENUM exactly as on such a desktop context. */
  matrixMode(mode) {
    if (this.insideBegin('glMatrixMode')) return;
    const name = GLBridge.MATRIX_MODES.get(mode);
    if (name === undefined) { this.recordError('glMatrixMode', 0x0500); return; }
    this.matrixModeName = name;
  }
  setMatrixTop(m) {
    const stack = this.matrixStack(this.matrixModeName);
    stack[stack.length - 1] = m;
    this.matrixSerial++;
  }
  pushMatrix() {
    if (this.insideBegin('glPushMatrix')) return;
    const stack = this.matrixStack(this.matrixModeName);
    if (stack.length >= GLBridge.MATRIX_LIMITS[this.matrixModeName]) {
      this.matrixStats.overflow++;
      this.recordError('glPushMatrix', GLBridge.GL_STACK_OVERFLOW);
      return;
    }
    stack.push(Float64Array.from(stack[stack.length - 1]));
  }
  popMatrix() {
    if (this.insideBegin('glPopMatrix')) return;
    const stack = this.matrixStack(this.matrixModeName);
    if (stack.length <= 1) {
      this.matrixStats.underflow++;
      this.recordError('glPopMatrix', GLBridge.GL_STACK_UNDERFLOW);
      return;
    }
    stack.pop();
    this.matrixSerial++;
  }
  loadIdentity() { if (!this.insideBegin('glLoadIdentity')) this.setMatrixTop(GLBridge.identity()); }
  /** glLoadMatrixf / glMultMatrixf with 16 column-major values. */
  loadMatrix(values, multiply = false) {
    const what = multiply ? 'glMultMatrixf' : 'glLoadMatrixf';
    if (this.insideBegin(what)) return;
    if (values.length < 16) throw new Error(`${what} needs 16 floats, got ${values.length}`);
    const m = new Float64Array(16);
    for (let k = 0; k < 16; k++) m[k] = Number(values[k]);
    this.setMatrixTop(multiply ? GLBridge.multiply(this.matrixTop(), m) : m);
  }
  multMatrix(values) { this.loadMatrix(values, true); }
  /** The 16 little-endian floats behind the native pointer of nglLoadMatrixf / nglMultMatrixf. */
  loadMatrixBytes(bytes, multiply) {
    if (bytes.length < 64) throw new Error(`glLoadMatrixf/glMultMatrixf need 64 bytes, got ${bytes.length}`);
    const view = new DataView(bytes.buffer, bytes.byteOffset, 64), values = new Float64Array(16);
    for (let k = 0; k < 16; k++) values[k] = view.getFloat32(k * 4, true);
    this.loadMatrix(values, !!multiply);
  }
  ortho(l, r, b, t, n, f) {
    if (this.insideBegin('glOrtho')) return;
    if (l === r || b === t || n === f) { this.recordError('glOrtho', 0x0501); return; } // GL_INVALID_VALUE
    const m = GLBridge.identity();
    m[0] = 2 / (r - l); m[5] = 2 / (t - b); m[10] = -2 / (f - n);
    m[12] = -(r + l) / (r - l); m[13] = -(t + b) / (t - b); m[14] = -(f + n) / (f - n);
    this.setMatrixTop(GLBridge.multiply(this.matrixTop(), m));
  }
  translate(x, y, z) {
    if (this.insideBegin('glTranslate')) return;
    const m = GLBridge.identity(); m[12] = x; m[13] = y; m[14] = z;
    this.setMatrixTop(GLBridge.multiply(this.matrixTop(), m));
  }
  scale(x, y, z) {
    if (this.insideBegin('glScale')) return;
    const m = GLBridge.identity(); m[0] = x; m[5] = y; m[10] = z;
    this.setMatrixTop(GLBridge.multiply(this.matrixTop(), m));
  }
  /** glRotate(angle in degrees, axis). The spec leaves a zero axis undefined; like Mesa
   * (_math_matrix_rotate) the matrix is then left unchanged. */
  rotate(angle, x, y, z) {
    if (this.insideBegin('glRotate')) return;
    const len = Math.hypot(x, y, z);
    if (len <= 1e-4) return;
    x /= len; y /= len; z /= len;
    const rad = angle * Math.PI / 180, c = Math.cos(rad), s = Math.sin(rad), k = 1 - c;
    const m = GLBridge.identity();
    m[0] = x * x * k + c;     m[4] = x * y * k - z * s; m[8] = x * z * k + y * s;
    m[1] = y * x * k + z * s; m[5] = y * y * k + c;     m[9] = y * z * k - x * s;
    m[2] = x * z * k - y * s; m[6] = y * z * k + x * s; m[10] = z * z * k + c;
    this.setMatrixTop(GLBridge.multiply(this.matrixTop(), m));
  }
  /** Upload the top matrices to the current program's translated built-ins, once per change. */
  applyMatrices() {
    const entry = this.currentProgram;
    if (entry === null || !entry.linked) return;
    if (entry.matrixLocations === undefined) {
      const loc = (name) => this.call('getUniformLocation', entry.program, name) ?? null;
      entry.matrixLocations = { mv: loc('pzModelViewMatrix'), p: loc('pzProjectionMatrix'),
        mvp: loc('pzModelViewProjectionMatrix'), normal: loc('pzNormalMatrix') };
      entry.matrixSerial = -1;
    }
    const L = entry.matrixLocations;
    if (L.mv === null && L.p === null && L.mvp === null && L.normal === null) return;
    if (entry.matrixSerial === this.matrixSerial) return;
    const mv = this.matrixTop('MODELVIEW'), p = this.matrixTop('PROJECTION');
    if (L.mv !== null) this.call('uniformMatrix4fv', L.mv, false, new Float32Array(mv));
    if (L.p !== null) this.call('uniformMatrix4fv', L.p, false, new Float32Array(p));
    if (L.mvp !== null) this.call('uniformMatrix4fv', L.mvp, false, new Float32Array(GLBridge.multiply(p, mv)));
    if (L.normal !== null) this.call('uniformMatrix3fv', L.normal, false, GLBridge.normalMatrix(mv));
    entry.matrixSerial = this.matrixSerial;
    this.matrixStats.uploads++;
  }
  /* Immediate mode. Every glVertex captures the current colour, texture coordinate (unit 0) and
   * normal, as desktop does; glEnd streams the batch into a scratch VBO/VAO and draws it with the
   * current program: pzVertex (gl_Vertex; generic attribute 0 when the program has none, which
   * glVertex aliases in the compatibility profile), pzColor, pzMultiTexCoord0 and pzNormal.
   * GL_QUADS become triangles (a,b,c)(a,c,d), GL_QUAD_STRIP the identical TRIANGLE_STRIP and
   * GL_POLYGON a TRIANGLE_FAN. WebGL2 has no fixed-function pipeline, so glEnd with program 0
   * raises instead of drawing something different from desktop. */
  static IMMEDIATE_STRIDE = 15; // x y z w | r g b a | s t r q | nx ny nz
  begin(mode) {
    if (this.immediate !== null) { this.recordError('glBegin', 0x0502); return; } // GL_INVALID_OPERATION
    if (!Number.isInteger(mode) || mode < 0 || mode > 9) { this.recordError('glBegin', 0x0500); return; } // GL_INVALID_ENUM
    this.immediate = { mode, data: [] };
  }
  vertex(x, y, z = 0, w = 1) {
    const batch = this.immediate;
    if (batch === null) throw new Error('glVertex outside glBegin/glEnd: desktop GL leaves the result undefined, this backend refuses it');
    batch.data.push(Number(x), Number(y), Number(z), Number(w), ...this.currentColor, ...this.currentTexCoord, ...this.currentNormal);
  }
  texCoord(s, t) { this.currentTexCoord = [Number(s), Number(t), 0, 1]; }
  normal(x, y, z) { this.currentNormal = [Number(x), Number(y), Number(z)]; }
  immediateLocations(entry) {
    if (entry.immediateLocations !== undefined) return entry.immediateLocations; // reset by linkProgram
    const at = (name) => { const v = this.call('getAttribLocation', entry.program, name); return Number.isInteger(v) ? v : -1; };
    const vertex = at('pzVertex');
    entry.immediateLocations = { vertex: vertex >= 0 ? vertex : 0, color: this.colorLocation(entry),
      texCoord: at('pzMultiTexCoord0'), normal: at('pzNormal') };
    return entry.immediateLocations;
  }
  end() {
    const batch = this.immediate;
    if (batch === null) { this.recordError('glEnd', 0x0502); return; } // GL_INVALID_OPERATION
    this.immediate = null;
    const entry = this.currentProgram;
    if (entry === null)
      throw new Error('glEnd: WebGL2 has no fixed-function pipeline; immediate mode needs a current program (glUseProgram is 0)');
    const S = GLBridge.IMMEDIATE_STRIDE;
    let data = batch.data, count = data.length / S, mode = batch.mode;
    if (mode === 7) { // GL_QUADS -> GL_TRIANGLES
      const quads = Math.floor(count / 4), out = [];
      for (let q = 0; q < quads; q++) for (const v of [0, 1, 2, 0, 2, 3]) {
        const base = (q * 4 + v) * S;
        for (let k = 0; k < S; k++) out.push(data[base + k]);
      }
      data = out; count = quads * 6; mode = 4;
    } else if (mode === 8) { mode = 5; count -= count % 2; } // GL_QUAD_STRIP -> GL_TRIANGLE_STRIP
    else if (mode === 9) mode = 6; // GL_POLYGON (convex, as desktop requires) -> GL_TRIANGLE_FAN
    if (count === 0) return; // desktop draws nothing for an incomplete primitive
    const gl = this.context, L = this.immediateLocations(entry);
    const vao = this.immediateVao ?? (this.immediateVao = this.call('createVertexArray'));
    const buffer = this.immediateBuffer ?? (this.immediateBuffer = this.call('createBuffer'));
    const previousVao = (this.currentVao ?? 0) === 0 ? null : (this.vertexArrays.get(this.currentVao) ?? null);
    const previousArray = this.boundArrays;
    this.applyMatrices();
    this.call('bindVertexArray', vao);
    try {
      this.call('bindBuffer', gl.ARRAY_BUFFER, buffer);
      this.call('bufferData', gl.ARRAY_BUFFER, new Float32Array(data.slice(0, count * S)), gl.STREAM_DRAW);
      const wanted = new Map();
      for (const [loc, size, offset] of [[L.vertex, 4, 0], [L.color, 4, 4], [L.texCoord, 4, 8], [L.normal, 3, 12]])
        if (loc >= 0 && !wanted.has(loc)) wanted.set(loc, [size, offset]);
      // The scratch VAO keeps arrays from earlier batches: switch off the ones this program does not take.
      for (const loc of this.immediateEnabled ?? []) if (!wanted.has(loc)) this.call('disableVertexAttribArray', loc);
      for (const [loc, [size, offset]] of wanted) {
        this.call('enableVertexAttribArray', loc);
        this.call('vertexAttribPointer', loc, size, gl.FLOAT, false, S * 4, offset * 4);
      }
      this.immediateEnabled = new Set(wanted.keys());
      this.call('drawArrays', mode, 0, count);
    } finally {
      this.call('bindVertexArray', previousVao);
      this.call('bindBuffer', gl.ARRAY_BUFFER, previousArray);
    }
    this.immediateDraws = (this.immediateDraws || 0) + 1;
  }
  /* --- Round 80 (front F): instancing (WebGL2 core), glIsEnabled, framebuffer readback ------ */
  vertexAttribDivisor(index, divisor) {
    if (index < 0) throw new Error(`Attribute index out of range: ${index}`);
    this.call('vertexAttribDivisor', index, divisor >>> 0);
    const entry = this.attribs.get(index) || {};
    entry.divisor = divisor >>> 0;
    this.attribs.set(index, entry);
  }
  static checkInstancedMode(mode, what) {
    if (Number.isInteger(mode) && mode >= 0 && mode <= 6) return;
    throw new Error(`${what}: WebGL2 has no instanced primitive mode 0x${Number(mode).toString(16)}`);
  }
  drawArraysInstanced(mode, first, count, primcount) {
    GLBridge.checkInstancedMode(mode, 'glDrawArraysInstanced');
    this.applyColor(); this.applyMatrices();
    this.call('drawArraysInstanced', mode, first, count, primcount);
  }
  drawElementsInstanced(mode, count, type, indices, primcount) {
    GLBridge.checkInstancedMode(mode, 'glDrawElementsInstanced');
    if (!this.hasElementBuffer()) throw new Error('glDrawElementsInstanced needs a bound ELEMENT_ARRAY_BUFFER');
    const webglType = GLBridge.indexType(type);
    const elementBytes = webglType === 0x1405 ? 4 : webglType === 0x1403 ? 2 : 1;
    if (indices % elementBytes !== 0)
      throw new Error(`Index offset ${indices} is not a multiple of the ${elementBytes}-byte index size`);
    this.applyColor(); this.applyMatrices();
    this.call('drawElementsInstanced', mode, count, webglType, indices, primcount);
  }
  // Desktop starts with GL_DITHER and GL_MULTISAMPLE enabled; every other recorded cap starts disabled.
  static FIXED_FUNCTION_DEFAULTS = new Map([[0x0bd0, true], [0x809d, true]]);
  /** glIsEnabled: WebGL2 capabilities from the context, desktop-only ones from the recorded state. */
  isEnabled(cap) {
    if (GLBridge.FIXED_FUNCTION_CAPS.has(cap))
      return this.fixedFunctionCaps.get(cap)?.enabled ?? (GLBridge.FIXED_FUNCTION_DEFAULTS.get(cap) ?? false);
    if ([0x8074, 0x8075, 0x8076, 0x8078].includes(cap)) return !!this.clientArrays?.[cap.toString(16)];
    const name = GLBridge.CAPS.get(cap);
    if (name === undefined || this.context[name] === undefined)
      throw new Error(`WebGL2 has no capability for GL token 0x${Number(cap).toString(16)} (glIsEnabled)`);
    return !!this.call('isEnabled', this.context[name]);
  }
  /** glReadBuffer: WebGL2 takes BACK (default framebuffer), NONE and COLOR_ATTACHMENTi with the
   * desktop numbers; GL_FRONT, the stereo and aux buffers have no WebGL2 counterpart and raise. */
  readBuffer(mode) {
    if (!(mode === 0x0405 || mode === 0 || (mode >= 0x8ce0 && mode <= 0x8cef)))
      throw new Error(`WebGL2 has no read buffer for GL token 0x${Number(mode).toString(16)} (glReadBuffer)`);
    this.call('readBuffer', mode);
  }
  /** readPixels format/type pairs WebGL2 guarantees (RGBA/UNSIGNED_BYTE) plus the context's own
   * IMPLEMENTATION_COLOR_READ_FORMAT/TYPE; any other desktop pair raises with both tokens. */
  readFormat(format, type) {
    const gl = this.context;
    const channels = new Map([[0x1908, 4], [0x1907, 3], [0x8227, 2], [0x1903, 1], [0x1906, 1]]).get(format);
    const element = new Map([[0x1401, 1], [0x1400, 1], [0x1403, 2], [0x1402, 2], [0x1405, 4], [0x1404, 4], [0x1406, 4], [0x140b, 2]]).get(type);
    const guaranteed = format === 0x1908 && type === 0x1401;
    const implementation = channels !== undefined && element !== undefined
      && format === Number(gl.getParameter(0x8b9b)) && type === Number(gl.getParameter(0x8b9a));
    if (!guaranteed && !implementation)
      throw new Error(`WebGL2 cannot read pixels as format 0x${Number(format).toString(16)} type 0x${Number(type).toString(16)} (glReadPixels)`);
    return { channels, element };
  }
  /** Bytes desktop glReadPixels writes for this block under the real PACK_* state. */
  readPixelsBytes(width, height, format, type) {
    const { channels, element } = this.readFormat(format, type);
    if (width < 0 || height < 0) throw new Error(`glReadPixels: negative size ${width}x${height}`);
    if (width === 0 || height === 0) return 0;
    const gl = this.context, p = (pname) => Number(gl.getParameter(pname) ?? 0);
    const alignment = p(0x0d05) || 4, rowLength = p(0x0d02) || width; // PACK_ALIGNMENT, PACK_ROW_LENGTH
    const pixel = channels * element, rowBytes = Math.ceil(rowLength * pixel / alignment) * alignment;
    return p(0x0d03) * rowBytes + p(0x0d04) * pixel + (height - 1) * rowBytes + width * pixel; // SKIP_ROWS/PIXELS
  }
  readPixels(x, y, width, height, format, type, data) {
    const { element } = this.readFormat(format, type);
    const Ctor = { 0x1401: Uint8Array, 0x1400: Int8Array, 0x1403: Uint16Array, 0x1402: Int16Array, 0x140b: Uint16Array,
      0x1405: Uint32Array, 0x1404: Int32Array, 0x1406: Float32Array }[type];
    if (element > 1 && data.byteOffset % element !== 0)
      throw new Error(`glReadPixels: destination ${data.byteOffset} is not aligned to ${element} bytes`);
    const view = element === 1 && Ctor === Uint8Array ? data : new Ctor(data.buffer, data.byteOffset, Math.floor(data.byteLength / element));
    this.call('readPixels', x, y, width, height, format, type, view);
  }
  drawProbe() {
    const gl = this.context;
    const vertex = this.shader(gl.VERTEX_SHADER, `#version 300 es
      const vec2 p[3]=vec2[3](vec2(-.65,-.55),vec2(.65,-.55),vec2(0.,.65));
      void main(){gl_Position=vec4(p[gl_VertexID],0.,1.);}`);
    const fragment = this.shader(gl.FRAGMENT_SHADER, `#version 300 es
      precision highp float; out vec4 color;
      void main(){color=vec4(0.25,0.85,0.65,1.);}`);
    const program = this.call('createProgram');
    this.call('attachShader', program, vertex); this.call('attachShader', program, fragment);
    this.call('linkProgram', program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
    const vao = this.call('createVertexArray');
    this.call('bindVertexArray', vao); this.call('useProgram', program);
    this.flushErrors(); const probeSeq = this.callSeq;
    this.call('drawArrays', gl.TRIANGLES, 0, 3);
    const pixel = new Uint8Array(4);
    this.call('readPixels', gl.drawingBufferWidth >> 1, gl.drawingBufferHeight >> 1, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel);
    this.call('bindVertexArray', null); this.call('useProgram', null);
    gl.deleteVertexArray(vao); gl.deleteProgram(program); gl.deleteShader(vertex); gl.deleteShader(fragment);
    this.checkInternal(probeSeq, 'WebGL probe');
    return [...pixel];
  }
}
