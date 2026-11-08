# Estado del port personal

Repositorio /home/agente47/Projects/pz-web. Rondas anteriores (hasta la 52) archivadas en docs/status-archive.md. Estado vigente: la ronda más reciente abajo. Bloqueo actual: ninguno (el de TextureFBO / OpenGL30 se resolvió en la ronda 58). Lo pendiente está en la sección "Pendientes vigentes" al final.

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

## Ronda 61 (2026-10-30, inicio 10:07)
- 10:15 `port/runtime/gl.mjs`: `clear(...)` ahora traduce la máscara de escritorio a WebGL2
  (`CLEAR_BITS`: COLOR 0x4000->COLOR_BUFFER_BIT, DEPTH 0x100->DEPTH_BUFFER_BIT, STENCIL 0x400->
  STENCIL_BUFFER_BIT). Si la máscara no tiene ningún bit de escritorio se pasa tal cual (la sonda
  de FBO del bridge usa tokens WebGL2 y sigue funcionando). Los bits que WebGL2 no puede limpiar se
  registran en `clearMaskGaps` ({from,token,unmapped,seq}), nunca se descartan en silencio.
  `node --check port/runtime/gl.mjs` OK. Evidencia que motiva el cambio: la última línea de
  `build/real-browser/console-round60d.txt` es `WebGL: INVALID_VALUE: clear: invalid mask`.
- 10:20 `port/src/main/java/port/compiler/StartupProgress.java`: nuevo set `FBO_CLASSES_METHODS`
  sumado al gate de `transformClass` (antes el gate leía METHODS/EXTRA_METHODS/CLASSES/EXTRA_CLASSES/
  ANIM_CLASSES/own). Cubre con descriptores confirmados por `javap -s` sobre el jar pinned:
  `Texture#<init>(III)V`, `Texture#<init>(TextureID,String)V`, `TextureID#<init>(III)V`,
  `TextureID#createTexture()V/(Z)V`, `TextureID#generateHwId(Z)V`, las tres rutinas de `TextureFBO`
  (`<init>`, `init`, `initInternal`), `checkFBOSupport`, `getFuncs`, `attach`, `startDrawing`,
  `endDrawing`, las equivalentes de `TextureFBODepth`, y los wrappers del shim
  (`port.shim.GL11#glClear(I)V`, `glClearColor`, `nglGenTextures`, `glTexImage2D`, `glTexParameteri`,
  `port.shim.GL30#glBindFramebuffer/glCheckFramebufferStatus/glFramebufferTexture2D/nglGenFramebuffers`).
  Los natives `org.lwjgl.opengl.GL11/GL30` no existen post-rename: se instrumentan los wrappers
  `port.shim.*` (confirmado en `pulse-map.json`, que sólo tiene `port.shim.*` y `org.lwjglx.*`).

## Ronda 63 (2026-10-30, inicio 11:22)
- 11:23 `git apply /datos/Projects/pz-web-code/patches/ronda63-models-poll.patch` OK ->
  `tools/diag/models_poll.mjs` (nuevo, `node --check` OK). Primera corrida
  `PZ_DIAG_MAX=900000 node tools/diag/models_poll.mjs` (`/tmp/diag-models.log`): **la carga de
  modelos SÍ arranca y se estanca medido**, no es un artefacto de la ventana de espera.
  Desde ~203 s de `performance.now()`: `ModelManager#initAnimationMeshes:entered=1`,
  75 `FileTask_AbstractLoadModel#<init>` (75/75), 75 `#call` (75/75), 1 `ModelLoader#loadTxt`
  (1/1, 108 getMatrix / 684 getVector / 684 getQuaternion), 1 `ModelMesh#onLoadedTxt` (1/1),
  1 `ModelMesh#onBeforeReady` (1/1), 1 `ModelLoader#applyToMesh` (1/1) y
  **`ModelLoader#lambda$applyToMesh$1:entered=1` con `returned=0` durante 170+ s** (SAMPLE 4..13,
  203 s -> 353 s). El loop del juego sigue vivo: `Display#update` y `isLoadingAnimationMeshes`
  suben 406 -> 5506 y `swaps` 408 -> 5508, o sea la fibra de render no está muerta; el lambda del
  render item nunca termina. `ERRS []`, `error:""`, `progress` fijo en 672.
- 11:31 Parche a la sonda (mismo archivo): excluye del "key" de estancamiento las claves del loop
  por frame (`Display#update`, `isLoadingAnimationMeshes`, `ModelMesh#isReady`, `DoLoadingText`) y
  vuelca `RING` + `DEBUGLOG` al detectar el stall. Segunda corrida con `PZ_STALL_MS=120000`
  (`/tmp/diag-models2.log`): **RESULT stalled** y el DebugLog
  (`/Zomboid/Logs/2026-10-30_14-34_DebugLog.txt`, 89 492 B) termina con las dos últimas entradas
  `ERROR: ... java.lang.UnsupportedOperationException: Arena address requires a registered/direct
  ByteBuffer view` (una de ellas "thrown during invoke()") justo después de
  `UI_Loading_ModelsAnimations`. Eso es el `funcs.glBufferData(...)` del VBO.
- 11:40 **Causa confirmada** (vineflower sobre clases extraídas del jar pinned, salida en
  `/tmp/r63/...`, nada en el repo): `zombie.core.skinnedmodel.model.VertexBufferObject$VertexArray.<init>`
  usa **`org.lwjgl.BufferUtils.createByteBuffer(numVertices * stride)`** (LWJGL3), y nuestro
  transformer `BufferUtilsCompatibility` sólo reescribía `org.lwjglx.BufferUtils` (LWJGL2). El
  buffer queda una vista heap de TeaVM, y `MemoryUtil.memAddress` en
  `VertexBufferObject.LoadVBO -> funcs.glBufferData(GL_ARRAY_BUFFER, vertices.buffer, ...)` lanza
  el `UnsupportedOperationException` del DebugLog. `ModelMesh#isReady()` sigue pidiendo `vb != null`
  -> el loop de `initAnimationMeshes` no termina nunca.
- 11:42 Fix: `BufferUtilsCompatibility` ahora reescribe **ambas** clases
  (`TARGETS = Set.of("org.lwjglx.BufferUtils","org.lwjgl.BufferUtils")` + `import java.util.Set`).
  `mvn -q -f port/pom.xml -Dmaven.repo.local=.toolchain/m2 compile` -> EXIT=0 (19 s).
- 11:52 Build completo `scripts/build.sh --with-game` (log `build-round63-game.log`, termina `EXIT=0`, 7:50).
  `boot.wasm` 144 949 958 B; `pulse-map.json` 2217 labels / 244 de `skinnedmodel`.
- 12:04 Tercera corrida (`PZ_DIAG_MAX=900000 PZ_STALL_MS=120000 PZ_POLL_MS=20000`, `/tmp/diag-models3.log`,
  `local-run-63.log`): **`RESULT reached OnGameBoot`** (~200 s). `ModelLoader#lambda$applyToMesh$1` 1/1,
  `ModelManager#loadAnimsFromDir` 1/1, `initAnimationMeshes:returned=1`, `GameWindow#enter:returned=1`,
  `mainThreadStep` 214/213 y `frameStep/logic/renderInternal` girando. En el tally del DebugLog
  (`2026-10-30_15-02_DebugLog.txt`, 91 188 B) **desaparecieron** las entradas "Arena address".
  La carga de modelos quedó resuelta por el fix de `BufferUtilsCompatibility` (LWJGL3 + LWJGL2).
- 12:06 Nuevo bloqueo (primer error post-`UI_Loading_OnGameBoot`, DebugLog 15:04:35.461):
  `java.lang.UnsupportedOperationException: Java_zombie_MapCollisionData_n_1setGameState__Ljava_lang_String_2Z`
  -> native `zombie.MapCollisionData.setGameState(String,boolean)` sin implementación de navegador.
  En la misma cadena: `LOG: Failed to load file Guid table..` + `FileNotFoundException` (tally 8; los 7
  restantes de packs 2x son esperables y no se persiguen). Preexistentes no bloqueantes:
  `glfwCreateCursor` x2, `System.loadLibrary RakNet64`, `Bullet.defineRagdollBodyDynamics`,
  `Class cannot be created (missing no-arg constructor)` de scripts de entidades.
- Siguiente paso concreto (orden): (1) módulo JSBody honesto para `MapCollisionData.setGameState`
  siguiendo el patrón `NativeModuleCompatibility` + `port/natives/*` (nunca simular éxito; el resto de
  sus natives sigue con fail-fast); (2) investigar el `Guid table` con `vfs.exists/list` vía evaluate,
  no suponer la ruta; (3) instrumentar `MainScreenState`/`LoadingState`/`GameStateMachine`/`LuaManager`
  en `StartupProgress` para ver el tramo del menú; (4) build + sonda con `PZ_DIAG_MAX` mayor.
  `MENU_REACHED` sigue sin crearse: el menú todavía no se renderiza.
## Ronda 64 (2026-10-30, inicio 12:07 tras cerrar la 63)
- 12:10 Cierre documentado de la ronda 63 arriba (build + 3ª corrida `RESULT reached OnGameBoot` +
  nuevo bloqueo `Java_zombie_MapCollisionData_n_1setGameState__Ljava_lang_String_2Z`).
- 12:15 Fix del nativo: `port/src/main/java/port/natives/MapCollision64.java` (nuevo) registra en
  `globalThis.pzRuntime.mapCollision.state` cada clave/valor que el juego publica, con contador por
  tipo y `recordedKeys()` para Playwright. Los cinco overloads de `n_setGameState` (descriptores
  confirmados con `javap -p -s`: `(Ljava/lang/String;Z/I/F/D/Ljava/lang/String;)V`) se reescriben con
  `port/compiler/MapCollisionStateCompatibility.java` (patrón `LightingNativeCompatibility`, registrado
  en `BuildCompiler` después de `NativeModuleCompatibility`). Las 12 natives restantes de
  `zombie.MapCollisionData` (n_init, n_initMeta*, n_chunkUpdateTask, n_squareUpdateTask, n_pathTask,
  n_hasDataForThread, n_shouldWait, n_update, n_save, n_stop) siguen con fail-fast por su nombre JNI.
  `mvn -q -f port/pom.xml compile` EXIT=0.
- 12:20 Causa del `Failed to load file Guid table..`: `ZomboidFileSystem.loadFileGuidTable()` abre
  `getMediaFile("fileGuidTable.xml")`; el archivo existe en `PZ_HOME/media` (252 438 B) pero no está
  montado en el VFS -> FileNotFoundException (es el FileNotFound nº 8).
- 12:25 Montaje del Guid table: `startup_extra.py` (`GUID_TABLE='fileGuidTable.xml'`) lo exige y lo
  agrega; `startup.txt` 4513 entradas. Re-empacado completo (ruta ignorada `build/`):
  `pack.py --stage build/real-browser/stage --max-bytes 80000000` (3 blobs), `alias_index.py`
  (24 + 99 alias), `case_alias.py` (2952 alias lowercase), `boot_options.py` ->
  `index.json` **7589 entradas, 427 129 259 B montados de 536 870 912 B**. Verificado por HTTP sin
  reiniciar el server: `/packs/index.json` 200 con `/media/fileGuidTable.xml` y
  `/media/fileGuidTable.xml` -> 200, 252 438 B.
- 12:28 `StartupProgress`: nuevo set `MENU_CLASSES_METHODS` sumado al gate de `transformClass` para
  ver el tramo post-OnGameBoot: `GameStateMachine#update()V/#render()V`,
  `MainScreenState#<init>()V/#enter()V/#render()V/#renderBackground()V/#update()...StateAction;/
  #preloadBackgroundTextures()V`, `zombie.Lua.LuaManager#init()V/#LoadDirBase(Ljava/lang/String;)V/
  #initChecksum()V`. Ojo: la clase real es `zombie.Lua.LuaManager` (no `zombie.scripting.lua.LuaManager`).
- 12:55 (ronda 64) Sonda con stack en el pageerror (`models_poll.mjs`: `e.stack` + `PZ_SHOT`) y
  `StartupProgress` instrumentando `port.runtime.PortXmlBeans/PortUnmarshaller/PortMarshaller`
  (`XML_BEAN_PREFIXES` en `own()`). Build sin cambios de wasm (solo `mvn compile`), corrida
  `/tmp/diag-menu64b.log` + `test-results/menu-round64-stack.png`: el `illegal cast` quedó
  **atribuido** por el stack: `RuntimeError: illegal cast at zombie.FileGuidTable::setModID <-
  zombie.ZomboidFileSystem::loadFileGuidTable <- getFileGuidTable <- getFilePathFromGuid <-
  resolveFileOrGUID`. La fibra NO está parkeda en el unmarshal: el unmarshal termina y el
  `checkcast zombie/FileGuidPair` de `setModID` es lo que tira. `progress` 743 sigue congelado
  porque ese RuntimeError mata la fibra del MainThread (por eso `GameWindow#mainThreadStep entered=1
  returned=0` y nunca `GameStateMachine#update`).
- 12:58 Causa (propia, no del juego): `PortXmlBeans.readBean` pasaba `field.getType()` como tipo de
  cada hijo de una coleccion, asi que para `public ArrayList<FileGuidPair> files` instanciaba un
  `java.util.ArrayList` vacio por cada `<files>` y lo guardaba en la lista. `javap -c
  zombie.FileGuidTable` confirma `checkcast #48 zombie/FileGuidPair` en `setModID`.
- 12:59 Fix: `PortXmlBeans.elementType(Field)` (RI: `@XmlElement(type=...)`, si no el unico
  parametro generico del campo via `getGenericType()`; si no se puede determinar, `JAXBException`
  con el nombre del campo, nunca adivinar) + `readValue` lanza `JAXBException` si el tipo declarado
  es `Collection`/`Map` en vez de leer un elemento como coleccion. `mvn compile` EXIT=0.
  Build completo en curso: `build-round64b-game.log` (`MAVEN_OPTS="-Xmx8g -Xss16m"`).
- 13:50 Ronda 64 (2ª parte) causas confirmadas del corte del MainThread en `MainScreenState#renderBackground`
  (sonda `/tmp/diag-menu66.log` + DebugLog `2026-10-30_16-36`): `renderBackground()` llama
  `renderVideo()` -> `VideoTexture.getOrCreate("title_screen_background.bik",1920,1080)`, y el
  `<clinit>` de `zombie.core.textures.VideoTexture` (javap -p -c, `/tmp/r64/vt.txt:458-487`) ejecuta
  sin guarda `System.loadLibrary("Bink2x64")` + `"bink64"` (ruta Linux). Nuestro shim lanza la
  excepción honesta con el nombre JNI -> `GameThread exited..` -> el menú nunca se pinta.
- 13:52 Arreglo por la vía propia del juego (sin reimplementar Bink): `tools/pack/boot_options.py`
  escribe ahora `doVideoEffects=false` en `/Zomboid/options.ini` (nuevo flag `--video-effects`).
  Verificado en bytecode: `Core.loadOptions()` parsea cada línea vía `optionByName.get(...).parse(...)`
  (`zombie.core.Core` 5476-269) y `BooleanConfigOption.parse/isValidString` acepta `true/false/1/0`;
  `MainScreenState.renderBackground()` es `if (!getOptionDoVideoEffects() || !renderVideo())
  renderOriginalBackground(a)` y el propio juego ya apaga la opción con <8 GB de RAM
  (`MainScreenState.enter`), así que es el camino documentado del juego, no un stub.
- 13:55 Arte del fondo estático montado en `tools/pack/startup_extra.py` (`MENU_UI_FILES`,
  `MENU_UI_PREFIXES`): los 8 `media/ui/Title*.png`, `NinePatch1..4.png` (barra del logo),
  `ui/Logos/TheIndieStoneLogo_credits.png`, `media/ui/playstyleIcons/` (6) y
  `media/ui/controllerBindingsEditor/` (3) = 22 entradas, 4 106 333 B. Todo existe en la copia
  pinned y se exige con `SystemExit` si falta.
- 13:56 Re-empacado (ruta ignorada `build/`): `pack.py --stage build/real-browser/stage
  --max-bytes 80000000` (4535 archivos, 3 blobs), `alias_index.py` (24 alias), `case_alias.py`
  (2952 alias lowercase), `boot_options.py` -> `index.json` **7512 entradas, 417 867 444 B montados
  de 536 870 912 B** (bajó por el re-empacado limpio; headroom ~119 MB). Verificado: las 22 rutas
  nuevas responden 200 con sus bytes y `sha256` coincide con el blob (`/media/ui/Title.png` 497 338 B).
- 14:30 (ronda 64, 3ª parte) Causa del `RuntimeError: array element access out of bounds` en
  `ModelLoader::loadTxt` (2 pageerrors, `loadTxt entered=8 returned=6`): `Bob_Idle.x` y `Bob_Run.x`
  (y `Kate_Idle.x`/`Kate_Run.x`) **no traen** `MeshNormals`/`MeshTextureCoords` (su geometría está
  en `DeclData`), así que `x_anim_to_txt.convert` emitía `# Vertex Count: 0`. Con `numVertices == 0`
  el `case 6` de `loadTxt` no entra nunca y su `reader.readLine()` final deja de comerse el comentario
  `# Number of Faces:`, por lo que `case 8` lee la primera línea del esqueleto (`"0"`) como cara y
  `spl[1]` queda fuera de bounds. Fuente: réplica exacta del consumo de líneas del parser
  (`/tmp/r65/exact.py`, escrito sobre `/tmp/r60/out/ModelLoader.java`), que reporta el fallo en los 3
  archivos con `Vertex Count: 0` y valida los 7 restantes sin errores. Ningún `.txt` que trae el
  juego tiene 0 vértices (`media/animsold/*.txt`: 536/525 vértices).

## Ronda 79 (Code, 31/10 01:10–06:45) — menú -> partida
- Funciona y verificado en Chrome (puerto 8098, capturas en ~/pzshots/r79-*.png): NEW GAME, selección de spawn con el
  mapa real de Muldraugh, profesión/rasgos, personalización, PLAY -> carga completa del mundo (343 s) -> "Click to Start"
  -> `IngameState.enter` (jugador creado).
- No logrado: el mundo no se dibuja (negro) y no hay control WASD. Bloqueo: nativas Clipper/ClipperOffset (ver
  docs/notes-from-code.md, Ronda 79).
- Simplificaciones documentadas de nativas: Lighting64 (luz ambiental, sin oclusión), PopMan64 (sin población virtual =
  sin zombis), BulletWorld (sin cuerpos), MapCollision64 (sin path tasks), pathfinder Java del juego vía debug-options.ini.

## Ronda 80 (Code, 31/10) — Clipper, vehículos, pantalla negra
- Fase: menú -> partida; `IngameState.enter` completa (con vehículos desactivados en Custom Sandbox). Commits `acf5ac6`,
  `d80bc78`, `f676527`, `0b3f48f` en `code/menu`.
- Evidencia: nativas Clipper (13) y ClipperOffset (6) en Java sobre Clipper2-java 2.0.1 + earcut4j 2.2.2
  (`port/natives/ClipperEngine.java`, `ClipperOffsetEngine.java`); Clipper ya no aborta `IngameState.enter`.
  `Bullet.setVehicleParams` responde como la librería ante un vehículo sin cuerpo (aviso y -1). Dos causas de la pantalla
  negra corregidas en el puente GL: built-ins GLSL redeclarados por `math.glsl` (21 programas no compilaban) y
  `glColor3f/4f` sin mapear que llenaba las pilas de atributos. Tests: Node 68/68, inventario python OK, JVM
  `ClipperEngineTest` 13/13. Detalle en docs/notes-from-code.md, Ronda 80.
- Resultado de la corrida pzd86 (build e, Custom Sandbox sin vehículos): la UI de la partida se dibuja (Survival Guide,
  inventario, panel del personaje, "- Game Paused -"; `~/pzshots/r86-after-d.png`). Al cerrar la guía el mundo mostró
  sólo un lavatorio 3D y rectángulos de contorno rojos/verdes sobre negro (`r86-noguide.png`). `Display.update` quedó
  congelado en 5963: `glGetInteger(GL_CURRENT_PROGRAM)` usaba la constante errónea 0x8bad (corregido en `d117493`,
  0x8B8D). Errores de shaders restantes: `pow/clamp` con argumentos int, identificador `texture`, `ivec2*float`
  (corregidos en `421a22d`, sin probar en esa corrida). No se verificó todavía el mundo ni WASD.
- Faltantes: `Bullet.addVehicle`/`getVehiclePhysics` (física de vehículos; no se inventó un modelo), modo inmediato
  `glBegin/glEnd` (`DeadBodyAtlas`, `WorldItemAtlas`), pila de matrices de función fija (`ModelManager.RenderParticles`),
  estado de arrays habilitados por VAO. Siguen las simplificaciones de Ronda 79 (Lighting64, PopMan64, BulletWorld,
  MapCollision64).
- Bloqueos: con vehículos activados, `Bullet.addVehicle` (`CarController.<init>` vía `BaseVehicle.createPhysics` en
  `IsoChunk.doLoadGridsquare`) no está implementada.
- Pasos manuales: para probar la partida, Custom Sandbox (956,540) -> NEXT -> NEXT -> pestaña Vehicles (58,376) ->
  destildar "Vehicles" (938,127). Un solo build de Maven y un solo Chrome a la vez (RAM).

## Ronda 80 — frente L (31/10): jassimp64 sobre caché preconvertido (personaje invisible)
- Hecho: nativa `jassimp64` (`port.natives.Jassimp64` + `JassimpNativeCompatibility`, commits `b9af297`, `d609401`):
  `aiImportFile` lee en la ruta `.x` del VFS el caché que `tools/jassimp/JassimpCache.java` genera corriendo la
  `libjassimp64.so` fijada con los flags de `FileTask_LoadMesh.loadX`. JVM: `JassimpCacheTest` 3/3; ida y vuelta real
  (`--verify`) idéntica en MaleBody/FemaleBody/Male_Skeleton/Female_Skeleton. boot.wasm con Jassimp64: build de M 19:50.
- Pack: `tools/pack/stage_jassimp.py` -> `build/real-browser/stage-jassimp`; `overlay.py --add` de `/media/models_X/` y
  `/media/models_x/` encolado tras el swap (bajo chrome.lock). Respaldo: `build/real-browser/packs/index.json.before-frontL`.
- Sin verificar en Chrome: que el jugador se vea. Riesgo: clips todavía `.txt` sobre esqueleto jassimp.
- Faltan: ropa/pelo/otros modelos de `models_X` (sólo 4 .x servidos), FBX/GLB (metadata de nodos no soportada en el caché).

## Ronda 81 (Code, 31/10–01/11) — velocidad
- **Fase**: arranque y fps. Versión rápida: `scripts/fast.sh` + `http://127.0.0.1:8098/fast.html` (= `game.html?build=release-minpulse&persist=1&texcache=1`).
- **Evidencia** (bench repetible `scripts/bench.sh`, misma partida, Chrome headless con la RTX 3090): arranque a términos 342 s -> 35 s, menú
  352 s -> 37 s, entrar a la partida guardada 437 s -> 126 s (carga del mundo 74 s), fps de juego en partida 16–22 (dev) -> 58,8 quieto / 57,6
  caminando (tope rAF 60), jugador movido 4,4 tiles, QUIT -> menú 17,8 s, segundo CONTINUE 46 s. Tabla y condiciones en `~/pzshots/CLAUDE_REPORT_R81.md`.
- **Qué lo explica** (por impacto): diagnóstico `BrowserMonitor` (44 % del arranque) y `StringBuilder.delete` O(n²) (17,6 %) corregidos; temporizadores
  `setTimeout(0)` por `MessageChannel` (+56/71 % de fps); release TeaVM ADVANCED + `wasm-opt -O2` con inlining acotado (el sin acotar crashea el renderer
  por OOM de TurboFan); caché de texturas en IndexedDB (~945 MB, verificada campo por campo) y lectura en bloque de lotheaders.
- **Hilos**: no viable en Chrome 156 (Wasm GC no cruza a Workers, sin tipos `shared`); sin prototipo. Detalle en `docs/notes-from-code.md`.
- **Faltantes**: por qué un menú con partida guardada rinde menos que sin ella (37 vs 16 fps en dev); el segundo CONTINUE rinde 37 fps (el primero 59);
  no se repitió el perfil en partida después de los temporizadores; vehículos/zombis/pelo siguen pendientes (ronda 80).
- **Bloqueos**: ninguno. **Pasos manuales**: ninguno (la caché de texturas ocupa ~945 MB del perfil de Chrome; `?nocache=1` la apaga).


## Ronda 82 (multijugador)
- Hecho y verificado (capturas locales, no versionadas): dos clientes web entran al servidor dedicado real (127.0.0.1:16261, -nosteam, sin UPnP) mediante /ws + puente Java con la RakNet real, y se ven moverse. Detalles, cómo lanzarlo y riesgos: docs/multiplayer-design.md y ~/pzshots/CLAUDE_REPORT_R82.md.
- Falta: recompilar release (fast.html aún no trae multijugador), vehículos (Bullet.addVehicle) y voz (RakVoice), lista de servidores con ping, reconexión y más de dos clientes.

## Ronda 83 (modo directo)
- `/play.html` (release) y `/play-dev.html`: entran solos al servidor local con una cuenta por navegador (localStorage), crean personaje y aparecen en la región única; dos navegadores nuevos se vieron en el mismo mundo (capturas locales). Cómo lanzarlo y riesgos: ~/pzshots/CLAUDE_REPORT_R83.md. LAN: docs/lan-exposure.md (sin aplicar).
- Decisiones: ToS aceptado por el script (?tos=1 lo muestra); servidor de prueba con DoLuaChecksum=false.

## Ronda 84 (túnel público)
- scripts/public-tunnel.sh start|stop|status: dev server en modo público con clave + cloudflared; verificado con curl (403/302/200, /ws 403/101) y con un Chrome por la URL pública hasta ingame. Detalles y cómo cerrar: ~/pzshots/CLAUDE_REPORT_R84.md.

## Ronda 88 (carga inicial, master)
- Hecho (sin medir en Chrome todavía): `game.mjs` compila `boot.wasm` en paralelo con VFS/packs (`earlyWasm`, engancha `compileStreaming` solo para boot.wasm; `?earlycompile=0` lo apaga; `pzEarlyCompile` indica `used` o `fallback`), lanza antes `media-manifest.json` y `stdlib.*`, y pide `navigator.storage.persist()` (`pzStoragePersisted`). `packs.mjs` descarga, verifica (tamaño + sha256) y guarda los packs en paralelo; la verificación por archivo no cambia.
- Evidencia: `node --check` OK; `npm test` 151/152 (el fallo es `autojoin.test.mjs` por `repl = false` del cambio ajeno sin confirmar). `scripts/bench.sh` no corrió: `chrome.lock` ocupado por otra sesión.
- Pendiente: medir con `scripts/bench.sh --page fast.html` (caché fría y caliente) cuando el candado esté libre; perfilar la carga del mundo (74 s) con `tools/diag/profile.mjs`; precalentar la caché de texturas; reducir los ~2150 XHR restantes.
- 88b: precarga en segundo plano de `/warm-media.json` (4720 rangos, ~167 MiB, en orden de uso) al montar la media remota; no bloquea el arranque, `?prefetch=0`/`?nocache=1` la apagan, lo no usado se descarta a los 10 min (`pzPrefetchBackground`, log `prefetch-bg`). Test de autojoin tolerante a `repl = false`; `npm test` 152/152. Sigue sin medirse en Chrome (`chrome.lock` ocupado por otra sesión). La caché de texturas no se puede precalentar desde fuera: vive en el IndexedDB del perfil de Chrome que juega; la primera corrida de cada build la llena.

## R86 (2/11/2026): zombis y combate
- Hecho y aplicado en vivo (OK del dueño): clips de zombi y de combate del jugador, mallas de armas, arreglo del acorde de ratón, clic derecho sin menú; servidor PZ reiniciado sin resetear el mundo.
- Evidencia (instancia de prueba, 2 clientes headless): mismos IDs/estados de zombi en ambos, persecución, mordidas (el 2.º jugador murió), muerte de un zombi con bate y ratón real (`OnZombieDead`).
- Pendientes cerrados (rama `feat/combate-zombis-r86-pendientes`): la muerte de un zombi se ve en el 2.º cliente con **bate, palanca, hacha y machete** (desaparece de su lista, `OnZombieDead`, cadáver en la casilla; cada impacto también llega a `b`). Instancia de prueba propia (PZ 16311, web 8399, mundo nuevo), 2 clientes headless, ratón real. La prueba usa admin con `/godmode`+`/invisible`, `/removezombies` y `/teleportto` junto al zombi (con la densidad nueva las mordidas cancelan cada golpe: corridas previas 0/4). No probado: armas de fuego, ni cuerpo a cuerpo sin godmode.
- Hallazgo y arreglo: `game.mjs` montaba los packs dos veces (merge de #12) y la carga fría con los packs reales moría con `VFS memory budget exceeded`; arreglado en esta rama con test guardia. Lo que sirve el 8098 en vivo lo tiene hasta publicar.
- Faltantes: la densidad nueva (`Zombies=3`) solo aplica a un mundo nuevo (las celdas visitadas guardan su población en `zpop/`; `reset-world` borra la partida y necesita OK del dueño; documentado en `docs/operacion/manejar.md`); con esa densidad el punto de aparición quedó con ~19 zombis naturales alrededor (una muestra): decidir con el dueño si se baja antes de un mundo nuevo. Cambiar la contraseña de admin del mundo viejo si es `pzweb82`. La reconexión en la misma página tras una caída queda en «Contacting server…» hasta recargar (sin investigar).
## Carga inicial: caché persistente (rama `perf/cache-carga-inicial`)
- Hecho: caché persistente de `/media` (`port/runtime/mediacache.mjs`, `?mediacache=0` la apaga); packs grandes en OPFS, sin reescribir lo cacheado, reintento ante corrupción y limpieza de packs viejos (`packs.mjs`); manifest con `version` + ETag/304 y packs `immutable` (`dev-server/server.mjs`); `boot.wasm` pedido desde `game.html` antes de la cascada de imports, `modulepreload`, y SQLite/Bullet/texcache/pulse-map arrancando en paralelo (`game.mjs`). Análisis: `docs/analisis-carga-inicial.md`.
- Evidencia: `npm test` 160/160 (salvo pzbullet, como la CI; 10 pruebas nuevas). Chrome estable con servidor y datos sintéticos (puerto temporal 8199, perfil en /tmp): importmap y módulos resuelven; segunda carga sin pedir ningún `.bin` (packs desde Cache API y OPFS) y lectura de `/media` sin XHR (restaurada); `boot.wasm` pasa del pedido 35/37 al 19/37.
- Pendiente: medir con el juego real (`scripts/bench.sh --page fast.html`, frío y caliente) — no hay build ni copia del juego en la máquina donde se hizo; reiniciar el dev server en vivo (con OK) para la versión con mtimes y las cabeceras; memo de GLSL, sha256 de packs por carga y caché de texturas por build quedan como decisiones abiertas en el análisis.
- 2.ª tanda (rama `perf/carga-inicial-glsl-medicion`, desde `dev` con #9 y #10 ya mergeados): memo persistente de la traducción GLSL (`port/runtime/glslcache.mjs`, `?glslcache=0|verify`) y marcas de tiempo del arranque (`pzBootTimings`, consola `boot-timings` y `caches`). `npm test` 168/168 (4 pruebas nuevas); también marca `first-frame` (cierre de la pantalla de carga de #10). Sin Chrome: por `docs/operacion`, en macOS no se levanta nada. Pendiente: correr el protocolo de medición de `docs/analisis-carga-inicial.md` en la máquina Linux y anotar la línea base acá.

## R87 (2/11/2026): navegador
- Carga (medida con `tools/diag/load-bytes.mjs`, perfil vacío, instancia de prueba): antes 841 MB / 516 MB (1.ª / 2.ª); con PR #9: 841 MB / 44,7 MB. Faltante: carga por partes del mapa/packs para bajar la 1.ª (packs 580 MB, media 193 MB en ~4450 pedidos, wasm 63 MB).
- Server PZ en wasm: no viable hoy. WebRTC: viable como transporte opcional, ~4-5 rondas, TURN requiere cuenta o VPS ajeno. Detalle en los informes; nada de esto afecta lo en vivo hasta implementarse.

## Rendimiento por jugador (rama `feat/fps-ping-jugadores`, sin build)
- Hecho: indicador arriba a la derecha en `game.html`/`play.html`/`fast.html` (`port/runtime/perfhud.mjs`): fps reales del juego (cada `swapBuffers`), peor cuadro del último segundo, `ocupado %` (tareas largas del hilo principal, `PerformanceObserver('longtask')`), ping al dev server (`GET /health?perf=N` cada 5 s, tomado de Resource Timing `responseStart - requestStart`) y memoria JS de la pestaña. Colores: verde/amarillo/rojo por umbral. `?hud=0` lo apaga; `globalThis.pzPerf` tiene el último resumen.
- Evidencia: `npm test` 172/175 (fallan solo las 3 de pzbullet, como en la CI; 4 pruebas nuevas en `tests/node/perfhud.test.mjs`). Sin Chrome: en macOS no se levanta nada (`docs/operacion`).
- Pendiente: probar en 8199 contra el `boot.wasm` compilado y ver que el indicador no tape nada del juego; el panel con todos los jugadores juntos va en un PR aparte porque necesita reiniciar el dev server.
- 2.ª tanda (rama `feat/panel-jugadores`, encima de la anterior; **necesita reiniciar el dev server**): cada pestaña manda su resumen con `GET /perf/report` cada 5 s (el mismo pedido mide el ping; no se anota en `/tmp/pzweb-access.log`); `GET /perf` devuelve todos los jugadores de los últimos 30 s y el estado de la máquina (carga, memoria libre, RSS del dev server, puentes RakNet); `/jugadores.html` los muestra en una tabla que se actualiza cada 2 s. Nombre = cuenta del modo directo (`p…`) o `web-xxxx` por pestaña. `npm test` 176/179 (las 3 de pzbullet; 4 pruebas nuevas). Pendiente: reiniciar 8098 con OK del dueño y mirar el panel con dos jugadores reales.

## Rotación de la clave admin (rama `chore/clave-admin-mundo`, preparada, NO aplicada)
- Hecho: `scripts/mp-server.sh` lee la clave admin de `PZ_MP_ADMIN_PASSWORD_FILE` o `~/.config/pz-web/admin-pass` (600; antes solo `/tmp/pz82/admin.pw`, que se pierde al reiniciar la máquina) y `start` se niega si el archivo nombrado falta o no es 600. Subcomandos nuevos: `admin-pass-file` (solo la ruta) y `gen-admin-pass [--force]` (escribe una clave aleatoria sin imprimirla). Los diag `play-two`/`play-repl` leen el mismo archivo.
- Evidencia: instancia propia (PZ 16281, datos en `/tmp/pzrot`): con el archivo A el admin del mundo recién creado verifica contra A; se paró, se rotó el archivo y se arrancó de nuevo sobre el mundo existente: el admin verifica contra la nueva y ya no contra la vieja (el servidor reaplica `-adminpassword` en cada arranque). `npm test` con la prueba nueva `tests/node/mp-server-admin-pass.test.mjs`. El servidor en vivo (pid 482406) no se tocó.
- Pendiente: **NEED RESTART: admin password rotation** — al próximo reinicio agrupado del servidor PZ: `bash scripts/mp-server.sh stop && bash scripts/mp-server.sh start` en la máquina en vivo (el `start` ya toma el archivo nuevo). Hasta entonces el admin en vivo sigue con la clave anterior, y `play-two --admin` contra el servidor en vivo falla por eso.

## Controles del canvas (rama `fix/controles-input`, sin build)
- Ya estaba de r86b/r87: `contextmenu` cancelado en `document`, botón derecho = GLFW 1 (down/up, también acorde con el izquierdo), rueda, latch de teclas y botones, sueltas al `blur`. Esta rama verifica eso en Chrome 156 y Firefox 159 y cierra lo que faltaba:
  - `GLFW_FOCUSED` seguía siempre en 1: ahora sigue el `focus`/`blur` real de la ventana.
  - El canvas recupera el foco al volver a la ventana/pestaña y al hacer clic en el HUD (los campos de formulario conservan el suyo).
  - Pointer lock: al perderlo (Esc) se sueltan teclas y botones y el siguiente clic lo vuelve a pedir si el juego dejó `GLFW_CURSOR_DISABLED`; con el lock activo el cursor es virtual (suma `movementX/Y`) porque el navegador congela `clientX/Y`.
  - `mousedown`/`mouseup` del canvas cancelados (botones atrás/adelante).
- Evidencia: `npm test` 185/188 (fallan solo las 3 de pzbullet, que ya fallaban sin esta rama y la CI excluye; 9 pruebas nuevas en `tests/node/input-controls.test.mjs`). `tests/input-browser.manual.mjs`: Chrome 156 (headless, con el candado de Chrome de entonces, `chrome.lock`; ahora `pz-chrome-slot`) 16/16 y Firefox 159 portable + geckodriver (en `/tmp/pz-fx`, sin sudo; Playwright no soporta Firefox en este SO) 17/17, con eventos reales del navegador sobre una página sintética que usa `CanvasInput` y `BrowserWindow` reales.
- Límites: no se corrió el juego real (no hubo build ni partida; la entrada del juego pasa por estos mismos módulos). El menú contextual propio del juego no se probó. En Chrome headless el cambio de pestaña no emite `blur` y Esc inyectado por CDP no sale del lock (se usó `exitPointerLock`); el flujo con `blur` real se vio en Firefox. No hay callback de foco hacia Java (haría falta build).
- Pendiente tras publicar: mirar en la partida en vivo que `GLFW_FOCUSED=0` en una pestaña sin foco no cambie nada visible (el juego solo consulta el atributo).

## R89 (2/11/2026): carga comprimida y Tiles2x en tramos (rama `perf/carga-1b-brotli-chunks`)
- Hecho: br/gzip precomprimidos para wasm y packs, texto comprimido en memoria, Tiles2x partido en 369 tramos con sha256 (`scripts/lazy-packs.mjs`, `lazypack.mjs`), la 2.ª llamada a `preloadPacks()` de `game.mjs` (que hacía fallar la carga con `VFS memory budget exceeded`) la quitó antes #26; aquí solo se midió.
- Evidencia (juego real, instancia propia 8211/8212 + PZ 16291, `load-bytes.mjs`): 1.ª carga 889,9 -> 641,5 MB, 2.ª 95,0 -> 84,2 MB; ingame en 285 s / 288 s en loopback y 714 s -> 668 s con 25 Mbps simulados. Detalle y tablas en `docs/analisis-carga-inicial.md`. `npm test`: solo fallan las 3 de pzbullet (como en la CI).
- Hallazgo: el juego lee los 369 PNG de Tiles2x en la carga del mundo; Tiles2x no se puede bajar parcialmente (la ganancia es solapar la descarga, no ahorrar bytes).
- Pendiente: `/media` (241 MB, 4730 Range) sigue sin comprimir; medir por el túnel real (la simulación es de 25 Mbps).
- **Para publicar**: JS nuevo (`sync_web`) y `lazy-packs.mjs` + `precompress.mjs` sin cortar nada; **reiniciar 8098** para servir lo comprimido (`NEED RESTART`, OK del dueño).


## Mecánicas de movimiento (rama `fix/mecanicas-saltar-trepar`, 2/11/2026)
- Hecho: causa raíz = faltaban los clips `anims_X/Bob` de trepar/saltar; `stage_jassimp.py --bob-anims mobility` los preconvierte (87 clips, 15,1 MB, salen de `PZ_HOME`, no se versionan). Detalle, tabla de resultados y por qué no se arreglan las demás: `docs/mecanicas-movimiento.md`.
- Evidencia (Chrome con el juego, instancia de prueba 16281/8201/8202, antes y después con los mismos binarios): ventana — de estado atascado para siempre a `Climb… → Idle` y cruza (1,14 casillas); valla real — el estado ya termina pero el personaje aún no cruza (pendiente). Pared alta, soga y vault con teclado: sin objetivo válido o no concluyente. `npm test` 176/179 (las 3 de pzbullet, como la CI) y `python3 -m unittest tests/python/test_stage_txt.py` OK.
- Pendiente: aplicar en vivo = ampliar los paquetes con el modo `mobility` en la máquina en vivo (no corta la partida; ~80 MB de presupuesto del VFS libres tras esto); investigar por qué la valla no desplaza; hallazgo aparte: `PZPopMan64: n_init has not run yet` saca al cliente al menú tras `/removezombies`.

## Ronda 89: carga del mundo (rama `perf/carga-mundo`)
- Hecho:
  - A: caché de texturas llaveada por `CODEC_VERSION` y no por build; sobrevive a los builds nuevos.
  - B: precarga en lotes por `/media-ranges`, ~48 pedidos en vez de ~4720, también por https.
  - C: sin `getError` real tras llamadas sin condición de error; nuevo contador `gl.realGetErrors`.
  - D: los packs de la caché local se validan por tamaño, sin sha256 completo. Después de #27 eso ahorra el sha256 de ~245 MiB (packs no perezosos), no de ~500 MB: Tiles2x ya va por tramos verificados.
  - E: marcas `world-load-start/end` e `ingame-enter`, segunda línea `caches` al terminar la carga, y cartel "Cargando el mundo…" en el modo directo.
  - Interruptores para volver atrás: `?texcache=build`, `?prefetchbatch=0`, `?glerrors=strict`, `?packverify=1`. Análisis y protocolo en `docs/analisis-carga-mundo.md`.
- Evidencia: `npm test` 195/198. Fallan solo las 3 de `pzbullet.test.mjs`, como en la CI. Hay 18 pruebas nuevas, en texcache, media-ranges, gl-error-drain y packs-cache. `node --check` OK.
- Rebase sobre `dev` con #25–#28 (rama `perf/carga-mundo-rebased`, reemplaza a #18):
  - Las entradas del warm-list que `/media-ranges` no sirve (los tramos `/packs/<sha>.bin` de Tiles2x, #27) van por el camino de a uno y pasan primero por `onEntry` (lazypack), en paralelo con los lotes; `limitBytes` cuenta también `lazyBytes`. Prueba nueva en `media-ranges.test.mjs`.
  - `depthRange` y `lineWidth` salen de `NO_ERROR_CALLS` (pueden fallar); prueba nueva.
  - `CODEC_VERSION` = 2; cuándo subirla, en `docs/operacion/actualizar.md`.
  - `npm test` 231/234 (las 3 de pzbullet).
- Sin evidencia todavía:
  - Chrome con el juego y build real: en macOS no se levanta nada.
  - `PackImageCache.java` solo cambió en un comentario.
- Pendiente: correr el protocolo de `docs/analisis-carga-mundo.md` en la máquina Linux (frío/caliente, rebuild, verify, túnel) y anotar acá la línea base y el resultado.
- Pasos manuales: reiniciar el dev server, con OK del dueño, para tener `/media-ranges`. Sin el reinicio, el cliente vuelve solo al modo anterior.

## Pendientes vigentes (2/11/2026)
- En vivo hoy: #25–#28 mergeados en `dev` y publicados con #29 (`dev → main`, merge `f890e7f`); el checkout en vivo está en `f890e7f`. Packs ampliados con los clips `mobility` (#28) y corridos `lazy-packs.mjs` + `precompress.mjs`; 8098 reiniciado con lo precomprimido (`boot.wasm` 134,3 → 19,8 MB en br). Con eso ya están en vivo `/jugadores.html` (#17) y las cabeceras de #9.
- Medición de la 1.ª carga con el juego real después de publicar (instancia de prueba aparte: web 8211, PZ 16291, loopback): 1.ª carga 642,8 MB transferidos (904,1 MB decodificados), menú a los 82,4 s, dentro del juego a los 300,4 s; 2.ª carga 88,4 MB, dentro del juego a los 211,1 s. Reparto de la 1.ª: tramos de Tiles2x 319,1 MB en 369 pedidos, `/media` 241,8 MB en 4730 pedidos (sin comprimir), packs 65,4 MB, `boot.wasm` 14,2 MB.
- Ramas: las ya absorbidas se borraron; `perf/carga-1a-compresion` y `feat/service-worker-descartado` también, con copia local. Quedan abiertas #18 (rebase en curso en `perf/carga-mundo-rebased`) y #22 (reemplazado por el PR de esta rama).
- Decisión del dueño: PR #18 (ronda 89, carga del mundo), con las dos políticas que `docs/analisis-carga-inicial.md` le dejó: no repetir el sha256 de packs cacheados y caché de texturas por versión del códec (se apagan con `?packverify=1` y `?texcache=build`). `/media-ranges` llega con #18 y pide reiniciar 8098 después de mergearlo.
- Carga: comprimir `/media` (en curso en la rama `perf/media-compression`); medir por el túnel real (lo medido fue en loopback y con 25 Mbps simulados); URL con hash e `immutable` para `boot.wasm` y `/vendor/*` (hoy solo los packs son `immutable`); `scripts/bench.sh --page fast.html`; fps del menú con partida guardada (16 vs 37) y del segundo CONTINUE (37 vs 59).
- Juego:
  - Densidad de zombis: sin cambios, por decisión del dueño; `Zombies=3` solo aplica a un mundo nuevo.
  - Las vallas: el estado de trepar termina, pero el personaje no cruza (`docs/mecanicas-movimiento.md`). Falta ver en la partida en vivo los clips `mobility` (ventanas).
  - `PZPopMan64: n_init has not run yet` saca al cliente al menú tras `/removezombies`.
  - La reconexión en la misma página tras una caída queda en «Contacting server…» hasta recargar.
  - Sin probar: armas de fuego y cuerpo a cuerpo sin godmode (#26 cerró bate, palanca, hacha y machete, y la muerte de zombi vista desde el 2.º cliente).
  - Faltan vehículos (`Bullet.addVehicle`), voz, `glBegin/glEnd`, la pila de matrices de función fija y la ropa/pelo de `models_X`; siguen simplificados Lighting64, PopMan64, MapCollision64 y BulletWorld.
- Controles (#25): mirar en vivo que `GLFW_FOCUSED=0` en una pestaña sin foco no cambie nada visible.
- Clave admin (#23): sin confirmar si el servidor PZ en vivo ya se reinició con el archivo nuevo (el reinicio de 8098 no lo hace); si no, sigue el `NEED RESTART: admin password rotation` de la sección de rotación de arriba.
- Consejo para instancias de prueba: un mundo de prueba recién creado arranca con `DoLuaChecksum=true` hasta que `mp-server.sh` reescribe el `.ini` en un segundo arranque; ponerlo en `false` antes de conectar o el servidor expulsa al cliente del modo directo.

## R90 (2/11/2026): `/media` comprimido (rama `perf/media-compression`)
- Hecho: tramos de `/media` pedidos como `?r=a-b` sin Range (solo si el manifest trae `slices:1`), comprimidos al vuelo con br/gzip y una caché en memoria por ruta+tamaño+mtime+tramo; los bundles de lotheader también se comprimen. Con Range todo sigue como antes. Diseño y razones por tipo en `docs/analisis-carga-inicial.md`.
- Evidencia (juego real, instancia propia 8221 + PZ 16301, `load-bytes.mjs --runs 2`): 1.ª carga 642,8 → 557,8 MB (`/media` 241,8 → 156,7 MB), ingame 300,4 → 321,8 s en loopback (una corrida, máquina con carga ~4); 2.ª carga 88,4 → 49,2 MB, 211,1 → 209,6 s. `npm test` 218/221 (fallan solo las 3 de pzbullet, como en la CI; 12 pruebas nuevas en `tests/node/server-media-compress.test.mjs`).
- Pendiente: **NEED RESTART: dev server 8098** + `sync_web.sh` para publicarlo (con el server viejo o la página vieja todo sigue con Range, sin comprimir). Medir por el túnel real. `.pack` (108 MB del warm-list, 0,94) y `.png` (34 MB) siguen sin comprimir.

## R91 (2/11/2026): `/media-ranges` comprimido (rama `perf/media-ranges-compression`)
- Los lotes de la precarga salen con br/gzip (cuerpo entero, caché en memoria por sha256, `PZWEB_RANGES_CACHE_MB`). El cliente no cambia.
- Medido (juego real, instancia propia web 8410 = `origin/dev` / 8411 = rama, PZ 16311, misma copia del warm-list, `load-bytes.mjs --runs 2`, loopback): 1.ª carga 635,3 → 557,1 / 551,3 MB; 2.ª carga 158,7 → 84,1 / 84,1 MB. `/media-ranges` 224,7 → 140,6 MB (1.ª) y 158,6 → 84,1 MB (2.ª). Segundos hasta `ingame` sin cambio apreciable en loopback (291,6 → 297,6 / 300,7 la 1.ª; 216,5 → 233,3 / 205,5 la 2.ª).
- Falta: medir por el túnel; publicar requiere reiniciar el dev server 8098 (no hay cambio de cliente).

## Pocos zombis (2/11/2026, rama `feat/low-zombie-population-v2`)
- `scripts/mp-server.sh` deja por defecto `Zombies=5` (Low), `ZombieRespawn=4` (None), `PlayerSpawnZombieRemoval=1`, `Distribution=2` y `ZombieConfig` explícito (`PopulationMultiplier=0.15`, `Start=0.5`, `Peak=1.0`, `RespawnHours=0`); cada valor se cambia con `PZWEB_*` y se valida antes de escribir. Nuevo subcomando `apply-config`. Tabla en `docs/operacion/manejar.md`.
- Medido (juego real; servidor PZ de prueba 16281/16291, web propia 8203, un cliente, conteo de `getCell():getZombieList()` vivos, 6 muestras en 2,5 min junto al bar, día 1): mundo nuevo con los valores viejos 24 cargados (10–13 a ≤40 casillas, 2–6 a ≤20); mundo nuevo con los nuevos 3 (1 a ≤40, 0–1 a ≤20); mundo viejo sin `zpop/` y arrancado con los nuevos 5 (5 a ≤40, 0 a ≤20). Un mundo por caso: la variación entre semillas no está medida. `npm test`: 264/267 (fallan solo las 3 de pzbullet, igual que sin el cambio); 9 pruebas nuevas en `tests/node/mp-server-zombies.test.mjs`.
- Pendiente: **NEED RESTART: servidor PZ en vivo** para que tome los valores; las celdas ya visitadas conservan la densidad alta hasta `reset-world` o hasta sacar `zpop/` con el servidor parado (ver `manejar.md`). Las dos cosas necesitan OK del dueño.

## Idioma español (2/11/2026, rama `feat/spanish-language`)
- El juego arranca en **ES** (menú, partida y bienvenida del chat). `port/runtime/language.mjs` reescribe `language=` del `options.ini` del pack antes de `boot.wasm`; `?lang=<CÓDIGO>` lo cambia y se recuerda, `?lang=auto` lo borra; el dev server anuncia `PZWEB_LANGUAGE` (ES) en `/media-manifest.json`; `scripts/mp-server.sh` pone `language=` en el `options.ini` del servidor y la bienvenida en español si era la del juego. Las traducciones ES se leen bajo demanda de `PZ_HOME` (no se empaquetan). Detalle en `docs/operacion/manejar.md#idioma-español-por-defecto`.
- Medido (juego real; web de prueba 8311 con `port/web`+`port/runtime` de la rama, packs y `release-minpulse` del checkout en vivo; servidor PZ de prueba 16291 con mundo nuevo; un cliente): consola `language {"applied":true,"code":"ES","source":"server","previous":"EN"}`, Lua `getCore():getOptionLanguageName()=ES`, `Translator.getLanguage()=ES`; capturas en `~/pzshots/pzes-06-load-menu.png` (menú), `pzes2-01-load-connect-1.png`, `pzes2-02-ingame.png` (HUD + bienvenida), `pzes2-03-ingame-menu-contextual.png`, `pzes2-04-ingame-pausa.png`. `npm test` 273/276 (fallan solo las 3 de pzbullet, sin build en el worktree).
- No probado en Chrome: `?lang=EN` y un `PZWEB_LANGUAGE` distinto (cubiertos por pruebas unitarias).
- Para que quede en vivo: merge a `main` + `sync_web.sh` alcanza para el cliente en ES (un dev server viejo no anuncia idioma y el cliente usa ES). `publicar_web.sh` se va a negar (sale 4) porque entran `dev-server/server.mjs` y `scripts/mp-server.sh`. **NEED RESTART** (con OK del dueño, opcionales): dev server 8098 solo para que `PZWEB_LANGUAGE` tenga efecto; servidor PZ en vivo para `language=ES` del servidor y la bienvenida en español.

## Calidad gráfica mínima (rama `feat/calidad-minima`, sin build)
- Hecho: `port/runtime/graphics.mjs` + `port/web/game.mjs`: 16 opciones gráficas en mínimo antes de `boot.wasm`; `?calidad=juego|minima|auto` (localStorage `pzweb.quality`).
- Evidencia (juego real; web de prueba 8199 + PZ de prueba 16271, un cliente, `play-repl.mjs`): el juego toma los 16 valores (Lua: fog=2 water=2 puddles=2 lightFPS=10 skybox=2 perfPuddles=3 refl=false corpseShadows=false blood=0 ground3d=false uiFPS=30 precip=3 clothes=3); fps quieto 50,1 (juego) → 60,0 (mínima, tope), caminando 41,3 → 45,0. `npm test` 280/280.
- Encontrado de paso: apuntar con un arma de fuego llama `Bullet.updateBallisticsAimReticleQuaternion` (de 20 nativas de balística sin implementar) → excepción → sale de `IngameState`, pantalla negra. Pendiente: ronda de balística.

## 2026-11-08: perfil de la carga y caché de texturas (rama `perf/texcache-sin-descartes`)
- Medido (juego real, instancia de prueba 8199/16291, loopback, 1 corrida por caso): 1.ª carga 288–301 s hasta la partida, 2.ª 208 s con la caché de texturas llena. De los ~190 s entre elegir personaje y entrar, ~110 s son CPU de wasm en decodificar PNG y trabajar píxeles (`ImageData.getPixelClamped/setPixel/performPreMultipliedAlpha`, mipmaps).
- Hallazgo: la caché de texturas descartaba el 45 % de las páginas en la 1.ª carga (253 de 561) por el tope de 256 MB de escrituras pendientes. Con 2 GB: 0 descartes y 2.ª carga 170 s.
- Hecho: tope por defecto 1 GB + `?texpending=MB` (`port/runtime/texcache.mjs`, sin build). El valor de 1 GB no se midió; solo 256 MB y 2 GB.
- Faltantes: texturas de profundidad (`TilesetDepthTexture.load`, ~26 s) y `NativeImage.read` (~15 s) no pasan por la caché; `MetaGridLoader.loadCell` (~21 s) es lógica del juego. La caché ocupa ~3 GB en IndexedDB con todas las páginas guardadas. Decodificar en paralelo con workers en la 1.ª carga sigue sin probarse.
- `tools/diag/load-bytes.mjs` con `PZ_CPUPROF=archivo` guarda un perfil de CPU (cambio hecho en el worktree `pzweb-tile1x`, sin subir).
## 2026-11-08: pantalla de carga hasta el mapa (rama `feat/carga-hasta-mapa`, sin build)
- Hecho: en el modo directo la carga tapa todo hasta `ingame` (`port/web/game.mjs`, `LoadingScreen.entering` en `port/runtime/loading.mjs`); `?intro=1` la quita con el primer cuadro como antes. Sonda: `tools/diag/loading-hold.mjs`.
- Evidencia (juego real, web 8199 + PZ 16291, Chrome aparte, 1 corrida): overlay presente en `tos-accepted`, `menu`, `profession`, `character`; desaparece en `ingame` (333,6 s en frío); `npm test` 280/280. Capturas locales, no se suben.
- Faltantes: la barra es indeterminada durante los ~290 s de carga del mundo (no hay progreso real); no se probó en el túnel ni en móvil.

## 2026-11-08: diagnóstico `slowread` (rama `perf/diag-lecturas-lentas`, sin build)
- Hecho: lecturas síncronas de `/media` de 30 ms o más quedan en `globalThis.pzSlowReads` y en la consola (`port/runtime/vfs.mjs`).
- Evidencia: `npm test` 280/280. Una caminata de 90 s en Chrome de prueba (web 8199, PZ 16291) no reprodujo el 0 fps: 1 parón de 335 ms, sin pedidos de media; no se confirmó que el personaje se moviera.
- Faltante: que el dueño capture las líneas `slowread` en una sesión real al caminar a zonas nuevas.

## 2026-11-08: mapa de un mod (Maplewood) sin Muldraugh (rama `feat/mods-de-mapa`)
- Hecho: el dev server sirve `PZWEB_MODS_DIR` (`/mods-manifest.json`, `/modfiles/…`) y el cliente lo monta en `/Zomboid/mods/`; `mp-server.sh` y `dev_server_8098.sh` leen `~/.config/pz-web/mapa`.
- Evidencia (servidor de prueba 16293 + web 8199, 1 cliente): `IsoMetaGrid` sin Muldraugh (1,5 s contra 2,7 s), el cliente carga el mod (`loading Maplewood`) y entra a la partida a los 279,6 s; captura del personaje en la zona de Maplewood. `npm test` 280/280.
- Faltantes: no se probó caminar fuera de las 4 celdas ni varios jugadores a la vez; el punto de aparición (31,32,160,74) sale de las coordenadas viejas del mod más el desplazamiento de B42 y cayó en un bosque.

## 2026-11-08: más memoria para el servidor PZ (rama `perf/servidor-mas-memoria`)
- Hecho: heap de 6 GB por defecto en `scripts/mp-server.sh`, configurable. Estimación sin medir: 8–12 jugadores cómodos con 2 GB, 20–32 (el tope de `MaxPlayers`) con 6 GB si la subida de internet aguanta la primera descarga (~700 MB por jugador nuevo).
- Faltante: prueba con varios jugadores reales a la vez.

## 2026-11-08: warm-list persistente (rama `fix/warm-list-persistente`, ronda 92)
- Hecho: `scripts/dev_server_8098.sh` exporta `PZWEB_WARM_FILE=~/.local/share/pz-web/warm.json` (respeta uno ya exportado; migra una vez `/tmp/pzweb-warm.json`); `dev-server/warm-list.mjs` crea la carpeta al guardar y `flush()` cancela el guardado pendiente. Prueba Node nueva en `tests/node/prefetch.test.mjs`.
- Evidencia: `npm test` 278/281 en el worktree (las 3 que fallan son `pzbullet.test.mjs`, que pide `port/target/web/vendor/pzbullet`, salida del build ausente en el worktree); `bash -n` de los scripts; lógica de la variable probada en subshells (migración, no pisa, variable propia, herencia al hijo) sin lanzar el servidor.
- Pendiente en vivo: reiniciar 8098 (corta a los jugadores un momento; con el túnel abierto, con la clave) para que tome la ruta nueva. Sin eso, el server actual sigue escribiendo en `/tmp`.
