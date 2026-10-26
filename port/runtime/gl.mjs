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
