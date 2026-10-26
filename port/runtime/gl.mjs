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
