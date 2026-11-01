// Ronda 81 (frente 2): qué páginas de media/texturepacks/*.pack lee el arranque hasta el ToS.
// Envuelve pzRuntime.vfs.readInto (sólo observa) y vuelca [t, path, pos, len] de cada lectura de .pack.
// Uso: flock /tmp/pz80-locks/chrome.lock node tools/diag/pack_reads.mjs OUT.json [SEGUNDOS=600]
import { chromium } from '@playwright/test';
import { writeFileSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';

const [out = '/tmp/pack_reads.json', secs = '600'] = process.argv.slice(2);
const browser = await chromium.launch(launchOptions());
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.addInitScript(() => {
    const reads = globalThis.__packReads = [];
    let rt;
    Object.defineProperty(globalThis, 'pzRuntime', { configurable: true, get: () => rt, set: v => {
      rt = v; const vfs = v.vfs, base = vfs.readInto.bind(vfs);
      vfs.readInto = (path, position, target) => {
        const n = base(path, position, target);
        if (/\.pack$/.test(path)) reads.push([Math.round(performance.now()), path, position, n]);
        return n;
      };
    } });
  });
  await page.goto(`http://127.0.0.1:${process.env.PZ_DIAG_PORT || 8098}/game.html`, { waitUntil: 'domcontentloaded' });
  const end = Date.now() + +secs * 1000; let tos = 0;
  while (Date.now() < end && tos < 5) {
    await new Promise(r => setTimeout(r, 5000));
    tos = await Promise.race([page.evaluate(() => (globalThis.pzGameCounts || {})['zombie.gameStates.TermsOfServiceState#render()V:returned'] | 0),
      new Promise(r => setTimeout(() => r(0), 10000))]).catch(() => 0);
  }
  const reads = await page.evaluate(() => ({ tos: performance.now(), reads: globalThis.__packReads }));
  writeFileSync(out, JSON.stringify(reads));
  console.log('tos', tos, 'reads', reads.reads.length, 'at', Math.round(reads.tos));
} finally { await browser.close(); }
