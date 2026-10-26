import { GLBridge } from '/runtime/gl.mjs';
import { CanvasInput } from '/runtime/input.mjs';
import { VirtualFileSystem, normalizePath } from '/runtime/vfs.mjs';
import { preloadPacks } from '/runtime/packs.mjs';

const el = id => document.getElementById(id);
const fail = error => {
  el('status').textContent = 'ERROR';
  el('error').hidden = false; el('error').textContent = error.message;
  console.error(error);
};
try {
  if (!crossOriginIsolated) throw new Error('Faltan COOP/COEP. Usá el servidor de desarrollo local.');
  el('isolation').textContent = 'OK';
  const canvas = el('canvas');
  const input = new CanvasInput(canvas);
  input.resize();
  const gl = new GLBridge(canvas);
  gl.viewport(0, 0, canvas.width, canvas.height);
  const vfs = await new VirtualFileSystem().open();
  const probe = await fetch('/probe.txt');
  if (!probe.ok) throw new Error(`Recurso de prueba HTTP ${probe.status}`);
  vfs.mount('/media/platform-probe.txt', new Uint8Array(await probe.arrayBuffer()));
  el('vfs').textContent = 'OK';
  globalThis.pzRuntime = { gl, vfs, input, normalizePath };
  if (!globalThis.TeaVM) throw new Error('Falta el runtime de TeaVM: ejecutá scripts/build.sh');
  const teavm = await TeaVM.wasmGC.load('/boot.wasm');
  teavm.exports.main([]);
  if (!globalThis.pzBoot?.ready) throw new Error('El entrypoint Java no terminó');
  const pixel = gl.drawProbe();
  el('wasm').textContent = 'OK';
  el('native').textContent = 'ERROR EXPLÍCITO';
  el('buffer').textContent = `${pzBoot.bufferChecksum} / 170`;
  el('pixel').textContent = pixel.slice(0, 3).join(' · ');
  el('status').textContent = 'PLATAFORMA LISTA';
  const measurements = { samples:[], frames:0, started:performance.now(), paused:false };
  let previous;
  function frame(now) {
    try {
      if (!measurements.paused) {
        measurements.frames = teavm.exports.frame(now);
        if (previous !== undefined) {
          measurements.samples.push(now - previous);
          if (measurements.samples.length > 600) measurements.samples.shift();
        }
        previous = now;
        if (measurements.frames % 30 === 0) el('frames').textContent = `${measurements.frames} frames`;
      } else previous = undefined;
      requestAnimationFrame(frame);
    } catch (error) { fail(error); }
  }
  requestAnimationFrame(frame);
  function metrics() {
    const samples = [...measurements.samples].sort((a,b) => a-b);
    const percentile = fraction => samples[Math.min(samples.length - 1, Math.floor(samples.length * fraction))] ?? null;
    return { frames:measurements.frames, frame_ms_p50:percentile(.5), frame_ms_p95:percentile(.95),
      js_heap_bytes:performance.memory?.usedJSHeapSize ?? null, vfs_bytes:vfs.byteLength,
      gl_calls:{ ...gl.calls }, sample_count:samples.length,
      note:'Platform probe only; JS heap does not measure total GPU or process memory.' };
  }
  const savePath = '/Zomboid/platform-note.txt';
  const encoder = new TextEncoder(), decoder = new TextDecoder();
  if (vfs.exists(savePath)) {
    el('note').value = decoder.decode(vfs.readFile(savePath));
    el('saved').textContent = 'Archivo recuperado después de recargar.';
  } else el('saved').textContent = 'Todavía no hay un archivo guardado.';
  el('save').disabled = false;
  el('note').addEventListener('beforeinput', event => {
    if (!event.isComposing && event.data) input.emit({ type:'text', text:event.data });
  });
  el('note').addEventListener('compositionend', event => {
    if (event.data) input.emit({ type:'text', text:event.data });
  });
  el('save-form').addEventListener('submit', async event => {
    event.preventDefault(); el('save').disabled = true;
    try {
      vfs.writeFile(savePath + '.tmp', encoder.encode(el('note').value));
      vfs.rename(savePath + '.tmp', savePath);
      await vfs.flush();
      el('saved').textContent = 'Guardado. Ya podés recargar la página.';
    } catch (error) { fail(error); }
    finally { el('save').disabled = false; }
  });
  el('pause').addEventListener('click', () => {
    measurements.paused = !measurements.paused;
    el('pause').textContent = measurements.paused ? 'Reanudar loop' : 'Pausar loop';
  });
  window.addEventListener('resize', () => {
    input.resize(); gl.viewport(0, 0, canvas.width, canvas.height); gl.drawProbe();
  });
  globalThis.pzLab = { vfs, gl, input, teavm, pixel, metrics, preloadPacks };
  el('diagnostics').textContent = JSON.stringify({ ...pzBoot, pixel, ...metrics() }, null, 2);
  document.querySelector('details').addEventListener('toggle', () => {
    el('diagnostics').textContent = JSON.stringify({ ...pzBoot, pixel, ...metrics() }, null, 2);
  });
} catch (error) { fail(error); }
