// Indicador de rendimiento en pantalla: fps reales del juego (llamadas a swapBuffers), el peor cuadro del último segundo,
// cuánto tiempo estuvo ocupado el hilo principal en tareas largas, el ping al dev server y la memoria JS de la pestaña.
// Sirve para ver, jugador por jugador, si algo se está comiendo el tiempo de la página. ?hud=0 lo apaga.
// Cada 5 s manda el último resumen al dev server (GET /perf/report, respuesta vacía; el panel /jugadores.html los junta) y
// ese mismo pedido mide el ping, tomado de Resource Timing (responseStart - requestStart), que no incluye la espera del
// hilo principal: un cuadro lento no se confunde con red lenta.

export const LEVELS = {
  fps: v => (v >= 45 ? 'ok' : v >= 25 ? 'warn' : 'bad'),
  worstMs: v => (v <= 50 ? 'ok' : v <= 120 ? 'warn' : 'bad'),
  busyPct: v => (v <= 30 ? 'ok' : v <= 70 ? 'warn' : 'bad'),
  pingMs: v => (v <= 120 ? 'ok' : v <= 250 ? 'warn' : 'bad'),
};

/** Acumula cuadros, tareas largas y pings; sample() devuelve el resumen desde la muestra anterior y reinicia la ventana. */
export class PerfMeter {
  constructor(now = () => performance.now()) {
    this.now = now;
    this.windowStart = now(); this.frames = 0; this.lastFrame = null; this.worst = 0; this.busy = 0;
    this.pingMs = null; this.totalFrames = 0;
  }
  frame(t = this.now()) {
    if (this.lastFrame !== null) this.worst = Math.max(this.worst, t - this.lastFrame);
    this.lastFrame = t; this.frames++; this.totalFrames++;
  }
  longTask(ms) { this.busy += ms; }
  ping(ms) { if (Number.isFinite(ms) && ms >= 0) this.pingMs = ms; }
  sample(memoryBytes = null, t = this.now()) {
    const elapsed = Math.max(1, t - this.windowStart);
    // Sin cuadros en la ventana, el peor cuadro es todo lo que lleva el juego sin dibujar (si ya había dibujado alguna vez).
    const stalled = this.lastFrame !== null ? t - this.lastFrame : 0;
    const s = {
      fps: Math.round(this.frames * 10000 / elapsed) / 10,
      worstMs: Math.round(Math.max(this.worst, this.frames ? 0 : stalled)),
      busyPct: Math.min(100, Math.round(this.busy * 100 / elapsed)),
      pingMs: this.pingMs === null ? null : Math.round(this.pingMs),
      memMB: memoryBytes === null ? null : Math.round(memoryBytes / 1048576),
      frames: this.totalFrames,
    };
    this.windowStart = t; this.frames = 0; this.worst = 0; this.busy = 0;
    return s;
  }
}

/** Texto por partes, cada una con su nivel (ok/warn/bad) para colorearla. */
export function formatPerf(s) {
  const part = (key, text) => ({ text, level: s[key] === null || s[key] === undefined ? 'none' : LEVELS[key](s[key]) });
  const parts = [
    part('fps', `${s.fps} fps`),
    part('worstMs', `peor ${s.worstMs} ms`),
    part('busyPct', `ocupado ${s.busyPct} %`),
    part('pingMs', s.pingMs === null ? 'ping —' : `ping ${s.pingMs} ms`),
  ];
  if (s.memMB !== null) parts.push({ text: `${s.memMB} MB`, level: 'none' });
  return parts;
}

const COLORS = { ok: '#9be3b8', warn: '#ffd37a', bad: '#ff8a7a', none: '#c9d3cf' };

/** Pedido con el último resumen de esta pestaña (dev-server/players.mjs); solo los valores conocidos. */
export function reportUrl(name, seq, s) {
  const q = new URLSearchParams({ u: name, n: String(seq) });
  if (s) for (const [k, v] of [['fps', s.fps], ['worst', s.worstMs], ['busy', s.busyPct], ['ping', s.pingMs], ['mem', s.memMB]]) if (v !== null) q.set(k, String(v));
  return `/perf/report?${q}`;
}

/** Nombre del panel: la cuenta del modo directo o, sin ella, uno por pestaña. */
export function perfName(user, rand = () => Math.random()) {
  return /^[a-z0-9-]{1,20}$/.test(user || '') ? user : `web-${Math.floor(rand() * 0x10000).toString(16).padStart(4, '0')}`;
}

/**
 * Engancha el medidor al BrowserWindow (cada swapBuffers es un cuadro del juego) y muestra el panel.
 * Devuelve { meter, stop }; el último resumen queda en globalThis.pzPerf para sondas y consola.
 */
export function installPerfHud(browserWindow, { doc = document, fetchImpl = globalThis.fetch.bind(globalThis), name = perfName(),
  intervalMs = 1000, pingEveryMs = 5000, Observer = globalThis.PerformanceObserver } = {}) {
  const meter = new PerfMeter();
  const own = Object.hasOwn(browserWindow, 'swapBuffers'), original = browserWindow.swapBuffers;
  browserWindow.swapBuffers = handle => { const r = original.call(browserWindow, handle); meter.frame(); return r; };

  const observers = [];
  const observe = (type, onEntry) => {
    if (!Observer?.supportedEntryTypes?.includes(type)) return false;
    const o = new Observer(list => { for (const e of list.getEntries()) onEntry(e); });
    o.observe({ type, buffered: false }); observers.push(o); return true;
  };
  observe('longtask', e => meter.longTask(e.duration));
  // La entrada de Resource Timing llega aparte del fetch; si no hay observador se usa el tiempo de pared.
  const timed = observe('resource', e => {
    if (e.name.includes('/perf/report?') && e.responseStart > 0 && e.requestStart > 0) meter.ping(e.responseStart - e.requestStart);
  });
  let pingSeq = 0, last = null;
  const ping = async () => {
    const t0 = performance.now();
    try {
      const r = await fetchImpl(reportUrl(name, ++pingSeq, last), { cache: 'no-store' });
      await r.arrayBuffer();
      if (!timed) meter.ping(performance.now() - t0);
    } catch { /* sin red: el ping queda en el último valor */ }
  };

  const box = doc.createElement('div');
  box.id = 'pz-perf';
  box.style.cssText = 'position:fixed;right:8px;top:6px;z-index:3;font:12px ui-monospace,monospace;padding:3px 7px;border-radius:4px;' +
    'background:rgba(0,0,0,.55);color:#c9d3cf;pointer-events:none;white-space:nowrap';
  doc.body.appendChild(box);
  const render = s => {
    box.replaceChildren(...formatPerf(s).flatMap((p, i) => {
      const span = doc.createElement('span'); span.textContent = p.text; span.style.color = COLORS[p.level];
      return i ? [doc.createTextNode(' · '), span] : [span];
    }));
  };

  const tick = setInterval(() => {
    const s = meter.sample(performance.memory?.usedJSHeapSize ?? null);
    last = s; globalThis.pzPerf = { ...s, name, at: Date.now() };
    render(s);
  }, intervalMs);
  ping();
  const pinger = setInterval(ping, pingEveryMs);
  const stop = () => { clearInterval(tick); clearInterval(pinger); for (const o of observers) o.disconnect(); box.remove();
    if (own) browserWindow.swapBuffers = original; else delete browserWindow.swapBuffers;
  };
  return { meter, stop };
}
