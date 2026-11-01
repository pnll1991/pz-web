// Ronda 81 (frente 4): sonda de capacidades de hilos en el Chrome de pruebas, sin código del juego.
// Uso: flock /tmp/pz80-locks/chrome.lock node tools/diag/threads_probe.mjs [N_BLOQUES=48]
// Copia tools/diag/threads_probe.page.mjs a port/target/web/f4/ (ignorado) y abre /f4/probe.html en 8098.
// Mide: objetos Wasm GC a un Worker (postMessage/structuredClone), tipos `shared` sin flag, Atomics.wait en
// el hilo principal y en un worker, OffscreenCanvas+WebGL2+rAF en un worker, latencia de una llamada
// síncrona worker->worker por SharedArrayBuffer+Atomics, y lectura remota de /media/binary.dat por bloques
// (XHR síncrono de texto en el hilo principal como vfs.mjs, XHR síncrono arraybuffer y fetch paralelo en worker).
import { chromium } from '@playwright/test';
import { mkdirSync, copyFileSync, writeFileSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';
const root = new URL('../../', import.meta.url).pathname;
const dir = `${root}port/target/web/f4`; mkdirSync(dir, { recursive: true });
copyFileSync(`${root}tools/diag/threads_probe.page.mjs`, `${dir}/probe.mjs`);
writeFileSync(`${dir}/probe.html`, '<!doctype html><meta charset="utf-8"><title>f4</title><canvas id="c" width="64" height="64"></canvas><pre id="out"></pre><script type="module" src="probe.mjs"></script>\n');
const n = +(process.argv[2] || 48);
const extra = process.env.PZ_JS_FLAGS ? [`--js-flags=${process.env.PZ_JS_FLAGS}`] : [];
const browser = await chromium.launch(launchOptions({ args: extra }));
try {
  const page = await browser.newPage();
  page.on('pageerror', e => console.log('PAGEERROR', e.message));
  await page.goto(`http://127.0.0.1:${process.env.PZ_DIAG_PORT || 8098}/f4/probe.html?n=${n}`);
  try { await page.waitForFunction(() => globalThis.f4?.done, null, { timeout: +(process.env.F4_TIMEOUT_MS || 300000) }); }
  catch (e) { console.log('TIMEOUT', e.message.split('\n')[0]); }
  console.log(JSON.stringify(await page.evaluate(() => globalThis.f4), null, 1));
} finally { await browser.close(); }
