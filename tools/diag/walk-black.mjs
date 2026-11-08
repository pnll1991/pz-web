// Diagnóstico temporal: entra por play.html, camina en línea recta y guarda capturas + errores de consola para ver cuándo la pantalla se pone negra.
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';
const URL_ = process.argv[2], OUT = process.argv[3]; mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch(launchOptions());
const page = await (await browser.newContext({ viewport: { width: 1280, height: 720 } })).newPage();
const t0 = Date.now(), errs = []; let ingame = false, tIn = 0;
page.on('console', m => { const t = m.text(); if (/PZWEB-STATE:ingame/.test(t)) { ingame = true; tIn = Date.now(); }
  if (ingame && errs.length < 40 && (m.type() === 'error' || /Exception|ERROR|Error:|slowread|IngameState|black/i.test(t))) errs.push(`${((Date.now() - tIn) / 1000).toFixed(0)}s ${t.slice(0, 200)}`); });
page.on('pageerror', e => errs.push('pageerror ' + String(e).slice(0, 200)));
await page.goto(URL_, { waitUntil: 'domcontentloaded' });
while (!ingame && Date.now() - t0 < 12 * 60000) await new Promise(r => setTimeout(r, 2000));
await new Promise(r => setTimeout(r, 5000));
await page.mouse.click(640, 360).catch(() => {});
const dirs = process.argv[4] ? process.argv[4].split(',') : ['a'];
const hud = () => page.evaluate(() => document.body.innerText.match(/\d+ fps[^\n]*/)?.[0] ?? '').catch(() => '');
const log = [];
for (let i = 0; i < +(process.argv[5] || 8); i++) {
  const k = dirs[i % dirs.length];
  await page.keyboard.down('Shift'); await page.keyboard.down(k); await new Promise(r => setTimeout(r, 15000)); await page.keyboard.up(k); await page.keyboard.up('Shift');
  await page.screenshot({ path: `${OUT}/w${String(i).padStart(2, '0')}-${k}.png` }); log.push([i, k, await hud()]);
}
console.log(JSON.stringify({ log, errs }));
await browser.close();
