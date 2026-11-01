// Ronda 81 (frente 0): benchmark corto y repetible del arranque y del juego en Chrome de prueba.
// Lanzar con scripts/bench.sh (toma /tmp/pz80-locks/chrome.lock); no a mano.
//   --mode menu|ingame|create-save   (por defecto menu)
//     menu:        carga -> ToS -> ACCEPT -> menú principal (+ fps del menú)
//     ingame:      menu + CONTINUE -> "game loading took" -> Click to Start -> cierra la guía -> fps quieto y con W
//     create-save: menu + NEW GAME (Custom Sandbox sin vehículos) -> en juego -> Esc -> QUIT -> verifica IndexedDB
//   --page game.html  --query persist=1  --extra probe=1  (o --url URL completa; base http://127.0.0.1:8098)
//   --profile /tmp/pz81/profile   perfil PERSISTENTE (IndexedDB con la partida y caché HTTP sobreviven entre corridas)
//   --front 0 --change baseline-dev --notes "..."   columnas de /tmp/pz81/bench.tsv (front, change, metric, value, notes)
//   ingame: idle_diff/walk_diff/walk_moved = diferencia de pantalla (sin sonda); walk_dist sólo con --extra probe=1 (correr aparte: la sonda cuesta fps)
//   --fps-seconds 30 --walk-seconds 10 --menu-fps-seconds 10 --no-shots --force (create-save aunque ya haya partida)
// Tiempos T_* en segundos desde la navegación (performance.now() de la página); los de pzGameProgress son el
// primer pulso de cada etiqueta. fps_display = delta de Display#update / segundos; fps_raf = requestAnimationFrame.
// Salida: filas en el TSV, resumen JSON en <run>/summary.json (y stdout), consola en <run>/console.txt.
import { chromium } from '@playwright/test';
import { appendFileSync, writeFileSync, mkdirSync, readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { launchOptions, profileName } from './chrome-flags.mjs';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i++) {
  const k = argv[i].replace(/^--/, '');
  if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) A[k] = argv[++i]; else A[k] = true;
}
const MODE = A.mode || 'menu';
if (!['menu', 'ingame', 'create-save'].includes(MODE)) throw new Error(`--mode desconocido: ${MODE}`);
const PORT = process.env.PZ_DIAG_PORT || '8098';
const BASE = `http://127.0.0.1:${PORT}`;
const query = [A.query ?? 'persist=1', A.extra].filter(x => x && x !== true).join('&');
const URL_ = A.url || `${BASE}/${String(A.page || 'game.html').replace(/^\//, '')}${query ? '?' + query : ''}`;
if (!/^http:\/\/127\.0\.0\.1:/.test(URL_)) throw new Error('sólo 127.0.0.1');
const PROFILE = A.profile || '/tmp/pz81/profile';
if (!PROFILE.startsWith('/tmp/')) throw new Error('--profile debe estar bajo /tmp');
const TSV = A.tsv || '/tmp/pz81/bench.tsv';
const FRONT = A.front ?? '0', CHANGE = A.change || 'sin-etiqueta', NOTES = A.notes && A.notes !== true ? A.notes : '';
const FPS_S = +(A['fps-seconds'] || 30), WALK_S = +(A['walk-seconds'] || 10), MENU_FPS_S = +(A['menu-fps-seconds'] || 10);
const SHOTS = !A['no-shots'];
const RUN = `${A.out || '/tmp/pz81/bench-runs'}/${new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)}-${MODE}`;
mkdirSync(RUN, { recursive: true }); mkdirSync(PROFILE, { recursive: true });
const t0 = Date.now();
const log = (...a) => { const s = `[${new Date().toTimeString().slice(0, 8)} +${Math.round((Date.now() - t0) / 1000)}s] ${a.map(x => typeof x === 'string' ? x : JSON.stringify(x)).join(' ')}`; console.log(s); appendFileSync(`${RUN}/log.txt`, s + '\n'); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const capped = (p, ms, what) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error(`${what} sin respuesta en ${ms} ms`)), ms))]);

// --- procesos propios (descendientes de este node) para CPU/RSS y para matar sólo lo nuestro ---
const CLK = 100;
function procTable() {
  const t = new Map();
  for (const d of readdirSync('/proc')) {
    if (!/^\d+$/.test(d)) continue;
    try {
      const st = readFileSync(`/proc/${d}/stat`, 'utf8'), r = st.slice(st.lastIndexOf(')') + 2).split(' ');
      t.set(+d, { pid: +d, ppid: +r[1], cpu: (+r[11]) + (+r[12]), rssMb: (+r[21]) * 4096 / 1048576 });
    } catch { /* proceso ya terminó */ }
  }
  return t;
}
function ownChrome() {
  const t = procTable(), out = [], q = [process.pid];
  while (q.length) { const p = q.shift(); for (const e of t.values()) if (e.ppid === p) { q.push(e.pid); out.push(e); } }
  for (const e of out) { try { const c = readFileSync(`/proc/${e.pid}/cmdline`, 'utf8'); e.type = (c.match(/--type=([a-z-]+)/) || [, 'browser'])[1]; e.ext = c.includes('--extension-process'); } catch { e.type = '?'; } }
  return out;
}
function cpuSample() { const m = {}; for (const e of ownChrome()) m[e.pid] = e; return { at: Date.now(), m }; }
function cpuReport(a, b) {
  const secs = (b.at - a.at) / 1000, by = {};
  for (const e of Object.values(b.m)) {
    const prev = a.m[e.pid]; if (!prev) continue;
    const k = e.type === 'renderer' && !e.ext ? 'renderer' : e.type === 'gpu-process' ? 'gpu' : e.type === 'browser' ? 'browser' : 'other';
    const pct = 100 * (e.cpu - prev.cpu) / CLK / secs;
    if (k === 'renderer') { if (!by.renderer || e.rssMb > by.renderer.rssMb) by.renderer = { pct, rssMb: e.rssMb }; }
    else { by[k] = by[k] || { pct: 0, rssMb: 0 }; by[k].pct += pct; by[k].rssMb += e.rssMb; }
  }
  return by;
}

// --- métricas ---
const summary = { mode: MODE, url: URL_, profile: PROFILE, gpu: profileName(), front: FRONT, change: CHANGE, notes: NOTES, run: RUN, metrics: {}, events: [] };
try { const st = statSync('port/target/web/game/boot.wasm'); summary.bootWasm = { bytes: st.size, mtime: st.mtime.toISOString() }; } catch {}
const tsvNote = () => [NOTES, `mode=${MODE}`, `run=${RUN.split('/').pop()}`, summary.bootWasm ? `boot.wasm=${summary.bootWasm.mtime.slice(0, 16)}` : '', URL_.includes('?') ? URL_.slice(URL_.indexOf('?')) : ''].filter(Boolean).join(' ');
function metric(name, value, digits = 2) {
  if (value == null || Number.isNaN(value)) { log('METRIC', name, 'n/a'); return; }
  const v = typeof value === 'number' ? +value.toFixed(digits) : value;
  summary.metrics[name] = v; log('METRIC', name, v);
  if (!existsSync(TSV)) writeFileSync(TSV, 'front\tchange\tmetric\tvalue\tnotes\n');
  appendFileSync(TSV, `${FRONT}\t${CHANGE}\t${name}\t${v}\t${tsvNote()}\n`);
}
const ev = (name, extra) => { const e = { name, wallS: +((Date.now() - tNav) / 1000).toFixed(1), ...extra }; summary.events.push(e); log('EVENT', name, extra || ''); };

// --- navegador ---
const opts = launchOptions();
const context = await chromium.launchPersistentContext(PROFILE, { ...opts, viewport: { width: 1280, height: 720 } });
log('BROWSER', profileName(), `headless=${opts.headless}`, `profile=${PROFILE}`, `url=${URL_}`);
let finished = false;
async function finish(code) {
  if (finished) return; finished = true;
  writeFileSync(`${RUN}/summary.json`, JSON.stringify(summary, null, 1) + '\n');
  console.log('SUMMARY ' + JSON.stringify(summary));
  await capped(context.close(), 30000, 'context.close').catch(e => log('close:', String(e)));
  for (const e of ownChrome()) { try { process.kill(e.pid, 'SIGKILL'); } catch {} } // sólo descendientes propios
  process.exit(code);
}
for (const s of ['SIGTERM', 'SIGINT', 'SIGHUP']) process.on(s, () => { summary.error = s; finish(130); });

await context.addInitScript(() => {
  const B = globalThis.__bench = { raf: 0, compiles: [], instantiates: [] };
  const tick = () => { B.raf++; requestAnimationFrame(tick); }; requestAnimationFrame(tick);
  const cs = WebAssembly.compileStreaming, inst = WebAssembly.instantiate;
  WebAssembly.compileStreaming = async function (src, ...rest) {
    const t0 = performance.now(); let url = '';
    try { url = (await src)?.url || ''; } catch {}
    const m = await cs.call(this, src, ...rest); B.compiles.push({ url, t0, t1: performance.now() }); return m;
  };
  WebAssembly.instantiate = async function (m, imports, ...rest) {
    const t0 = performance.now(), r = await inst.call(this, m, imports, ...rest);
    B.instantiates.push({ teavm: !!(imports && imports.teavmAsync), t0, t1: performance.now() }); return r;
  };
});
const page = context.pages()[0] || await context.newPage();
const consoleLines = [];
let loadingTook = null, loadingAt = null;
page.on('console', m => {
  const s = m.text(); appendFileSync(`${RUN}/console.txt`, s.slice(0, 2000) + '\n');
  const g = /game loading took (\d+) seconds/.exec(s);
  if (g) { loadingTook = +g[1]; loadingAt = Date.now(); }
  if (/STATE: (enter|exit)|game loading took|PAGEERROR/.test(s)) consoleLines.push([Date.now(), s]);
});
page.on('pageerror', e => { const s = String(e.stack || e.message).slice(0, 800).replace(/\n/g, ' | '); log('PAGEERROR', s); (summary.pageErrors ||= []).push(s.slice(0, 300)); });
page.on('crash', () => { log('PAGE-CRASH'); summary.error = 'page crash'; });

const evalc = (fn, arg, ms = 15000) => capped(page.evaluate(fn, arg), ms, 'evaluate');
const DU = 'org.lwjglx.opengl.Display#update(Z)V:returned';
const TOS = 'zombie.gameStates.TermsOfServiceState#render()V:returned';
const MENU = 'zombie.gameStates.MainScreenState#render()V:returned';
const snap = () => evalc(([du, tos, menu]) => {
  const c = globalThis.pzGameCounts || {}, f = globalThis.pzRuntime?.vfs?.autoFlushStats;
  return { now: performance.now(), du: c[du] | 0, tos: c[tos] | 0, menu: c[menu] | 0, raf: globalThis.__bench?.raf | 0,
    flush: f ? { ...f } : null, phase: globalThis.pzGame?.phase || null, runtime: !!globalThis.pzRuntime };
}, [DU, TOS, MENU]).catch(e => ({ error: String(e).slice(0, 120) }));
const remote = async (label) => { // pedido de front 4: XHR síncronos de /media (vfs.remoteStats)
  const r = await evalc(() => { const s = globalThis.pzRuntime?.vfs?.remoteStats; return s ? { ...s } : null; }).catch(() => null);
  if (r) { summary[`remote_${label}`] = r;
    metric(`remote_req_${label}`, r.requests, 0); metric(`remote_mb_${label}`, r.bytes / 1048576, 1); metric(`remote_ms_${label}`, r.ms, 0); }
  // pedido de front 2: estadísticas de la caché de texturas (?texcache=1)
  const t = await evalc(() => { const s = globalThis.pzRuntime?.texCache?.stats; return s ? JSON.parse(JSON.stringify(s)) : null; }).catch(() => null);
  if (t) { summary[`texcache_${label}`] = t; if (t.hit != null) metric(`texcache_hit_${label}`, t.hit, 0); if (t.miss != null) metric(`texcache_miss_${label}`, t.miss, 0); }
};
const firstPulse = labels => evalc(ls => ls.map(l => { const p = (globalThis.pzGameProgress || []).find(x => x.label === l); return p ? p.time : null; }), labels).catch(() => labels.map(() => null));
async function waitFor(what, pred, timeoutMs, everyMs = 2000) {
  const end = Date.now() + timeoutMs; let s;
  while (Date.now() < end) { s = await snap(); if (s.phase === 'failed' || s.phase === 'loader-failed') throw new Error(`fase ${s.phase} esperando ${what}`); if (!s.error && pred(s)) return s; await sleep(everyMs); }
  throw new Error(`timeout esperando ${what} (${timeoutMs / 1000} s); último ${JSON.stringify(s)}`);
}
async function click(x, y, ms = 120000) { // como live.mjs: mover, 300 ms, down 120 ms up
  await capped((async () => { await page.mouse.move(x, y); await sleep(300); await page.mouse.down(); await sleep(120); await page.mouse.up(); })(), ms, `click ${x},${y}`);
}
async function key(k, ms) { await capped((async () => { await page.keyboard.down(k); await sleep(ms); await page.keyboard.up(k); })(), ms + 60000, `key ${k}`); }
async function shot(name) {
  if (!SHOTS) return;
  try { await capped(page.screenshot({ path: `${RUN}/${name}.png`, timeout: 90000 }), 95000, 'screenshot'); log('SHOT', `${RUN}/${name}.png`); }
  catch (e) { log('shot falló', name, String(e).slice(0, 120)); }
}
async function rate(seconds, label, during) { // fps por Display#update y por rAF, con CPU de los procesos propios
  const a = await snap(), ca = cpuSample();
  if (during) await during(seconds); else await sleep(seconds * 1000);
  const b = await snap(), cb = cpuSample();
  if (a.error || b.error) { log('rate sin muestra', label, a.error || b.error); return null; }
  const dt = (b.now - a.now) / 1000, cpu = cpuReport(ca, cb);
  metric(`fps_${label}_display`, (b.du - a.du) / dt); metric(`fps_${label}_raf`, (b.raf - a.raf) / dt);
  if (cpu.renderer) { metric(`cpu_${label}_renderer_pct`, cpu.renderer.pct, 0); metric(`rss_${label}_renderer_mb`, cpu.renderer.rssMb, 0); }
  if (cpu.gpu) metric(`cpu_${label}_gpu_pct`, cpu.gpu.pct, 0);
  return { a, b };
}
async function savesInfo() {
  return evalc(async () => {
    const v = globalThis.pzRuntime?.vfs; if (!v) return null;
    const files = v.list('/Zomboid/Saves'), dirs = {};
    for (const p of files) { const m = /^\/Zomboid\/Saves\/([^/]+\/[^/]+)\//.exec(p); if (m) dirs[m[1]] = (dirs[m[1]] || 0) + 1; }
    // IndexedDB real (no la VFS en memoria): claves bajo /Zomboid/Saves y latestSave.ini
    const db = await new Promise((res, rej) => { const r = indexedDB.open(v.name); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });
    const keys = await new Promise((res, rej) => { const r = db.transaction('files').objectStore('files').getAllKeys(); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });
    db.close();
    const idbDirs = {};
    for (const k of keys) { const m = /^\/Zomboid\/Saves\/([^/]+\/[^/]+)\//.exec(k); if (m) idbDirs[m[1]] = (idbDirs[m[1]] || 0) + 1; }
    return { vfsDirs: dirs, idbDirs, idbKeys: keys.length, latestSave: keys.includes('/Zomboid/latestSave.ini'),
      thumbs: keys.filter(k => k.endsWith('/thumb.png')) };
  }, undefined, 60000).catch(e => ({ error: String(e).slice(0, 200) }));
}
const sec = ms => ms == null ? null : ms / 1000;

let tNav = Date.now();
try {
  tNav = Date.now();
  await page.goto(URL_, { waitUntil: 'domcontentloaded', timeout: 120000 });
  ev('domcontentloaded');
  await waitFor('pzRuntime', s => s.runtime, 20 * 60000);
  const saves0 = await savesInfo(); summary.savesBefore = saves0; log('SAVES antes', saves0);
  const hasSave = saves0 && !saves0.error && saves0.thumbs.length > 0; // thumb.png sólo lo escribe el guardado del QUIT
  if (MODE === 'ingame' && !hasSave) throw new Error('el perfil no tiene partida: correr scripts/bench.sh --mode create-save primero');
  if (MODE === 'create-save' && hasSave && !A.force) log('el perfil ya tiene partida; create-save sigue (NEW GAME agrega otra). Usar un perfil nuevo para empezar de cero');

  const sTos = await waitFor('TermsOfServiceState#render>=20', s => s.tos >= 20, 25 * 60000);
  ev('tos-ready', { tos: sTos.tos });
  const B = await evalc(() => ({ ...globalThis.__bench, res: performance.getEntriesByType('resource').filter(e => /boot\.wasm$|\/\/[^/]+\/packs\//.test(e.name)).map(e => ({ n: e.name.replace(/^.*\/\/[^/]+/, ''), end: e.responseEnd, start: e.startTime, enc: e.encodedBodySize, tx: e.transferSize })) }));
  const comp = B.compiles.find(c => /boot\.wasm$/.test(c.url)), inst = B.instantiates.find(i => i.teavm);
  const wasmRes = B.res.find(r => /boot\.wasm$/.test(r.n)), packRes = B.res.filter(r => r.n.startsWith('/packs/')); // sólo los packs del arranque, no /media/texturepacks (XHR de la VFS)
  summary.wasm = { compile: comp, instantiate: inst, resource: wasmRes, packs: { n: packRes.length, endMax: Math.max(0, ...packRes.map(r => r.end)), tx: packRes.reduce((s, r) => s + (r.tx || 0), 0) } };
  metric('T_packs_s', sec(summary.wasm.packs.endMax));
  metric('pack_transfer_mb', summary.wasm.packs.tx / 1048576, 1);
  metric('T_wasm_fetch_s', sec(wasmRes?.end));
  metric('wasm_compile_s', comp ? sec(comp.t1 - comp.t0) : null);
  metric('T_wasm_s', sec(comp?.t1));
  metric('T_instantiated_s', sec(inst?.t1));
  const [tTos] = await firstPulse(['zombie.gameStates.TermsOfServiceState#render()V:entered']);
  metric('T_tos_s', sec(tTos));

  await click(610, 515); // ACCEPT del ToS
  const sAcc = await snap(); ev('tos-accept');
  const sMenu = await waitFor('MainScreenState#render>=100', s => s.menu >= 100, 15 * 60000, 1000);
  const [tMenu] = await firstPulse(['zombie.gameStates.MainScreenState#render()V:entered']);
  metric('T_menu_s', sec(tMenu));
  metric('menu_after_accept_s', tMenu != null && !sAcc.error ? sec(tMenu - sAcc.now) : null);
  metric('T_menu100_s', sec(sMenu.now));
  await remote('menu');
  await sleep(3000);
  await shot('menu');
  await rate(MENU_FPS_S, 'menu');

  if (MODE === 'ingame' || MODE === 'create-save') {
    let enter;
    if (MODE === 'ingame') {
      enter = click(109, 172, 40 * 60000); ev('continue-click'); // CONTINUE (ronda 80); bloquea mientras carga
    } else {
      const seq = [[130, 335, 12000], [956, 540, 3000], [1225, 696, 25000], [1225, 696, 25000], [58, 376, 6000], [938, 127, 5000], [1225, 696, 40000], [1226, 697, 40000]];
      for (const [x, y, w] of seq) { await click(x, y); await sleep(w); }
      await shot('newgame-char');
      enter = click(1226, 696, 40 * 60000); ev('play-click');
    }
    const tStart = Date.now();
    enter.catch(e => log('click de entrada:', String(e).slice(0, 120)));
    while (loadingTook == null) { if (Date.now() - tStart > 40 * 60000) throw new Error('timeout esperando "game loading took"'); await sleep(2000); }
    ev('game-loading-took', { seconds: loadingTook });
    metric('game_loading_s', loadingTook, 0);
    metric('enter_to_loaded_s', (loadingAt - tStart) / 1000, 1);
    await enter.catch(() => {});
    await remote('loaded'); // acumulado desde la navegación (incluye el del menú)
    // Estado del juego por la sonda del jugador (PlayerProbe, IngameState#update): se prende sólo para decidir
    // y se apaga durante las ventanas de fps (cuesta un JSON por frame). La bandera se relee cada 1 s.
    const setProbe = on => evalc(v => { globalThis.PZ_PLAYER_PROBE = v; return v; }, on).catch(() => null);
    const pstate = () => evalc(() => { const p = globalThis.pzRuntime?.playerProbe; return p ? { time: p.time, frames: p.frames, ticks: p.numberTicks,
      paused: p.ingamePaused, gtp: p.gameTimePaused, msg: p.showPausedMessage, player: p.player0, x: p.x, y: p.y, fps: p.averageFPS } : null; }).catch(() => null);
    await setProbe(true);
    let st = null;
    const tClick = Date.now();
    // La sonda puede no publicar nunca (visto 01/11 en el build 00:15: playerProbe=null aun con la guía abierta);
    // en ese caso se sigue a ciegas con la receta de la ronda 80 (3 clics, 35 s, Esc 800 ms) y se avisa en el resumen.
    let blind = false;
    // Señal robusta: tamaño del PNG de la captura (pantalla "Click to Start" = casi negra, <15 KB; mundo/guía > 40 KB).
    const shotSize = async () => { try { return (await capped(page.screenshot({ timeout: 90000 }), 95000, 'screenshot')).length; } catch { return -1; } };
    let size = await shotSize(); log('captura antes de empezar', size);
    for (let i = 0; i < 10 && size < 40000; i++) { // "Click to Start": a ~3 fps un clic puede no tomar
      await click(640, 400); await sleep(8000); size = await shotSize(); log('click-to-start', i + 1, 'png bytes', size);
    }
    if (size < 40000) throw new Error(`"Click to Start" no avanzó (png ${size} B)`);
    await sleep(25000); // la guía tarda en aparecer
    st = await pstate(); blind = !st; summary.blind = blind; log('estado tras empezar', st);
    await shot('guide');
    if (blind) { await key('Escape', 800); await sleep(5000); log('modo a ciegas: Esc enviado (verificar con ingame.png)'); }
    else if (st && st.gtp) { // Survival Guide abierta (pausa el juego): Esc 800 ms la cierra
      for (let i = 0; i < 3 && st && st.gtp; i++) { await key('Escape', 800); await sleep(4000); st = await pstate(); log('cerrar guía', i + 1, st); }
      if (st && st.gtp) throw new Error(`la guía no se cerró: ${JSON.stringify(st)}`);
    }
    if (MODE === 'ingame') { // la ventana Info del personaje queda abierta en el centro tras Esc (ronda 81): cerrarla y dar foco al canvas
      await click(310, 110); await sleep(3000); await click(640, 360); await sleep(3000);
    }
    const sIn = await snap(); ev('ingame', { du: sIn.du, probe: st });
    metric('T_ingame_wall_s', (Date.now() - tStart) / 1000, 1);
    await shot('ingame');
    await setProbe(false); await sleep(2500);

    if (MODE === 'ingame') {
      await rate(FPS_S, 'idle');
      // Movimiento sin sonda: diferencia de pantalla completa antes/después de W (la cámara sigue al jugador).
      // Las capturas se toman >= 4 s después de la última tecla/clic (artefacto de la esfera azul tras soltar una tecla).
      // idle_diff (dos capturas de la página quieta) calibra el ruido (reloj, animaciones, luz).
      const diffPage = await context.newPage(); // página aparte: no toca el hilo del juego
      const shotB64 = async () => (await capped(page.screenshot({ timeout: 90000 }), 95000, 'screenshot')).toString('base64');
      const diffRatio = (a, b) => capped(diffPage.evaluate(async ([a, b]) => {
        const load = async x => { const bm = await createImageBitmap(await (await fetch('data:image/png;base64,' + x)).blob());
          const c = new OffscreenCanvas(bm.width, bm.height), g = c.getContext('2d'); g.drawImage(bm, 0, 0); return g.getImageData(0, 0, bm.width, bm.height).data; };
        const [da, db] = await Promise.all([load(a), load(b)]); let n = 0;
        for (let i = 0; i < da.length; i += 4) if (Math.abs(da[i] - db[i]) + Math.abs(da[i + 1] - db[i + 1]) + Math.abs(da[i + 2] - db[i + 2]) > 60) n++;
        return n / (da.length / 4);
      }, [a, b]), 60000, 'diff').catch(e => { log('diff falló', String(e).slice(0, 100)); return null; });
      await sleep(4000);
      const i0 = await shotB64(); await sleep(5000); const i1 = await shotB64();
      const idleDiff = await diffRatio(i0, i1); metric('idle_diff', idleDiff, 4);
      await rate(FPS_S, 'idle');
      await sleep(4000);
      const w0 = await shotB64();
      const probe = async () => { await setProbe(true); await sleep(2500); const p = await pstate(); await setProbe(false); await sleep(1500); return p; };
      const p0 = await probe(); log('antes de W', p0);
      await rate(WALK_S, 'walk', async s => { await key('w', s * 1000); });
      await sleep(5000); // pasa el artefacto de la esfera
      const w1 = await shotB64();
      const walkDiff = await diffRatio(w0, w1); metric('walk_diff', walkDiff, 4);
      if (walkDiff != null && idleDiff != null) metric('walk_moved', walkDiff > Math.max(0.02, 3 * idleDiff) ? 1 : 0, 0);
      await diffPage.close().catch(() => {});
      const p1 = await probe(); log('después de W', p1);
      if (p0 && p1 && p0.x != null) { summary.walk = { p0, p1 }; metric('walk_dist', Math.hypot(p1.x - p0.x, p1.y - p0.y)); }
      await shot('after-walk');
    } else {
      await sleep(30000);
      const f0 = await snap(); log('autoFlush antes de QUIT', f0.flush);
      await setProbe(true);
      for (let i = 0; i < 3; i++) { await key('Escape', 800); await sleep(5000); st = await pstate(); log('Esc -> pausa', i + 1, st); if (blind || (st && st.gtp)) break; }
      await shot('pause');
      if (!blind && (!st || !st.gtp)) throw new Error('Esc no abrió el menú de pausa');
      const menuBefore = (await snap()).menu;
      await click(175, 524, 20 * 60000); ev('quit-click'); // QUIT
      await waitFor('menú tras QUIT', s => s.menu > menuBefore + 50, 20 * 60000);
      ev('menu-after-quit');
      let last = -1, stableSince = Date.now();
      for (const end = Date.now() + 10 * 60000; Date.now() < end;) { // autoFlush quieto 20 s
        const s = await snap(); const b = s.flush ? s.flush.batches : -1;
        if (b !== last) { last = b; stableSince = Date.now(); } else if (Date.now() - stableSince > 20000) break;
        await sleep(2000);
      }
      const saves1 = await savesInfo(); summary.savesAfter = saves1; log('SAVES después', saves1);
      await shot('menu-after-quit');
      if (!saves1 || saves1.error || !Object.keys(saves1.idbDirs || {}).length) throw new Error('no hay partida en IndexedDB tras QUIT');
      metric('save_idb_keys', Object.values(saves1.idbDirs).reduce((a, b) => Math.max(a, b), 0), 0);
    }
  }
  summary.consoleStates = consoleLines.map(([t, s]) => [+((t - tNav) / 1000).toFixed(1), s.slice(0, 160)]);
  log('OK');
  await finish(0);
} catch (e) {
  summary.error = String(e).slice(0, 500); log('FAIL', summary.error);
  await shot('fail');
  summary.consoleStates = consoleLines.map(([t, s]) => [+((t - tNav) / 1000).toFixed(1), s.slice(0, 160)]);
  await finish(1);
}
