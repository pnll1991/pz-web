import { translateShader } from './glsl.mjs';

/**
 * GL entrypoints this backend implements beyond raw WebGL2. Keep in sync with the mappings in
 * tools/glgen/generate.py and the wrappers in port.shim.GL11: an address is only handed out for
 * a name that can actually be called.
 */
const PORT_PROVIDED = new Set([
  'glClearColor', 'glClear', 'glViewport', 'glGetError', 'glEnable', 'glDisable', 'glScissor',
  'glDepthMask', 'glDepthFunc', 'glCullFace', 'glFrontFace', 'glPixelStorei', 'glLineWidth',
  'glBlendFunc', 'glBlendFuncSeparate', 'glClearDepthf', 'glStencilMask', 'glClearStencil',
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
  'glBindVertexArray', 'glDrawArrays', 'glDrawElements',
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
]);

/** WebGL2 context and object handles; unsupported entrypoints must throw. */
export class GLBridge {
  constructor(canvas) {
    const context = canvas.getContext('webgl2', { alpha: false, antialias: false, preserveDrawingBuffer: true });
    if (!context) throw new Error('WebGL 2 is required');
    this.context = context;
    this.calls = Object.create(null);
    this.viewportBox = [0, 0, canvas.width, canvas.height];
    this.scissorBox = [0, 0, canvas.width, canvas.height];
    this.buffers = new Map(); // GL handle -> {buffer,size,target}
    this.queries = new Map(); // GL handle -> {query,target}
    this.shaders = new Map(); // GL handle -> {shader,type,source,compiled,log}
    this.programs = new Map(); // GL handle -> {program,shaders:Set,log,linked,used}
    this.attribs = new Map(); // attribute index -> {enabled,size,...} recorded by glVertexAttribPointer
    this.locations = new Map(); // location handle -> WebGLUniformLocation (0 means "no location")
    this.boundElements = null; // last ELEMENT_ARRAY_BUFFER handle (drawElements needs it)
    this.vertexArrays = new Map(); // GL handle -> WebGLVertexArrayObject (GL30 natives)
    this.boundArrays = null; // last ARRAY_BUFFER handle bound by bindBuffer (needed by attrib pointers)
    this.nextHandle = 1; // 0 is reserved for "none", as in desktop GL
    this.lost = false;
    this.capabilities = new Map(); // desktop capability token -> {enabled, webgl} actually applied
    canvas.addEventListener('webglcontextlost', event => { event.preventDefault(); this.lost = true; });
    canvas.addEventListener('webglcontextrestored', () => {
      // Resource reconstruction is not implemented yet: do not continue with invalid handles.
      this.lost = true;
    });
  }
  call(name, ...args) {
    if (this.lost) throw new Error('WebGL context lost: reload required');
    this.calls[name] = (this.calls[name] || 0) + 1;
    if (name === 'viewport') this.viewportBox = args.slice(0, 4).map(Number);
    if (name === 'scissor') this.scissorBox = args.slice(0, 4).map(Number);
    const method = this.context[name];
    if (typeof method !== 'function') throw new Error(`Unsupported WebGL function: ${name}`);
    return method.apply(this.context, args);
  }
  clearColor(...args) { this.call('clearColor', ...args); }
  clear(...args) { this.call('clear', ...args); }
  viewport(...args) { this.call('viewport', ...args); }
  getError() { return this.call('getError'); }
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
      return;
    }
    if (constant === undefined)
      throw new Error(`WebGL2 has no capability for GL token 0x${cap.toString(16)} (${on ? 'glEnable' : 'glDisable'})`);
    this.call(on ? 'enable' : 'disable', constant);
    this.capabilities.set(cap, { enabled: on, webgl: name });
  }
  enable(cap) { this.capability(cap, true); }
  disable(cap) { this.capability(cap, false); }
  /** State the Java/runtime report exposes: applied WebGL2 caps plus recorded desktop-only caps. */
  capabilityState() {
    const applied = {};
    for (const [token, entry] of this.capabilities) applied[token.toString(16)] = entry;
    const recorded = {};
    for (const [token, entry] of this.fixedFunctionCaps) recorded[token.toString(16)] = entry;
    return { applied, fixedFunctionRecorded: recorded };
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
  ]);
  static BOOL_ANSWERS = new Map([
    [0x0c11, 'SCISSOR_TEST'], [0x0b44, 'CULL_FACE'], [0x0b71, 'DEPTH_TEST'], [0x0b90, 'STENCIL_TEST'],
    [0x0be2, 'BLEND'], [0x8037, 'POLYGON_OFFSET_FILL'], [0x8c89, 'RASTERIZER_DISCARD'],
    [0x809e, 'SAMPLE_ALPHA_TO_COVERAGE'], [0x80a0, 'SAMPLE_COVERAGE'],
  ]);
  intParameters(pname) {
    const gl = this.context;
    if (pname === 0x0ba2) return this.viewportBox; // GL_VIEWPORT: what this bridge last set
    if (pname === 0x0c36) return this.scissorBox; // GL_SCISSOR_BOX
    if (pname === 0x820b) return [3]; // GL_MAJOR_VERSION, from the declared apiVersion()
    if (pname === 0x820d) return [3]; // GL_MINOR_VERSION, from the declared apiVersion()
    if (pname === 0x821d) return [this.extensions().length]; // GL_NUM_EXTENSIONS
    if (pname === 0x9126) return [0]; // GL_CONTEXT_PROFILE_MASK: WebGL2 has no profile bits
    const constant = GLBridge.INT_ANSWERS.get(pname);
    if (constant === undefined || gl[constant] === undefined)
      throw new Error(`Unsupported WebGL2 integer parameter: 0x${pname.toString(16)}`);
    return [Number(gl.getParameter(gl[constant]))];
  }
  floatParameters(pname) {
    if (pname === 0x0b51) return [0, 1]; // GL_DEPTH_RANGE: WebGL2 always uses [0,1]
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
      if (target === gl.ARRAY_BUFFER) this.boundArrays = null; else this.boundElements = null;
      return;
    }
    const buffer = this.bufferEntry(handle, 'bindBuffer').buffer;
    this.call('bindBuffer', target, buffer);
    if (target === gl.ARRAY_BUFFER) this.boundArrays = buffer; else this.boundElements = buffer;
  }
  isBuffer(handle) { return this.buffers.has(handle); }
  bufferData(target, size, data) {
    const gl = this.context;
    if (target !== gl.ARRAY_BUFFER && target !== gl.ELEMENT_ARRAY_BUFFER)
      throw new Error(`Unsupported WebGL2 buffer target: 0x${target.toString(16)}`);
    // The desktop usage hint is dropped: WebGL2 has no storage hints.
    this.call('bufferData', target, data === null ? size : data, gl.DYNAMIC_DRAW);
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
    if (pname === 0x8764) return [Number(gl.getBufferParameter(target, gl.BUFFER_SIZE))]; // BUFFER_SIZE
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
      this.call('texImage2D', gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, bitmap);
      if (this.getError() !== gl.NO_ERROR) throw new Error('Texture upload failed');
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
      this.call('readPixels', 0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
      if (this.getError() !== gl.NO_ERROR) throw new Error('Texture readback failed');
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
    const translated = translateShader(parts.join('\n'), stage);
    entry.source = translated.source;
    entry.replacements = translated.replacements;
    entry.uniformDefaults = translated.uniformDefaults;
    entry.compiled = false;
    entry.log = '';
    this.call('shaderSource', entry.shader, entry.source);
  }
  compileShader(handle) {
    const entry = this.shaderEntry(handle, 'compileShader');
    if (entry.source === '') throw new Error(`Shader ${handle} compiled without source`);
    this.call('compileShader', entry.shader);
    entry.compiled = this.call('getShaderParameter', entry.shader, this.context.COMPILE_STATUS) ? true : false;
    entry.log = String(this.call('getShaderInfoLog', entry.shader) ?? '');
  }
  createProgram() {
    const handle = this.nextHandle++;
    this.programs.set(handle, { program: this.call('createProgram'), shaders: new Set(), log: '', linked: false });
    return handle;
  }
  deleteProgram(handle) {
    const entry = this.programs.get(handle);
    if (entry === undefined) return;
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
    this.call('attachShader', program.program, shader.shader);
  }
  detachShader(programHandle, shaderHandle) {
    const program = this.programEntry(programHandle, 'detachShader');
    const shader = this.shaderEntry(shaderHandle, 'detachShader');
    program.shaders.delete(shaderHandle);
    this.call('detachShader', program.program, shader.shader);
  }
  linkProgram(handle) {
    const program = this.programEntry(handle, 'linkProgram');
    this.call('linkProgram', program.program);
    program.linked = this.call('getProgramParameter', program.program, this.context.LINK_STATUS) ? true : false;
    program.log = String(this.call('getProgramInfoLog', program.program) ?? '');
  }
  useProgram(handle) {
    if (handle === 0) { this.call('useProgram', null); return; }
    this.call('useProgram', this.programEntry(handle, 'useProgram').program);
  }
  validateProgram(handle) {
    const program = this.programEntry(handle, 'validateProgram');
    this.call('validateProgram', program.program);
  }
  /** Desktop shader/program parameter tokens answered from real WebGL2 parameters. */
  shaderParameter(handle, pname) {
    const gl = this.context, entry = this.shaderEntry(handle, 'getShaderiv');
    const answers = new Map([
      [0x8b8d, gl.COMPILE_STATUS], [0x8b81, gl.SHADER_TYPE], [0x8b85, gl.DELETE_STATUS],
      [0x8b86, gl.INFO_LOG_LENGTH], [0x8b87, gl.INFO_LOG_LENGTH], [0x8ae2, gl.SHADER_SOURCE_LENGTH],
    ]);
    const constant = answers.get(pname);
    if (constant === undefined) throw new Error(`Unsupported WebGL2 shader parameter: 0x${pname.toString(16)}`);
    const value = gl.getShaderParameter(entry.shader, constant);
    if (pname === 0x8b8d) return [entry.compiled ? 1 : 0]; // this bridge records the compile result
    if (pname === 0x8b81) return [entry.type];
    if (pname === 0x8b86 || pname === 0x8b87) return [entry.log.length + 1]; // desktop log length includes NUL
    if (pname === 0x8b85) return [1]; // the handle is live while it is in this map
    if (pname === 0x8ae2) return [entry.source.length + 1];
    return [typeof value === 'boolean' ? (value ? 1 : 0) : Number(value)];
  }
  programParameter(handle, pname) {
    const gl = this.context, entry = this.programEntry(handle, 'getProgramiv');
    const answers = new Map([
      [0x8a86, gl.LINK_STATUS], [0x8b85, gl.DELETE_STATUS], [0x8b86, gl.INFO_LOG_LENGTH],
      [0x8b87, gl.INFO_LOG_LENGTH], [0x8a87, gl.VALIDATE_STATUS], [0x8a8a, gl.ACTIVE_ATTRIBUTES],
      [0x8a8b, gl.ACTIVE_UNIFORMS], [0x8b88, gl.ATTACHED_SHADERS], [0x8e20, gl.TRANSFORM_FEEDBACK_BUFFER_COUNT],
    ]);
    const constant = answers.get(pname);
    if (constant === undefined) throw new Error(`Unsupported WebGL2 program parameter: 0x${pname.toString(16)}`);
    if (pname === 0x8a86) return [entry.linked ? 1 : 0]; // this bridge records the link result
    if (pname === 0x8b88) return [entry.shaders.size];
    if (pname === 0x8b85) return [1];
    if (pname === 0x8b86 || pname === 0x8b87) return [entry.log.length + 1];
    return [Number(gl.getProgramParameter(entry.program, constant))];
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
  /**
   * Desktop uniform/attribute names and pointers become WebGL2 locations. A location handle
   * is monotonic and 0 means "no such location" (WebGL2 returns null), matching the desktop
   * GLint contract the game checks for.
   */
  uniformLocation(programHandle, name) {
    const program = this.programEntry(programHandle, 'getUniformLocation');
    const location = this.call('getUniformLocation', program.program, name);
    if (location === null) return 0;
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
    const index = this.call('getAttribLocation', program.program, name);
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
    if (transpose) throw new Error(`WebGL2 cannot transpose a uniform matrix (transpose=true, uniformMatrix${size}fv)`);
    const total = count * size * size;
    const name = `uniformMatrix${size}fv`;
    if (total > values.length) throw new Error(`${name}: count ${count} needs ${total} floats, the view has ${values.length}`);
    this.call(name, this.locationOf(handle, name), false, values.subarray(0, total));
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
    this.attribs.set(index, { enabled: true, size, type: webglType, normalized: !!normalized, stride, offset });
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
      this.call('deleteVertexArray', vao);
    }
  }
  bindVertexArray(handle) {
    if (handle === 0) { this.call('bindVertexArray', null); return; }
    const vao = this.vertexArrays.get(handle);
    if (vao === undefined) throw new Error(`Unknown vertex array handle: ${handle}`);
    this.call('bindVertexArray', vao);
  }
  /* --- Draw calls (GL11 natives) ------------------------------------------------------ */
  drawElements(mode, count, type, indices) {
    if (this.boundElements === null)
      throw new Error('glDrawElements needs a bound ELEMENT_ARRAY_BUFFER');
    const webglType = GLBridge.indexType(type);
    const elementBytes = webglType === 0x1405 ? 4 : webglType === 0x1403 ? 2 : 1;
    if (indices % elementBytes !== 0)
      throw new Error(`Index offset ${indices} is not a multiple of the ${elementBytes}-byte index size`);
    // As in desktop GL, `indices` is an offset into the bound ELEMENT_ARRAY_BUFFER.
    this.call('drawElements', mode, count, webglType, indices);
  }
  static indexType(type) {
    const map = new Map([[0x1401, 0x1401], [0x1403, 0x1403], [0x1405, 0x1405]]);
    const value = map.get(type);
    if (value === undefined) throw new Error(`Unsupported WebGL2 index type: 0x${type.toString(16)}`);
    return value;
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
    this.call('drawArrays', gl.TRIANGLES, 0, 3);
    const pixel = new Uint8Array(4);
    this.call('readPixels', gl.drawingBufferWidth >> 1, gl.drawingBufferHeight >> 1, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel);
    this.call('bindVertexArray', null); this.call('useProgram', null);
    gl.deleteVertexArray(vao); gl.deleteProgram(program); gl.deleteShader(vertex); gl.deleteShader(fragment);
    if (this.getError() !== gl.NO_ERROR) throw new Error('WebGL probe generated an error');
    return [...pixel];
  }
}
    this.capabilities = new Map(); // desktop capability token -> {enabled, webgl} actually applied
    this.fixedFunctionCaps = new Map(); // desktop-only token -> last requested state, recorded not applied
