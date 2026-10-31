// Ronda 80: perfil de CPU (CDP Profiler, muestreo) del arranque de game.html hasta el menú.
// Uso: flock /tmp/pz80-locks/chrome.lock node tools/diag/boot_profile.mjs [SEGUNDOS=420]
// Env: PZ_PROF_DIR (salida, /tmp/pzprof), PZ_PROF_US (intervalo de muestreo, 2000), PZ_JS_FLAGS (--js-flags extra),
//      PZ_GPU (ver chrome-flags.mjs), PZ_DIAG_PORT (8098).
// Escribe boot.cpuprofile, events.txt (líneas de consola con tiempo y peticiones de red) y luego
// node tools/diag/profile_agg.mjs DIR resume self-time por función/paquete/fase.
import { chromium } from '@playwright/test';
import { writeFileSync, appendFileSync, mkdirSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';

const SECS = +(process.argv[2] || 420);
const D = process.env.PZ_PROF_DIR || '/tmp/pzprof'; mkdirSync(D, { recursive: true });
const PORT = process.env.PZ_DIAG_PORT || '8098';
const extra = process.env.PZ_JS_FLAGS ? [`--js-flags=${process.env.PZ_JS_FLAGS}`] : [];
const browser = await chromium.launch(launchOptions({ args: extra }));
const ev = `${D}/events.txt`; writeFileSync(ev, '');
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  const cdp = await page.context().newCDPSession(page);
  let t0 = 0; const t = () => ((Date.now() - t0) / 1000).toFixed(1);
  page.on('console', m => { const s = m.text(); if (/STATE:|booting|Exception|LOADING|loading|Loaded|PZ-/.test(s)) appendFileSync(ev, `${t()} C ${s.slice(0, 300)}\n`); });
  page.on('pageerror', e => appendFileSync(ev, `${t()} E ${String(e.message).slice(0, 300)}\n`));
  page.on('requestfinished', async r => { try { const z = await r.sizes(); const tm = r.timing();
    appendFileSync(ev, `${t()} R ${r.method()} ${r.url().slice(0, 160)} body=${z.responseBodySize} dur=${Math.round(tm.responseEnd)}ms\n`); } catch {} });
  await cdp.send('Profiler.enable');
  await cdp.send('Profiler.setSamplingInterval', { interval: +(process.env.PZ_PROF_US || 2000) });
  await cdp.send('Profiler.start');
  t0 = Date.now();
  await page.goto(`http://127.0.0.1:${PORT}/game.html`, { waitUntil: 'domcontentloaded' });
  appendFileSync(ev, `${t()} LOADED\n`);
  const end = t0 + SECS * 1000;
  while (Date.now() < end) {
    await new Promise(r => setTimeout(r, 15000));
    const n = await Promise.race([page.evaluate(() => Object.entries(globalThis.pzGameCounts || {})
      .filter(([k]) => k.includes('MainScreenState#render') && k.endsWith(':returned')).reduce((s, [, v]) => s + v, 0)),
      new Promise(r => setTimeout(() => r(-1), 10000))]).catch(() => -2);
    appendFileSync(ev, `${t()} H menuRenders=${n}\n`);
    if (n >= 5) break;
  }
  const stats = await Promise.race([page.evaluate(() => ({
    timers: globalThis.pzTimers?.calls, timersFired: globalThis.pzTimers?.fired, yield: globalThis.pzYield,
    asyncOffers: globalThis.pzAsync?.offers, pulses: Object.entries(globalThis.pzGameCounts || {})
      .filter(([k]) => !k.endsWith('#last')).reduce((s, [, v]) => s + v, 0),
    topPulses: Object.entries(globalThis.pzGameCounts || {}).filter(([k]) => !k.endsWith('#last'))
      .sort((a, b) => b[1] - a[1]).slice(0, 15), remote: globalThis.pzRuntime?.vfs?.remoteStats })),
    new Promise(r => setTimeout(() => r('evaluate-timeout'), 30000))]).catch(e => String(e));
  appendFileSync(ev, `${t()} STATS ${JSON.stringify(stats)}\n`);
  const t1 = Date.now();
  const { profile } = await cdp.send('Profiler.stop');
  appendFileSync(ev, `${t()} STOP (Profiler.stop tardó ${Date.now() - t1} ms)\n`);
  writeFileSync(`${D}/boot.cpuprofile`, JSON.stringify(profile));
  console.log(`perfil: ${D}/boot.cpuprofile nodos=${profile.nodes.length} muestras=${profile.samples.length}`);
} finally { await browser.close(); }
