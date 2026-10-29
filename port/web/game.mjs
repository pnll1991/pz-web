import { sqliteBridge } from '/runtime/sqlite.mjs';
import { bulletBridge } from '/runtime/bullet.mjs';
import { GLBridge } from '/runtime/gl.mjs';
import { CanvasInput } from '/runtime/input.mjs';
import { BrowserWindow } from '/runtime/window.mjs';
import { VirtualFileSystem, normalizePath } from '/runtime/vfs.mjs';
import { preloadPacks } from '/runtime/packs.mjs';

const status = document.getElementById('game-status');
// TeaVM derives Java frames from V8 stacks; the default ten frames hide callers.
// Diagnostic depth for round 28: the RangeError reported in rounds 27b/28 has a repeating
// Fiber::push/reversePush/Arrays::copyOf cycle. A higher limit keeps the base frames of that
// recursion visible, which is what identifies the real Java caller.
if (typeof Error.stackTraceLimit === 'number') Error.stackTraceLimit = Infinity;
// Round 31: the render fiber's last boundary is Thread#sleep(J)V:entered and it is never resumed,
// while the wrapped teavmAsync.offer reported exactly one offer (the renderThread start). To find
// out which JS path really registers timers here, observe setTimeout itself: who calls it, with
// which delay, and how many timers actually fire. Observation only; the call still happens.
const timerObs = globalThis.pzTimers = { calls: 0, fired: 0, bySite: {}, items: [] };
const baseSetTimeout = globalThis.setTimeout.bind(globalThis);
globalThis.setTimeout = (fn, ms, ...rest) => {
  timerObs.calls++;
  const site = ((new Error()).stack ?? '').split('\n').slice(1, 4)
    .map((line) => line.trim().slice(0, 90)).join(' | ');
  timerObs.bySite[site] = (timerObs.bySite[site] ?? 0) + 1;
  if (timerObs.items.length < 40) timerObs.items.push({ n: timerObs.calls, ms: ms ?? null, site });
  return baseSetTimeout(() => { timerObs.fired++; fn(); }, ms, ...rest);
};
try {
  const response = await fetch('/game/build-state.json');
  if (!response.ok) throw new Error('Todavía no hay una compilación del juego.');
  const build = await response.json();
  if (!build.success || build.mode !== 'game-main') throw new Error('La compilación real del juego está bloqueada. Consultá el reporte local de TeaVM.');
  if (!crossOriginIsolated) throw new Error('Faltan COOP/COEP.');
  // Round 32: the boundary transformer emits int ids (port.shim.BrowserProgress.pulse(int)) because
  // a String constant injected in a frame that resumes after a suspend is still unmaterialized on
  // WasmGC and crashed the module (diag56). Decode the ids here, before boot.wasm runs, so the
  // diagnostics keep their label shape. A missing map only silences the diagnostics; it never
  // changes the game.
  try {
    const pulseResponse = await fetch('/game/pulse-map.json');
    if (pulseResponse.ok) globalThis.pzPulseLabels = await pulseResponse.json();
  } catch (error) { console.warn('pulse-map no disponible:', error.message); }
  // Round 32: suspending the render fiber corrupts TeaVM's fiber state while the game fiber is
  // also parked (see port/shim/BrowserYield). Round 32 measurement: with suspension OFF the page
  // never returns to the JS event loop (Playwright evaluate/screenshot both time out, diag64), so the
  // default is suspension ON; the wasm gate skips the suspend while a render-context item is in
  // flight, which is the only window where two fibers are parked. '?suspend=0' disables it.
  globalThis.pzYieldSuspend = new URLSearchParams(location.search).get('suspend') !== '0';
  const canvas = document.getElementById('game-canvas');
  canvas.hidden = false;
  const vfs = await new VirtualFileSystem().open();
  await preloadPacks(vfs);
  globalThis.pzRuntime = { vfs, normalizePath, gl:new GLBridge(canvas), input:new CanvasInput(canvas) };
  pzRuntime.window = new BrowserWindow(canvas,pzRuntime.gl,pzRuntime.input);
  await pzRuntime.window.prepare();
  pzRuntime.sqlite = await sqliteBridge(vfs);
  // The wasm32 Bullet module is the only library this runtime honestly provides.
  pzRuntime.bullet = await bulletBridge();
  pzRuntime.loadedLibraries = pzRuntime.bullet.loadedFor;
  // Round 31: the render fiber parks in Thread.sleep(0L) and no Fiber#resume ever happens. On
  // WasmGC that suspension is resumed by TeaVM's own timer import (teavmAsync.offer -> setTimeout
  // -> EventQueue.run -> AsyncCallback.complete). Wrap that import through the loader's documented
  // installImports hook to observe, without changing it: how many events TeaVM hands to the timer,
  // how many timers actually fire, whether the fired callback throws, and how many are cancelled.
  const asyncObs = globalThis.pzAsync = { offers: 0, fired: 0, errors: 0, kills: 0, errorsSeen: [], last: [] };
  const runtime = await TeaVM.wasmGC.load('/game/boot.wasm', {
    installImports(imports) {
      const base = imports.teavmAsync;
      if (!base || typeof base.offer !== 'function' || typeof base.kill !== 'function')
        throw new Error('TeaVM loader exposes no teavmAsync.offer/kill import: the timer path cannot be observed.');
      imports.teavmAsync = {
        offer(event, callback, time) {
          const delay = Math.max(0, time - Date.now());
          asyncObs.offers++;
          // Round 38: which Java frame handed the event to the timer? Two fibers are parked and only
          // an EventQueue event can wake one of them, so the caller identity decides who is at fault.
          let frames = [];
          try {
            const limit = Error.stackTraceLimit;
            Error.stackTraceLimit = 16;
            frames = String((new Error()).stack || '').split('\n')
              .map(function (s) { return s.trim(); })
              .filter(function (s) { return s.indexOf('::') >= 0; }).slice(0, 8);
            Error.stackTraceLimit = limit;
          } catch (e) { frames = []; }
          asyncObs.last.push({ n: asyncObs.offers, delay, t: performance.now(), f: frames });
          if (asyncObs.last.length > 40) asyncObs.last.splice(0, asyncObs.last.length - 40);
          return setTimeout(() => {
            asyncObs.fired++;
            try { callback(event); }
            catch (error) {
              asyncObs.errors++;
              if (asyncObs.errorsSeen.length < 5) asyncObs.errorsSeen.push(`${error.name}: ${error.message}`);
              throw error;
            }
          }, delay);
        },
        kill(id) { asyncObs.kills++; return base.kill(id); }
      };
    }
  });
  // The browser runtime provides no fmod native library. Passing the game's own documented
  // -nosound argument makes MainScreenState parse it into Core.soundDisabled, which is the real
  // path the desktop game takes when sound is off: GameWindow.mainThreadInit then installs
  // DummySoundManager/DummyAmbientStreamManager instead of calling FMODManager.init(). Sound is
  // genuinely absent here, so this reports that honestly rather than faking a fmod bridge.
  runtime.exports.main(['-nosound']);
  const phases = { starting:'Arranque real en curso…', failed:'El arranque encontró un error.', 'main-returned':'El main retornó. Menú todavía no verificado.' };
  const update = () => {
    status.textContent = phases[globalThis.pzGame?.phase] || 'Esperando el arranque Java…';
    document.getElementById('game-error').textContent = globalThis.pzGame?.error || '';
    requestAnimationFrame(update);
  };
  update();
} catch (error) {
  globalThis.pzGame = { phase:'loader-failed', error:`${error.name}: ${error.message}`, menuRendered:false };
  status.textContent = 'Juego pendiente de integración';
  document.getElementById('game-error').textContent = error.message;
  console.error(error);
}
