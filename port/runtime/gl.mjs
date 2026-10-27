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
]);

/** WebGL2 context and object handles; unsupported entrypoints must throw. */
export class GLBridge {
  constructor(canvas) {
    const context = canvas.getContext('webgl2', { alpha: false, antialias: false, preserveDrawingBuffer: true });
    if (!context) throw new Error('WebGL 2 is required');
    this.context = context;
    this.calls = Object.create(null);
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
