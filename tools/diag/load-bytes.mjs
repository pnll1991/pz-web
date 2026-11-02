// Ronda 87/89: bytes de red de la 1.ª (perfil vacío) y la 2.ª (mismo perfil) carga de /play.html hasta entrar al mundo, por categoría,
// y segundos hasta cada estado (menu, ingame).
//   flock /tmp/pz80-locks/chrome.lock node tools/diag/load-bytes.mjs --label before [--runs 2] [--cold 1,3] [--url http://127.0.0.1:8211/play.html?port=16291]
// --throttle <Mbps> [--latency <ms>] limita la bajada con CDP (Network.emulateNetworkConditions) para parecerse a un túnel; sin eso es loopback.
// --cold lista las cargas que parten de un perfil vacío (por defecto solo la 1.ª; las demás reutilizan el perfil de la anterior).
// Usa CDP Network: encodedDataLength = bytes por el cable (comprimidos, 0 si salió de la caché) y dataLength = bytes ya descomprimidos.
// Solo contra una instancia de prueba en 127.0.0.1 (nunca 8098/8097/8092). Los pedidos de `/packs/` con Range son tramos de un pack perezoso.
import { chromium } from '@playwright/test';
import { rmSync, mkdirSync, writeFileSync, appendFileSync } from 'node:fs';
import { launchOptions } from './chrome-flags.mjs';

const argv = process.argv.slice(2), A = {};
for (let i = 0; i < argv.length; i++) { const k = argv[i].replace(/^--/, ''); if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) A[k] = argv[++i]; else A[k] = true; }
const URL_ = A.url || 'http://127.0.0.1:8199/play.html?port=16271';
const target = new URL(URL_);
if (target.hostname !== '127.0.0.1' || ['8098', '8097', '8092'].includes(target.port)) throw new Error('solo una instancia de prueba en 127.0.0.1 (no 8098/8097/8092)');
const RUNS = +(A.runs || 2), LABEL = A.label || 'run', OUT = A.out || '/tmp/pz86/load-bytes'; mkdirSync(OUT, { recursive: true });
const PROFILE = A.profile || `${OUT}/profile-${LABEL}`; rmSync(PROFILE, { recursive: true, force: true });
const COLD = new Set(String(A.cold || '1').split(',').map(Number));
const LIMIT_MIN = +(A.minutes || 25);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const cat = (u, ranged) => /boot\.wasm(\?|$)/.test(u) ? 'boot.wasm'
  : /\/packs\/[a-f0-9]{64}\.bin/.test(u) ? (ranged ? 'packs-tramos' : 'packs')
  : /\/media(-bundle)?[/?]/.test(u) ? 'media' : /\.(wasm)(\?|$)/.test(u) ? 'otros-wasm'
  : /\.(m?js|lua|lbc|json|html|css)(\?|$)/.test(u) ? 'codigo/json' : 'otros';

const results = [];
for (let run = 1; run <= RUNS; run++) {
  if (COLD.has(run)) rmSync(PROFILE, { recursive: true, force: true });
  rmSync(`${OUT}/${LABEL}-run${run}.log`, { force: true });
  const ctx = await chromium.launchPersistentContext(PROFILE, { ...launchOptions(), viewport: { width: 1280, height: 720 } });
  const page = ctx.pages()[0] || await ctx.newPage();
  const cdp = await ctx.newCDPSession(page); await cdp.send('Network.enable');
  if (A.throttle) await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: +(A.latency || 40), downloadThroughput: +A.throttle * 125000, uploadThroughput: +A.throttle * 125000 });
  const reqs = new Map(), tot = {}, seconds = {}; let ingame = false; const t0 = Date.now();
  cdp.on('Network.requestWillBeSent', e => reqs.set(e.requestId, { url: e.request.url, ranged: !!(e.request.headers.Range || e.request.headers.range), decoded: 0 }));
  cdp.on('Network.dataReceived', e => { const r = reqs.get(e.requestId); if (r) r.decoded += e.dataLength; });
  cdp.on('Network.loadingFinished', e => {
    const r = reqs.get(e.requestId); if (!r) return;
    const c = cat(r.url, r.ranged); (tot[c] ??= { bytes: 0, decoded: 0, n: 0 });
    tot[c].bytes += e.encodedDataLength; tot[c].decoded += r.decoded; tot[c].n++;
  });
  page.on('console', m => {
    const text = m.text();
    // errores, estado de las cachés y de los packs perezosos al archivo de la corrida (el resto del log del juego es enorme)
    if (m.type() === 'error' || m.type() === 'warning' && !/GL|WebGL/.test(text) || /^(caches|lazyfill|prefetch|mediacache|boot-timings|PZWEB-STATE)/.test(text)) appendFileSync(`${OUT}/${LABEL}-run${run}.log`, `${((Date.now() - t0) / 1000).toFixed(1)} ${m.type()} ${text.slice(0, 1500)}\n`);
    const s = /PZWEB-STATE:(\S+)/.exec(text);
    if (s) { seconds[s[1]] ??= +((Date.now() - t0) / 1000).toFixed(1); if (s[1] === 'ingame') ingame = true; }
  });
  await page.goto(URL_, { waitUntil: 'domcontentloaded', timeout: 120000 });
  const end = Date.now() + LIMIT_MIN * 60000; while (!ingame && Date.now() < end) await sleep(2000);
  await sleep(+(A.settle || 5) * 1000);
  const stats = await page.evaluate(() => ({ pack: globalThis.pzPackStats && { sources: globalThis.pzPackStats.sources, lazy: globalThis.pzPackStats.lazy, lazyStore: globalThis.pzPackStats.lazyStore },
    lazyFiles: globalThis.pzLazyFiles && Object.fromEntries(Object.entries(globalThis.pzLazyFiles).map(([k, f]) => [k, { ...f.stats, touched: f.touched.size, touchedMB: +(f.touchedBytes / 1e6).toFixed(1) }])) })).catch(() => null);
  const sum = Object.values(tot).reduce((n, v) => n + v.bytes, 0), sumDecoded = Object.values(tot).reduce((n, v) => n + v.decoded, 0);
  const r = { label: LABEL, run, throttleMbps: A.throttle ? +A.throttle : null, ingame, secondsToIngame: seconds.ingame ?? null, seconds, totalMB: +(sum / 1e6).toFixed(1), decodedMB: +(sumDecoded / 1e6).toFixed(1),
    byCategory: Object.fromEntries(Object.entries(tot).map(([k, v]) => [k, { MB: +(v.bytes / 1e6).toFixed(1), decodedMB: +(v.decoded / 1e6).toFixed(1), requests: v.n }])), stats };
  console.log(JSON.stringify(r)); results.push(r);
  await ctx.close();
}
writeFileSync(`${OUT}/${LABEL}.json`, JSON.stringify(results, null, 1));
rmSync(PROFILE, { recursive: true, force: true });
