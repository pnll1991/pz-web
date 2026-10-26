import { glfwKey } from './input.mjs';

// Browser window system. Handles belong to this backend, never to the host OS.
export class BrowserWindow {
  constructor(canvas, gl, input) {
    this.canvas = canvas;
    this.gl = gl;
    this.input = input;
    this.initialized = false;
    this.created = false; // Display.create and GLFW window lifecycle remain unimplemented.
    this.layout = null;
    this.events = [];
    this.keyCodes = new Map();
    const codes = ['Space','Quote','Comma','Minus','Period','Slash','Semicolon','Equal',
      'BracketLeft','Backslash','BracketRight','Backquote','Escape','Enter','Tab','Backspace',
      'Insert','Delete','ArrowRight','ArrowLeft','ArrowDown','ArrowUp','PageUp','PageDown',
      'Home','End','CapsLock','ScrollLock','NumLock','PrintScreen','Pause','ShiftLeft',
      'ControlLeft','AltLeft','MetaLeft','ShiftRight','ControlRight','AltRight','MetaRight','ContextMenu'];
    for (let n=65;n<=90;n++) codes.push(`Key${String.fromCharCode(n)}`);
    for (let n=0;n<=9;n++) codes.push(`Digit${n}`,`Numpad${n}`);
    for (let n=1;n<=25;n++) codes.push(`F${n}`);
    for (const code of codes) this.keyCodes.set(glfwKey(code),code);
  }
  async prepare() {
    // Layout is optional and permission/policy dependent. Do not invent host labels.
    if (navigator.keyboard?.getLayoutMap) {
      try { this.layout = await navigator.keyboard.getLayoutMap(); }
      catch { this.layout = null; }
    }
  }
  init() {
    if (!this.canvas.isConnected || this.input.canvas !== this.canvas || this.input.controller?.signal.aborted ||
        !this.gl.context || this.gl.context.canvas !== this.canvas || this.gl.lost || this.gl.context.isContextLost())
      throw new Error('Browser window initialization requires a live canvas, input and WebGL2 context');
    if (this.initialized) return;
    this.initialized = true;
    this.events.push({type:'initialized',backend:'browser',width:this.desktopWidth(),
      height:this.desktopHeight(),bitsPerPixel:this.colorBits(),refreshRate:0,
      renderer:this.gl.context.getParameter(this.gl.context.VERSION)});
  }
  requireInit() { if (!this.initialized) throw new Error('Browser window system is not initialized'); }
  desktopWidth() { return window.screen.width; }
  desktopHeight() { return window.screen.height; }
  colorBits() { return window.screen.colorDepth; }
  keyScancode(key) { this.requireInit(); return this.keyCodes.has(key) ? key : -1; }
  keyName(key, scancode) {
    this.requireInit();
    const token = key === -1 ? scancode : key;
    const printable = (token>=39 && token<=96) || token===161 || token===162 ||
      (token>=320 && token<=334) || token===336;
    if (!printable) return null;
    const code = this.keyCodes.get(token);
    return this.layout?.get(code) || null;
  }
  key(windowHandle,key) {
    this.requireWindow(windowHandle);
    return this.input.keys.has(key) ? 1 : 0;
  }
  button(windowHandle,button) {
    this.requireWindow(windowHandle);
    return this.input.buttons.has(button) ? 1 : 0;
  }
  requireWindow(handle) {
    this.requireInit();
    if (handle !== 1 || !this.created) throw new Error(`Invalid browser window handle: ${handle}`);
  }
}
