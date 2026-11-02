const special = { Space:32, Quote:39, Comma:44, Minus:45, Period:46, Slash:47, Semicolon:59, Equal:61,
  BracketLeft:91, Backslash:92, BracketRight:93, Backquote:96, Escape:256, Enter:257, Tab:258,
  Backspace:259, Insert:260, Delete:261, ArrowRight:262, ArrowLeft:263, ArrowDown:264, ArrowUp:265,
  PageUp:266, PageDown:267, Home:268, End:269, CapsLock:280, ScrollLock:281, NumLock:282,
  PrintScreen:283, Pause:284, ShiftLeft:340, ControlLeft:341, AltLeft:342, MetaLeft:343,
  ShiftRight:344, ControlRight:345, AltRight:346, MetaRight:347, ContextMenu:348,
  NumpadDecimal:330, NumpadDivide:331, NumpadMultiply:332, NumpadSubtract:333, NumpadAdd:334, NumpadEnter:335, NumpadEqual:336, IntlBackslash:161 };

// GLFW modifier bits for key/button events: shift 1, control 2, alt 4, super 8, caps lock 16, num lock 32.
const mods = e => (e.shiftKey?1:0)|(e.ctrlKey?2:0)|(e.altKey?4:0)|(e.metaKey?8:0)|(e.getModifierState?.('CapsLock')?16:0)|(e.getModifierState?.('NumLock')?32:0);

export function glfwKey(code) {
  if (/^Key[A-Z]$/.test(code)) return code.charCodeAt(3);
  if (/^Digit[0-9]$/.test(code)) return code.charCodeAt(5);
  if (/^F([1-9]|1[0-9]|2[0-5])$/.test(code)) return 289 + Number(code.slice(1));
  if (/^Numpad[0-9]$/.test(code)) return 320 + Number(code.slice(6));
  return special[code] ?? -1;
}

export class CanvasInput {
  constructor(canvas) {
    this.canvas = canvas; this.keys = new Set(); this.buttons = new Set();
    this.events = []; this.droppedEvents = 0;
    this.closeRequested = false; // set only by glfwSetWindowShouldClose on this window
    this.controller = new AbortController();
    const on = (target, type, callback, options) => target.addEventListener(type, callback, { ...options, signal: this.controller.signal });
    const key = (event, action) => {
      const code = glfwKey(event.code);
      if (code < 0) return;
      // REPEAT only when the game already saw the press (a held key from before focus/blur is a new press).
      const had = this.keys.has(code);
      if (action) { this.keys.add(code); if (!had) (this.latchedKeys ??= new Set()).add(code); } else this.keys.delete(code);
      this.emit({ type:'key', key:code, action: action ? (had ? 2 : 1) : 0, mods:mods(event) });
      // Keys the browser would act on (scroll, find, help, reload, focus change): keep them for the game. F11/F12 and Ctrl/Meta combos stay with the browser.
      if (event.code !== 'F11' && event.code !== 'F12' && !event.ctrlKey && !event.metaKey) event.preventDefault();
    };
    // A canvas is not editable, so Chrome never fires beforeinput on it: without this the game could not receive any typed
    // text (server name/IP, user name, password). Printable keys become text on keydown until a real beforeinput shows up
    // (IME/composed text), after which beforeinput is the only text source so nothing is delivered twice.
    let beforeInputSeen = false;
    on(canvas, 'keydown', event => {
      key(event, 1);
      if (!beforeInputSeen && event.key && event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey && !event.isComposing)
        this.emit({ type:'text', text:event.key });
    });
    on(canvas, 'keyup', event => key(event, 0));
    on(canvas, 'beforeinput', event => { if (event.data) { beforeInputSeen = true; this.emit({ type:'text', text:event.data }); } });
    // Round 86b: the right button belongs to the game (aim, its own context menus), so the browser's menu must never open over the page
    // (listening on the document also covers the HUD/overlay elements and Ctrl+click on macOS); the middle button must not start autoscroll.
    on(document, 'contextmenu', event => event.preventDefault());
    on(canvas, 'auxclick', event => event.preventDefault());
    on(canvas, 'pointerdown', event => {
      event.preventDefault(); // no text selection/drag; on Linux the context menu opens on pointerdown of the right button
      canvas.focus(); try { canvas.setPointerCapture(event.pointerId); } catch { /* synthetic or already-ended pointer */ }
      const button = [0,2,1][event.button] ?? event.button;
      this.buttons.add(button); (this.latched ??= new Set()).add(button); this.emit({ type:'button', button, action:1, mods:mods(event) });
    });
    on(canvas, 'pointerup', event => {
      const button = [0,2,1][event.button] ?? event.button;
      this.buttons.delete(button); this.emit({ type:'button', button, action:0, mods:mods(event) });
      this.sync(event);
    });
    on(canvas, 'pointermove', event => {
      this.sync(event);
      const rect = canvas.getBoundingClientRect();
      this.emit({ type:'cursor', x:(event.clientX-rect.left)*canvas.width/rect.width,
        y:(event.clientY-rect.top)*canvas.height/rect.height, dx:event.movementX, dy:event.movementY });
    });
    on(canvas, 'wheel', event => {
      const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? canvas.clientHeight : 1;
      this.emit({ type:'scroll', x:-event.deltaX*scale, y:-event.deltaY*scale });
      event.preventDefault(); // no page scroll / Ctrl+wheel browser zoom
    }, { passive:false });
    for (const target of [canvas, window]) on(target, 'blur', () => this.releaseAll());
    on(canvas, 'pointercancel', () => this.releaseAll());
    on(document, 'visibilitychange', () => { if (document.hidden) this.releaseAll(); });
  }
  // A button whose up/down is missing sticks or vanishes in the game, so under pressure only cursor/scroll events are dropped.
  emit(event) {
    if (event.type === 'cursor') {
      const last = this.events[this.events.length - 1];
      if (last?.type === 'cursor') { event.dx += last.dx; event.dy += last.dy; this.events[this.events.length - 1] = event; return; }
    }
    if (this.events.length >= 512) {
      const i = this.events.findIndex(e => e.type === 'cursor' || e.type === 'scroll');
      if (i >= 0) { this.events.splice(i, 1); this.droppedEvents++; }
      else if (event.type === 'cursor' || event.type === 'scroll') { this.droppedEvents++; return; }
    }
    this.events.push(event);
  }
  // Release buttons the browser stopped reporting (up outside the page, chords): event.buttons bits 0/1/2 = GLFW 0/1/2.
  sync(event) {
    if (typeof event.buttons !== 'number') return;
    for (const button of [...this.buttons]) if (button < 3 && !((event.buttons >> button) & 1)) { this.buttons.delete(button); this.emit({ type:'button', button, action:0 }); }
  }
  drain() { return this.events.splice(0); }
  releaseAll() {
    for (const key of this.keys) this.emit({ type:'key', key, action:0 });
    for (const button of this.buttons) this.emit({ type:'button', button, action:0 });
    this.keys.clear(); this.buttons.clear(); this.latched?.clear(); this.latchedKeys?.clear();
  }
  resize() {
    const ratio = 1; // see window.mjs create(): buffer == window size
    this.canvas.width = Math.max(1, Math.round(this.canvas.clientWidth * ratio));
    this.canvas.height = Math.max(1, Math.round(this.canvas.clientHeight * ratio));
    return [this.canvas.width, this.canvas.height];
  }
  async lockPointer() { await this.canvas.requestPointerLock(); }
  gamepads() {
    return [...navigator.getGamepads()].filter(Boolean).map(pad => {
      if (pad.mapping !== 'standard') throw new Error(`Unsupported gamepad mapping: ${pad.id}`);
      return { index:pad.index, axes:[...pad.axes], buttons:pad.buttons.map(button => button.value) };
    });
  }
  dispose() { this.releaseAll(); this.controller.abort(); }
}
