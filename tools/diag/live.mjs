// Ronda 80: sesión Playwright persistente (un solo Chrome de prueba vivo entre iteraciones).
// Lanzar con scripts/live_chrome.sh start (setsid + flock de /tmp/pz80-locks/chrome.lock); no a mano.
// Comandos: una línea por comando agregada a $PZ_LIVE/cmd.txt (o `scripts/live_chrome.sh cmd "..."`):
//   click X Y | move X Y | key KEY [ms] | type TEXT | wait MS | shot NAME | eval JS | counts REGEX
//   until KEYSUBSTR MIN TIMEOUT_MS | reload | goto URL | console-clear | newpage | quit
// Cada resultado va a $PZ_LIVE/out.txt con el número de comando (`#N OK|FAIL ...`); la consola a
// $PZ_LIVE/console.txt; latido cada 10 s a $PZ_LIVE/heartbeat.json; capturas a ~/pzshots/NAME.png.
import { chromium } from '@playwright/test';
import { writeFileSync, appendFileSync, readFileSync, mkdirSync, rmSync } from 'node:fs';
import { launchOptions, profileName } from './chrome-flags.mjs';

const PORT = process.env.PZ_DIAG_PORT || '8098';
const BASE = `http://127.0.0.1:${PORT}`;
const D = process.env.PZ_LIVE || '/tmp/pzlive'; mkdirSync(D, { recursive: true });
const SHOTS = `${process.env.HOME}/pzshots`; mkdirSync(SHOTS, { recursive: true });
for (const f of ['cmd.txt', 'out.txt', 'console.txt']) writeFileSync(`${D}/${f}`, '');
writeFileSync(`${D}/live.pid`, `${process.pid}\n`);
const t0 = Date.now();
const stamp = () => `[${new Date().toTimeString().slice(0, 8)} +${Math.round((Date.now() - t0) / 1000)}s]`;
const log = (...a) => appendFileSync(`${D}/out.txt`, `${stamp()} ${a.map(String).join(' ')}\n`);
const con = s => appendFileSync(`${D}/console.txt`, s.slice(0, 2000) + '\n');

const opts = launchOptions();
const browser = await chromium.launch(opts);
log('BROWSER', profileName(), `headless=${opts.headless}`, `pid=${process.pid}`, `chrome=${browser.version()}`);
const context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
let page, cur = '?';
async function newPage() {
  if (page) await page.close().catch(() => {});
  page = await context.newPage();
  page.on('console', m => con(m.text()));
  page.on('pageerror', e => { const s = String(e.stack || e.message).slice(0, 1500).replace(/\n/g, ' | '); con('PAGEERROR ' + s); log(`#${cur} PAGEERROR`, s); });
  page.on('crash', () => { con('PAGE CRASH'); log(`#${cur} PAGE-CRASH (usar newpage o reload)`); });
}
await newPage();
const url = u => /^[a-z]+:/i.test(u) ? u : `${BASE}/${u.replace(/^\//, '')}`;
if (process.env.LIVE_URL) { await page.goto(url(process.env.LIVE_URL), { waitUntil: 'domcontentloaded' }); log('LOADED', page.url()); }

let quitting = false;
async function shutdown(why) {
  if (quitting) return; quitting = true;
  log('QUIT', why); await browser.close().catch(() => {});
  rmSync(`${D}/live.pid`, { force: true }); process.exit(0);
}
for (const s of ['SIGTERM', 'SIGINT', 'SIGHUP']) process.on(s, () => shutdown(s));
browser.on('disconnected', () => { if (!quitting) { log('BROWSER-DISCONNECTED'); rmSync(`${D}/live.pid`, { force: true }); process.exit(1); } });

// Latido: el proceso escribe aunque la página esté ocupada (evaluate con tope de 8 s).
let busy = '';
setInterval(async () => {
  const h = { time: new Date().toISOString(), pid: process.pid, uptimeS: Math.round((Date.now() - t0) / 1000), busy, url: page.url() };
  try {
    h.page = await Promise.race([page.evaluate(() => {
      const c = globalThis.pzGameCounts || {}, r = globalThis.pzRuntime;
      return { phase: globalThis.pzGame?.phase, keys: Object.keys(c).filter(k => k.endsWith(':returned')).length,
        remote: r?.vfs?.remoteStats, missing: (r?.missingPaths || []).slice(-3) };
    }), new Promise(res => setTimeout(() => res('evaluate-timeout'), 8000))]);
  } catch (e) { h.page = 'evaluate-fail: ' + String(e).slice(0, 200); }
  writeFileSync(`${D}/heartbeat.json`, JSON.stringify(h) + '\n');
}, 10000);

// Tope por comando: un hilo principal colgado no debe bloquear la cola (frente V, ronda 80).
const CMD_TIMEOUT = +(process.env.LIVE_CMD_TIMEOUT_MS || 60000);
const capped = (p, ms = CMD_TIMEOUT, what = 'evaluate') => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error(`${what} sin respuesta en ${ms} ms`)), ms))]);
const evalc = (fn, arg, ms) => capped(page.evaluate(fn, arg), ms);
const countsOf = (r) => evalc(r => Object.fromEntries(Object.entries(globalThis.pzGameCounts || {}).filter(([k]) => !k.endsWith('#last') && new RegExp(r).test(k))), r);
let done = 0;
for (;;) {
  await new Promise(r => setTimeout(r, 300));
  const lines = readFileSync(`${D}/cmd.txt`, 'utf8').split('\n').filter(Boolean);
  while (done < lines.length) {
    const line = lines[done++]; cur = String(done); busy = line;
    const [c, ...a] = line.split(' ');
    try {
      if (c === 'click') { await page.mouse.move(+a[0], +a[1]); await page.waitForTimeout(300); await page.mouse.down(); await page.waitForTimeout(120); await page.mouse.up(); }
      else if (c === 'move') await page.mouse.move(+a[0], +a[1]);
      else if (c === 'key') { await page.keyboard.down(a[0]); await page.waitForTimeout(+(a[1] || 120)); await page.keyboard.up(a[0]); }
      else if (c === 'type') await page.keyboard.type(a.join(' '), { delay: 80 });
      else if (c === 'wait') await page.waitForTimeout(+a[0]);
      else if (c === 'shot') { await capped(page.screenshot({ path: `${SHOTS}/${a[0]}.png`, timeout: 90000 }), 95000, 'screenshot'); log(`#${cur} SHOT`, `${SHOTS}/${a[0]}.png`); }
      else if (c === 'eval') log(`#${cur} EVAL`, String(JSON.stringify(await evalc(a.join(' ')))).slice(0, 6000));
      else if (c === 'counts') log(`#${cur} COUNTS`, JSON.stringify(await countsOf(a.join(' '))).slice(0, 6000));
      else if (c === 'until') { // until KEY_SUBSTRING MINCOUNT TIMEOUT_MS
        const end = Date.now() + +a[2]; let n = 0;
        while (Date.now() < end) { n = await evalc(k => Object.entries(globalThis.pzGameCounts || {}).filter(([x]) => x.includes(k) && x.endsWith(':returned')).reduce((s, [, v]) => s + v, 0), a[0], 10000).catch(() => 0); if (n >= +a[1]) break; await new Promise(r => setTimeout(r, 2000)); }
        log(`#${cur} UNTIL`, a[0], n, n >= +a[1] ? 'reached' : 'timeout');
      }
      else if (c === 'reload') {
        try { await page.reload({ waitUntil: 'domcontentloaded', timeout: 60000 }); }
        catch (e) { const u = page.url(); log(`#${cur} reload falló (${String(e).slice(0, 120)}); página nueva`); await newPage(); await page.goto(u, { waitUntil: 'domcontentloaded' }); }
        log(`#${cur} LOADED`, page.url());
      }
      else if (c === 'goto') { await page.goto(url(a.join(' ') || 'game.html'), { waitUntil: 'domcontentloaded', timeout: 60000 }); log(`#${cur} LOADED`, page.url()); }
      else if (c === 'newpage') { const u = page.url(); await newPage(); if (u && u !== 'about:blank') await page.goto(u, { waitUntil: 'domcontentloaded' }); log(`#${cur} LOADED`, page.url()); }
      else if (c === 'console-clear') writeFileSync(`${D}/console.txt`, '');
      else if (c === 'quit') { log(`#${cur} OK`, line); await shutdown('quit'); }
      else throw new Error(`comando desconocido: ${c}`);
      log(`#${cur} OK`, line);
    } catch (e) { log(`#${cur} FAIL`, line, String(e).slice(0, 300).replace(/\n/g, ' | ')); }
    busy = '';
  }
}
