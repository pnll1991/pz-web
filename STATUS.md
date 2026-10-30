# Estado del port personal

Repositorio /home/agente47/Projects/pz-web. Rondas anteriores (hasta la 52) archivadas en docs/status-archive.md. Estado vigente: la ronda más reciente abajo. Bloqueo actual: ver docs/CODEX_TASK_CONTINUE.md (TextureFBO / OpenGL30).

## Ronda 53 (inicio 22:27:41 según supervisor.log, corte duro 02:27)
- 22:31 Estado de partida: repo limpio (`git status` sin cambios), `boot.wasm` 144.945.224 B del build
  de las 21:15 (ronda 52), dev server 127.0.0.1:8092 vivo, packs reempaquetados con Tiles2x+Clock2x.
  Freno heredado: `glTexImage2D failed: INVALID_VALUE ... 1024x1024 data=null` -> textura sin HW id ->
  `TextureFBO::initInternal` con textura nula -> trampa de puntero nulo en `UI_Loading_ModelsAnimations`.
- 22:33 Probe mínimo en Chrome (`port/target/probe53.mjs`, SwiftShader, `MAX_TEXTURE_SIZE=8192`):
  la **asignación con srcData `null` SÍ es válida en WebGL2/SwiftShader**: 1024x1024 RGBA8/RGBA/U8
  `null` -> `NO_ERROR` y el FBO queda `FRAMEBUFFER_COMPLETE` con píxeles `(0,0,0,0)`; ídem 64x64 y
  4096x4096. Con 16384x16384 da `INVALID_VALUE` (fuera de MAX_TEXTURE_SIZE). `ArrayBuffer` sin vista y
  `Uint8Array` de 1 MiB con internalformat RGBA8 dan `INVALID_VALUE` por **stride/unpack** (no es el
  caso del juego, que usa vistas del arena). Omitir el argumento (8 args) lanza
  "Overload resolution failed". Conclusión: el `INVALID_VALUE` de la ronda 52 **no** viene de la forma
  de la llamada ni de la allocación sin datos.
- 22:35 Probe de la secuencia exacta que emite `TextureID.generateHwId` (`javap` de la copia fijada:
  bind, `TEXTURE_MIN_FILTER=9987`, `MAG=9729`, y con el flag de compresión `WRAP_S/WRAP_T = 33071`)
  en `port/target/probe53b.mjs`: **todo NO_ERROR**, subida `null` 1024x1024 OK, FBO `FRAMEBUFFER_COMPLETE`.
  Nota: `33071` es `GL_GENERATE_MIPMAP` desktop; este bridge lo mapea a `CLAMP_TO_EDGE` (mismo número
  en WebGL2), por eso no ensucia. Descartado también el orden de la rama de mips (el juego llama
  `glTexImage2D(..., aconst_null)` en la rama "sin datos", línea 588 del bytecode).
- 22:36 Medición de coste de `getError()` en Chrome (`probe53c.mjs`): 4000 `texParameteri` sin drenar
  = 1,7 ms; con `getError()` después de cada uno = 434 ms. El drenaje **sí** es caro, así que no se
  drena por cuenta propia (sería además cambiar la semántica del juego).
- Hipótesis dominante entonces: la cola de errores de WebGL2 es **sticky**. Como el bridge llama
  `getError()` *después* de `texImage2D`, un error encolado por una llamada anterior (por ejemplo un
  `getTexParameter`/uniform/draw inválido) se le atribuye a la subida. Falta evidencia: se instrumenta.
- Cambios de esta ronda (observabilidad, sin simular éxito): `port/runtime/gl.mjs` ahora lleva
  `callSeq`/`callLog` (los últimos 160 WebGL2 calls realmente ejecutados, en orden),
  `errorDrains` (los últimos 32 `glGetError` que devolvieron error, con su secuencia) y
  `lastErrorDrainSeq` (el punto donde el juego drenó la cola por última vez). `lastUploadFailure`
  agrega `boundTexture`, `unit`, `sinceDrain` (los calls desde el último drenaje), `lastDrainSeq`,
  `errorDrains` y `textureParamSnapshot()` (estado WebGL2 real de la textura: filtros/wraps/LOD leídos
  con `getTexParameter`, drenando el error propio de esa lectura). `uploadFailCount` y
  `firstUploadFailure` para no perder el primer fallo. `npm test` **43/43**.
- Sonda nueva `port/target/diag157.mjs` (evaluates chicos + races de 120 s; escribe
  `build/real-browser/debuglog-round53.txt`, `console-round53.txt`, `test-results/round53-diag157.png`).
- 23:05 Arreglado el `npm test` que rompió la cola emulada: los caminos internos del bridge
  (`glGetTexImage`, `loadTexture`, `readTexture`, `drawProbe`, anillo de `bufferData`, snapshot de
  texParams) ya no llaman `getError()` (que ahora es la cola del juego); usan `internalErrors()`/
  `checkInternal()` que sólo miran los errores atribuidos a su propia secuencia. `texImage2D` y
  `texSubImage2D` tampoco drenan la cola: consultan `errorsFor(seq)` de su propia llamada, así el
  juego sigue leyendo su error en `PZGLUtil.checkGLErrorThrowTexture`. `npm test` **43/43**.
- 23:11 Sonda `port/target/diag158.mjs` (misma corrida + `aroundUpload` con los 30 calls vecinos a la
  subida y los errores atribuidos a su secuencia): `build/real-browser/debuglog-round53b.txt`,
  `console-round53b.txt`, `test-results/round53-diag158.png`. Objetivo: identificar la llamada que
  realmente ensucia la cola (esperado `drawRangeElements` o un uniform), no la subida.

## Ronda 57 (inicio 01:15:33 según supervisor.log, corte duro ~05:15)
- 01:59 Estado de partida: cambios de esta ronda ya en repo y compilando (`mvn -f port/pom.xml compile`
  BUILD SUCCESS, `npm test` 48/48). Bloque heredado (no re-analizado): `TextureFBO.funcs == null` por
  `GLCapabilities.OpenGL30=false` en WebGL2 -> puntero nulo en `TextureFBO::initInternal`.
- Cambios: sondeo FBO real en `port/runtime/gl.mjs#framebufferProbe()` (textura WebGL2 con píxeles
  reales + framebuffer + renderbuffer `DEPTH24_STENCIL8` + `checkFramebufferStatus` + `clear` +
  `readPixels`; `complete` sólo si COMPLETE **y** el clear renderizó; errores del sondeo se registran
  y se sacan de la cola del juego); shim `BrowserGL.fboProbeComplete()/fboProbeReportText()`;
  transformer nuevo `port/compiler/FBOSupportCompatibility.java` que reemplaza la lectura de
  `GLCapabilities.OpenGL30` en `TextureFBO.checkFBOSupport` y `TextureFBODepth.checkFBOSupport` por el
  sondeo (throw si el gate cambió: no parcha a ciegas); registrado en `BuildCompiler.java`.
- Tests nuevos `tests/node/gl-fbo-probe.test.mjs` (5) con contexto WebGL2 sintético: secuencia de
  attach real, incomplete -> false (nunca clear), clear sin render -> false, cola limpia + errores
  registrados, cache + JSON. Total `npm test` **48/48**.
- 02:06 Build `--with-game` lanzado foreground (log `/tmp/build57.log`, `MAVEN_OPTS="-Xmx6g -Xss16m"`;
  `-Xmx8g` se evita por el OOM de la ronda 49 con Strata ~19 GB y swap lleno). Tests JVM 52/52 OK;
  TeaVM en DEPENDENCY_ANALYSIS/compilación.
- Sonda nueva `port/target/diag160.mjs` (clon de diag159 + `FBO_PROBE`, `NULL_HITS`, `NULL_FBO`,
  `LOG_FBO_LINES`; escribe `build/real-browser/debuglog-round57.txt`, `console-round57.txt`,
  `test-results/round57-diag160.png`).

## Ronda 58 (inicio 02:18:56 según supervisor.log, corte duro ~06:18)
- 02:20 Estado de partida: repo limpio (ronda 57 commiteada como 16655b9). El parche pedido por
  CODEX_TASK_CONTINUE **ya está aplicado** desde la ronda 57 y verificado en el repo:
  `port/runtime/gl.mjs#framebufferProbe()` (sondeo FBO real: textura WebGL2 con píxeles +
  framebuffer + renderbuffer DEPTH24_STENCIL8 + checkFramebufferStatus + clear + readPixels;
  `complete` sólo si COMPLETE y el clear renderizó) y el transformer
  `port/src/main/java/port/compiler/FBOSupportCompatibility.java`, registrado en `BuildCompiler.java`,
  que reemplaza la lectura de `GLCapabilities.OpenGL30` en `TextureFBO/TextureFBODepth.checkFBOSupport`
  por ese sondeo. Shim `port.shim.BrowserGL.fboProbeComplete()/fboProbeReportText()` vía `@JSBody`.
  `boot.wasm` servido es el del build 02:13 de la ronda 57 (144.945.646 B, BUILD SUCCESS, 10.229
  clases) y el dev server 127.0.0.1:8092 está vivo. Por tanto esta ronda NO repite el análisis:
  el paso que falta es **probar en Chrome si el sondeo se ejecuta y si el gate queda true**.
- 02:21 `npm test` **48/48** OK (incluye los 5 tests del sondeo FBO de la ronda 57).
- 02:22 Lanzada en background la sonda `port/target/diag160.mjs` (200 s de corrida) ->
  `diag161.log`, `build/real-browser/debuglog-round57.txt`, `console-round57.txt`,
  `test-results/round57-diag160.png`. Objetivo concreto: leer `FBO_PROBE.runs/probe` (¿se corrió el
  sondeo? ¿complete?), `NULL_HITS` y si sigue el `dereferencing a null pointer` en
  `TextureFBO::initInternal` (ronda 53: seguía ahí). RAM medida: 27 GiB usados de 31, swap 12 GiB;
  no se lanza build pesado mientras corra el navegador.
- 02:31 Resultado de diag161.log (sonda de la ronda 57 sobre el wasm 02:13): el sondeo **sí se
  ejecuta** (`FBO_PROBE.runs=1`) y WebGL2 responde `FRAMEBUFFER_COMPLETE` en las dos etapas. Pero
  `rendered=false` porque el readback del `clear` da `[128,64,191,255]` y el control de la ronda 57
  exigía `[128,64,192,255]` exacto: SwiftShader cuantiza 0.75 -> 191. Por eso `complete=false`, el
  gate quedó false y siguió `dereferencing a null pointer` en `TextureFBO::initInternal`
  (`UI_Loading_ModelsAnimations`). Causa real: **la comparación exacta, no el FBO**.
- 02:33 Fix: `port/runtime/gl.mjs` acepta tolerancia de cuantización +/-2 por canal y expone
  `report.expected` / `report.maxChannelError` (un FBO roto lee 0,0,0 o píxeles viejos, sigue
  dando false). Test nuevo en `tests/node/gl-fbo-probe.test.mjs` con el readback medido en Chrome
  ([128,64,191,255] -> true) y deriva mayor (189 / color equivocado -> false). `npm test` **49/49**.
  Copiado a `port/target/web/runtime/gl.mjs` (el dev server sirve esa copia; el wasm no cambia
  porque el gate lee el sondeo vía `@JSBody`, no se recompila).
- 02:34 Sonda nueva `port/target/diag161.mjs` (clon de diag160 con salidas round58) lanzada en
  foreground -> `diag162.log`, `build/real-browser/debuglog-round58.txt`, `console-round58.txt`,
  `test-results/round58-diag161.png`. Objetivo: ver `complete=true` y si el arranque avanza más allá
  de `UI_Loading_ModelsAnimations`.
