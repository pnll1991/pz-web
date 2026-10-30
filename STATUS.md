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

## Ronda 59 (inicio 03:02:55 según supervisor.log, corte duro ~07:02)
- 03:04 Estado de partida: el parche pedido por CODEX_TASK_CONTINUE ya está (rondas 57/58) y VERDE en
  Chrome según `diag162.log`: `FBO_PROBE.probe.complete=true`, `FRAMEBUFFER_COMPLETE`, `rendered=true`,
  readback `[128,64,191,255]`, y `NULL_FBO.count=0` / `NULL_HITS=[]` (desapareció el
  `dereferencing a null pointer` de `TextureFBO::initInternal`). No se repite ese análisis.
- 03:05 Nuevo bloqueo medido en esa misma corrida: `RuntimeError: dereferencing a null pointer` en
  `zombie.tileDepth.TileSeamManager::init` ← `zombie.GameWindow::enter` ← `mainThreadStart`.
  Causa verificada en bytecode pinned (`javap -p -c zombie.tileDepth.TileSeamManager`): `init()` hace
  `ldc "media/depthmaps/SEAMS_01.png"` → `Texture.getSharedTexture(String;I)` → `aload_2;
  invokevirtual Texture.getTextureId()`. `media/depthmaps/` **no estaba en el pack** (0 entradas en
  `build/real-browser/packs/index.json`), así que el shared texture es null y se dereferencia.
- 03:09 Aplicado el parche pendiente de Code `/datos/Projects/pz-web-code/patches/ronda52b-png-grayalpha.patch`
  (`git apply --check` OK, aplicado): `port/shim/BrowserStb.java` ahora decodifica PNG gris (tipo 0),
  gris+alpha (4) y paleta (3), que son los `media/ui/cursor_*.png`. `mvn -o -f port/pom.xml compile`
  BUILD SUCCESS (293 fuentes).
- 03:10 `tools/pack/startup_extra.py`: monta todo `media/depthmaps/` (221 archivos, 5.928.298 B en la
  copia pinned; verificado `SEAMS_01.png` = PNG 1024x256 colortype 6, existe). startup.txt 1450 → 1671
  entradas. Re-empacado `pack.py --max-bytes 80000000` → 3 blobs; luego `alias_index.py` (179 + 12) y
  `boot_options.py` → `index.json` con 1863 entradas y **428.596.181 B montados de 536.870.912 B**
  (sigue dentro del presupuesto de `tools/pack/budget.py`).
- 03:12 El dev server de la ronda anterior ya no existía (solo Strata en 8083). Relanzado en sesión viva
  foreground: `PORT=8092 PZ_PACKS=build/real-browser/packs node dev-server/server.mjs`. Verificado por
  HTTP: `/packs/index.json` sirve 1863 entradas con 221 `depthmaps` y `/media/depthmaps/SEAMS_01.png`;
  `/game/boot.wasm` 206 (Range) y `/runtime/gl.mjs` 200 con el fix de `framebufferTexture2D` de la ronda 58.
- 03:14 Sonda nueva `port/target/diag163.mjs` (clon de diag161 con salidas round59) lanzada foreground
  -> `diag163.log`, `build/real-browser/debuglog-round59.txt`, `console-round59.txt`,
  `test-results/round59-diag163.png`. Objetivo: ver si `TileSeamManager::init` supera el null pointer y
  en qué etapa queda el arranque.
- 03:20 Resultado de `diag163.log` (EXIT=0, ~4 min, corrida 200 s sobre el wasm 02:13 con el pack 1863):
  `ALIVE {"phase":"main-returned","error":"","progress":601,"trace":400}`, `ERRS []` (cero RuntimeError de
  wasm). `GameWindow#init` y `GameWindow#mainThreadInit` **returned**; `GameWindow#enter` **entered** a los
  184 370 ms. El `dereferencing a null pointer` de `TileSeamManager::init` **desapareció** (montar
  `media/depthmaps/` lo resolvió). El arranque ahora llega más lejos y el bloqueo nuevo es Java-level:
  `java.lang.NullPointerException: Required Animation Asset not found: bob/bob_idle` en MainThread ->
  `GameThread exited` -> luego `UnsupportedOperationException: System.exit(0): a browser tab is not an OS
  process` dentro de `MainScreenState.main`. Traducido con `scripts/wasm_stack.py`:
  `ModelManager::getAnimationAssetRequired <- ModelManager::create <- GameWindow::enter <- mainThreadStart`.
- 03:22 Causa: `build/real-browser/debuglog-round59.txt` muestra decenas de
  `WARN: Failed to load asset: AssetPath{ "Skinned/MaleBody" }` (y FemaleBody, CowBody,
  `IsoObject/fixtures_*`, `IsoObject/door_*.glb`) justo antes del NPE. Los `.x` de modelos/animaciones
  **no están montados**: `models_X` y `anims_X` no figuran en `index.json`, el `ModelMesh` de "Human"
  no queda `isReady()` y `loadHumanAnimations` hace `return` temprano (verificado en bytecode), por lo
  que nunca se cargan `bob/bob_idle|walk|run` y `getAnimationAssetRequired` lanza.
- 03:23 Inventario real de la copia pinned (sin copiar nada al repo): `media/models` 46 archivos .txt
  (4,2 MB, no hay .x); `models_X` 312 MB con `Skinned/MaleBody.x` 194010 B, `FemaleBody.x` 200557 B,
  `Male_Skeleton.x` 231141 B, `Female_Skeleton.x` 243910 B; `anims_X` 508 MB de los cuales `anims_X/Bob`
  son 373 MB/1615 archivos (`Bob_Idle.x` 200774, `Bob_Walk.x` 194840, `Bob_Run.x` 222958) y `anims_X/Kate`
  2,3 MB/11; `media/AnimSets` 14 MB/2949 .xml (`player.xml` con folderPrefixes Bob/Kate y namePrefixes
  `Bob_`/`Kate_`; `base.xml` es 0 bytes). Presupuesto `tools/pack/budget.py` = 512 MiB; montado hoy
  428.596.181 B -> headroom ~108 MB. No se montan los 373 MB de `anims_X/Bob`.
- 04:10 Corrección real en `tools/pack/startup_extra.py`: el filtro montaba `'media/ui/' + UI_PROGRESS_PREFIX`
  pero el prefijo ya es relativo a `media/` (`ui/Progress/`), por lo que nunca matcheaba y el comando reportaba
  `Appended 0 startup entries`. Con `media/ + prefix` agrega las 3 entradas reales (26 837 B):
  `media/ui/Progress/{MaleDoor,MaleSprint06,MaleWalk2.png}`. Re-empacado (4641 archivos, 3 blobs) +
  `alias_index.py` (179/12 alias) + `case_alias.py` (18 alias lowercase) + `boot_options.py` →
  `index.json` servido por HTTP con **4851 entradas y 438 727 957 B montados de 536 870 912 B** (headroom ~98 MB),
  y las 3 rutas `/media/ui/Progress/*.png` respondiendo 200 con sus bytes.
- 04:13 Sonda `port/target/diag165.mjs` (clon de diag164 con salidas round59c) sobre el mismo wasm 02:13:
  `EXIT=0`, `ERRS []`, `NULL_FBO {"count":0}`, `ALIVE {"phase":"starting","progress":591}` y
  `GameWindow#enter()V:entered` a 199 611 ms (nunca retorna). Los 3 `ExecutionException:
  FileNotFoundException` de `media/ui/Progress/*.png` **desaparecieron**: el archivo ahora se lee, pero el
  fallo se movió un paso adentro: `ExecutionException: UnsupportedOperationException: Browser LWJGL memory
  operation unavailable: org.lwjgl.system.MemoryUtil.memCopy(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V`
  (3 veces, `Message: /media/ui/Progress/{MaleDoor,MaleSprint06,MaleWalk2.png}`). `Failed to load asset` = 0.
- 04:20 Causa verificada sin repetir análisis: `MemoryCompatibility` sustituye cada método estático de
  `org.lwjgl.system.MemoryUtil` que existe en `port.runtime.memory.NativeMemory` con la misma firma, y
  lanza el "memory operation unavailable" para las que no. `NativeMemory` tenía `memCopy(long,long,long)`
  pero **no** el overload `(ByteBuffer,ByteBuffer)` que usan `zombie.core.textures.PNGDecoder#decodeFrame`
  y `zombie.core.textures.ImageData#<init>` (verificado con `javap -c` sobre la copia fijada). El bytecode
  de LWJGL hace `Checks.check(target, source.remaining())` y `memcpy(memAddress(source), memAddress(target),
  source.remaining())` sin avanzar posiciones; el juego hace `MipMapLevel.rewind()` antes, así que conservo
  esa semántica exacta.
- 04:33 Build completo `bash scripts/build.sh --with-game` con `MAVEN_OPTS="-Xmx6g -Xss16m"`: BUILD SUCCESS,
  `port/target/web/game/boot.wasm` 144 947 076 B (04:33) y los tres gates (thread-fiber, stringutils,
  assertions) OK en `build-round59-game.log`. Incluye el parche de `BrowserStb` de Code y el `memCopy` nuevo.
- 04:45 Sonda `port/target/diag166.mjs` (wasm nuevo, mismo pack 4851): `EXIT=0`, `ERRS []`, `NULL_FBO {"count":0}`.
  Desaparecieron las 2 líneas `failed to read image /media/ui/cursor_*.png` (fix BrowserStb confirmado) y las 3
  `ExecutionException` de `memCopy(ByteBuffer,ByteBuffer)` sobre `media/ui/Progress/*.png` (fix de NativeMemory
  confirmado). El arranque llega a `UI_Loading_ModelsAnimations` y el bloqueo ahora es el esperado y honesto:
  `UnsatisfiedLinkError: System.loadLibrary: browser implementation is missing for jassimp64` -> todos los
  `Failed to load asset: AssetPath{ "Skinned/..." }` (0 en la corrida anterior por el FBO/NPE previo, 20+ ahora)
  -> `NullPointerException: Required Animation Asset not found: bob/bob_idle` en MainThread ->
  `UnsupportedOperationException: System.exit(0)` en `MainScreenState.main`. También 2 nuevas
  `Browser GLFW operation unavailable: org.lwjgl.glfw.GLFW#glfwCreateCursor(GLFWImage;II)J` (cursor del UI,
  toleradas por el juego).

## Ronda 60 (inicio 06:28:09 según supervisor.log, corte duro ~10:28; dejar de programar 09:58)
- 07:56 El parche pedido `ronda59b-x-anim-to-txt.patch` está **ya aplicado** (commiteado en `d8bc6a3`):
  `git apply --check` dice `tools/pack/x_anim_to_txt.py: already exists`. No se rehace.
- 08:00 `tools/pack/x_anim_to_txt.py` ampliado: ahora lee la **malla** del `.x` (posiciones, normales,
  texcoords, blend weights/índices desde los hijos `SkinWeights`, caras desde `Mesh.data`). Corrección
  importante: el `.x` del juego usa `DWORD nFaces` seguido de entradas `MeshFace` (`3 i j k`), **no** un
  array plano de índices; el parser previo leía `nFaces` como `nFaceVertexIndices` y por eso las mallas
  salían con caras mal (617 verts pero 305 caras). Ahora `malebody` = 617 verts / 916 caras,
  `femalebody` 660/984, `male_skeleton` 793/1096, `female_skeleton` 792/1095 (coinciden con los conteos
  reales del `.x`). Tangentes: se emiten zeros (el parser activa `bHasTangent` por la palabra "Tangent"
  del encabezado de stride y exige 3 números por vértice).
- 08:05 Semántica de matrices fijada contra `media/Bob_Sprint.txt` (viene con el juego): Bind Pose =
  matriz **local** del hueso sin transponer; Inverse Bind Pose = inversa de esa local; Skin Offset
  Matrices = **world de bind acumulada** `W[k] = W[parent] * bind[k]` (row-major). El conversor anterior
  emitía bind transpuesto y offset = invBind, que estaba mal.
- 08:05 Herramienta nueva `tools/pack/stage_txt.py`: genera el staging fuera de PZ_HOME con las 4 mallas
  lowercase (`media/models/skinned/{malebody,femalebody,male_skeleton,female_skeleton}.txt`), los anims
  en `media/anims_X/Bob/*.txt` (la carpeta que `ModelManager` enumera) y el fallback lowercase
  `media/anims/bob/bob_{idle,walk,run}.txt` (la ruta que `checkExtensionType` prueba cuando no hay .x).
  Total staging 1.843.865 B, 10 archivos.
- 08:08 `tools/pack/startup_extra.py`: **deja de montar los `.x`** de `models_X/Skinned` y los `.x` de
  anims (montarlos hace que `checkExtensionType` elija X y caiga en el `UnsatisfiedLinkError jassimp64`).
  Monta sólo los `.txt` del staging y exige (SystemExit) los 10 archivos requeridos. Re-empacado con
  `pack.py --stage`: `startup.txt` 4403 entradas, 3 blobs; `case_alias.py` sumó 2952 alias lowercase de
  AnimSets/anims_X/models_X; `boot_options.py` -> `index.json` **7355 entradas, 398.554.624 B montados
  de 536.870.912 B** (antes 4851/438 MB). Verificado por HTTP: `/packs/index.json` 200 y las rutas
  `/media/models/skinned/*.txt`, `/media/anims/bob/*.txt`, `/media/anims_X/Bob/Bob_Idle.txt`,
  `/media/anims_x/bob/bob_idle.txt` presentes. Sin `media/fonts` montados a propósito (285 MB; el juego
  tolera los 7 FileNotFoundException de fuentes, ver diag166.log `missing: []`).
- 08:33 Sonda `port/target/diag167.mjs` (clon de diag166 con salidas round60 y las rutas .txt): el
  bloqueo `jassimp64` y el NPE `Required Animation Asset not found: bob/bob_idle` **desaparecieron**.
  `ASSET_PATHS`: los `.x` ya no existen y las 7 rutas `.txt` (`/media/models/skinned/*.txt`,
  `/media/anims/bob/*.txt`, `/media/anims_X/Bob/Bob_Idle.txt`) existen con sus bytes; `DIR_LIST`
  confirma que el VFS lista `Bob`/`bob` como directorios con los 3 `.txt` (la enumeración de
  `loadAnimsFromDir` y el fallback lowercase están cubiertos). `NULL_FBO {"count":0}`, `ERRS []`.
  El arranque **avanza** pero muere antes de `GameWindow#enter`: en `GameWindow#initFonts()` un
  `java.io.FileNotFoundException` (RuntimeException no capturado por MainThread) -> `onGameThreadExited`
  -> `System.exit(0)`. `mountedBytes` 398.620.076, `uploadFailCount 0`, `missing: []` (el anillo de
  rutas faltantes no captura el path porque el FileReader usa el nombre ya resuelto).
- 08:38 Causa y arreglo: al regenerar `startup.txt` con `tests/prepare_real.py` se perdieron las
  entradas de fuentes que la ronda 40 había escrito a mano (no son commiteables: `build/` está
  ignorado). Herramienta nueva commiteable `tools/pack/font_files.py`: deriva las rutas de `media/fonts/EN/fonts.txt`
  (`fnt = X,` / `img = X,` con `[^,\s;]+`, no `(\S+)`), `media/fonts/zomboidNormal11..25.fnt` y los
  `page id=... file="..."` de cada `.fnt`, resolviendo en el orden de `TextManager.getFontFilePath`
  (`fonts/EN/1x/`, `fonts/`, `media/`). Agregó 107 entradas (13.576.720 B, 40 `.fnt` + 67 `.png`);
  re-empacado -> `index.json` **7558 entradas y 426.872.850 B montados de 536.870.912 B**.
- 08:52 Causa del `RuntimeError: dereferencing a null pointer` en `AngelCodeFont::getLineHeight`
  (visto en `diag167.log` ERRS, stack `ExceptionLogger::render` <- `Core::EndFrameUI` <-
  `ModelManager::initAnimationMeshes` <- `GameWindow::enter`): `media/fonts/EN/fonts.txt` **no estaba
  montado**. `TextManager.Init` llama `FontsFile.read(...)`, que *captura* `FileNotFoundException` y
  devuelve `false` (`/tmp/r60c/outff/FontsFile.java`), así que el mapa queda vacío, todos los `UIFont`
  conservan `AngelCodeFont == null`, y el primer `getFontHeight()` trae NPE. No era un error del
  conversor de modelos.
- 08:53 `tools/pack/font_files.py` ahora monta también los listings `media/fonts/EN/fonts.txt` y
  `fontsDyslexic.txt` (constantes `LISTINGS`). Regenerado `startup.txt` (4510 entradas) y re-empacado:
  `index.json` **7588 entradas**, `boot_options.py` -> **426 876 821 B montados de 536 870 912 B**.
  Verificado por HTTP: `/media/fonts/EN/fonts.txt` responde 200 (2127 B). Sonda `port/target/diag168.mjs`
  (salidas round60b) con espera 420 s para ver si `UI_Loading_ModelsAnimations` progresa a la siguiente etapa.
- 09:15 Sonda `diag168.mjs` (fonts.txt montado, espera 420 s): `ERRS []` (el NPE de
  `AngelCodeFont::getLineHeight` **desapareció**), `NULL_FBO {"count":0}`, `FBO_PROBE {"runs":1,
  "complete":true,"status":"FRAMEBUFFER_COMPLETE","readback":[128,64,191,255]}`,
  `drawRangeElements: 1056`, `swap: 130`, y la captura `test-results/round60b-diag168.png` ya tiene
  texto/UI (34 394 B, 192 colores). El arranque sigue vivo en `GameWindow#enter()` todo el tiempo.
- 09:20 Nuevo bloqueo real y su causa: 116 `java.lang.IllegalStateException: Buffer allocation was
  freed` por frame, desde `SpriteRenderer$RingBuffer.next -> GLVertexBufferObject.map`.
  `GL15.unmap()` liberaba el bloque del arena; al volver a mapear, LWJGL llama
  `APIUtil.apiGetMappedBuffer(old_buffer, addr, cap)` que empieza con `MemoryUtil.memAddress(old_buffer)`
  sobre el `ByteBuffer` cacheado del `GLVertexBufferObject` (`/tmp/r60d/outapi/APIUtil.java:141-147`) y
  `NativeArena.address` lanza para una view liberada. Además el arena es un bump allocator: liberar y
  reasignar 2 bloques por frame crecía ~131 KB/frame sin reciclar.
  Arreglo en `port/src/main/java/port/shim/GL15.java`: el bloque del último map de cada target queda
  vivo en `reusable` y se reuse cuando `size >= size` pedido (semántica de escritorio: el puntero
  sigue comparable tras unmap y la dirección se mantiene estable entre maps).
- 09:31 Build con el fix de `GL15.java`: `scripts/build.sh --with-game` -> **BUILD SUCCESS** (6:14 min),
  `port/target/web/game/boot.wasm` 144 947 484 B. Nota: `--with-game` aborta con "requires PZ_HOME and a
  matching inventory" si `PZ_HOME` no está exportado (no figura en `scripts/env.sh`).
- 09:36 Sonda `port/target/diag169.mjs` (salidas `round60c`, espera 300 s) con el wasm nuevo y los packs
  de 426 876 821 B: **desaparecieron** las 116 `IllegalStateException: Buffer allocation was freed` por
  frame (0 ocurrencias en el log), el NPE de `AngelCodeFont::getLineHeight`, y el
  `Pattern does not specify integer digits`. `ERRS []`, `NULL_FBO {"count":0,"sample":[]}`,
  `mountedBytes` 427 119 790, `drawRangeElements` 31 135, `swap` 4 427. La captura
  `test-results/round60c-diag169.png` (34 394 B, 1280x720, 192 colores analizados con PIL porque
  `view_image` no está habilitado) muestra UI real con texto.
- 09:44 Dónde se detiene el arranque HOY (verificado con diag169 + `build/real-browser/debuglog-round60c.txt`,
  1154 líneas): `GameWindow#init()` y `#mainThreadInit()` **retornan** (179.4 s) y
  `GameWindow#enter()` entra y nunca retorna; el DebugLog avanza
  `UI_Loading_Lua` -> `UI_Loading_Scripts` -> `UI_Loading_Clothing` -> `UI_Loading_Texturepack` (x4) ->
  `UI_Loading_ModelsAnimations` y ahí queda, con la pestaña viva 300 s (4 427 swaps). Los 74
  `Failed to load asset` son de `Skinned/{Deer,Cow,Sheep,Rat,...}` e `IsoObject/*.glb` que **no están
  montados** (esperado, no inventar). `Skinned/MaleBody`/`FemaleBody` **no** aparecen entre los failures:
  la malla Human cargó bien desde los `.txt` del staging.
- 09:44 Bloqueo actual: el bucle `while(isLoadingAnimationMeshes())` de `ModelManager.initAnimationMeshes(false)`
  (`/tmp/r60/out4/ModelManager.java:1751-1805`) no progresa. `isLoadingAnimationMeshes()` pide que cada
  `am.modelMesh` esté `isReady()` (`super.isReady() && (!hasVbo || vb != null)`,
  `/tmp/r60/out17/ModelMesh.java:90-92`), y el anillo de instrumentación (`StartupProgress.CLASSES`, 400
  eventos) **no incluye ninguna clase `skinnedmodel`**: los 25 labels del último PROGRESS terminan en
  `GameWindow#enter()V:entered`, así que no hay evidencia de qué llamada interna está trabada.
  Queda además 1 `UnsupportedOperationException: Arena address requires a registered/direct ByteBuffer
  view` a las 12:35:01.893 (durante ModelsAnimations) - hipótesis principal: `ModelLoader.loadTxt` usa
  `BufferUtils.createByteBuffer`, cuya view de heap de TeaVM no está registrada en `NativeArena`
  (ver `port/src/main/java/port/compiler/BufferUtilsCompatibility.java` y
  `VertexBufferObject.LoadVBO(byte[])`, `/tmp/r60d/outvb/VertexBufferObject.java:113-150`).
- 09:44 Siguientes pasos concretos (orden, para la ronda 61):
  1. Añadir a `StartupProgress.CLASSES` (transformer en `port/src/main/java/port/compiler/StartupProgress.java`):
     `zombie.core.skinnedmodel.ModelManager`, `zombie.core.skinnedmodel.model.ModelMesh`,
     `zombie.core.skinnedmodel.model.ModelLoader`, `zombie.asset.MeshAssetManager`,
     `zombie.asset.FileTask_LoadAnimation`, `zombie.core.skinnedmodel.advancedanimation.AdvancedAnimator`;
     rebuild ~6 min con `PZ_HOME` y `JAVA_HOME` exportados y `MAVEN_OPTS="-Xmx6g -Xss16m"` (quedan ~4 GB).
  2. Correr un `diag170.mjs` (clon de `port/target/diag169.mjs`, salidas `round60d`, `PZ_DIAG_WAIT=300000`)
     y leer el PROGRESS para ver si `isLoadingAnimationMeshes()` es true por un mesh no-ready o por una
     tarea async que nunca se completa (`fileSystem.updateAsyncTransactions()`).
  3. Si el `Arena address...` viene de `loadTxt`, convertir esa heap view a arena en el puente propio
     (`BufferUtilsBridge`/`NativeArena.buffer`); **nunca** tocar el juego ni montar los `.x`
     (montarlos hace que `checkExtensionType` elija X y caiga en `UnsatisfiedLinkError jassimp64`).
  4. Presupuesto: quedan ~110 MB de headroom (536 870 912 B). No montar `anims_X/Bob` completo (~373 MB)
     ni `Kate` todavía; montar los `Skinned/*.txt` que fallen por malla, no los animals `.glb`.
- 09:48 Instrumentación del bucle de ModelsAnimations: `StartupProgress` tiene ahora `ANIM_CLASSES`
  (`ModelManager`, `ModelMesh`, `ModelLoader`, `FileTask_AbstractLoadModel`, `FileTask_LoadAnimation`)
  sumada al gate de `transformClass`. Descubrimiento importante: **`FONT_CLASSES` estaba declarado pero
  nunca usado** en el gate (solo `METHODS`/`EXTRA_METHODS`/`CLASSES`/`EXTRA_CLASSES`/`own`), así que las
  clases de fuentes nunca se instrumentaron. Descriptores confirmados con `javap -s` sobre el jar pinned.
- 09:57 Rebuild `scripts/build.sh --with-game` -> **BUILD SUCCESS** (6:18 min), `boot.wasm`
  144 950 365 B. Verificación de la instrumentación: `port/target/web/game/pulse-map.json` pasó de
  1929 labels con **0** hits de `skinnedmodel` a **2173 labels con 244** de `skinnedmodel`
  (incluye `ModelManager#initAnimationMeshes(Z)V`, `#isLoadingAnimationMeshes()Z`, `ModelMesh#isReady()Z`,
  `FileTask_AbstractLoadModel#call()`, `FileTask_LoadAnimation#loadTxt()`). Nota de herramienta: los
  labels no se buscan con `grep -c` en `boot.wasm` (el string está en la data section comprimida/fragmentada);
  la fuente de verdad es `pulse-map.json`. Sonda nueva `port/target/diag170.mjs` (clon de diag169,
  salidas `round60d`, `node --check` OK).
- 10:03 Sonda `port/target/diag170.mjs` (wasm instrumentado con `ANIM_CLASSES`, espera 150 s, salida
  `build/real-browser/debuglog-round60d.txt` 1043 líneas, captura `test-results/round60d-diag170.png`
  34 394 B / 192 colores): **el bucle de ModelsAnimations no es el bloqueo actual.** El anillo de
  pulsos muestra como último límite observado
  `zombie.core.skinnedmodel.ModelManager#create()V:entered` a 179.7 s (despues de
  `GameWindow#enter():entered` y del `<clinit>/<init>` de ModelManager), y
  `ModelManager#initAnimationMeshes(Z)V` **nunca entra** (244 labels de `skinnedmodel` existen en
  `pulse-map.json`, así que no es un problema de instrumentación). Por lo tanto el main fiber está
  trabado *antes* de la línea `this.initAnimationMeshes(false)`, o sea en
  `new Texture(1024,1024,16)` / `new Texture(1024,1024,512)` / `new TextureFBO(...)`
  (`/tmp/r60/out4/ModelManager.java:149-167`), que es territorio del port, no del juego.
  Evidencia adicional en `build/real-browser/console-round60d.txt`: la ultima línea despues de
  `UI_Loading_ModelsAnimations` es `WebGL: INVALID_VALUE: clear: invalid mask` - el clear de
  `TextureFBO.initInternal` pasa una máscara que WebGL2 rechaza.
  Contadores: `swap` **3** y `drawRangeElements` 167 en 150 s (contra 4 427 swaps en diag169): el
  fiber de render quedó hambriento porque el main fiber ejecuta una sección síncrona larga, lo que
  confirma el bloqueo dentro de `create()` y no en el bucle con `Thread.sleep(10)`.
  `ERRS []`, `NULL_FBO {"count":0}`, `uploadFailCount 0`, `mountedBytes` 427 092 824,
  `TALLY {"UnsupportedOperationException":4,"FileNotFoundException":7,"Class cannot be created...":15}`.
  Cambios respecto de diag169: desapareció el `UnsupportedOperationException: Arena address requires a
  registered/direct ByteBuffer view` (6 -> 4 UnsupportedOperationException) y no hay ninguna línea
  `Failed to load asset` (el run es más corto: 150 s vs 300 s y no llega a cargar los assets).
  No existe `MENU_REACHED` (no se creó: el menú aún no se renderiza).
- 10:05 **Bloqueo actual** (reemplaza el supuesto de ModelsAnimations): el main fiber entra a
  `ModelManager#create()` y no sale; la sospecha principal es el camino del FBO
  (`Texture` 1024x1024 + `TextureFBO.initInternal`) con la máscara de `clear` inválida para WebGL2.
- 10:05 Siguientes pasos concretos (ronda 61, en orden):
  1. Instrumentar el tramo que falta: agregar a `StartupProgress.METHODS`
     `zombie.core.textures.TextureFBO#initInternal()V`, `#initDepth()V`,
     `zombie.core.textures.TextureFBODepth` y los `<init>` de `zombie.core.textures.Texture`
     (`javap -s` para los descriptores exactos antes de parchar). Con eso el anillo dice cuál de las
     tres llamadas de `create()` no retorna.
  2. Arreglar la máscara de `glClear`: en `port/runtime/gl.mjs` (`clear(...)`) y/o `port/shim/GL11.java`
     mapear los bits de escritorio (`GL_COLOR_BUFFER_BIT=0x00004000`, `GL_DEPTH_BUFFER_BIT=0x00000100`,
     `GL_STENCIL_BUFFER_BIT=0x00000400`) a los de WebGL2 (`COLOR=0x1`, `DEPTH=0x100`, `STENCIL=0x200`)
     y enmascarar los bits desconocidos, sin simular éxito: propagar el `glGetError()` real al diag.
  3. Verificar el `Texture(1024,1024,512)` de profundidad: `PerformanceSettings.useFbos=false` queda en
     false justo antes de construir el FBO en `create()`, así que la ruta de depth-textura es la nuestra.
  4. No montar más assets todavía (headroom ~110 MB) y no volver a montar `.x`.
