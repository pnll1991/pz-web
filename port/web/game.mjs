import { sqliteBridge } from '/runtime/sqlite.mjs';
import { bulletBridge } from '/runtime/bullet.mjs';
import { GLBridge } from '/runtime/gl.mjs';
import { NetBridge } from '/runtime/net.mjs';
import { installAutoJoin, watchConsole, clickUntilIngame } from '/runtime/autojoin.mjs';
import { prefetchMedia } from '/runtime/prefetch.mjs';
import { CanvasInput } from '/runtime/input.mjs';
import { BrowserWindow } from '/runtime/window.mjs';
import { VirtualFileSystem, normalizePath } from '/runtime/vfs.mjs';
import { preloadPacks } from '/runtime/packs.mjs';
import { TextureCache } from '/runtime/texcache.mjs';
import { MediaCache, mediaCacheVersion } from '/runtime/mediacache.mjs';
import { LoadingScreen, formatMB } from '/runtime/loading.mjs';
import { ShaderCache, translatorVersion } from '/runtime/glslcache.mjs';
import { installPerfHud, perfName } from '/runtime/perfhud.mjs';

const status = document.getElementById('game-status');
// Pantalla de carga: tapa la página hasta que el juego dibuja su primer cuadro (BrowserWindow.swapCount).
const loading = new LoadingScreen(document);
loading.step('build', 'active');
// Medición de la carga inicial: ms desde el inicio de la navegación hasta cada fase del arranque (performance.now()),
// también como performance.mark('pz:<fase>') para el panel Performance de Chrome. `boot-timings {...}` sale por consola
// al llamar a main y cada vez que se agrega una fase del juego (phase de pzGame, estados del modo directo, menú dibujado).
const bootTimings = globalThis.pzBootTimings = {};
const bootMark = name => {
  if (name in bootTimings) return;
  bootTimings[name] = Math.round(performance.now());
  try { performance.mark(`pz:${name}`); } catch { /* sin User Timing */ }
};
const bootReport = () => console.log(`boot-timings ${JSON.stringify(bootTimings)}`);
bootMark('modules');
// TeaVM derives Java frames from V8 stacks; the default ten frames hide callers.
// Diagnostic depth for round 28: the RangeError reported in rounds 27b/28 has a repeating
// Fiber::push/reversePush/Arrays::copyOf cycle. A higher limit keeps the base frames of that
// recursion visible, which is what identifies the real Java caller.
if (typeof Error.stackTraceLimit === 'number') Error.stackTraceLimit = Infinity;
// Round 31: the render fiber's last boundary is Thread#sleep(J)V:entered and it is never resumed,
// while the wrapped teavmAsync.offer reported exactly one offer (the renderThread start). To find
// out which JS path really registers timers here, observe setTimeout itself: who calls it, with
// which delay, and how many timers actually fire. Observation only; the call still happens.
const timerObs = globalThis.pzTimers = { calls: 0, fired: 0, bySite: {}, items: [], zero: 0 };
const baseSetTimeout = globalThis.setTimeout.bind(globalThis);
const baseClearTimeout = globalThis.clearTimeout.bind(globalThis);
// Round 81 (front 3): the call-site capture below ran `new Error().stack` with stackTraceLimit = Infinity inside
// the (deep) fiber stack on EVERY setTimeout -- 3-4 % of the in-game frame in the release profile -- so it is
// opt-in now (?timerdiag=1). Counters stay.
const timerDiag = new URLSearchParams(location.search).get('timerdiag') === '1';
// Round 81 (front 3): setTimeout(fn, 0) chains (BrowserYield, TeaVM EventQueue.offer with no delay) are clamped
// to >= 4 ms by the browser after 5 nested timers, and the in-game profile showed 30-37 % of the main thread idle
// between frames. A MessageChannel task has no clamp and keeps FIFO order. Ids are negative so clearTimeout can
// cancel them; `?zerotimer=0` restores the plain setTimeout.
const zeroTimer = new URLSearchParams(location.search).get('zerotimer') !== '0';
const zeroQueue = [], zeroItems = new Map();
let zeroId = 0;
const zeroChannel = new MessageChannel();
zeroChannel.port1.onmessage = () => {
  const item = zeroQueue.shift();
  if (item === undefined) return;
  zeroItems.delete(item.id);
  if (!item.cancelled) { timerObs.fired++; item.fn(); }
};
globalThis.clearTimeout = (id) => {
  if (typeof id === 'number' && id < 0) { const item = zeroItems.get(id); if (item !== undefined) { item.cancelled = true; zeroItems.delete(id); } return; }
  return baseClearTimeout(id);
};
globalThis.setTimeout = (fn, ms, ...rest) => {
  timerObs.calls++;
  if (timerDiag) {
    const site = ((new Error()).stack ?? '').split('\n').slice(1, 4)
      .map((line) => line.trim().slice(0, 90)).join(' | ');
    timerObs.bySite[site] = (timerObs.bySite[site] ?? 0) + 1;
    if (timerObs.items.length < 40) timerObs.items.push({ n: timerObs.calls, ms: ms ?? null, site });
  }
  if (zeroTimer && !(ms > 0) && rest.length === 0 && typeof fn === 'function') {
    timerObs.zero++;
    const item = { id: --zeroId, fn, cancelled: false };
    zeroItems.set(item.id, item); zeroQueue.push(item);
    zeroChannel.port2.postMessage(0);
    return item.id;
  }
  return baseSetTimeout(() => { timerObs.fired++; fn(); }, ms, ...rest);
};
// Ronda 81: ?build=release carga el build optimizado (scripts/build.sh --with-game --release), publicado en
// /release/game/ con su propio build-state.json y pulse-map.json; ?build=<nombre> carga /<nombre>/game/ (variantes
// para comparar); sin el parámetro, el build dev de /game/.
const buildName = new URLSearchParams(location.search).get('build');
const gameDir = buildName && /^[a-z0-9-]+$/.test(buildName) ? `/${buildName}/game` : '/game';
// Ronda 88 (carga inicial): empezar a bajar y compilar boot.wasm ya, mientras se abren la VFS y los packs, en vez de
// hacerlo al final (el cargador de TeaVM llama a compileStreaming recien al llegar a TeaVM.wasmGC.load). Mas abajo se
// engancha compileStreaming para que el cargador reciba este modulo ya compilado. ?earlycompile=0 lo apaga; si la
// compilacion anticipada falla se vuelve al camino original (descarga normal).
// Carga inicial: game.html ya lanza esa descarga en un script clasico (globalThis.pzEarlyWasm), antes de los imports de
// este modulo; si no esta (otra pagina, o fallo), se lanza aca como antes. Su Response da ademas el id del build (ETag).
const earlyWasmUrl = `${gameDir}/boot.wasm`;
const earlyFetch = globalThis.pzEarlyWasm?.url === earlyWasmUrl ? globalThis.pzEarlyWasm
  : new URLSearchParams(location.search).get('earlycompile') === '0' ? null : (() => {
    try {
      const response = fetch(earlyWasmUrl), compiled = WebAssembly.compileStreaming(response, { builtins:['js-string'] });
      response.catch(() => {}); compiled.catch(() => {});
      return { url: earlyWasmUrl, response, compiled };
    } catch { return null; }
  })();
const earlyWasm = earlyFetch?.compiled ?? null;
// Id del build para la caché de texturas: el ETag de la respuesta temprana de boot.wasm; si no hay, un HEAD como antes.
const buildIdOf = response => response.headers.get('ETag') || response.headers.get('Last-Modified') || '';
async function buildId() {
  try { const response = await earlyFetch?.response; if (response?.ok && buildIdOf(response)) return buildIdOf(response); } catch { /* HEAD */ }
  const head = await fetch(earlyWasmUrl, { method:'HEAD', cache:'no-cache' });
  return head.ok ? buildIdOf(head) : '';
}
earlyFetch?.response?.then(response => {
  const size = Number(response.headers.get('Content-Length'));
  loading.step('engine', 'active', size > 0 ? formatMB(size) : undefined);
}, () => {});
earlyWasm?.then(() => loading.step('engine', 'done'), () => {});
// Chrome puede desalojar IndexedDB/Cache API bajo presion de disco; la cache de texturas (~945 MB) y los packs se pierden
// y el arranque siguiente es en frio. Pedir almacenamiento persistente es solo una peticion: si se niega no cambia nada.
navigator.storage?.persist?.().then(granted => { globalThis.pzStoragePersisted = granted; }, () => {});
try {
  const response = await fetch(`${gameDir}/build-state.json`);
  if (!response.ok) throw new Error('Todavía no hay una compilación del juego.');
  const build = await response.json();
  if (!build.success || build.mode !== 'game-main') throw new Error('La compilación real del juego está bloqueada. Consultá el reporte local de TeaVM.');
  if (!crossOriginIsolated) throw new Error('Faltan COOP/COEP.');
  loading.step('build', 'done');
  bootMark('build-state');
  earlyWasm?.then(() => bootMark('wasm-compiled'), () => {});
  // Round 32: the boundary transformer emits int ids (port.shim.BrowserProgress.pulse(int)) because
  // a String constant injected in a frame that resumes after a suspend is still unmaterialized on
  // WasmGC and crashed the module (diag56). Decode the ids here, before boot.wasm runs, so the
  // diagnostics keep their label shape. A missing map only silences the diagnostics; it never
  // changes the game.
  // Carga inicial: se pide ya y se espera justo antes de cargar boot.wasm (mas abajo), no antes de abrir la VFS.
  const pulseReady = fetch(`${gameDir}/pulse-map.json`)
    .then(async pulseResponse => { if (pulseResponse.ok) globalThis.pzPulseLabels = await pulseResponse.json(); })
    .catch(error => console.warn('pulse-map no disponible:', error.message));
  // Round 32: suspending the render fiber corrupts TeaVM's fiber state while the game fiber is
  // also parked (see port/shim/BrowserYield). Round 32 measurement: with suspension OFF the page
  // never returns to the JS event loop (Playwright evaluate/screenshot both time out, diag64), so the
  // default is suspension ON; the wasm gate skips the suspend while a render-context item is in
  // flight, which is the only window where two fibers are parked. '?suspend=0' disables it.
  globalThis.pzYieldSuspend = new URLSearchParams(location.search).get('suspend') !== '0';
  const canvas = document.getElementById('game-canvas');
  canvas.hidden = false;
  // Ronda 88: estas lecturas no dependen de la VFS ni de los packs; se lanzan ya y se consumen mas abajo.
  const remoteWanted = new URLSearchParams(location.search).get('remote') !== '0';
  const manifestFetch = remoteWanted ? fetch('/media-manifest.json') : null;
  manifestFetch?.catch(() => {});
  const classpathFetches = ['stdlib.lua', 'stdlib.lbc'].map(name => ({ name, response: fetch(`/classpath/resource/0/${name}`) }));
  for (const { response } of classpathFetches) response.catch(() => {});
  // Carga inicial: SQLite (loader + wasm), Bullet (manifest + glue + wasm) y la caché de texturas (IndexedDB) no dependen
  // de la VFS abierta ni de los packs: arrancan ya y se esperan en el mismo punto que antes. Un error se sigue
  // lanzando ahí (el .catch de abajo solo evita el aviso de promesa sin manejar mientras tanto).
  const vfsInstance = new VirtualFileSystem();
  const sqliteReady = sqliteBridge(vfsInstance), bulletReady = bulletBridge();
  sqliteReady.catch(() => {}); bulletReady.catch(() => {});
  // Ronda 81 (frente 2): páginas de texturas decodificadas por el juego, guardadas en IndexedDB y
  // reutilizadas en el arranque siguiente (port.runtime.PackImageCache). Ronda 89: clave = CODEC_VERSION (texcache.mjs)
  // + SHA-256 del PNG, no el build (?texcache=build lo vuelve a llavear por ETag de boot.wasm); activa por defecto (?nocache=1 / ?texcache=0 la apagan), ?texcache=verify compara sin usarla.
  // Si IndexedDB falla el juego sigue con el call() original (modo 0) y el error queda en stats.
  const texCache = TextureCache.fromLocation(location.search, '');
  const texCacheReady = texCache.mode === 0 ? Promise.resolve() : (async () => {
    try {
      if (texCache.keying === 'build') texCache.build = await buildId();
      await texCache.open();
    } catch (error) {
      texCache.stats.lastError = String(error); texCache.mode = 0;
      console.warn('texcache apagada:', error);
    }
  })();
  // Caché persistente de las lecturas de /media (port/runtime/mediacache.mjs): se abre con la versión del manifest y
  // restaura lo guardado en vfs.prefetched mientras se cargan los packs. Si falla, el juego lee de la red como antes.
  const mediaCache = globalThis.pzMediaCache = MediaCache.fromLocation(location.search);
  const mediaReady = manifestFetch ? (async () => {
    const manifest = await manifestFetch;
    if (!manifest.ok) throw new Error(`media manifest HTTP ${manifest.status}`);
    const manifestBytes = await manifest.arrayBuffer();
    const listed = JSON.parse(new TextDecoder().decode(manifestBytes));
    if (listed.schema_version !== 1 || !Array.isArray(listed.files)) throw new Error('Unsupported media manifest');
    if (mediaCache.mode !== 0) {
      try {
        await mediaCache.open(await mediaCacheVersion(manifestBytes, listed));
        console.log(`mediacache restore ${JSON.stringify(await mediaCache.restore(vfsInstance))}`);
      } catch (error) {
        mediaCache.stats.lastError = String(error); mediaCache.mode = 0;
        console.warn('mediacache apagada:', error);
      }
    }
    return listed;
  })() : null;
  mediaReady?.catch(() => {});
  // Memo de la traducción GLSL (port/runtime/glslcache.mjs): versión = hash del traductor y del parser servidos.
  const shaderCache = globalThis.pzShaderCache = ShaderCache.fromLocation(location.search);
  const shaderReady = shaderCache.mode === 0 ? Promise.resolve() : (async () => {
    try { await shaderCache.load(await translatorVersion()); }
    catch (error) { shaderCache.stats.lastError = String(error); shaderCache.mode = 0; console.warn('glslcache apagada:', error); }
  })();
  const vfs = await vfsInstance.open();
  // La barra sigue los bytes de los packs; los eventos llegan por bloque, se pintan una vez por cuadro.
  let packProgress = null, packFrame = 0;
  const paintPacks = () => {
    packFrame = 0;
    const { phase, loaded, total } = packProgress;
    if (phase === 'download') {
      loading.step('packs', 'active', formatMB(total));
      loading.bar(total > 0 ? loaded / total : null, 'Descargando recursos del juego…', `${formatMB(loaded)} / ${formatMB(total)}`);
    } else loading.bar(null, phase === 'verify' ? 'Verificando los recursos descargados…' : 'Montando los recursos…');
  };
  await preloadPacks(vfs, undefined, { onProgress: p => { packProgress = p; packFrame ||= requestAnimationFrame(paintPacks); } });
  cancelAnimationFrame(packFrame);
  loading.step('packs', 'done');
  loading.step('prepare', 'active');
  loading.bar(null, 'Preparando gráficos, guardado y física…');
  bootMark('vfs');
  await preloadPacks(vfs);
  bootMark('packs');
  // Round 79: the rest of PZ_HOME/media (maps are 4.3 GB) is read on demand from the local dev
  // server; the manifest only lists names and sizes. '?remote=0' keeps the packs-only behaviour.
  if (mediaReady) {
    const listed = await mediaReady;
    bootMark('media-manifest');
    if (mediaCache.mode === 1) {
      vfs.onRemoteFetch = (url, range, bytes) => mediaCache.record(url, range, bytes);
      // Para los benchmarks (consola): estado cada 30 s mientras cambie.
      let lastMedia = '';
      setInterval(() => {
        const m = JSON.stringify({ ...mediaCache.stats, prefetchHits: vfs.prefetchHits, remote: vfs.remoteStats });
        if (m !== lastMedia) { lastMedia = m; console.log(`mediacache t=${Math.round(performance.now() / 1000)}s ${m}`); }
      }, 30000);
    }
    // models_X/anims_X hold .x/.fbx/.glb meshes that only the jassimp64 native can read; the boot
    // packs carry the game's own .txt mesh/anim format instead (round 60), so those trees stay hidden.
    const assimpOnly = /^(models_X|anims_X)\//;
    vfs.bundleEnabled = new URLSearchParams(location.search).get('nocache') !== '1' && new URLSearchParams(location.search).get('bundle') !== '0';
    globalThis.pzRemoteMedia = vfs.attachRemote(listed.files.filter(([name]) => !assimpOnly.test(name)));
    // Ronda 88: cada lectura sincrona de /media que no esta en los packs es un XHR (~2150 en una carga). El dev server recuerda
    // los rangos que sesiones reales pidieron, en orden de uso (/warm-media.json); se traen ya, en paralelo y en segundo plano
    // (no se espera: lo que no llego a tiempo se lee como siempre). Cada entrada se usa una vez; lo que sobra se descarta a los
    // 10 min. Con ?prefetch=1 (o https) el camino de autojoin de abajo si espera a que termine. ?prefetch=0 / ?nocache=1 lo apagan.
    const prefetchQuery = new URLSearchParams(location.search);
    if (prefetchQuery.get('prefetch') !== '0' && prefetchQuery.get('nocache') !== '1') {
      // Ronda 89: la precarga va por lotes (/media-ranges; ?prefetchbatch=0 vuelve a un fetch por rango) y es una sola: el camino de
      // autojoin de abajo (https o ?prefetch=1) espera esta misma promesa en vez de lanzar otra, y la barra sigue su progreso.
      globalThis.pzPrefetchProgress = () => {};
      globalThis.pzPrefetchBackground = prefetchMedia(vfs, { concurrency: 8, batch: prefetchQuery.get('prefetchbatch') !== '0',
        onFetched: vfs.onRemoteFetch ?? undefined, onProgress: f => globalThis.pzPrefetchProgress(f) })
        .then(stats => { console.log(`prefetch-bg ${JSON.stringify(stats)}`); return stats; }, error => ({ error: String(error) }));
    }
    // Lo precargado (red o caché persistente) que el juego no usó en 10 min se descarta: es memoria, no datos.
    setTimeout(() => { vfs.prefetched.clear(); }, 10 * 60 * 1000);
  }
  // Round 43: KahluaUtil.setupLibraryText opens new FileInputStream(new File("stdlib.lua")
  // .getAbsoluteFile()), which resolves to the VFS root "/stdlib.lua". Those bytes are the pinned
  // classpath resource the dev server already serves from PZ_HOME (sha256 583c2949...31eb for
  // stdlib.lua, 3772 B for stdlib.lbc); nothing is copied into the repo. store() marks anything
  // outside /Zomboid read-only, so the game can only read them. stdlib.lbc is the bytecode the
  // same class loads through setupLibrary/loadByteCodeFromFile, mounted for the same reason.
  for (const { name, response: pending } of classpathFetches) {
    const response = await pending;
    if (!response.ok) throw new Error(`Classpath resource ${name} is unavailable: HTTP ${response.status}`);
    vfs.store(`/${name}`, new Uint8Array(await response.arrayBuffer()));
  }
  // Round 80: everything the game writes under /Zomboid (Saves/, Sandbox Presets/, latestSave.ini,
  // ...) is persisted to IndexedDB once the VFS is quiet, so a game created here survives a reload
  // and the game's own main-menu "Continue"/"Load" works. Opt-in with '?persist=1' (round 80, while
  // other test runs share this page); without it only SQLite/FileChannel.force() flush, as before.
  if (new URLSearchParams(location.search).get('persist') === '1') {
    const stopAutoFlush = vfs.autoFlush();
    addEventListener('pagehide', stopAutoFlush, { once:true });
  }
  // Round 79: the browser runtime has no PZPathFind64 library. DebugOptions.load() reads
  // <cacheDir>/debug-options.ini on every boot, and Pathfind.UseNativeCode=false makes
  // PathfindNative.checkUseNativeCode() keep the game's own Java pathfinder (PolygonalMap2).
  // Only written when the user has no such file; the game may rewrite it later.
  if (!vfs.exists('/Zomboid/debug-options.ini')) {
    vfs.writeFile('/Zomboid/debug-options.ini', new TextEncoder().encode(
      '# pz-web: no PZPathFind64 native in the browser; use the game\'s Java pathfinder.\nPathfind.UseNativeCode=false\n'));
  }
  globalThis.pzRuntime = { vfs, normalizePath, gl:new GLBridge(canvas), input:new CanvasInput(canvas), net:new NetBridge() };
  // Round 83: direct mode (?autojoin=1, entered through /play.html): per-browser account, the menu is driven by a Lua script
  // mounted into media/lua/client (port/web/play.lua), and the loading screen's click is sent from here.
  if (new URLSearchParams(location.search).get('autojoin') === '1') {
    const auto = pzRuntime.autoJoin = await installAutoJoin(vfs, location.search);
    const label = Object.assign(document.createElement('div'), { id: 'pz-play-status' });
    label.style.cssText = 'position:fixed;left:8px;bottom:4px;z-index:3;font:12px sans-serif;color:#bbb;pointer-events:none;text-shadow:0 0 3px #000';
    document.body.appendChild(label);
    const text = { installed: 'Preparando…', 'script-loaded': 'Preparando…', 'tos-accepted': 'Términos aceptados', menu: 'Menú', spawn: 'Aparición',
      profession: 'Creando personaje', character: 'Creando personaje', world: 'Cargando el mundo…', loaded: 'Mundo cargado', ingame: '' };
    const show = state => { label.textContent = state.startsWith('connect-failed') ? `No se pudo conectar (${state.slice(15)}); reintentando…`
      : state.startsWith('connect') ? `Conectando como ${auto.user}…` : (text[state] ?? state) && `${text[state] ?? state} · ${auto.user}`; };
    watchConsole(auto, { onState: state => { if (!state.startsWith('connect')) { bootMark(`play:${state}`); bootReport(); } show(state); if (state === 'loaded') clickUntilIngame(auto, pzRuntime.input, canvas); } });
    show('installed');
    auto.show = show; // ronda 89: el bucle de abajo muestra 'world' al entrar GameLoadingState
    // Over the public https tunnel (or ?prefetch=1) bring the media the game will read in parallel first: one round trip per read is too slow.
    if (location.protocol === 'https:' || new URLSearchParams(location.search).get('prefetch') === '1') {
      loading.step('prefetch', 'active');
      const onProgress = f => {
        label.textContent = `Precargando recursos… ${Math.round(f * 100)} %`;
        loading.step('prefetch', 'active', `${Math.round(f * 100)} %`);
        loading.bar(f, 'Precargando el mapa…', `${Math.round(f * 100)} %`);
      };
      if (globalThis.pzPrefetchBackground) globalThis.pzPrefetchProgress = onProgress; // la de arriba, no otra
      const stats = pzRuntime.prefetchStats = globalThis.pzPrefetchBackground ? await globalThis.pzPrefetchBackground
        : await prefetchMedia(vfs, { onFetched: vfs.onRemoteFetch ?? undefined, onProgress, batch: new URLSearchParams(location.search).get('prefetchbatch') !== '0' });
      loading.step('prefetch', 'done');
      console.log(`prefetch ${JSON.stringify(stats)}`); show('installed');
    }
  }
  pzRuntime.texCache = texCache;
  await texCacheReady;
  bootMark('texcache');
  if (pzRuntime.texCache.mode !== 0) {
    // Para los benchmarks (la consola queda en console.txt): estado cada 30 s mientras cambie.
    let lastStats = '';
    setInterval(() => {
      const s = JSON.stringify({ mode:pzRuntime.texCache.mode, ...pzRuntime.texCache.stats, lookupMs:Math.round(pzRuntime.texCache.stats.lookupMs) });
      if (s !== lastStats) { lastStats = s; console.log(`texcache t=${Math.round(performance.now() / 1000)}s ${s}`); }
    }, 30000);
  }
  pzRuntime.window = new BrowserWindow(canvas,pzRuntime.gl,pzRuntime.input);
  await pzRuntime.window.prepare();
  // fps, peor cuadro, tiempo ocupado y ping arriba a la derecha (queda debajo de la pantalla de carga). ?hud=0 lo apaga.
  if (new URLSearchParams(location.search).get('hud') !== '0') pzRuntime.perfHud = installPerfHud(pzRuntime.window, { name: perfName(pzRuntime.autoJoin?.user) });
  pzRuntime.sqlite = await sqliteReady;
  bootMark('sqlite');
  // The wasm32 Bullet module is the only library this runtime honestly provides.
  pzRuntime.bullet = await bulletReady;
  bootMark('bullet');
  await pulseReady;
  await shaderReady;
  pzRuntime.gl.shaderCache = shaderCache.mode === 0 ? null : shaderCache;
  bootMark('glslcache');
  pzRuntime.loadedLibraries = pzRuntime.bullet.loadedFor;
  // Round 31: the render fiber parks in Thread.sleep(0L) and no Fiber#resume ever happens. On
  // WasmGC that suspension is resumed by TeaVM's own timer import (teavmAsync.offer -> setTimeout
  // -> EventQueue.run -> AsyncCallback.complete). Wrap that import through the loader's documented
  // installImports hook to observe, without changing it: how many events TeaVM hands to the timer,
  // how many timers actually fire, whether the fired callback throws, and how many are cancelled.
  const asyncObs = globalThis.pzAsync = { offers: 0, fired: 0, errors: 0, kills: 0, errorsSeen: [], last: [] };
  // Ronda 88: el cargador hace fetch(boot.wasm) + compileStreaming; entregarle el modulo que ya se compilo en paralelo.
  // Solo se intercepta la primera llamada cuyo Response viene de boot.wasm (el cargador tambien llama a compileStreaming
  // con un modulo diminuto para detectar js-string builtins: eso pasa sin tocar). Se restaura al terminar la carga.
  const nativeCompileStreaming = WebAssembly.compileStreaming.bind(WebAssembly);
  if (earlyWasm) {
    let handed = false;
    WebAssembly.compileStreaming = async (source, options) => {
      const response = await source;
      if (handed || !response?.url?.endsWith(earlyWasmUrl)) return nativeCompileStreaming(response, options);
      handed = true;
      try {
        const module = await earlyWasm;
        response.body?.cancel().catch(() => {});
        globalThis.pzEarlyCompile = 'used';
        return module;
      } catch (error) {
        globalThis.pzEarlyCompile = `fallback: ${error}`;
        return nativeCompileStreaming(response, options);
      }
    };
  }
  loading.step('prepare', 'done');
  if (!earlyWasm) loading.step('engine', 'active');
  loading.bar(null, 'Cargando el motor del juego…');
  const runtime = await TeaVM.wasmGC.load(earlyWasmUrl, {
    installImports(imports) {
      const base = imports.teavmAsync;
      if (!base || typeof base.offer !== 'function' || typeof base.kill !== 'function')
        throw new Error('TeaVM loader exposes no teavmAsync.offer/kill import: the timer path cannot be observed.');
      imports.teavmAsync = {
        offer(event, callback, time) {
          const delay = Math.max(0, time - Date.now());
          asyncObs.offers++;
          // Round 38: which Java frame handed the event to the timer? Two fibers are parked and only
          // an EventQueue event can wake one of them, so the caller identity decides who is at fault.
          let frames = [];
          if (timerDiag) try {
            const limit = Error.stackTraceLimit;
            Error.stackTraceLimit = 16;
            frames = String((new Error()).stack || '').split('\n')
              .map(function (s) { return s.trim(); })
              .filter(function (s) { return s.indexOf('::') >= 0; }).slice(0, 8);
            Error.stackTraceLimit = limit;
          } catch (e) { frames = []; }
          asyncObs.last.push({ n: asyncObs.offers, delay, t: performance.now(), f: frames });
          if (asyncObs.last.length > 40) asyncObs.last.splice(0, asyncObs.last.length - 40);
          return setTimeout(() => {
            asyncObs.fired++;
            try { callback(event); }
            catch (error) {
              asyncObs.errors++;
              if (asyncObs.errorsSeen.length < 5) asyncObs.errorsSeen.push(`${error.name}: ${error.message}`);
              throw error;
            }
          }, delay);
        },
        kill(id) { asyncObs.kills++; return base.kill(id); }
      };
    }
  });
  WebAssembly.compileStreaming = nativeCompileStreaming;
  loading.step('engine', 'done');
  loading.step('start', 'active');
  loading.bar(null, 'Iniciando Project Zomboid…');
  // The browser runtime provides no fmod native library. Passing the game's own documented
  // -nosound argument makes MainScreenState parse it into Core.soundDisabled, which is the real
  // path the desktop game takes when sound is off: GameWindow.mainThreadInit then installs
  // DummySoundManager/DummyAmbientStreamManager instead of calling FMODManager.init(). Sound is
  // genuinely absent here, so this reports that honestly rather than faking a fmod bridge.
  bootMark('teavm-loaded');
  runtime.exports.main(['-nosound']);
  bootMark('main');
  bootReport();
  // Estado de las cachés de esta carga, una vez que el juego llegó al menú (o a los 3 min).
  const cachesReport = () => console.log(`caches ${JSON.stringify({ packs: globalThis.pzPackStats && { ...globalThis.pzPackStats, cleanup: undefined, opfsDone: undefined },
    media: globalThis.pzMediaCache?.stats, glsl: shaderCache.stats, tex: pzRuntime.texCache?.stats, earlyCompile: globalThis.pzEarlyCompile,
    gl: { realGetErrors: pzRuntime.gl?.realGetErrors } })}`);
  let cachesReported = false;
  setTimeout(() => { if (!cachesReported) { cachesReported = true; cachesReport(); } }, 180000);
  const phases = { starting:'Arranque real en curso…', failed:'El arranque encontró un error.', 'main-returned':'El main retornó. Menú todavía no verificado.' };
  // Ronda 89: marcas de la carga del mundo desde los pulsos de GameLoadingState/IngameState (están también en release-minpulse):
  // world-load-start (entra GameLoadingState), world-load-end (sale) e ingame-enter. Al entrar, el modo directo muestra 'Cargando el mundo…'.
  const worldPulses = { 'world-load-start':'zombie.gameStates.GameLoadingState#enter()V:', 'world-load-end':'zombie.gameStates.GameLoadingState#exit()V:',
    'ingame-enter':'zombie.gameStates.IngameState#enter()V:' };
  let worldLabels = null;
  const worldMarks = () => {
    const labels = globalThis.pzPulseLabels, counts = globalThis.pzGameCounts;
    if (!labels || !counts) return;
    worldLabels ??= Object.entries(worldPulses).map(([mark, prefix]) =>
      [mark, Object.values(labels).filter(label => typeof label === 'string' && label.startsWith(prefix))]);
    for (const [mark, list] of worldLabels) {
      if (mark in bootTimings || !list.some(label => counts[label] > 0)) continue;
      bootMark(mark); bootReport();
      if (mark === 'world-load-start') pzRuntime.autoJoin?.show?.('world');
      if (mark === 'world-load-end') cachesReport(); // segunda línea `caches`: lo que la carga del mundo sacó de cada caché
    }
  };
  const update = () => {
    worldMarks();
    const phase = globalThis.pzGame?.phase;
    if (phase && !(`phase:${phase}` in bootTimings)) { bootMark(`phase:${phase}`); bootReport(); }
    if (globalThis.pzGame?.menuRendered && !('menu-rendered' in bootTimings)) {
      bootMark('menu-rendered'); bootReport();
      if (!cachesReported) { cachesReported = true; cachesReport(); }
    }
    status.textContent = phases[globalThis.pzGame?.phase] || 'Esperando el arranque Java…';
    document.getElementById('game-error').textContent = globalThis.pzGame?.error || '';
    if (globalThis.pzGame?.phase === 'failed') loading.fail(globalThis.pzGame.error || 'El arranque encontró un error.');
    else if (pzRuntime.window.swapCount > 0) {
      loading.finish();
      if (!('first-frame' in bootTimings)) { bootMark('first-frame'); bootReport(); }
    }
    requestAnimationFrame(update);
  };
  update();
} catch (error) {
  globalThis.pzGame = { phase:'loader-failed', error:`${error.name}: ${error.message}`, menuRendered:false };
  status.textContent = 'Juego pendiente de integración';
  document.getElementById('game-error').textContent = error.message;
  loading.fail(error.message);
  console.error(error);
}
