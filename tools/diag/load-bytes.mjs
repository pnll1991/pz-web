// Ronda 87: bytes de red de la 1.ª (perfil vacío) y la 2.ª (mismo perfil) carga de /play.html hasta entrar al mundo, por categoría.
//   node tools/diag/load-bytes.mjs --label before [--runs 2] [--url http://127.0.0.1:8199/play.html?port=16271]
// Usa CDP Network (encodedDataLength = bytes por el cable, 0 si salió de la caché). Sólo contra la web de pruebas 8199.
import { chromium } from '@playwright/test';
import { rmSync, mkdirSync, writeFileSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i++) { const k = argv[i].replace(/^--/, ''); if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) A[k] = argv[++i]; else A[k] = true; }
const URL_ = A.url || 'http://127.0.0.1:8199/play.html?port=16271';
if (!URL_.includes('127.0.0.1:8199')) throw new Error('sólo la web de pruebas 8199');
const RUNS = +(A.runs || 2), LABEL = A.label || 'run', OUT = '/tmp/pz86/load-bytes'; mkdirSync(OUT, { recursive: true });
const PROFILE = '/tmp/pz86/profile-load'; rmSync(PROFILE, { recursive: true, force: true });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const cat = u => /boot\.wasm(\?|$)/.test(u) ? 'boot.wasm' : /\/packs\//.test(u) ? 'packs' : /\/media(-bundle)?[/?]/.test(u) ? 'media' : /\.(wasm)(\?|$)/.test(u) ? 'otros-wasm' : /\.(m?js|lua|lbc|json|html|css)(\?|$)/.test(u) ? 'codigo/json' : 'otros';

const results = [];
for (let run = 1; run <= RUNS; run++) {
  const ctx = await chromium.launchPersistentContext(PROFILE, { ...launchOptions(), viewport: { width: 1280, height: 720 } });
  const page = ctx.pages()[0] || await ctx.newPage();
  const cdp = await ctx.newCDPSession(page); await cdp.send('Network.enable');
  const reqs = new Map(), tot = {}; let ingame = false, t0 = Date.now(), tIn = null;
  cdp.on('Network.requestWillBeSent', e => reqs.set(e.requestId, e.request.url));
  cdp.on('Network.loadingFinished', e => { const u = reqs.get(e.requestId) || ''; const c = cat(u); (tot[c] ??= { bytes: 0, n: 0 }); tot[c].bytes += e.encodedDataLength; tot[c].n++; });
  page.on('console', m => { if (/PZWEB-STATE:ingame$/.test(m.text())) { ingame = true; tIn = (Date.now() - t0) / 1000; } });
  await page.goto(URL_, { waitUntil: 'domcontentloaded', timeout: 120000 });
  const end = Date.now() + 25 * 60000; while (!ingame && Date.now() < end) await sleep(2000);
  await sleep(5000);
  const sum = Object.values(tot).reduce((n, v) => n + v.bytes, 0);
  const r = { label: LABEL, run, ingame, secondsToIngame: tIn, totalMB: +(sum / 1e6).toFixed(1), byCategory: Object.fromEntries(Object.entries(tot).map(([k, v]) => [k, { MB: +(v.bytes / 1e6).toFixed(1), requests: v.n }])) };
  console.log(JSON.stringify(r)); results.push(r);
  await ctx.close();
}
writeFileSync(`${OUT}/${LABEL}.json`, JSON.stringify(results, null, 1));
rmSync(PROFILE, { recursive: true, force: true });
