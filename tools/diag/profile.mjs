// Ronda 81 (frente 3): perfiles de CPU (CDP Profiler, muestreo) del arranque y de la partida, y su resumen.
//
//   flock /tmp/pz80-locks/chrome.lock node tools/diag/profile.mjs boot   [opciones]
//     Perfila desde page.goto hasta N renders de MainScreenState (acepta el ToS con un clic real).
//   flock /tmp/pz80-locks/chrome.lock node tools/diag/profile.mjs ingame [opciones]
//     Arranca con ?persist=1 en un perfil de Chrome persistente (--userdir, el de la partida guardada),
//     ToS -> menú -> CONTINUE (clic en --continue X,Y) -> "Click to Start" -> calentamiento, y perfila
//     la carga (CONTINUE -> "game loading took", --load-profile 0 la omite) y dos ventanas: quieto (--idle s)
//     y caminando (--walk s, teclas rotando S,D,A,W cada 4 s; --keys). Build: --query 'build=release-minpulse&persist=1&texcache=1'.
//   node tools/diag/profile.mjs report ARCHIVO.cpuprofile [--top 15] [--wasm port/target/web/game/boot.wasm]
//     Self/total por función, por archivo JS y por categoría (Java del juego por paquete, shims port.*,
//     gl.mjs, vfs.mjs, runtime TeaVM/GC/fibras, frontera wasm<->JS, nativas del navegador).
//
// Opciones de captura: --out DIR (/tmp/pz81/prof), --us 200 (intervalo de muestreo en µs), --timeout s,
//   --menu-renders 5, --tos X,Y (610,515), --continue X,Y (109,172), --start X,Y ("Click to Start", 640,400),
//   --warm s (20), --idle s (30), --walk s (30), --userdir DIR, --query 'persist=1'.
// Entorno: PZ_GPU, PZ_CHROME_HEADED (chrome-flags.mjs), PZ_DIAG_PORT (8098).
// Cada fase escribe DIR/<fase>.cpuprofile y DIR/<fase>.json (duración, frames = swapBuffers, fps) y
// DIR/events.txt (consola relevante con tiempos). Nunca usa el perfil del usuario ni otro puerto.
import { readFileSync, writeFileSync, appendFileSync, mkdirSync } from 'node:fs';

const argv = process.argv.slice(2);
const mode = argv[0];
const opt = (k, d) => { const i = argv.indexOf(`--${k}`); return i >= 0 ? argv[i + 1] : d; };
const xy = s => s.split(',').map(Number);

if (mode === 'report') { report(argv[1], +opt('top', 15), opt('wasm', null), +opt('from', 0), +opt('to', Infinity)); process.exit(0); }
if (mode !== 'boot' && mode !== 'ingame') {
  console.error('uso: profile.mjs boot|ingame [opciones] | report ARCHIVO.cpuprofile'); process.exit(2);
}

const { chromium } = await import('@playwright/test');
const { launchOptions, userDataDir } = await import('./chrome-flags.mjs');
const D = opt('out', '/tmp/pz81/prof'); mkdirSync(D, { recursive: true });
const PORT = process.env.PZ_DIAG_PORT || '8098';
const US = +opt('us', 200);
const TIMEOUT = +opt('timeout', 900) * 1000;
const ev = `${D}/events.txt`; writeFileSync(ev, '');
let t0 = Date.now();
const t = () => ((Date.now() - t0) / 1000).toFixed(1);
const note = s => { appendFileSync(ev, `${t()} ${s}\n`); console.log(`${t()} ${s}`); };

const lo = launchOptions();
let context, browser;
if (mode === 'ingame') {
  const dir = opt('userdir', null) || userDataDir('pz81prof');
  context = await chromium.launchPersistentContext(dir, { ...lo, viewport: { width: 1280, height: 720 } });
  note(`userdir ${dir}`);
} else {
  browser = await chromium.launch(lo);
  context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
}
const page = context.pages()[0] || await context.newPage();
const consoleLines = [];
page.on('console', m => { const s = m.text();
  if (/STATE:|Click to Start|Exception|game loading took|LOADING/.test(s)) { consoleLines.push(s); appendFileSync(ev, `${t()} C ${s.slice(0, 300)}\n`); } });
page.on('pageerror', e => appendFileSync(ev, `${t()} E ${String(e.message).slice(0, 300)}\n`));
const cdp = await context.newCDPSession(page);
const capped = (p, ms, d) => Promise.race([p, new Promise(r => setTimeout(() => r(d), ms))]).catch(() => d);
const counts = k => capped(page.evaluate(k => Object.entries(globalThis.pzGameCounts || {})
  .filter(([x]) => x.includes(k) && x.endsWith(':returned')).reduce((s, [, v]) => s + v, 0), k), 10000, -1);
const swaps = () => capped(page.evaluate(() => globalThis.pzRuntime?.window?.swapCount ?? -1), 10000, -1);
const sawConsole = re => consoleLines.some(s => re.test(s));
async function waitFor(what, test, ms = TIMEOUT, every = 2000) {
  const end = Date.now() + ms;
  while (Date.now() < end) { if (await test()) { note(`REACHED ${what}`); return true; } await page.waitForTimeout(every); }
  note(`TIMEOUT ${what}`); return false;
}
// Como live.mjs/bench.mjs: mover, esperar, down 120 ms, up.
async function click([x, y], hold = 120) { note(`click ${x},${y}`); await page.mouse.move(x, y); await page.waitForTimeout(300); await page.mouse.down(); await page.waitForTimeout(hold); await page.mouse.up(); }
async function startProfile() {
  await cdp.send('Profiler.enable');
  await cdp.send('Profiler.setSamplingInterval', { interval: US });
  await cdp.send('Profiler.start');
}
async function stopProfile(phase, extra) {
  const t1 = Date.now();
  const { profile } = await cdp.send('Profiler.stop');
  writeFileSync(`${D}/${phase}.cpuprofile`, JSON.stringify(profile));
  // Los @JSBody de TeaVM son `new Function(...)`: scripts sin URL que el perfil muestra como "(anon)".
  // Guardar su fuente (recortada) para que el resumen los nombre por su cuerpo.
  const ids = [...new Set(profile.nodes.filter(n => !n.callFrame.url && n.callFrame.scriptId !== '0' && +n.callFrame.scriptId > 0).map(n => n.callFrame.scriptId))];
  const src = {};
  try {
    await capped(cdp.send('Debugger.enable'), 60000);
    for (const scriptId of ids) { const r = await capped(cdp.send('Debugger.getScriptSource', { scriptId }), 10000, null); if (r) src[scriptId] = r.scriptSource.slice(0, 2000); }
    await capped(cdp.send('Debugger.disable'), 10000);
  } catch (e) { note(`scripts: ${String(e).slice(0, 200)}`); }
  writeFileSync(`${D}/${phase}.scripts.json`, JSON.stringify(src));
  const wasm = await capped(page.evaluate(() => fetch(`/${new URLSearchParams(location.search).get('build') || ''}/game/boot.wasm`.replace('//', '/'), { method: 'HEAD' }).then(r => `${r.headers.get('etag')} ${r.headers.get('last-modified')}`)), 15000, '?');
  const meta = { phase, us: US, wasm, durationS: (profile.endTime - profile.startTime) / 1e6, samples: profile.samples.length, stopMs: Date.now() - t1, ...extra };
  writeFileSync(`${D}/${phase}.json`, JSON.stringify(meta, null, 1));
  note(`PROFILE ${phase} ${JSON.stringify(meta)}`);
}
// ToS: aparece en cada carga (frente V). Clic hasta que salga de TermsOfServiceState.
async function acceptTos() {
  await waitFor('TermsOfServiceState', async () => (await counts('TermsOfServiceState#render')) >= 20);
  for (let i = 0; i < 5 && !sawConsole(/exit zombie\.gameStates\.TermsOfServiceState/); i++) {
    await click(xy(opt('tos', '610,515'))); await page.waitForTimeout(8000);
  }
}

try {
  const url = `http://127.0.0.1:${PORT}/game.html${opt('query', mode === 'ingame' ? 'persist=1' : '') ? '?' + opt('query', mode === 'ingame' ? 'persist=1' : '') : ''}`;
  if (mode === 'boot') {
    await startProfile();
    t0 = Date.now();
    await page.goto(url, { waitUntil: 'domcontentloaded' });
    note(`LOADED ${url}`);
    await acceptTos();
    const ok = await waitFor('menu', async () => (await counts('MainScreenState#render')) >= +opt('menu-renders', 5));
    await stopProfile('boot', { reachedMenu: ok, bootS: +t(), frames: await swaps() });
  } else {
    t0 = Date.now();
    await page.goto(url, { waitUntil: 'domcontentloaded' });
    note(`LOADED ${url}`);
    await acceptTos();
    await waitFor('menu', async () => (await counts('MainScreenState#render')) >= 5);
    await page.waitForTimeout(5000);
    // Mismas coordenadas que tools/diag/bench.mjs (front 0, ronda 81). El clic bloquea mientras carga.
    // Ventana "load" (pedido de front 4): CONTINUE -> "game loading took", para separar XHR/vfs.mjs de Java.
    const loadProf = opt('load-profile', '1') === '1';
    if (loadProf) await startProfile();
    const l0 = Date.now();
    click(xy(opt('continue', '109,172'))).catch(() => {});
    const loaded = await waitFor('game loading took', async () => sawConsole(/game loading took/), TIMEOUT, 3000);
    if (loadProf) await stopProfile('load', { wallS: (Date.now() - l0) / 1000, remote: await capped(page.evaluate(() => globalThis.pzRuntime?.vfs?.remoteStats), 15000, null) });
    if (!loaded) throw new Error('no cargó la partida');
    await page.waitForTimeout(3000);
    for (let i = 0; i < 3; i++) { await click(xy(opt('start', '640,400'))); await page.waitForTimeout(5000); }
    await page.waitForTimeout(35000);
    await click([1181, 674]); await page.waitForTimeout(5000); await click([310, 110]); await page.waitForTimeout(5000); // Survival Guide
    note(`INGAME ${sawConsole(/enter zombie\.gameStates\.IngameState/) ? '(IngameState visto)' : '(sin línea IngameState)'}`);
    await capped(page.screenshot({ path: `${D}/ingame-start.png` }), 60000);
    await page.waitForTimeout(+opt('warm', 20) * 1000);
    const win = async (phase, secs, act) => {
      const f0 = await swaps(); await startProfile(); const w0 = Date.now();
      await act(secs);
      const wall = (Date.now() - w0) / 1000; const f1 = await swaps();
      await stopProfile(phase, { wallS: wall, frames: f1 - f0, fps: +((f1 - f0) / wall).toFixed(2) });
    };
    await win('idle', +opt('idle', 30), s => page.waitForTimeout(s * 1000));
    // El jugador de /tmp/pz81/profile queda pegado a una pared (frente 0): rotar S,D,A,W cada 4 s para que
    // alguna dirección avance. --keys cambia el orden.
    const keys = opt('keys', 's,d,a,w').split(',');
    await win('walk', +opt('walk', 30), async s => {
      const end = Date.now() + s * 1000;
      for (let i = 0; Date.now() < end; i++) { const k = keys[i % keys.length];
        await page.keyboard.down(k); await page.waitForTimeout(Math.min(4000, Math.max(0, end - Date.now()))); await page.keyboard.up(k); await page.waitForTimeout(300); }
    });
    await capped(page.screenshot({ path: `${D}/ingame-end.png` }), 60000);
  }
} catch (e) { note(`FAIL ${String(e).slice(0, 300)}`); process.exitCode = 1; }
finally { await context.close().catch(() => {}); if (browser) await browser.close().catch(() => {}); }

// ---------------------------------------------------------------------------------------------------
function wasmNames(path) {
  const bytes = readFileSync(path); let p = 8; const names = [];
  const leb = () => { let v = 0, s = 0; for (;;) { const b = bytes[p++]; v += (b & 127) * 2 ** s; if (b < 128) return v; s += 7; } };
  while (p < bytes.length) {
    const id = bytes[p++], size = leb(), end = p + size;
    if (id === 0) { const nl = leb(), nm = bytes.subarray(p, p + nl).toString(); p += nl;
      if (nm === 'name') while (p < end) { const sub = bytes[p++], ss = leb(), se = p + ss;
        if (sub === 1) { const n = leb(); for (let i = 0; i < n; i++) { const idx = leb(), l = leb(); names[idx] = bytes.subarray(p, p + l).toString(); p += l; } }
        p = se; } }
    p = end;
  }
  return names;
}

/** Categoría de un frame: Java del juego por paquete, runtime TeaVM, shims, archivos JS del puerto, nativas. */
export function category(fn, url) {
  if (/^\((idle|program|garbage collector|root)\)$/.test(fn)) return `V8 ${fn}`;
  if (/^(js-to-wasm|wasm-to-js)/.test(fn) || /^(GenericJSToWasm|WasmToJs|JSToWasm)/.test(fn)) return 'frontera wasm<->JS';
  const file = url ? url.replace(/[?#].*$/, '').replace(/^.*\//, '') : '';
  if (file.endsWith('.wasm') || fn.includes('::') || fn.startsWith('teavm@')) {
    if (file && !file.startsWith('boot')) return `wasm ${file}`;
    if (fn.startsWith('teavm@')) return 'TeaVM runtime (wasm)';
    const cls = fn.split(/::|#/)[0];
    if (/^org\.teavm\.runtime\.Fiber|^org\.teavm\.interop\.Async|Fiber/.test(cls)) return 'TeaVM fibras/corrutinas';
    if (/^org\.teavm\.(runtime|backend|jso|interop)/.test(cls)) return 'TeaVM runtime (wasm)';
    if (/^org\.teavm\.classlib|^java\.|^javax\.|^jdk\.|^sun\./.test(cls)) return 'JCL TeaVM (java.*)';
    if (/^port\./.test(cls)) return `shims port.${cls.split('.')[1]}`;
    if (/^org\.lwjgl/.test(cls)) return 'LWJGL (shim/juego)';
    if (/^se\.krka\.kahlua/.test(cls)) return 'juego: Kahlua (Lua VM)';
    if (/^zombie\./.test(cls)) { const parts = cls.split('.'); return `juego: ${parts.slice(0, parts.length > 3 ? 3 : 2).join('.')}`; }
    return `libs: ${cls.split('.').slice(0, 2).join('.')}`;
  }
  if (fn.startsWith('jsbody{')) return 'JS @JSBody (sin URL)';
  if (file === 'boot.wasm-runtime.js') return 'TeaVM JS runtime (boot.wasm-runtime.js)';
  if (file) return `JS ${file}`;
  return 'nativas/builtins del navegador';
}

function report(file, TOP, wasmPath, from = 0, to = Infinity) {
  const p = JSON.parse(readFileSync(file, 'utf8'));
  const names = wasmPath ? wasmNames(wasmPath) : null;
  let scripts = {}; try { scripts = JSON.parse(readFileSync(file.replace(/\.cpuprofile$/, '.scripts.json'), 'utf8')); } catch {}
  // Cuerpo de un @JSBody: lo que sigue a la cabecera de `new Function`, sin espacios repetidos.
  const body = id => { const t = scripts[id]; if (!t) return null;
    return t.replace(/^\(function anonymous\([^)]*\n?\) \{\n?/, '').replace(/\s+/g, ' ').slice(0, 110); };
  const byId = new Map(p.nodes.map(n => [n.id, n]));
  const parent = new Map(); for (const n of p.nodes) for (const c of n.children || []) parent.set(c, n.id);
  const fname = n => { let f = n.callFrame.functionName || '(anon)'; const m = /^wasm-function\[(\d+)\]$/.exec(f) || /^\$?func(\d+)$/.exec(f);
    if (m && names && names[+m[1]]) f = names[+m[1]];
    if (f === '(anon)' && !n.callFrame.url) { const b = body(n.callFrame.scriptId); if (b) f = `jsbody{${b}}`; }
    return f; };
  const label = n => { const f = fname(n), u = n.callFrame.url ? n.callFrame.url.replace(/^.*\//, '') : '';
    return u && !u.endsWith('.wasm') ? `${f} [${u}:${n.callFrame.lineNumber + 1}]` : f; };
  const cat = n => category(fname(n), n.callFrame.url);
  const fileOf = n => { const u = n.callFrame.url; return u ? u.replace(/[?#].*$/, '').replace(/^.*\//, '') : (/^\(/.test(n.callFrame.functionName) ? n.callFrame.functionName : '(nativo)'); };
  const ms = new Map();
  // --from/--to: ventana en segundos desde el inicio del perfil.
  let ts = p.startTime;
  for (let i = 0; i < p.samples.length; i++) { ts += p.timeDeltas[i]; const s = (ts - p.startTime) / 1e6; if (s < from || s > to) continue;
    const d = (p.timeDeltas[i + 1] ?? 0) / 1000; ms.set(p.samples[i], (ms.get(p.samples[i]) || 0) + d); }
  const total = [...ms.values()].reduce((a, b) => a + b, 0);
  const add = (m, k, v) => m.set(k, (m.get(k) || 0) + v);
  const self = new Map(), tot = new Map(), fself = new Map(), cself = new Map(), ctot = new Map(), cfold = new Map();
  for (const [id, v] of ms) {
    const n = byId.get(id); add(self, label(n), v); add(fself, fileOf(n), v); add(cself, cat(n), v);
    // Nativas (WebGL, typed arrays, ...) se atribuyen al primer llamador con categoría propia.
    let x = id; while (x !== undefined && cat(byId.get(x)) === 'nativas/builtins del navegador') x = parent.get(x);
    add(cfold, x === undefined ? cat(n) : cat(byId.get(x)) + (x === id ? '' : ' (+nativas)'), v);
    const seen = new Set(), seenC = new Set();
    for (let y = id; y !== undefined; y = parent.get(y)) { const yn = byId.get(y), k = label(yn), c = cat(yn);
      if (!seen.has(k)) { seen.add(k); add(tot, k, v); } if (!seenC.has(c)) { seenC.add(c); add(ctot, c, v); } }
  }
  const pct = v => `${(100 * v / total).toFixed(1)}%`;
  const show = (title, m, k = TOP, m2 = null) => {
    console.log(`\n### ${title}\n\n| # | ${m2 ? 'self s | self % | total %' : 's | %'} | nombre |\n|---|---|---|${m2 ? '---|' : ''}---|`);
    [...m].sort((a, b) => b[1] - a[1]).slice(0, k).forEach(([a, v], i) =>
      console.log(`| ${i + 1} | ${(v / 1000).toFixed(2)} | ${pct(v)}${m2 ? ' | ' + pct(m2.get(a) || 0) : ''} | ${a.replace(/\|/g, '\\|').slice(0, 160)} |`));
  };
  const busy = total - (cself.get('V8 (idle)') || 0);
  console.log(`perfil ${file}${from || to < Infinity ? ` [${from}-${to} s]` : ''}: muestreado ${(total / 1000).toFixed(1)} s, ocupado (no idle) ${(busy / 1000).toFixed(1)} s (${pct(busy)}), muestras ${p.samples.length}`);
  show('self-time por función (top)', self, TOP, tot);
  show('total (inclusivo) por función (top)', tot, TOP);
  show('self por archivo', fself, 12);
  show('self por categoría', cself, 25, ctot);
  show('self por categoría, nativas atribuidas al llamador', cfold, 25);
}
