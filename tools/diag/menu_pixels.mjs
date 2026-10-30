// Ronda 64b: ¿el canvas dibuja pixeles del menu? Lee el framebuffer por defecto en el mismo
// frame (readPixels tras cada callback de rAF) una vez que MainScreenState#render volvio >= N.
import { chromium } from '@playwright/test';
import { writeFileSync } from 'node:fs';
const PORT = process.env.PZ_DIAG_PORT || '8097';
const MAX = Number(process.env.PZ_DIAG_MAX || '900000');
const N = Number(process.env.PZ_RENDERS || '6');
const OUT = process.env.PZ_SHOT || `${process.env.HOME}/pzshots/menu-64b.png`;
const race = (p, ms) => Promise.race([p, new Promise((_, rj) => setTimeout(() => rj(new Error('timeout')), ms))]);
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-dev-shm-usage'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
const cons = []; page.on('console', m => { cons.push(m.text().slice(0, 300)); if (cons.length > 4000) cons.shift(); });
const errs = []; page.on('pageerror', e => errs.push(String(e.stack || e.message).slice(0, 4000)));
await page.addInitScript((N) => {
  const S = globalThis.__px = { frames: 0, shots: [], log: [] };
  const orig = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (t, o) {
    const c = orig.call(this, t, o);
    if (t === 'webgl2' && c && !S.gl) {
      S.gl = c; S.canvas = this; S.attrs = c.getContextAttributes();
      S.cur = { clears: 0, draws: 0, drawsDefault: 0, clearAfterDraw: 0, blits: 0, fbBinds: 0 };
      let fb = null; const bf = c.bindFramebuffer.bind(c);
      c.bindFramebuffer = (tg, f) => { if (tg !== c.READ_FRAMEBUFFER) fb = f; S.cur.fbBinds++; return bf(tg, f); };
      const wrap = (n, fn) => { const o = c[n].bind(c); c[n] = (...a) => { fn(a); return o(...a); }; };
      wrap('clear', () => { S.cur.clears++; if (fb === null && S.cur.drawsDefault) S.cur.clearAfterDraw++; });
      for (const n of ['drawArrays', 'drawElements', 'drawArraysInstanced', 'drawElementsInstanced', 'drawRangeElements'])
        wrap(n, () => { S.cur.draws++; if (fb === null) S.cur.drawsDefault++; });
      wrap('blitFramebuffer', () => S.cur.blits++);
      S.viewports = {}; // distinct glViewport/scissor boxes (what area the game really drew into)
      wrap('viewport', (v) => { const k = 'vp ' + v.join(','); S.viewports[k] = (S.viewports[k] || 0) + 1; });
      wrap('scissor', (v) => { const k = 'sc ' + v.join(','); S.viewports[k] = (S.viewports[k] || 0) + 1; });
      S.getFb = () => fb;
    }
    return c;
  };
  const raf = globalThis.requestAnimationFrame.bind(globalThis);
  globalThis.requestAnimationFrame = cb => raf(ts => {
    const r = cb(ts); S.frames++;
    const n = (globalThis.pzGameCounts || {})['zombie.gameStates.MainScreenState#render()V:returned'] || 0;
    if (S.gl && n >= N && S.shots.length < 5) {
      const gl = S.gl, w = gl.drawingBufferWidth, h = gl.drawingBufferHeight;
      const prev = S.getFb(); gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      const px = new Uint8Array(w * h * 4); gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, px);
      gl.bindFramebuffer(gl.FRAMEBUFFER, prev);
      let nb = 0, sum = 0; const hist = {};
      for (let i = 0; i < px.length; i += 4) { const s = px[i] + px[i + 1] + px[i + 2]; sum += s; if (s > 24) nb++; const key = (px[i] >> 5) + ',' + (px[i + 1] >> 5) + ',' + (px[i + 2] >> 5); hist[key] = (hist[key] || 0) + 1; }
      const top = Object.entries(hist).sort((a, b) => b[1] - a[1]).slice(0, 6);
      S.shots.push({ frame: S.frames, renders: n, w, h, nonBlack: +(nb / (w * h)).toFixed(4), meanRGB: +(sum / (w * h) / 3).toFixed(1),
        top, viewport: Array.from(gl.getParameter(gl.VIEWPORT)), scissor: gl.isEnabled(gl.SCISSOR_TEST) ? Array.from(gl.getParameter(gl.SCISSOR_BOX)) : null,
        frameOps: { ...S.cur }, url: S.shots.length === 0 ? S.canvas.toDataURL('image/png') : null });
    }
    S.cur && Object.keys(S.cur).forEach(x => S.cur[x] = 0);
    return r;
  });
}, N);
await page.goto(`http://127.0.0.1:${PORT}/game.html?maplog=1`, { waitUntil: 'domcontentloaded' });
const t0 = Date.now(); let res = null; let profiled = false;
const PROFILE_AT = Number(process.env.PZ_PROFILE_AT || '0'); // seconds; 0 = off
async function profile() { // CPU profile of the main thread, top self-time functions (diagnostic only)
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Profiler.enable'); await cdp.send('Profiler.setSamplingInterval', { interval: 2000 });
  await cdp.send('Profiler.start'); await new Promise(r => setTimeout(r, 30000));
  const { profile: pr } = await cdp.send('Profiler.stop');
  const self = new Map(), byId = new Map(pr.nodes.map(n => [n.id, n])); const dt = pr.timeDeltas || [];
  pr.samples.forEach((id, i) => { const n = byId.get(id); const k = n.callFrame.functionName + ' ' + n.callFrame.url.split('/').pop() + ':' + n.callFrame.lineNumber; self.set(k, (self.get(k) || 0) + (dt[i] || 0)); });
  const top = [...self.entries()].sort((a, b) => b[1] - a[1]).slice(0, 40).map(([k, v]) => (v / 1000).toFixed(0) + 'ms ' + k);
  writeFileSync(OUT.replace('.png', '-profile.txt'), top.join('\n')); console.log('PROFILE saved');
}
while (Date.now() - t0 < MAX) {
  await page.waitForTimeout(20000);
  if (PROFILE_AT && !profiled && Date.now() - t0 > PROFILE_AT * 1000) { profiled = true; try { await race(profile(), 120000); } catch (e) { console.log('PROFILE_FAIL', String(e)); } }
  try { res = await race(page.evaluate(() => { const S = globalThis.__px; return { frames: S.frames, attrs: S.attrs, viewports: S.viewports, canvas: S.canvas && [S.canvas.width, S.canvas.height], winEvents: (globalThis.pzRuntime?.window?.events || []).filter(e => /created|resized|init/.test(e.type)).slice(0, 8), shots: S.shots.length, out: S.shots.length >= 3 ? S.shots : null,
    renders: Object.entries(globalThis.pzGameCounts || {}).filter(([k]) => /MainScreenState#render/.test(k)) }; }), 90000); }
  catch (e) { console.log('SAMPLE_FAIL', String(e)); continue; }
  writeFileSync(OUT.replace('.png', '-console.txt'), cons.join('\n'));
  console.log('SAMPLE', Math.round((Date.now() - t0) / 1000), JSON.stringify({ frames: res.frames, shots: res.shots, renders: res.renders }), 'LAST', JSON.stringify(cons.slice(-2)));
  if (res.out) break;
}
if (res?.out) {
  const first = res.out[0]; if (first.url) writeFileSync(OUT, Buffer.from(first.url.split(',')[1], 'base64'));
  for (const s of res.out) { delete s.url; console.log('SHOT', JSON.stringify(s)); }
  console.log('VIEWPORTS', JSON.stringify(res.viewports), 'CANVAS', JSON.stringify(res.canvas), 'WIN', JSON.stringify(res.winEvents));
  console.log('ATTRS', JSON.stringify(res.attrs), 'SAVED', OUT);
} else console.log('RESULT no menu frame captured');
try { await page.screenshot({ path: OUT.replace('.png', '-page.png'), timeout: 120000 }); console.log('PAGESHOT ok'); } catch (e) { console.log('PAGESHOT_FAIL', String(e).slice(0, 200)); }
writeFileSync(OUT.replace('.png', '-console.txt'), cons.join('\n'));
console.log('ERRS', JSON.stringify(errs.slice(0, 10)));
await browser.close();
