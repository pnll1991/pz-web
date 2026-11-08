// Comprueba que la pantalla de carga (#pz-loading) sigue visible hasta el estado 'ingame' del modo directo.
//   flock /tmp/pz80-locks/chrome.lock node tools/diag/loading-hold.mjs [URL] [OUT_DIR]
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';
const URL_ = process.argv[2] || 'http://127.0.0.1:8199/play.html?port=16291', OUT = process.argv[3] || '/tmp/pz-loading-hold';
mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch(launchOptions());
const page = await (await browser.newContext({ viewport: { width: 1280, height: 720 } })).newPage();
const t0 = Date.now(), states = [], shots = new Set(); let ingame = false;
page.on('console', m => { const x = /PZWEB-STATE:(\S+)/.exec(m.text()); if (x) { states.push([+((Date.now() - t0) / 1000).toFixed(1), x[1]]); if (x[1] === 'ingame') ingame = true; } });
await page.goto(URL_, { waitUntil: 'domcontentloaded' });
const visible = () => page.evaluate(() => { const e = document.getElementById('pz-loading'); return e ? { here: true, out: e.classList.contains('pz-out'), label: e.querySelector('.pz-label')?.textContent } : { here: false }; }).catch(() => ({ here: 'nav' }));
const seen = []; let bad = 0;
while (!ingame && Date.now() - t0 < 12 * 60000) {
  await new Promise(r => setTimeout(r, 3000));
  const v = await visible(), last = states.at(-1)?.[1];
  if (!seen.length || seen.at(-1)[1] !== v.label) seen.push([+((Date.now() - t0) / 1000).toFixed(0), v.label]);
  if (last && last !== 'ingame' && ['tos-accepted', 'menu', 'profession', 'character', 'loaded', 'world'].includes(last) && (!v.here || v.out)) { bad++; }
  if (last && !shots.has(last) && ['menu', 'character', 'world', 'loaded'].includes(last)) { shots.add(last); await page.screenshot({ path: `${OUT}/${last}.png` }); }
}
await new Promise(r => setTimeout(r, 4000));
const after = await visible(); await page.screenshot({ path: `${OUT}/ingame.png` });
console.log(JSON.stringify({ ingame, secondsToIngame: states.find(s => s[1] === 'ingame')?.[0], states, overlayGoneTooEarly: bad, labels: seen, afterIngame: after }));
await browser.close();
