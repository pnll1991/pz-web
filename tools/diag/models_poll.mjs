// Ronda 63: sonda de carga de modelos con muestreo en el tiempo.
// Las sondas diag172-174 esperaban 170-175 s fijos y sacaban UNA foto; la carga de modelos
// empieza recien a ~186-187 s de performance.now(), asi que la foto caia 1-2 s despues de
// ModelManager#create y el "cuelgue" era el final de la ventana, no un estancamiento medido.
// Esta sonda muestrea cada PZ_POLL_MS y corta cuando el juego pasa a UI_Loading_OnGameBoot,
// cuando nada avanza durante PZ_STALL_MS, o a los PZ_DIAG_MAX ms.
// Uso: PZ_DIAG_MAX=900000 node tools/diag/models_poll.mjs | tee /tmp/diag-models.log
import { chromium } from '@playwright/test';
const PORT = process.env.PZ_DIAG_PORT || '8092';
const MAX = Number(process.env.PZ_DIAG_MAX || '900000');
const POLL = Number(process.env.PZ_POLL_MS || '15000');
const STALL = Number(process.env.PZ_STALL_MS || '180000');
const race = (p, ms) => Promise.race([p, new Promise((_, rj) => setTimeout(() => rj(new Error('evaluate timeout')), ms))]);
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-dev-shm-usage'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
const errs = [];
const texts = [];
page.on('pageerror', e => errs.push(String(e.message).slice(0, 300)));
page.on('console', m => { const t = m.text(); if (/UI_Loading_|LOADING|Exception|ModelMesh|anims/i.test(t)) texts.push(t.slice(0, 300)); });
await page.goto(`http://127.0.0.1:${PORT}/game.html?maplog=1`, { waitUntil: 'domcontentloaded' });
// Round 64: the boot reaches UI_Loading_OnGameBoot, so RE also covers the state machine and the
// MapCollisionData natives (see StartupProgress.MENU_CLASSES_METHODS), and PZ_DIAG_TARGET decides
// where the probe stops instead of always stopping at OnGameBoot.
const TARGET = process.env.PZ_DIAG_TARGET || 'UI_Loading_MainMenu';
const RE = 'initAnimationMeshes|isLoadingAnimationMeshes|ModelMesh#(isReady|onLoaded|onBeforeReady)|FileTask_AbstractLoadModel|FileSystemImpl#updateAsyncTransactions|ModelLoader|loadAnimsFromDir|invokeOnRenderContext\\(|Display#update|GameWindow#|Core#(StartFrame|EndFrame)|GameStateMachine#|MainScreenState#|LuaManager#(init|LoadDirBase|initChecksum)|MapCollisionData|TextureFBO#|GL11#glTexImage2D';
// The frame loop bumps Display#update / isLoadingAnimationMeshes / ModelMesh#isReady ~33 times per
// second, so including them in the key meant "stalled" could never fire. The stall clock now only
// watches counters that change when the port actually advances.
const LOOP_NOISE = /^(org\.lwjglx\.opengl\.Display#update|zombie\.core\.skinnedmodel\.ModelManager#isLoadingAnimationMeshes|zombie\.core\.skinnedmodel\.model\.ModelMesh#isReady|zombie\.GameWindow#DoLoadingText)/;
const sample = () => race(page.evaluate(re => {
  const c = globalThis.pzGameCounts || {}; const rx = new RegExp(re);
  const counts = {}; for (const [k, v] of Object.entries(c)) if (rx.test(k) && !k.endsWith('#last')) counts[k.replace(/\(.*?\)[^:]*/, '')] = v;
  const prog = globalThis.pzGameProgress || [];
  return { now: Math.round(performance.now()), phase: globalThis.pzGame?.phase ?? null,
    error: String(globalThis.pzGame?.error ?? '').slice(0, 200), progress: prog.length,
    lastLabel: prog.length ? prog[prog.length - 1].label : null,
    swaps: globalThis.pzRuntime?.window?.swapCount ?? null, yields: globalThis.pzYield ?? null,
    timers: globalThis.pzTimers ? { calls: globalThis.pzTimers.calls, fired: globalThis.pzTimers.fired } : null,
    menu: globalThis.pzRuntime?.mapCollision?.state ?? null, counts };
}, RE), 60000);
// Bounded DebugLog read: the fiber that dies inside a render item usually leaves its Throwable
// there, and the whole log is ~1 MB, so the page trims it before serialising.
const debugTail = () => race(page.evaluate(() => {
  const r = globalThis.pzRuntime; if (!r?.vfs) return { noVfs: true };
  const l = r.vfs.list('/Zomboid/Logs');
  const newest = l.slice().sort((a, x) => (r.vfs.lastModified(x) || 0) - (r.vfs.lastModified(a) || 0))[0];
  if (!newest) return { newest: null, text: '' };
  const full = new TextDecoder().decode(r.vfs.readFile(newest));
  const tally = {};
  for (const m of full.matchAll(/([A-Za-z.]+(?:Exception|Error)[^\n]{0,120})/g)) {
    const k = m[1].split(' at ')[0]; tally[k] = (tally[k] || 0) + 1;
  }
  return { newest, bytes: full.length, tally: Object.fromEntries(
    Object.entries(tally).sort((a, x) => x[1] - a[1]).slice(0, 12)),
    tail: full.slice(-6000) };
}), 90000);
const t0 = Date.now(); let lastKey = ''; let lastChange = Date.now(); let n = 0;
while (Date.now() - t0 < MAX) {
  await page.waitForTimeout(POLL);
  let s; try { s = await sample(); } catch (e) { console.log('SAMPLE_FAIL', String(e)); continue; }
  const quiet = Object.fromEntries(Object.entries(s.counts).filter(([k]) => !LOOP_NOISE.test(k)));
  const key = JSON.stringify([quiet, s.progress, s.lastLabel]);
  if (key !== lastKey) { lastKey = key; lastChange = Date.now(); }
  console.log('SAMPLE', ++n, JSON.stringify(s));
  if (new RegExp(TARGET).test(texts.join('\n'))) { console.log('RESULT reached target /' + TARGET + '/'); break; }
  // Round 64: the real menu signal is the state machine, not a loading string. Stop when
  // MainScreenState#render() has completed a frame (that is what puts pixels on the canvas).
  if ((s.counts['zombie.gameStates.MainScreenState#render'] || 0) >= 2) {
    console.log('RESULT MainScreenState#render done', s.counts['zombie.gameStates.MainScreenState#render']);
    break;
  }
  if (Date.now() - lastChange > STALL) {
    console.log('RESULT stalled', Math.round((Date.now() - lastChange) / 1000), 's without change');
    const ring = await race(page.evaluate(() => ({
      progress: (globalThis.pzGameProgress || []).slice(-30),
      trace: (globalThis.pzGameTrace || []).slice(-40) })), 90000);
    console.log('RING', JSON.stringify(ring, null, 1));
    console.log('DEBUGLOG', JSON.stringify(await debugTail(), null, 1));
    break;
  }
}
console.log('TEXTS', JSON.stringify(texts.slice(-40)));
console.log('ERRS', JSON.stringify(errs.slice(0, 20)));
const shot = 'test-results/menu-round64.png';
await page.screenshot({ path: shot });
console.log('SHOT', shot);
console.log('STATE', JSON.stringify(await race(page.evaluate(() => ({
  phase: globalThis.pzGame?.phase ?? null, error: String(globalThis.pzGame?.error ?? '').slice(0, 300),
  mapCollision: globalThis.pzRuntime?.mapCollision ?? null,
  stateCounts: Object.fromEntries(Object.entries(globalThis.pzGameCounts || {})
    .filter(([k]) => /GameStateMachine|MainScreenState|LuaManager#init|MapCollisionData/.test(k))),
  canvas: (() => { const c = document.querySelector('canvas'); return c ? { w: c.width, h: c.height } : null; })()
})), 90000)));
console.log('DEBUGLOG_FINAL', JSON.stringify(await debugTail(), null, 1));
await browser.close();
