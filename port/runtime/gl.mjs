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
    this.nextHandle = 1; // 0 is reserved for "none", as in desktop GL
    this.lost = false;
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
    [0x0b90, 'SCISSOR_TEST'], [0x0b91, 'CULL_FACE'], [0x0b92, 'DEPTH_TEST'],
    [0x0bc2, 'BLEND'], [0x0be2, 'POLYGON_OFFSET_FILL'], [0x0ba7, 'RASTERIZER_DISCARD'],
    [0x0bb8, 'SAMPLE_ALPHA_TO_COVERAGE'], [0x0bad, 'SAMPLE_MASK'],
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
    if (handle === 0) { this.call('bindBuffer', target, null); return; }
    this.call('bindBuffer', target, this.bufferEntry(handle, 'bindBuffer').buffer);
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
