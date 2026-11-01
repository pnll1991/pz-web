// Ronda 81 frente 4: sonda de capacidades de hilos en Chrome (sin código del juego).
const R = globalThis.f4 = { done: false, stage: 'inicio', stageMs: {} };
const T0 = performance.now(); const stage = (name) => { R.stageMs[R.stage] = Math.round(performance.now() - T0); R.stage = name; };
const sec = (id, b) => [id, b.length, ...b];
function gcModule() {
  const type = sec(1, [0x02, 0x5f, 0x01, 0x7f, 0x01, 0x60, 0x00, 0x01, 0x64, 0x00]);
  const enc = new TextEncoder().encode('make');
  const body = [0x00, 0x41, 0x2a, 0xfb, 0x00, 0x00, 0x0b];
  return new Uint8Array([0, 0x61, 0x73, 0x6d, 1, 0, 0, 0, ...type, ...sec(3, [1, 1]), ...sec(7, [1, enc.length, ...enc, 0, 0]), ...sec(10, [1, body.length, ...body])]);
}
const FILE = '/media/binary.dat', BLOCK = 1 << 20, N = +(new URLSearchParams(location.search).get('n') || 48);
function syncTextRange(start, end) { // igual que vfs.mjs fetchRange en el hilo principal
  const xhr = new XMLHttpRequest();
  xhr.open('GET', FILE + `?r=${start}-${end}`, false);
  xhr.overrideMimeType('text/plain; charset=x-user-defined');
  xhr.setRequestHeader('Range', `bytes=${start}-${end - 1}`); xhr.setRequestHeader('Cache-Control', 'no-store');
  xhr.send();
  const t = xhr.responseText, out = new Uint8Array(t.length);
  for (let i = 0; i < t.length; i++) out[i] = t.charCodeAt(i) & 0xff;
  return out;
}
const workerSrc = `
const FILE=${JSON.stringify(FILE)};
onmessage = async (e) => {
  const m = e.data;
  if (m.op === 'xhr') { const t0 = performance.now(); let sum = 0;
    for (let i = 0; i < m.n; i++) { const s = m.base + i * m.block; const x = new XMLHttpRequest();
      x.open('GET', FILE + '?w=' + s, false); x.responseType = 'arraybuffer';
      x.setRequestHeader('Range', 'bytes=' + s + '-' + (s + m.block - 1)); x.setRequestHeader('Cache-Control', 'no-store'); x.send();
      sum += new Uint8Array(x.response)[7]; }
    postMessage({ op: 'xhr', ms: performance.now() - t0, sum }); }
  else if (m.op === 'fetch') { const t0 = performance.now();
    await Promise.all(Array.from({ length: m.n }, (_, i) => { const s = m.base + i * m.block;
      return fetch(FILE + '?f=' + s, { headers: { Range: 'bytes=' + s + '-' + (s + m.block - 1) }, cache: 'no-store' }).then(r => r.arrayBuffer()); }));
    postMessage({ op: 'fetch', ms: performance.now() - t0 }); }
  else if (m.op === 'gl') { let r = {};
    try { const gl = m.canvas.getContext('webgl2'); r.webgl2 = !!gl; r.renderer = gl && gl.getParameter(gl.VERSION);
      r.raf = typeof requestAnimationFrame === 'function';
      if (r.raf) r.rafMs = await new Promise(res => { const t = performance.now(); requestAnimationFrame(() => res(performance.now() - t)); });
    } catch (err) { r.error = String(err); }
    postMessage({ op: 'gl', r }); }
  else if (m.op === 'pong') { const a = new Int32Array(m.sab); // servidor: espera pedido en [0], responde en [1]
    postMessage({ op: 'pong-ready' });
    for (;;) { Atomics.wait(a, 0, 0); const v = Atomics.exchange(a, 0, 0); if (v < 0) break; Atomics.store(a, 1, v + 1); Atomics.notify(a, 1); } }
  else if (m.op === 'ping') { const a = new Int32Array(m.sab); const t0 = performance.now();
    for (let i = 1; i <= m.n; i++) { Atomics.store(a, 1, 0); Atomics.store(a, 0, i); Atomics.notify(a, 0); Atomics.wait(a, 1, 0); }
    const ms = performance.now() - t0; Atomics.store(a, 0, -1); Atomics.notify(a, 0); postMessage({ op: 'ping', ms, n: m.n }); }
  else if (m.op === 'atomicswait') { try { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1); postMessage({ op: m.op, ok: true }); } catch (err) { postMessage({ op: m.op, ok: false, err: String(err) }); } }
};`;
const url = URL.createObjectURL(new Blob([workerSrc], { type: 'text/javascript' }));
const mk = () => new Worker(url);
const ask = (w, msg, transfer = []) => new Promise(res => { const h = e => { if (e.data.op === msg.op) { w.removeEventListener('message', h); res(e.data); } }; w.addEventListener('message', h); w.postMessage(msg, transfer); });
try {
  R.chrome = navigator.userAgent; R.crossOriginIsolated = crossOriginIsolated; R.cores = navigator.hardwareConcurrency;
  stage('gc');
  // 1. objeto Wasm GC a otro hilo
  const { instance } = await WebAssembly.instantiate(gcModule());
  const obj = instance.exports.make(); const w0 = mk();
  try { w0.postMessage(obj); R.gcPost = 'OK'; } catch (e) { R.gcPost = `${e.name}: ${e.message}`; }
  try { structuredClone(obj); R.gcClone = 'OK'; } catch (e) { R.gcClone = `${e.name}: ${e.message}`; }
  try { await WebAssembly.compile(new Uint8Array([0,0x61,0x73,0x6d,1,0,0,0,...sec(1,[1,0x65,0x5f,1,0x7f,1])])); R.sharedStructCompile = 'OK'; } catch (e) { R.sharedStructCompile = `${e.name}: ${e.message}`; }
  stage('atomics');
  // 2. Atomics.wait en el hilo principal / en un worker
  try { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1); R.mainAtomicsWait = 'OK'; } catch (e) { R.mainAtomicsWait = `${e.name}: ${e.message}`; }
  R.workerAtomicsWait = await ask(w0, { op: 'atomicswait' });
  stage('offscreen');
  // 3. OffscreenCanvas + WebGL2 + rAF en un worker
  const off = document.getElementById('c').transferControlToOffscreen();
  R.offscreen = (await ask(w0, { op: 'gl', canvas: off }, [off])).r;
  stage('synccall');
  // 4. latencia de una llamada síncrona worker->worker por SAB + Atomics
  const sab = new SharedArrayBuffer(8), srv = mk(), cli = mk();
  await ask(srv, { op: 'pong', sab });
  const p = await ask(cli, { op: 'ping', sab, n: 20000 });
  R.syncCallUs = +(p.ms * 1000 / p.n).toFixed(2);
  stage('remote');
  // 5. lectura remota de 1 MiB por bloque: hilo principal (texto) vs worker (arraybuffer) vs fetch async en paralelo
  let t0 = performance.now(), s = 0;
  for (let i = 0; i < N; i++) s += syncTextRange(i * BLOCK, (i + 1) * BLOCK)[7];
  R.mainSyncTextMsPerMB = +((performance.now() - t0) / N).toFixed(2);
  const big = syncTextRange(0, BLOCK); const txt = String.fromCharCode(...big.subarray(0, 0)); // calienta
  { const x = new XMLHttpRequest(); x.open('GET', FILE + '?d', false); x.overrideMimeType('text/plain; charset=x-user-defined');
    x.setRequestHeader('Range', `bytes=0-${BLOCK - 1}`); x.setRequestHeader('Cache-Control', 'no-store'); x.send();
    const t = x.responseText; const t1 = performance.now(); const o = new Uint8Array(t.length); for (let i = 0; i < t.length; i++) o[i] = t.charCodeAt(i) & 0xff; R.mainDecodeMsPerMB = +(performance.now() - t1).toFixed(2); }
  const wx = await ask(w0, { op: 'xhr', n: N, base: N * BLOCK, block: BLOCK }); R.workerSyncArrayBufferMsPerMB = +(wx.ms / N).toFixed(2);
  const wf = await ask(w0, { op: 'fetch', n: N, base: 2 * N * BLOCK, block: BLOCK }); R.workerParallelFetchMsPerMB = +(wf.ms / N).toFixed(2);
  // 64 KiB: tamaño típico de chunkdata/lotheader pequeños
  t0 = performance.now(); for (let i = 0; i < 200; i++) syncTextRange(3 * N * BLOCK + i * 65536, 3 * N * BLOCK + (i + 1) * 65536);
  R.mainSync64KMs = +((performance.now() - t0) / 200).toFixed(3);
  const w64 = await ask(w0, { op: 'xhr', n: 200, base: 4 * N * BLOCK, block: 65536 }); R.workerSync64KMs = +(w64.ms / 200).toFixed(3);

  stage('sha256');
  // 6. SHA-256 (crypto.subtle) de 256 MiB en trozos de 4 MiB: secuencial (como packs.mjs) vs en paralelo
  if (new URLSearchParams(location.search).get('hash') !== '0') {
    const buf = new Uint8Array(256 << 20); for (let i = 0; i < buf.length; i += 4096) buf[i] = i & 255;
    const parts = Array.from({ length: 64 }, (_, i) => buf.subarray(i << 22, (i + 1) << 22));
    let h0 = performance.now(); for (const q of parts) await crypto.subtle.digest('SHA-256', q); R.sha256SeqMsPer256MB = Math.round(performance.now() - h0);
    h0 = performance.now(); await Promise.all(parts.map(q => crypto.subtle.digest('SHA-256', q))); R.sha256ParMsPer256MB = Math.round(performance.now() - h0);
    h0 = performance.now(); let busy = 0; const pr = Promise.all(parts.map(q => crypto.subtle.digest('SHA-256', q)));
    let fin = false; pr.then(() => { fin = true; }); while (!fin) { const t = performance.now(); while (performance.now() - t < 20) busy++; await new Promise(r => setTimeout(r, 0)); }
    R.sha256ParWithMainBusyMs = Math.round(performance.now() - h0);
  }
  stage('wasm');
  // 7. compileStreaming de /game/boot.wasm (lo que hace el cargador TeaVM) y si deja libre el hilo principal
  if (new URLSearchParams(location.search).get('wasm') !== '0') {
    let c0 = performance.now(); let ticks = 0, maxGap = 0, last = performance.now(), done = false;
    const comp = WebAssembly.compileStreaming(fetch('/game/boot.wasm'), { builtins: ['js-string'] }).then(m => { done = true; return m; }, e => { done = true; R.wasmCompileError = String(e); });
    while (!done) { await new Promise(r => setTimeout(r, 0)); const n = performance.now(); maxGap = Math.max(maxGap, n - last); last = n; ticks++; }
    await comp; R.bootWasmCompileStreamingMs = Math.round(performance.now() - c0); R.mainMaxGapDuringCompileMs = Math.round(maxGap); R.mainTicksDuringCompile = ticks;
  }
} catch (e) { R.error = `${e.name}: ${e.message}`; }
stage('fin'); R.done = true; document.getElementById('out').textContent = JSON.stringify(R, null, 1);
