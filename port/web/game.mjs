import { sqliteBridge } from '/runtime/sqlite.mjs';
import { bulletBridge } from '/runtime/bullet.mjs';
import { GLBridge } from '/runtime/gl.mjs';
import { CanvasInput } from '/runtime/input.mjs';
import { BrowserWindow } from '/runtime/window.mjs';
import { VirtualFileSystem, normalizePath } from '/runtime/vfs.mjs';
import { preloadPacks } from '/runtime/packs.mjs';

const status = document.getElementById('game-status');
// TeaVM derives Java frames from V8 stacks; the default ten frames hide callers.
if (typeof Error.stackTraceLimit === 'number') Error.stackTraceLimit = Math.max(Error.stackTraceLimit,64);
try {
  const response = await fetch('/game/build-state.json');
  if (!response.ok) throw new Error('Todavía no hay una compilación del juego.');
  const build = await response.json();
  if (!build.success || build.mode !== 'game-main') throw new Error('La compilación real del juego está bloqueada. Consultá el reporte local de TeaVM.');
  if (!crossOriginIsolated) throw new Error('Faltan COOP/COEP.');
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
  const runtime = await TeaVM.wasmGC.load('/game/boot.wasm');
  runtime.exports.main([]);
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
