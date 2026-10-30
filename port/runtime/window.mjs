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
    // Whether the page's single WebGL2 context is currently bound to a window. GLFW starts with
    // no context current; Display.create makes handle 1 current, and Display.releaseContext
    // (glfwMakeContextCurrent(0)) detaches it. The game queries this through glfwGetCurrentContext.
    this.contextCurrent = false;
    this.visible = true;
    this.size = [0, 0]; // requested window size (CSS pixels)
    this.windowPos = [0, 0];
    this.cursor = [0, 0];
    this.title = '';
    this.focused = true;
    this.swapCount = 0;
    // Number of context_current/context_detached events recorded (bounded, like swap_buffers).
    this.contextEvents = 0;
    // Real GLFW constants: the mode is the key, the value is the cursor/sticky setting.
    // GLFW_CURSOR=208897 (values NORMAL=212993/HIDDEN=212994/DISABLED=212995/CAPTURED=212996),
    // GLFW_STICKY_KEYS=208898, GLFW_STICKY_MOUSE_BUTTONS=208899, GLFW_RAW_MOUSE_MOTION=208901.
    this.inputMode = { 208897: 212993, 208898: 0, 208899: 0, 208901: 0 };
    // Pointer lock state mirrored from the document, plus whether a lock was requested but is
    // still waiting for a user gesture (Chrome only grants it from inside a gesture handler).
    this.pointerLock = false;
    this.pointerLockPending = false;
    this.pointerLockError = null;
    this.hints = new Map();
    this.vsync = 1;
    this.callbackIds = new Map(); // kind -> callback id registered by BrowserCallbacks
    this.dispatchedEvents = 0;
    this.layout = null;
    this.events = [];
    this.keyCodes = new Map();
    // Gamepad slots mirrored from the browser Gamepad API. Chrome only reports gamepads after a
    // user interaction, so the honest initial state is 'no gamepads'.
    this.gamepads = new Map(); // slot -> { id, mapping }
    this.joystickEvents = [];
    this.joystickOverflow = false;
    // navigator.getGamepads() throws inside gamepad event callbacks, so the DOM events only ask
    // for a re-sync, which happens on the next poll().
    this.gamepadNeedsSync = true;
    this.gamepadController = new AbortController();
    const onGamepad = (type) => window.addEventListener(type, (event) => {
      const pad = event?.gamepad;
      if (pad && !(pad.index >= 0 && pad.index < 16) && !this.joystickOverflow) {
        this.joystickOverflow = true;
        this.events.push({ type: 'joystick_unrepresentable', index: pad.index, id: pad.id });
      }
      this.gamepadNeedsSync = true;
    }, { signal: this.gamepadController.signal });
    onGamepad('gamepadconnected');
    onGamepad('gamepaddisconnected');
    const codes = ['Space','Quote','Comma','Minus','Period','Slash','Semicolon','Equal',
      'BracketLeft','Backslash','BracketRight','Backquote','Escape','Enter','Tab','Backspace',
      'Insert','Delete','ArrowRight','ArrowLeft','ArrowDown','ArrowUp','PageUp','PageDown',
      'Home','End','CapsLock','ScrollLock','NumLock','PrintScreen','Pause','ShiftLeft',
      'ControlLeft','AltLeft','MetaLeft','ShiftRight','ControlRight','AltRight','MetaRight','ContextMenu'];
    for (let n=65;n<=90;n++) codes.push(`Key${String.fromCharCode(n)}`);
    for (let n=0;n<=9;n++) codes.push(`Digit${n}`,`Numpad${n}`);
    for (let n=1;n<=25;n++) codes.push(`F${n}`);
    for (const code of codes) this.keyCodes.set(glfwKey(code),code);
    // Pointer lock is granted by Chrome only from inside a user gesture handler, so a request
    // from the Java thread may fail; the state below records what actually happened.
    document.addEventListener('pointerlockchange', () => {
      this.pointerLock = document.pointerLockElement === this.canvas;
      if (this.pointerLock) this.pointerLockPending = false;
      this.events.push({type:'pointer_lock',locked:this.pointerLock});
    }, { signal: this.gamepadController.signal });
    document.addEventListener('pointerlockerror', () => {
      this.pointerLock = false;
      this.pointerLockError = 'pointerlockerror';
      this.events.push({type:'pointer_lock_error'});
    }, { signal: this.gamepadController.signal });
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
  create(width,height,monitor,title) {
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
    this.title = title ?? '';
    this.cursor = [width / 2, height / 2];
    this.events.push({type:'created',backend:'browser',width,height,
      drawingBufferWidth:this.canvas.width,drawingBufferHeight:this.canvas.height,devicePixelRatio:ratio,
      title:this.title});
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
  setShouldClose(handle,value) { this.requireWindow(handle); this.input.closeRequested = value === true; }
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
  focus(handle) {
    this.requireWindow(handle);
    this.focused = true;
    this.canvas.focus();
    this.events.push({type:'focused',backend:'browser'});
  }
  makeContextCurrent(handle) {
    this.requireWindow(handle);
    if (!this.gl.context || this.gl.lost || this.gl.context.isContextLost())
      throw new Error('Browser WebGL2 context is lost; cannot make it current');
    this.contextCurrent = true;
    if (this.contextEvents < 3) { this.contextEvents++; this.events.push({type:'context_current',backend:'browser',handle}); }
    return 1;
  }
  // glfwMakeContextCurrent(0): detach. The browser context is not a schedulable object, so the
  // honest effect is that no window owns it until a makeContextCurrent(window) happens again.
  detachContext() {
    this.requireInit();
    if (!this.gl.context || this.gl.lost || this.gl.context.isContextLost())
      throw new Error('Browser WebGL2 context is lost; cannot detach it');
    this.contextCurrent = false;
    if (this.contextEvents < 3) { this.contextEvents++; this.events.push({type:'context_detached',backend:'browser'}); }
    return 0;
  }
  // The page has one WebGL2 context; it is current only after makeContextCurrent and until
  // glfwMakeContextCurrent(0) detaches it. 0 means 'no context', exactly as GLFW reports.
  getCurrentContext() { this.requireWindow(1); return this.contextCurrent && this.gl.context ? 1 : 0; }
  swapBuffers(handle) {
    this.requireWindow(handle);
    // WebGL presents implicitly at the end of the task; nothing to flush or blit.
    this.swapCount++;
    if (this.swapCount <= 3) this.events.push({type:'swap_buffers',backend:'browser',count:this.swapCount});
    return 1;
  }
  swapInterval(interval) {
    // The browser composites on its own cadence; record the request as an observable event.
    this.vsync = interval;
    this.events.push({type:'swap_interval',interval});
  }
  title(handle,value) { this.requireWindow(handle); this.title = value; this.events.push({type:'title',value}); }
  // glfwSetWindowIcon pixels arrive through port.shim.BrowserStb as RGBA8 bytes. The tab's
  // favicon is the only icon surface a page has, so it is set (or replaced) with a Blob URL.
  setIcon(bytes,width,height) {
    if (!(bytes instanceof Uint8Array)) throw new Error('Browser window icon requires a Uint8Array of RGBA pixels');
    if (!Number.isInteger(width) || !Number.isInteger(height) || width <= 0 || height <= 0)
      throw new Error(`Invalid browser window icon size: ${width}x${height}`);
    if (bytes.length !== width * height * 4)
      throw new Error(`Browser window icon byte count mismatch: ${bytes.length} != ${width * height * 4}`);
    // Copy: the bytes come from the wasm heap, whose view may be detached later.
    const copy = new Uint8Array(bytes);
    const canvas = document.createElement('canvas');
    canvas.width = width; canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Cannot rasterize the window icon: no 2D context');
    const image = context.createImageData(width,height);
    image.data.set(copy);
    context.putImageData(image,0,0);
    const url = canvas.toDataURL('image/png');
    let link = document.querySelector('link[rel="icon"]');
    if (!link) {
      link = document.createElement('link');
      link.rel = 'icon';
      document.head.appendChild(link);
    }
    link.href = url;
    this.lastIcon = {width,height,bytes:copy.length};
    this.events.push({type:'icon_set',backend:'browser',width,height,bytes:copy.length});
  }
  setWindowPos(handle,x,y) { this.requireWindow(handle); this.windowPos = [x, y]; }
  // glfwSetWindowMonitor: 'fullscreen' in a tab means the canvas fills the screen work area.
  // The refresh rate the game requests is only recorded (the browser owns presentation cadence).
  setMonitorMode(handle,mode,refreshRate) {
    this.requireWindow(handle);
    this.monitorMode = mode;
    this.requestedRefreshRate = refreshRate;
    this.events.push({type:'monitor_mode',backend:'browser',monitor:mode === 1 ? 2 : 0,
      fullscreen:mode === 1,requestedRefreshRate:refreshRate,
      workareaWidth:window.screen.width,workareaHeight:window.screen.height});
  }
  setWindowSize(handle,width,height) {
    this.requireWindow(handle);
    if (width <= 0 || height <= 0) throw new Error(`Invalid browser window size: ${width}x${height}`);
    const ratio = window.devicePixelRatio || 1;
    this.size = [width, height];
    this.canvas.style.width = `${width}px`;
    this.canvas.style.height = `${height}px`;
    this.canvas.width = Math.max(1, Math.round(width * ratio));
    this.canvas.height = Math.max(1, Math.round(height * ratio));
    this.events.push({type:'resized',backend:'browser',width,height,drawingBufferWidth:this.canvas.width,
      drawingBufferHeight:this.canvas.height});
    // Native GLFW reports a resize through the window-size and framebuffer-size callbacks on the
    // next glfwPollEvents. lwjglx Display.getWidth() returns the value its window-size callback
    // stored (latestWidth), so without these events the game kept rendering at the 640x480 the
    // window was created with while the canvas was already 1280x720 (round 65 viewport probe).
    (this.sizeEvents ||= []).push([6, 1, width, height], [8, 1, this.canvas.width, this.canvas.height]);
  }
  // A tab has no window chrome: every frame edge is zero, and the content scale is the
  // device pixel ratio the canvas uses for its drawing buffer.
  frameMetric(handle,kind) { this.requireWindow(handle); return [0,0,0,0][kind]; }
  cursorMetric(handle,kind) { this.requireWindow(handle); return this.cursor[kind]; }
  contentScale(handle) { this.requireWindow(handle); return window.devicePixelRatio || 1; }
  setAttrib(handle,attrib,value) {
    this.requireWindow(handle);
    switch (attrib) {
      case 131075: this.hints.set(131075,value); break; // GLFW_RESIZABLE
      case 131076: this.visible = value === 1; this.canvas.style.visibility = value === 1 ? '' : 'hidden'; break;
      case 131078: case 131081: this.hints.set(attrib,value); break; // AUTO_ICONIFY / CENTER_CURSOR
      case 131077: case 131079: case 131080: case 131082:
        // GLFW_DECORATED / FLOATING / MAXIMIZED / TRANSPARENT_FRAMEBUFFER: a browser tab has no
        // window chrome, so these values are fixed by the page. The request is recorded (never
        // silently dropped) and getAttrib keeps reporting the fixed value, which is what GLFW
        // itself does on a host where the window manager ignores the change.
        this.hints.set(attrib, value);
        this.events.push({type:'attribute_ignored',backend:'browser',attrib,value,fixed:true,
          reason:'browser tab has no window chrome'});
        break;
      default: throw new Error(`Unsupported browser window attribute: ${attrib}`);
    }
  }
  // --- Gamepad slots (GLFW joystick indices 0..15) -------------------------------------------
  // The browser's Gamepad API is the only source of gamepads here. getGamepads() returns null for
  // an empty slot and its `id` is the hardware name the UA really reports; `mapping === 'standard'`
  // is what the UA declares about the button layout, which is what glfwJoystickIsGamepad means.
  // navigator.getGamepads() is forbidden inside gamepad event callbacks, so the DOM events only
  // queue a re-sync; the snapshot is taken on the next poll() or syncGamepads() call.
  syncGamepads() {
    const list = typeof navigator.getGamepads === 'function' ? navigator.getGamepads() : [];
    const seen = new Set();
    for (const pad of list) {
      if (!pad) continue;
      const slot = pad.index >= 0 && pad.index < 16 ? pad.index : -1;
      if (slot < 0) { // A slot outside GLFW's joystick range cannot be represented; report it once.
        if (!this.joystickOverflow) {
          this.joystickOverflow = true;
          this.events.push({ type: 'joystick_unrepresentable', index: pad.index, id: pad.id });
        }
        continue;
      }
      seen.add(slot);
      const previous = this.gamepads.get(slot);
      if (!previous) {
        this.gamepads.set(slot, { id: pad.id, mapping: pad.mapping });
        this.joystickEvents.push({ type: 'joystick', slot, connected: 1 });
      } else if (previous.id !== pad.id || previous.mapping !== pad.mapping) {
        this.joystickEvents.push({ type: 'joystick', slot, connected: 0 });
        this.gamepads.set(slot, { id: pad.id, mapping: pad.mapping });
        this.joystickEvents.push({ type: 'joystick', slot, connected: 1 });
      }
    }
    for (const slot of [...this.gamepads.keys()]) {
      if (!seen.has(slot)) {
        this.gamepads.delete(slot);
        this.joystickEvents.push({ type: 'joystick', slot, connected: 0 });
      }
    }
    this.gamepadNeedsSync = false;
    return this.gamepads.size;
  }
  refreshGamepads() {
    this.requireInit();
    if (this.gamepadNeedsSync) this.syncGamepads();
  }
  joystickPresent(slot) {
    this.refreshGamepads();
    return this.gamepads.has(slot) ? 1 : 0;
  }
  joystickName(slot) {
    this.refreshGamepads();
    const pad = this.gamepads.get(slot);
    return pad ? pad.id : null;
  }
  joystickIsGamepad(slot) {
    this.refreshGamepads();
    const pad = this.gamepads.get(slot);
    return pad && pad.mapping === 'standard' ? 1 : 0;
  }
  gamepadName(slot) {
    this.refreshGamepads();
    const pad = this.gamepads.get(slot);
    // Native GLFW returns NULL for a joystick that is not a gamepad; the API only names pads it
    // declares standard-mapped.
    return pad && pad.mapping === 'standard' ? pad.id : null;
  }
  poll() {
    // One serialized input event per call; JSBody cannot return arrays. Field layout:
    // kind, handle, then callback-specific numeric/text fields (tab separated). A key event
    // carries the GLFW key twice: as key and as scancode, because this backend maps DOM keys
    // onto themselves (keyScancode below). A joystick event (kind 11) instead carries
    // kind, slot, connected, because GLFWJoystickCallbackI reads slot 0 as the joystick index.
    if (this.initialized) this.refreshGamepads();
    if (this.sizeEvents && this.sizeEvents.length > 0) {
      this.dispatchedEvents++;
      return this.sizeEvents.shift().join('\t');
    }
    if (this.joystickEvents.length > 0) {
      const event = this.joystickEvents.shift();
      this.dispatchedEvents++;
      return [11, event.slot, event.connected].join('\t');
    }
    const events = this.input.drain();
    if (events.length === 0) return null;
    const event = events[0];
    const kind = {key:0, button:1, cursor:2, scroll:3, text:4}[event.type];
    if (kind === undefined) return this.poll(); // unknown event type: skip without dispatching
    this.dispatchedEvents++;
    const fields = [kind, 1];
    if (event.type === 'key') fields.push(event.key, event.key, event.action, 0);
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
  // Chrome only grants pointer lock from a user gesture, so this records the request and never
  // claims success: pointerLock stays false until the browser fires pointerlockchange.
  requestPointerLock() {
    this.pointerLockPending = true;
    try {
      const result = this.canvas.requestPointerLock();
      if (result && typeof result.catch === 'function')
        result.catch((error) => { this.pointerLockError = `${error.name}: ${error.message}`;
          this.events.push({type:'pointer_lock_error',error:this.pointerLockError}); });
    } catch (error) {
      this.pointerLockError = `${error.name}: ${error.message}`;
      this.events.push({type:'pointer_lock_error',error:this.pointerLockError});
    }
    return 1;
  }
  inputModeValue(handle,mode) {
    this.requireWindow(handle);
    if (!Object.hasOwn(this.inputMode,mode)) throw new Error(`Unsupported browser input mode: ${mode}`);
    return this.inputMode[mode];
  }
  setInputMode(handle,mode,value) {
    this.requireWindow(handle);
    if (mode === 208897) { // GLFW_CURSOR, values are the cursor modes below.
      if (value === 212993) { // GLFW_CURSOR_NORMAL: OS cursor over the canvas.
        this.canvas.style.cursor = '';
        if (document.pointerLockElement === this.canvas) document.exitPointerLock();
        this.pointerLockPending = false;
      } else if (value === 212994) { // GLFW_CURSOR_HIDDEN: cursor visible only outside the window.
        this.canvas.style.cursor = 'none';
        if (document.pointerLockElement === this.canvas) document.exitPointerLock();
        this.pointerLockPending = false;
      } else if (value === 212995) { // GLFW_CURSOR_DISABLED: pointer lock, needs a user gesture.
        this.canvas.style.cursor = 'none';
        this.requestPointerLock();
      } else if (value === 212996) { // GLFW_CURSOR_CAPTURED has no browser equivalent.
        throw new Error(`Browser cannot capture the cursor: GLFW_CURSOR value ${value}`);
      } else {
        throw new Error(`Unsupported GLFW_CURSOR value: ${value}`);
      }
      this.inputMode[mode] = value;
      this.events.push({type:'input_mode',mode,value,pointerLock:this.pointerLock});
      return 1;
    }
    if (mode === 208898 || mode === 208899) { // GLFW_STICKY_KEYS / GLFW_STICKY_MOUSE_BUTTONS
      this.inputMode[mode] = value;
      return 1;
    }
    if (mode === 208901) { // GLFW_RAW_MOUSE_MOTION: movementX/Y only matches GLFW while locked.
      this.inputMode[mode] = value;
      this.events.push({type:'raw_mouse_motion',value,locked:this.pointerLock});
      return 1;
    }
    throw new Error(`Unsupported browser input mode: ${mode}`);
  }
  // GLFW reports the window size in the coordinate space it handed to createWindow (CSS pixels),
  // while the framebuffer size is the canvas drawing buffer.
  windowWidth(handle) { this.requireWindow(handle); return this.size[0]; }
  windowHeight(handle) { this.requireWindow(handle); return this.size[1]; }
  framebufferWidth(handle) { this.requireWindow(handle); return this.canvas.width; }
  framebufferHeight(handle) { this.requireWindow(handle); return this.canvas.height; }
  posX(handle) { this.requireWindow(handle); return this.windowPos[0]; }
  posY(handle) { this.requireWindow(handle); return this.windowPos[1]; }
  attrib(handle,attrib) {
    this.requireWindow(handle);
    switch (attrib) {
      case 131073: return this.focused ? 1 : 0; // GLFW_FOCUSED
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
  registerCallback(kind,handle) {
    this.requireInit();
    this.callbackIds.set(kind,handle);
    this.events.push({type:'callback_registered',kind,handle});
    return 1;
  }
  callbackId(kind) { this.requireInit(); return this.callbackIds.get(kind) ?? 0; }
}
