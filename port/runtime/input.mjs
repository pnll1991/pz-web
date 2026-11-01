const special = { Space:32, Quote:39, Comma:44, Minus:45, Period:46, Slash:47, Semicolon:59, Equal:61,
  BracketLeft:91, Backslash:92, BracketRight:93, Backquote:96, Escape:256, Enter:257, Tab:258,
  Backspace:259, Insert:260, Delete:261, ArrowRight:262, ArrowLeft:263, ArrowDown:264, ArrowUp:265,
  PageUp:266, PageDown:267, Home:268, End:269, CapsLock:280, ScrollLock:281, NumLock:282,
  PrintScreen:283, Pause:284, ShiftLeft:340, ControlLeft:341, AltLeft:342, MetaLeft:343,
  ShiftRight:344, ControlRight:345, AltRight:346, MetaRight:347, ContextMenu:348 };

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
    const on = (target, type, callback) => target.addEventListener(type, callback, { signal: this.controller.signal });
    const key = (event, action) => {
      const code = glfwKey(event.code);
      if (code < 0) return;
      if (action) this.keys.add(code); else this.keys.delete(code);
      this.emit({ type:'key', key:code, action:action && event.repeat ? 2 : action });
      if (['Space','Tab','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.code)) event.preventDefault();
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
    on(canvas, 'pointerdown', event => {
      canvas.focus(); canvas.setPointerCapture(event.pointerId);
      const button = [0,2,1][event.button] ?? event.button;
      this.buttons.add(button); (this.latched ??= new Set()).add(button); this.emit({ type:'button', button, action:1 });
    });
    on(canvas, 'pointerup', event => {
      const button = [0,2,1][event.button] ?? event.button;
      this.buttons.delete(button); this.emit({ type:'button', button, action:0 });
    });
    on(canvas, 'pointermove', event => {
      const rect = canvas.getBoundingClientRect();
      this.emit({ type:'cursor', x:(event.clientX-rect.left)*canvas.width/rect.width,
        y:(event.clientY-rect.top)*canvas.height/rect.height, dx:event.movementX, dy:event.movementY });
    });
    on(canvas, 'wheel', event => {
      const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? canvas.clientHeight : 1;
      this.emit({ type:'scroll', x:-event.deltaX*scale, y:-event.deltaY*scale });
    });
    for (const target of [canvas, window]) on(target, 'blur', () => this.releaseAll());
    on(canvas, 'pointercancel', () => this.releaseAll());
    on(document, 'visibilitychange', () => { if (document.hidden) this.releaseAll(); });
  }
  emit(event) {
    if (this.events.length === 512) { this.events.shift(); this.droppedEvents++; }
    this.events.push(event);
  }
  drain() { return this.events.splice(0); }
  releaseAll() {
    for (const key of this.keys) this.emit({ type:'key', key, action:0 });
    for (const button of this.buttons) this.emit({ type:'button', button, action:0 });
    this.keys.clear(); this.buttons.clear();
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
