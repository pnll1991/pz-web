// Indicador de rendimiento: cuenta los cuadros reales (swapBuffers), el peor cuadro, el tiempo ocupado y el ping.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PerfMeter, formatPerf, installPerfHud, reportUrl, perfName } from '../../port/runtime/perfhud.mjs';

test('PerfMeter counts frames, the worst gap and busy time per window', () => {
  let t = 0;
  const m = new PerfMeter(() => t);
  for (const at of [0, 16, 32, 132, 148]) m.frame(at);
  m.longTask(120); m.ping(42.4);
  t = 1000;
  assert.deepEqual(m.sample(256 * 1048576), { fps: 5, worstMs: 100, busyPct: 12, pingMs: 42, memMB: 256, frames: 5 });
  // the next window starts empty but keeps the last ping
  m.frame(1016); m.frame(1032);
  t = 2000;
  assert.deepEqual(m.sample(), { fps: 2, worstMs: 868, busyPct: 0, pingMs: 42, memMB: null, frames: 7 });
});

test('a window without frames reports how long the game has not drawn', () => {
  let t = 0;
  const m = new PerfMeter(() => t);
  t = 1000;
  assert.equal(m.sample().worstMs, 0); // never drew: no stall to report
  m.frame(1100);
  t = 2000; m.sample();
  t = 3000;
  const s = m.sample();
  assert.equal(s.fps, 0);
  assert.equal(s.worstMs, 1900);
});

test('formatPerf colours each part by threshold and leaves unknown values neutral', () => {
  const parts = formatPerf({ fps: 58.8, worstMs: 140, busyPct: 40, pingMs: null, memMB: 900 });
  assert.deepEqual(parts.map(p => p.text), ['58.8 fps', 'peor 140 ms', 'ocupado 40 %', 'ping —', '900 MB']);
  assert.deepEqual(parts.map(p => p.level), ['ok', 'bad', 'warn', 'none', 'none']);
  assert.equal(formatPerf({ fps: 12, worstMs: 20, busyPct: 0, pingMs: 300, memMB: null }).length, 4);
});

test('installPerfHud wraps swapBuffers, reports to /perf/report and restores everything on stop', async () => {
  const el = () => ({ style: {}, children: [], replaceChildren(...c) { this.children = c; }, remove() { this.removed = true; } });
  const body = el(); body.appendChild = c => body.children.push(c);
  const doc = { body, createElement: el, createTextNode: text => ({ text }) };
  const win = { swapCount: 0, swapBuffers(handle) { this.swapCount++; return handle; } };
  const original = win.swapBuffers;
  const urls = [];
  const fetchImpl = async url => { urls.push(url); return { ok: true, arrayBuffer: async () => new ArrayBuffer(0) }; };
  const hud = installPerfHud(win, { doc, fetchImpl, name: 'pabc123', Observer: null, intervalMs: 5, pingEveryMs: 60000 });
  assert.equal(win.swapBuffers(1), 1);
  assert.equal(win.swapCount, 1);
  assert.equal(hud.meter.totalFrames, 1);
  await new Promise(r => setTimeout(r, 30));
  assert.equal(urls[0], '/perf/report?u=pabc123&n=1'); // first report: nothing sampled yet
  assert.equal(typeof hud.meter.pingMs, 'number'); // no PerformanceObserver: wall-clock fallback
  assert.equal(globalThis.pzPerf.frames, 1);
  assert.equal(globalThis.pzPerf.name, 'pabc123');
  assert.equal(body.children[0].id, 'pz-perf');
  hud.stop();
  assert.equal(win.swapBuffers, original);
  assert.equal(body.children[0].removed, true);
});

test('reportUrl sends only known values and perfName falls back to a per-tab name', () => {
  assert.equal(reportUrl('pabc', 3, { fps: 58.8, worstMs: 22, busyPct: 4, pingMs: null, memMB: 900 }),
    '/perf/report?u=pabc&n=3&fps=58.8&worst=22&busy=4&mem=900');
  assert.equal(perfName('pq7rst2'), 'pq7rst2');
  assert.equal(perfName(undefined, () => 0.5), 'web-8000');
  assert.equal(perfName('Bad Name!', () => 0), 'web-0000');
});
