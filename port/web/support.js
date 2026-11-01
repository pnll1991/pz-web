// Round 85: can this browser run the game at all? Checked before anything heavy loads, so a phone gets a clear message instead of
// "Load failed". Classic script (also loaded by Node tests through module.exports). The game needs a desktop-class browser: WebAssembly
// GC, a cross-origin-isolated page and several GB of memory (the renderer uses 2.5-4.5 GB; an iPhone tab is limited to about 1.5 GB).
(function (root) {
  // A minimal module with a WasmGC struct type: validates only where WebAssembly GC exists.
  const GC_PROBE = Uint8Array.of(0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 120, 0);

  function pzSupportProblem(env) {
    const ua = String(env.userAgent || '');
    const ios = /iPhone|iPad|iPod/.test(ua) || (env.platform === 'MacIntel' && (env.maxTouchPoints || 0) > 1);
    if (ios) return 'Necesitás una PC con Chrome, Edge o Firefox de escritorio. Este juego no corre en iPhone ni en iPad: necesita varios GB de memoria por pestaña y WebAssembly GC, y iOS no los da.';
    if (/Android/.test(ua) && /Mobile/.test(ua)) return 'Necesitás una PC con Chrome, Edge o Firefox de escritorio. Este juego no está pensado para celulares (necesita varios GB de memoria).';
    if (!env.wasmGC) return 'Tu navegador no soporta WebAssembly GC. Necesitás una PC con Chrome, Edge o Firefox de escritorio actualizados.';
    if (env.isSecureContext === false) return 'La página tiene que abrirse por https (o desde 127.0.0.1).';
    if (env.crossOriginIsolated === false) return 'El navegador no aisló la página (COOP/COEP). Probá con Chrome, Edge o Firefox de escritorio actualizados.';
    return null;
  }

  if (typeof module !== 'undefined' && module.exports) { module.exports = { pzSupportProblem, GC_PROBE }; return; }
  const env = {
    userAgent: root.navigator.userAgent, platform: root.navigator.platform, maxTouchPoints: root.navigator.maxTouchPoints,
    wasmGC: (() => { try { return root.WebAssembly.validate(GC_PROBE); } catch { return false; } })(),
    isSecureContext: root.isSecureContext, crossOriginIsolated: root.crossOriginIsolated,
  };
  // ?force=1 skips the check (to see what really fails on a phone; the server's access log then shows the failing request).
  const problem = new URLSearchParams(root.location.search).get('force') === '1' ? null : pzSupportProblem(env);
  root.pzUnsupported = problem;
  if (problem) {
    try { root.stop(); } catch { /* nothing left to stop */ }
    root.document.title = 'Project Zomboid · no disponible en este dispositivo';
    root.document.documentElement.innerHTML = '<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' +
      '<body style="margin:0;background:#111;color:#eee;font:18px/1.5 sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh">' +
      '<main style="max-width:30em;padding:1.5em"><h1 style="font-size:1.3em">Project Zomboid · jugar</h1><p id="pz-unsupported"></p></main></body>';
    root.document.getElementById('pz-unsupported').textContent = problem;
  }
})(typeof window !== 'undefined' ? window : globalThis);
