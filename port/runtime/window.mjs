import { glfwKey } from './input.mjs';

// Browser window system. Handles belong to this backend, never to the host OS.
// GLFW lifecycle: one window per canvas, handle 1; callbacks are registered by id and
// dispatched through port.shim.BrowserCallbacks (no libffi upcalls exist in wasm).
export class BrowserWindow {
  constructor(canvas, gl, input) {
    this.canvas = canvas;
    this.gl = gl;
    this.input = input;
    this.initialized = false;
    this.created = false; // set by createWindow; reset by destroyWindow
    this.visible = true;
    this.size = [0, 0]; // requested window size (CSS pixels)
    this.windowPos = [0, 0];
    this.cursor = [0, 0];
    this.inputMode = { 212993: 208896, 212994: 1, 212995: 0 }; // cursor/sticky-buttons/sticky-keys
    this.hints = new Map();
    this.vsync = 1;
    this.callbackIds = new Map(); // kind -> callback id registered by BrowserCallbacks
    this.dispatchedEvents = 0;
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
  requireWindow(handle) {
    this.requireInit();
    if (handle !== 1 || !this.created) throw new Error(`Invalid browser window handle: ${handle}`);
  }
  desktopWidth() { return window.screen.width; }
  desktopHeight() { return window.screen.height; }
  colorBits() { return window.screen.colorDepth; }
  // The page sees exactly one display: screen geometry is the monitor geometry.
  // OS panels are invisible to a browser tab, so the work area is the whole screen.
  monitorX() { this.requireInit(); return 0; }
  monitorY() { this.requireInit(); return 0; }
  monitorWorkareaWidth() { this.requireInit(); return window.screen.width; }
  monitorWorkareaHeight() { this.requireInit(); return window.screen.height; }
  // The canvas drawing buffer is the real drawable surface this page can present.
  bufferWidth() { this.requireInit(); return this.canvas.width; }
  bufferHeight() { this.requireInit(); return this.canvas.height; }
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
  // --- GLFW window lifecycle (browser backend) ---
  // The requested size is the CSS size; the drawing buffer multiplies by devicePixelRatio,
  // which is what window/framebuffer size queries report back to the game.
  create(width,height,monitor) {
    this.requireInit();
    if (monitor !== 0 && monitor !== 2) throw new Error(`Invalid browser monitor handle: ${monitor}`);
    if (width <= 0 || height <= 0) throw new Error(`Invalid browser window size: ${width}x${height}`);
    const ratio = window.devicePixelRatio || 1;
    this.size = [width, height];
    this.canvas.style.width = `${width}px`;
    this.canvas.style.height = `${height}px`;
    this.canvas.width = Math.max(1, Math.round(width * ratio));
    this.canvas.height = Math.max(1, Math.round(height * ratio));
    const hidden = this.hints.get(131076) === 0; // GLFW_VISIBLE
    this.visible = !hidden;
    this.canvas.style.visibility = hidden ? 'hidden' : '';
    this.created = true;
    this.cursor = [width / 2, height / 2];
    this.events.push({type:'created',backend:'browser',width,height,
      drawingBufferWidth:this.canvas.width,drawingBufferHeight:this.canvas.height,devicePixelRatio:ratio});
    return 1;
  }
  destroy(handle) {
    this.requireWindow(handle);
    this.created = false;
    this.callbackIds.clear();
    this.hints.clear();
    this.events.push({type:'destroyed',backend:'browser'});
  }
  shouldClose(handle) { this.requireWindow(handle); return this.input.closeRequested ? 1 : 0; }
  setShouldClose(handle,value) { this.requireWindow(handle); this.input.closeRequested = value === 1; }
  show(handle) {
    this.requireWindow(handle);
    this.visible = true;
    this.canvas.style.visibility = '';
    this.events.push({type:'shown',backend:'browser'});
  }
  hide(handle) {
    this.requireWindow(handle);
    this.visible = false;
    this.canvas.style.visibility = 'hidden';
    this.events.push({type:'hidden',backend:'browser'});
  }
  makeContextCurrent(handle) {
    this.requireWindow(handle);
    if (!this.gl.context || this.gl.lost || this.gl.context.isContextLost())
      throw new Error('Browser WebGL2 context is lost; cannot make it current');
    return 1;
  }
  swapBuffers(handle) {
    this.requireWindow(handle);
    // WebGL presents implicitly at the end of the task; nothing to flush or blit.
    return 1;
  }
  swapInterval(interval) {
    // The browser composites on its own cadence; record the request as an observable event.
    this.vsync = interval;
    this.events.push({type:'swap_interval',interval});
  }
  poll() {
    // One serialized input event per call; JSBody cannot return arrays. Field layout:
    // kind, handle, then callback-specific numeric/text fields (tab separated).
    const events = this.input.drain();
    if (events.length === 0) return null;
    const event = events[0];
    const kind = {key:0, button:1, cursor:2, scroll:3, text:4}[event.type];
    if (kind === undefined) return this.poll(); // unknown event type: skip without dispatching
    this.dispatchedEvents++;
    const fields = [kind, 1];
    if (event.type === 'key') fields.push(event.key, event.action, 0);
    else if (event.type === 'button') fields.push(event.button, event.action, 0);
    else if (event.type === 'cursor') fields.push(event.x, event.y);
    else if (event.type === 'scroll') fields.push(event.x, event.y);
    else fields.push(event.text);
    return fields.join('\t');
  }
  getTime() { return performance.now() / 1000; }
  getPlatform() { return 393221; } // GLFW_PLATFORM_NULL: this backend has no host window system
  setCursorPos(handle,x,y) {
    this.requireWindow(handle);
    this.cursor = [x, y];
    return 1;
  }
  cursorX(handle) { this.requireWindow(handle); return this.cursor[0]; }
  cursorY(handle) { this.requireWindow(handle); return this.cursor[1]; }
  inputModeValue(handle,mode) {
    this.requireWindow(handle);
    if (!Object.hasOwn(this.inputMode,mode)) throw new Error(`Unsupported browser input mode: ${mode}`);
    return this.inputMode[mode];
  }
  setInputMode(handle,mode,value) {
    this.requireWindow(handle);
    if (mode === 212993) { // GLFW_CURSOR
      // 208896 normal / 208897 hidden / 208898 captured. Pointer lock needs a user gesture
      // and is not integrated yet, so only the observable normal mode is accepted.
      if (value !== 208896)
        throw new Error(`Browser pointer lock is not integrated: GLFW_CURSOR mode ${value}`);
      this.inputMode[mode] = value;
      return 1;
    }
    if (mode === 212994 || mode === 212995) { // sticky mouse buttons / sticky keys
      this.inputMode[mode] = value;
      return 1;
    }
    throw new Error(`Unsupported browser input mode: ${mode}`);
  }
  windowWidth(handle) { this.requireWindow(handle); return this.canvas.width; }
  windowHeight(handle) { this.requireWindow(handle); return this.canvas.height; }
  framebufferWidth(handle) { this.requireWindow(handle); return this.canvas.width; }
  framebufferHeight(handle) { this.requireWindow(handle); return this.canvas.height; }
  posX(handle) { this.requireWindow(handle); return this.windowPos[0]; }
  posY(handle) { this.requireWindow(handle); return this.windowPos[1]; }
  attrib(handle,attrib) {
    this.requireWindow(handle);
    switch (attrib) {
      case 131073: return 1; // GLFW_FOCUSED: a focused canvas is the best the page can report
      case 131075: return 1; // GLFW_RESIZABLE: the canvas resizes with the page
      case 131076: return this.visible ? 1 : 0; // GLFW_VISIBLE
      case 131077: return 0; // GLFW_DECORATED: a tab has no window chrome
      case 131078: return 1; // GLFW_AUTO_ICONIFY
      case 131079: return 0; // GLFW_FLOATING
      case 131080: return 0; // GLFW_MAXIMIZED
      case 131081: return this.hints.get(131081) ?? 1; // GLFW_CENTER_CURSOR
      case 131082: return 0; // GLFW_TRANSPARENT_FRAMEBUFFER
      case 139265: return 196609; // GLFW_CLIENT_API -> GLFW_OPENGL_API
      case 143364: return this.vsync; // GLFW_VSYNC
      case 196609: return 3300; // GLFW_OPENGL_API -> no specific profile requested
      default: throw new Error(`Unsupported browser window attribute: ${attrib}`);
    }
  }
  defaultHints() { this.requireInit(); this.hints.clear(); }
  hint(name,value) {
    this.requireInit();
    this.hints.set(name,value);
    // GLFW_FULLSCREEN is a real native capability this backend does not implement.
    if (name === 131072 && value === 1)
      throw new Error('Browser cannot create a fullscreen window: org.lwjgl.glfw.GLFW#glfwWindowHint(I)V GLFW_FULLSCREEN');
    return 1;
  }
  registerCallback(id,kind) { this.requireInit(); this.callbackIds.set(kind,id); return 1; }
  callbackId(kind) { this.requireInit(); return this.callbackIds.get(kind) ?? 0; }
}
