# Estado del port personal

Actualizado: **2026-10-29 11:15 (-03), Ronda 47** (inicio 10:37:13 según `supervisor.log`, corte duro 14:37).
Repositorio `/home/agente47/Projects/pz-web`. Esta ronda **no hizo commits y no tocó `.git`**.
Artefacto vigente al empezar la ronda: build `build-round44c-game.log` (SUCCESS, gate PASS,
`probe8 'java.lang.Thread::<clinit>'` ⇒ `no seed`), `port/target/web/game/boot.wasm` de 143.117.203 B
del 2026-10-29 09:44. Dev server **8092** (no reiniciado) sirviendo `port/target/`.
**Leer la sección "Ronda 47" más abajo: ahí están el bloqueo actual y los siguientes pasos.**
Las rondas 43/44/45/46 están commiteadas (`a5d55a2`, `c54290f`, `b76fb7c`); las secciones de las
rondas 40/41/42 que siguen abajo son **histórico**.

## Ronda 47 (2026-10-29, inicio 10:37) — avances incrementales

Estado de partida medido en `diag135.log` (ronda 45, build round44c): `phase:"starting"`, `swap:2`,
canvas 95 % negro, DOS traps `RuntimeError: dereferencing a null pointer`:
1. `java.lang.Class::isAssignableFrom` ← `zombie.ai.State$Param::<init>` ← `ofSupplier/of_0/ofBool` ←
   `zombie.ai.states.AttackState::<clinit>` ← `Class::initialize` ← `reflect.Field::get` ←
   `LuaJavaClassExposer::exposeStatics/exposeLikeJava` (mata el MainThread).
   Causa raíz verificada con `javap` sobre la copia fijada: `State$Param.<init>` hace
   `StackWalker.getInstance(RETAIN_CLASS_REFERENCE).walk(s -> s.map(StackFrame::getDeclaringClass)
   .filter(not(this.getClass())).findFirst().orElse(null))` y luego `State.class.isAssignableFrom(<ese Class>)`.
   Nuestro `port.compat.java.lang.StackWalker` itera `Thread.currentThread().getStackTrace()`, y
   `TThread.getStackTrace()` de TeaVM 0.16.0 **devuelve un array vacío** (source jar, TThread.java:276),
   así que el walk da `null` y `isAssignableFrom(null)` hace `obj.classInfo` sobre null ⇒ trap. Con frames
   reales, `AttackState.<clinit>` (el `this.getClass()` es `Param`, no `State`) recorre la cadena hasta la
   subclase de `State` que llama a `ofBool(...)`, exactamente como en JVM.
2. `zombie.core.SpriteRenderer$RingBuffer$StateRun::render` ← `RingBuffer::render` ← `buildStateDrawBuffer`
   ← `postRender` ← `RenderThread::lockStepRenderStep` (loop de render; aún sin analizar).
Además 3 errores **no fatales** del juego: "AngelCodeFont failed to load page 0 texture
/media/mainfont_0.png, /media/mainfont2_0.png, /media/fonts/zomboidDialogue.bmfc_0.png". Verificado en la
copia 42.21: esos PNG **no existen en disco** (`media/font/mainfont.fnt.b` apunta a `mainfont_0.png`,
`mainfont2.fnt.b` a `mainfont2_0.png`, `zomboidDialogue.fnt` a `zomboidDialogue.bmfc_0.png` y el disco tiene
`zomboidDialogue_0.png`; `media/font/mainfont.fnt` es un placeholder que apunta a `test_0.tga`).
Es falta de assets de la copia fijada, no un bug del port; el juego lo catchea y sigue.

Cambios de esta ronda (en orden, cada uno verificado):

### Ronda 48 (inicio 11:33:58, corte duro 15:33)
- Aplicado el fix de Code "Ronda 47: AttackState": `port/src/main/java/port/runtime/StateParamCaller.java`
  (pila de clases llamada + `callerClass()`, falla si no hay call-site instrumentado) y
  `port/src/main/java/port/compiler/StateParamCallerCompatibility.java` (envuelve cada `invokestatic
  zombie.ai.State$Param.of*` con `enter(C)`/`exit()`; reemplaza el único `StackWalker.walk(Function)Object`
  de `Param.<init>(String,Class,Supplier)V` por `callerClass()`; falla el build si la forma del bytecode
  cambia o si hay call-sites fuera de `zombie.ai.states.*`). Registrado en `BuildCompiler` tras
  `CurrencyCompatibility`. `mvn -f port/pom.xml compile` OK.
- Scan del jar fijado (fuera de git, sólo conteos): 50 clases con call-sites, **207 sitios**, todos en
  `zombie.ai.states.*` (ClimbOverFenceState 16, GrappledThrownOutWindowState 15, PlayerDraggingCorpse 12).
- Check offline `tools/stateparam/Check.java` sobre `zombie.ai.State$Param` + 4 clases: `Param.<init>`
  `walk=false callerClass=true`, `enter=44 exit=44 == factorySites=44` → **OK** (sin stack en runtime).
- Build **única** con juego `build-round48-game.log`: BUILD SUCCESS (6:52 min), gate PASS
  (`Thread::currentThread/setCurrentThread fiberops: False`), `wasm_bytes=143118755`, RSS pico 5.89 GiB.
  Verificación pedida por la nota: `wasmfn.py boot.wasm 'zombie.ai.State$Param::<init>'` ya **no** llama
  `java.lang.StackWalker::walk` y sí `port.runtime.StateParamCaller::callerClass`; y
  `zombie.ai.states.AttackState::<clinit>` llama `StateParamCaller::enter/exit` + `State$Param::ofBool`.
- Chrome (`diag136.mjs`, 110 s, `diag136.log`): **desapareció el trap de `Class::isAssignableFrom`**
  (`exposeStatics` pasó los `*State`; `LuaManager#init` entered/returned, `initFonts` 2× returned,
  `checkRequiredLibraries` returned, phase `starting`, swap 2). Queda **un solo**
  `RuntimeError: dereferencing a null pointer`, ahora en el RenderThread:
  `SpriteRenderer$RingBuffer$StateRun::render` ← `RingBuffer::render` ← `buildStateDrawBuffer` ←
  `postRender` ← `lockStepRenderStep`. DebugLog 28.567 B: 8× `java.io.FileNotFoundException`,
  1× `UnsupportedOperationException: Unsupported platform: UNSUPPORTED` (oshi),
  1× `UnsatisfiedLinkError: ... missing for RakNet64`.


## Resumen del estado real hoy (actualizado en la ronda 42)

- **El boot llega a `GameWindow.init()` y avanza más lejos que nunca**: `initFonts()` retorna, las texturas de las
  fuentes se suben a WebGL2 (66 `createTexture/texImage2D`, 264 `texParameteri`, 0 raises) y `LightingJNI.init`
  ya tiene su librería gracias a nuestro módulo `port/natives/Lighting64.java`: **desapareció el `System.exit(1)`**.
- **Stop actual (ronda 42)**: `ZombiePopulationManager.init → System.loadLibrary("PZPopMan64")` lanza
  `UnsatisfiedLinkError` (ese `init` no tiene try/catch) → `GameWindow.mainThreadInit` lo reenvuelve en
  `RuntimeException` → `GameThread exited..` → el `RenderThread` termina el loop con `System.exit(0)`
  (registrado `{status:0, swapCount:2, windowCreated:false, contextCurrent:false}`, `errs: []`).
- Canvas: captura `test-results/round42-textures.png` (1280×720) sigue en **95.06 % negro** (`(0,0,0)` 876.098 px
  de 921.600, 12 colores). Ningún frame del menú dibujado todavía: `swapCount = 2`.
- Las secciones de las rondas 40/41 que siguen abajo son **histórico**: el `inflate error` de la ronda 40 ya está
  arreglado y las 48 texturas que fallaban ahora suben bien.

- Última etapa alcanzada (medida en Chrome con `port/target/diag102.mjs`, log `diag102.log`, 110 s de corrida):
  `GameWindow#<clinit>` → `InitDisplay` (640×480, ANGLE/SwiftShader, "OpenGL version: 3.3 (WebGL 2.0)") →
  `MainScreenState#loadIcons()` (entered/returned) → `LoggerManager#init` → `Bullet#init` → `DebugOptions#init` →
  `GameProfiler#init` → `RenderThread#init` → `ZomboidFileSystem#init` → `DebugFileWatcher#init` →
  `GameWindow#init()` → `GameWindow#initFonts()` **entered y sin excepción**.
  El anillo muestra el loop vivo: `RenderThread#flushInvokeQueue entered/returned`, `Display#isCloseRequested`,
  `DebugOptions#testThreadCrash` en cada iteración.
- **Stop actual**: `java.io.IOException: inflate error` (origen `java.util.zip.DataFormatException: Error occurred: -2`)
  **48 veces**, capturado por el juego por textura. Stack resuelto con `scripts/wasm_stack.py`:
  `TInflater::inflate` ← `zombie.core.textures.PNGDecoder::readChunkUnzip` ← `PNGDecoder::decode` ←
  `ImageData::<init>` ← `FileTask_LoadImageData::call` ← `FutureTask::run/get` ←
  `zombie.fileSystem.FileSystemImpl::updateAsyncTransactions` ← `GameWindow::initFonts`.
  Todas las fuentes `.fnt` ya parsean; las **páginas `.png` de las fuentes no decodifican**, por eso el canvas sigue negro.
- Captura `test-results/round40-final.png` (1280×720, 12 colores): 876.098 px negros de 921.600 (95 %).
  Analizada por histograma (PIL), `screenshot_visually_checked:false` — `view_image` está deshabilitado acá.
- **El menú principal real NO se ve. `MENU_REACHED` no existe y no se creó.**

## Cambios de esta Ronda 40 (sin commit)

1. `scripts/prepare_teavm.py` (fix #3 del `unreachable`): los accesos cualificados a `java.lang.Object::monitor`
   desde clases anidadas pasan por los helpers `monitorOf/monitorIdOr0/monitorCountOr0` de la clase externa.
   Build `build-round40c-game.log` SUCCESS y gate PASS. (Detaille en la sección "fix #3" más abajo.)
2. `tools/pack/alias_index.py` (nuevo): agrega entradas alias (`alias_of`) a un `index.json` de pack para que las
   rutas de fallback del juego (`media/fonts/<file>` y `media/<file>`) resuelvan al mismo offset del blob.
   Verifica sha256 contra el blob y el presupuesto de memoria montada.
3. `build/real-browser/startup.txt` (ruta ignorada por git, regenerable): la lista de recursos de arranque se
   **deriva del juego** en vez de adivinarse — las 29 entradas `fnt/img` de `media/fonts/EN/fonts.txt`, las cadenas
   de fallback reales de `TextManager.getFontFilePath` (constant pool: `media/fonts/{1}/{1}/{1}`,
   `media/fonts/{1}/{1}`, `media/fonts/EN/{1}/{1}`, `media/fonts/EN/{1}`, `media/fonts/{1}`, `media/{1}`), las
   `page id=… file="…"` de cada `.fnt`, y `media/fonts/zomboidNormal<N>.fnt` (N=11..25) que `TextManager.Init` pide
   por su cuenta. Resultado: **232 archivos, 19.023.519 B**, más 191 aliases ⇒ **423 entradas, 37.028.627 B montados**
   (blob único 19 MB). Regenerar:
   `python3 tools/pack/pack.py --pz-home $PZ_HOME --files build/real-browser/startup.txt --out build/real-browser/packs --max-bytes 32000000`
   y luego `python3 tools/pack/alias_index.py --index build/real-browser/packs/index.json --from-dir media/fonts/`
   (y `--from-dir media/fonts/EN/1x/`). El dev server sirve `build/real-browser/packs` porque `packs/` no existe; por eso
   **no hace falta rebuild ni reiniciar el server**: regenerar `index.json` y recargar la página.
4. Diagnósticos nuevos en `port/target/` (ruta ignorada): `diag91.mjs` (lee el DebugLog que PZ escribe en el VFS),
   `diag95/96/97/98.mjs` (instrumentan el VFS desde JS para ver qué rutas pide el juego y cuáles faltan),
   `diag100/101.mjs` (resumen por clase de excepción + offsets del primer stack), `diag102.mjs` (sonda final con captura).
5. `AGENT_LEARNINGS.md`: sección Ronda 40 con los errores de la ronda (regex con coma, `write_text` con ruta relativa,
   atribuir el `FileNotFoundException` a los aliases en vez de instrumentar el VFS, y los assets que el juego no tiene).

## Avance verificado de la excepción de `initFonts`

| Medición | Pack | Resultado |
|---|---|---|
| `diag91.log` | 133 archivos | `RuntimeException: java.io.FileNotFoundException` fatal en `GameWindow::initFonts` ← `TextManager::Init` ← `AngelCodeFont::<init>_0` ← `FileInputStream::<init>` |
| `diag92.log` | 133 (regenerado) | mismo stack |
| `diag93.log` | 133 + 36 aliases (1x) | `initFonts` ya parsea `.fnt`; aparecen `failed to load page 0 texture` |
| `diag94/95.log` | 202 archivos + aliases | sigue el `FileNotFoundException` (faltaban `media/fonts/zomboidNormal<N>.fnt`) |
| `diag99/100/101/102.log` | **232 archivos + 191 aliases** | **desaparece el `FileNotFoundException` y el hilo sobrevive**; quedan 48× `IOException: inflate error` |

## Fix #3 del `RuntimeError: unreachable` (lo que desbloqueó el boot real)

Causa raíz verificada (notas de Code, sección "Ronda 40" de `docs/notes-from-code.md`): `WasmGCClassGenerator.fillSimpleClassFields`
omite deliberadamente el campo sintético `monitor` de `java.lang.Object`, así que `getFieldIndex()` devuelve `-1` para **las demás**
clases y `WasmGCInstructionGenerationVisitor.loadNormalField/storeNormalField` emitían `unreachable` que truncaba el cuerpo. Los
dos caminos rotos, decodificados con `python3 /tmp/wdec2.py` sobre `boot.wasm`:
`java.lang.Object$NotifyListenerImpl::run` atrapaba justo después de `local.get 0; struct.get field=3 obj` (rel 219) y
`java.lang.Object$EnteringRequest::run` justo después de `struct.get field=3 object` (rel 86). Ningún acceso estaba en los métodos
propios de `Object`, donde sí aplica el intrinsic del generador.

Fix en `scripts/prepare_teavm.py`: se agregaron a `java.lang.Object` los helpers `monitorOf(TObject)`, `monitorIdOr0(TObject)` y
`monitorCountOr0(TObject)` (el acceso al `Monitor` queda **dentro** de `java.lang.Object`), y los dos puntos de acceso cualificados
desde las clases anidadas se reescribieron para usarlos (`reportMonitor(7, monitorIdOr0(obj), 0, (int) currentThread.getId(),
monitorCountOr0(obj), 0, 0)`). Único `unreachable` legítimo restante en el módulo: el brazo default del `br_table` de la máquina de
estados de corrutinas.

Evidencia del build (primer plano, ~7 min): `build-round40c-game.log` → `BUILD SUCCESS`, gate PASS, `fiberops: False` en
`java.lang.Thread::currentThread` y `::setCurrentThread`, y
`python3 tools/code-agent/probe8.py port/target/web/game/boot.wasm 'java.lang.Thread::<clinit>'` → **`no seed`** (verificado otra vez al cerrar la ronda).
Consecuencia medida en Chrome (`diag102.log`): `errs: []` (ningún `pageerror`), `phase:'starting'` con `error:''`, y el hilo de juego
vivo hasta 110 s; antes de este fix el boot moría con `main-returned` y `RuntimeError: unreachable`.

## Bloqueo actual (Ronda 40, nada simulado)

`java.util.zip.DataFormatException: Error occurred: -2` → `java.io.IOException: inflate error`, **48 veces** (una por página de
textura), capturadas por el juego y logueadas como `ExecutionException`. Stack resuelto con
`python3 scripts/wasm_stack.py port/target/web/game/boot.wasm <offset>` sobre los offsets que imprime el DebugLog de PZ:
`java.util.zip.TInflater::inflate` ← `zombie.core.textures.PNGDecoder::readChunkUnzip` ← `PNGDecoder::decode` ←
`ImageData::<init>` ← `FileTask_LoadImageData::call` ← `FutureTask::run/get` ←
`zombie.fileSystem.FileSystemImpl::updateAsyncTransactions` ← `GameWindow::initFonts`.
Es decir: **las fuentes `.fnt` parsean, las páginas `.png` no decodifican**. El canvas sigue negro por eso, no por GL.
Contadores medidos (`diag100.log`, DebugLog 206.898 B): `ExecutionException: inflate error` 48, `IOException: inflate error` 48,
`DataFormatException: Error occurred: -2` 48, y 1× `UnsupportedOperationException: Unsupported platform: UNSUPPORTED` (oshi, inofensivo).
Rutas afectadas vistas en el log: `/media/fonts/zomboid*`, `/media/zombiefon*`, `/media/fonts/cod*`, `/media/fonts/han*`,
`/media/fonts/sdf*`, `/media/fonts/Sdf*`. Además `AngelCodeFont failed to load page 0 texture` para
`/media/mainfont_0.png`, `/media/mainfont2_0.png` y `/media/fonts/zomboidDialogue.bmfc_0.png`, que **no existen en la copia
pinneada** (verificado con `find`): el juego sólo loguea y sigue — no hay que inventarlos.

Captura de esta ronda: `test-results/round40-final.png` (1280×720) — 876.098 px negros de 921.600 (**95,06 %**), 12 colores
únicos, dominados por `(0,0,0)`, `(11,17,22)` 36.000 y `(238,238,238)` 6.312. Analizada por histograma con PIL,
`screenshot_visually_checked:false` (no hay `view_image` acá). No hay menú ni texto legible.

## Siguientes pasos concretos, en orden (Ronda 41)

1. **Diagnóstico del `Inflater` de TeaVM 0.16.0** (sin rehacer lo ya verificado): el classlib real es `TInflater` sobre
   `com.jcraft.jzlib.Inflater`; `inflate(buf,off,nbytes)` lanza `"Error occurred: " + errCode` cuando `impl.inflate(0)` devuelve
   algo distinto de `Z_OK`/`Z_NEED_DICT`/`Z_STREAM_END`. Fuente: `.toolchain/downloads/teavm-classlib-0.16.0-sources.jar`, ya
   extraída en `/tmp/teavm_TInflater.java`. Revisar si la sustitución propia en `port/compat` rompe
   `setInput([BII)`, `needsInput()` (`inRead == inLength`) o `reset()`.
2. **Comparar con el `PNGDecoder` real de PZ** (bytecode ya extraído en `/tmp/pzdec2/png.txt`, líneas ~3198-3262):
   `readChunkUnzip` lanza `IOException("inflate error")` cuando `inflate()==0 && !finished() && !needsInput()`. Eso apunta a que
   el inflater devuelve error en el primer `inflate` con entrada mal entregada, no a datos corruptos.
3. **Prueba propia JVM/Node sobre PNGs reales del pack** (extraídos de `build/real-browser/packs`, nunca bytes inventados):
   inflar el chunk `IDAT` de una página `.fnt` con `java.util.zip.Inflater` de JDK y con la ruta del port, y comparar. Arreglar en
   `port/compat`/`port/shim` **sin stub**.
4. Si las fuentes decodifican: correr `port/target/diag89.mjs` / `diag90.mjs` buscando `MainScreenState#enter():entered`,
   `swap>2`, `glDraw>0`, `px.uniq>1`. Recién entonces actualizar `tests/browser/game-main.spec.mjs` (líneas ~25, 37 y 78-79 exigen
   hoy `menuRendered:false` y `phase:'starting'`) **sin relajar** los asserts de Bullet/GL/ventana/ícono/JAXB, y correr
   `npx playwright test --config playwright.game.config.mjs` con `PZ_REAL_TEST_PORT=8093`.
5. Nativas ausentes (`checkRequiredLibraries`: SteamUtils, RakNet, LightingJNI, ZombiePopulationManager, PZSQLUtils, Clipper,
   PathfindNative, WorldMapJNI) deben seguir lanzando `UnsatisfiedLinkError` con su nombre JNI — nunca stub.

## Pruebas de esta ronda (con `source scripts/env.sh`, `PZ_REAL_TEST_PORT=8093`)

- `npm test`: **19 pass / 0 fail** (dev server: byte ranges, suffixes, HEAD, ETag/If-Range, montaje read-only, symlinks, CORS/Host).
- `python3 -m unittest discover -s tests/python`: **10 OK** (`GL signatures: 5, verified scalar mappings: 4`; Lua exposure 2/2/1/1).
- `python3 tools/code-agent/probe8.py port/target/web/game/boot.wasm 'java.lang.Thread::<clinit>'` → `no seed` (re-verificado al cerrar).
- Chrome: `port/target/diag102.mjs` 110 s sobre el dev server **8092** → `errs: []`, `phase:'starting'`, loop vivo, captura negra 95 %.
- **No corrió** la suite Playwright del juego (`playwright.game.config.mjs`): sigue esperando el stop anterior y el stop cambió;
  cambiar los asserts sin menú sería relaxar la prueba. Tampoco hubo build nuevo (el artefacto analizado es `round40c`).

## Notas para la próxima ronda

- `menuRendered` está **hardcodeado `false`** en `port/src/main/java/port/GameBoot.java:28` (`@JSBody` de `state(...)`). No crear
  `MENU_REACHED`: no existe y no se creó. Sólo cuando el menú real se vea + captura + Playwright verde.
- El dev server **8092** sigue arriba sirviendo `port/target/`; sirve `build/real-browser/packs` porque `packs/` no existe. Regenerar
  `index.json` y recargar la página basta — no hace falta rebuild ni reiniciar el server.
- Build obligatorio con `-Xss16m`: `source scripts/env.sh; export PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid;
  export MAVEN_OPTS="-Xmx8g -Xss16m"; bash scripts/build.sh --with-game` — **un solo build en primer plano** (`nohup &` muere al
  cerrarse la sesión). No tocar Strata ni 8083. Servidor sólo `127.0.0.1`. Sin `rg` ni `wasm-objdump` (usar `grep`/Python).

## Ronda 42 (cerrada 05:25) — inicio 2026-10-29 01:40:52, corte 05:40

- 01:53 **causa raíz del `inflate error` verificada con repro JVM propio** (`/tmp/ztest`):
  `PNGDecoder.readChunkUnzip` llama `inflate()` **antes** del primer `setInput()`.
  El `TInflater` de TeaVM 0.16.0 mapea cualquier código fuera de `{Z_OK, Z_NEED_DICT, Z_STREAM_END}`
  a `DataFormatException("Error occurred: " + errCode)`; jzlib 1.1.3 devuelve `Z_STREAM_ERROR (-2)`
  porque `z.next_in` sigue `null`. El JDK en cambio devuelve `0` con `needsInput()==true` (medido:
  JDK → `no-input inflate -> 0 needsInput=true`; classlib sin parchear → `TDataFormatException: Error occurred: -2`).
- Fix: `scripts/prepare_teavm.py` → `patch_inflater()`, que genera
  `port/target/generated-sources/teavm/org/teavm/classlib/java/util/zip/TInflater.java` (ruta ignorada).
  En el brazo `default` devuelve `0` solo si `errCode` es `Z_STREAM_ERROR`/`Z_BUF_ERROR` **y**
  `impl.next_in == null || impl.avail_in == 0`; el `Z_DATA_ERROR (-3)` real sigue lanzando.
- Prueba JVM del parche sobre **109 PNG únicos reales** extraídos de `build/real-browser/packs`:
  `OK=109 FAIL=0` (salida idéntica byte a byte al `java.util.zip.Inflater` del JDK),
  `BAD-DATA throws DataFormatException ... Error occurred: -3`, y `reset()` reutiliza el inflater.
- 02:00 build **`build-round42-game.log`**: `BUILD SUCCESS` (6:06 min, peak RSS 6.58 GiB),
  `wasm_bytes:143128542`, gate PASS (`fiberops: False` en `Thread::currentThread/setCurrentThread`),
  `probe8 'java.lang.Thread::<clinit>'` → **`no seed`**.
- 02:03 Chrome (`diag104.mjs`, dev server 8092, 76 s): **desaparecen las 48 `inflate error`** y los
  `AngelCodeFont failed to load page 0 texture` (`fontFails: 0`). DebugLog pasa de 206.898 B a 4.344 B
  y el único error restante es `UnsupportedOperationException: Unsupported platform: UNSUPPORTED` (oshi, 1×, inofensivo).
  `errs: []` (ningún `pageerror`). El fix del inflater está confirmado en Wasm, no solo en JVM.
- 03:15 **causa raíz #2 verificada y corregida (texturas GL11)**. `TextureID.generateHwId` llama
  `GL11.glGenTextures()I` → native `GL11C.nglGenTextures(IJ)V`, que `tools/glgen/generate.py` no mapeaba,
  así que el fail-fast lanzaba `UnsupportedOperationException: Java_org_lwjgl_opengl_GL11C_nglGenTextures__IJ`
  dentro de `RenderContextQueueItem.invoke()` (48 veces en el DebugLog, 3 fuentes sin página 0). Como
  `generateHwId` abortaba antes de `ImageData.dispose()`, `DirectBufferAllocator` quedaba > 50 MB y
  `TextureIDAssetManager.waitFileTask` dormía para siempre → `initFonts` no volvía → canvas negro 95 %.
  Fix real (sin stubs): puente de texturas en `port/runtime/gl.mjs` (`genTextures/deleteTextures/isTexture/
  bindTexture/texParameteri/texImage2D/texSubImage2D/getTexImage/texLevelBytes` + `PORT_PROVIDED`),
  wrappers con descriptores exactos en `port/src/main/java/port/shim/GL11.java` (bytes por el arena
  como GL15), y las 8 entradas `(name,descriptor)` en `TEXTURE_DIRECT` de `tools/glgen/generate.py`
  (mapean GL11 **y** GL11C; 143 líneas en `gl-overrides.tsv`, antes 135).
  Tokens verificados con `javap -constants` sobre el jar pinneado y con los constantes WebGL2 leídos en
  Chrome (`port/target/glc.mjs`): filtro/wrap idénticos; `GL_RGBA+GL_UNSIGNED_BYTE→RGBA8`,
  `GL_DEPTH_COMPONENT+GL_UNSIGNED_SHORT→DEPTH_COMPONENT16`; `GL_TEXTURE_1D/PROXY/LUMINANCE/ALPHA8`
  lanzan con su identidad (WebGL2 no los tiene); `glGetTexImage` sólo RGBA/UNSIGNED_BYTE vía FBO+readPixels.
- 03:24 build **`build-round42c-game.log`** (con el puente de texturas): `BUILD SUCCESS` (9:20 min, peak RSS 6.59 GiB,
  94946 métodos), `wasm_bytes:143139239`, gate PASS (`fiberops: False`), `probe8 'java.lang.Thread::<clinit>'` → `no seed`.
  `scripts/build.sh` copió `port/runtime/gl.mjs` (56.617 B) a `port/target/web/runtime/`; dev server **8092** responde 200
  (no hace falta reiniciar). Nuevo diag combinado `port/target/diag113.mjs` (contadores GL + fontFails + DebugLog + captura).
- 04:05 **nuevo bloqueo verificado con `diag113.log`**: el puente de texturas funciona (0 raises de GL,
  `createTexture/texImage2D/texParameteri` 66/66/264 llamadas, `GameWindow#initFonts():returned`, 66 texturas subidas),
  pero el DebugLog (13.990 B) muestra dos finales: (a) `MainThread` muere en `LightingJNI.init` porque
  `System.loadLibrary("Lighting64")` lanza `UnsatisfiedLinkError`, el catch del juego hace `Thread.sleep(3000)` y
  `System.exit(1)` → nuestro `JdkSupport.exit` lanza `UnsupportedOperationException` (honesto: una pestaña no es un proceso)
  → `GameThread exited..`; (b) el `RenderThread` (que sí estaba en `MainScreenState#main`) aborta en
  `RenderThread#releaseContextReentrant → Display.releaseContext → GLFW.glfwMakeContextCurrent(0)` con
  `IllegalArgumentException: Invalid browser window handle: 0` (bytecode verificado: `Display.releaseContext` pasa `lconst_0`).
- 04:06 Fix (b) real: `port/runtime/window.mjs` ahora modela el contexto actual de GLFW (`contextCurrent`,
  `detachContext()`, `getCurrentContext()` devuelve 0 cuando está despegado) y `port/src/main/java/port/shim/BrowserWindow.java`
  acepta handle 0 como detach en `glfwMakeContextCurrent`. `node --check` OK; `npm test` **25 pass / 0 fail**.
- 04:14 build **`build-round42d-game.log`** (con el contexto actual de GLFW): `BUILD SUCCESS` (6:48 min),
  `wasm_bytes:143139425`, gate PASS (`fiberops: False`), `probe8 'java.lang.Thread::<clinit>'` → `no seed`.
- 04:22 Chrome (`diag113.mjs`, 150 s, dev server 8092): **desaparece `Invalid browser window handle: 0`**.
  `RenderThread#releaseContextReentrant/acquireContextReentrant` ahora entran y vuelven (0 raises en el
  wrapper GL, `glThrown: []`), `fontFails: 3` (las 3 páginas que no existen en la copia), DebugLog 13.741 B,
  `errs: []`, `phase:'main-returned'`. Bloqueo restante: `MainThread` muere en `LightingJNI.init`
  (`loadLibrary("Lighting64")` → `UnsatisfiedLinkError` → `Thread.sleep(3000)` → `System.exit(1)` → nuestro
  `JdkSupport.exit` lanza honestamente) y aparece además un `System.exit(0)`; por eso `swapCount` sigue nulo
  y la captura `test-results/round42-textures.png` sigue en 95 % negro.
- 04:52 **decisión + fix del bloqueo `LightingJNI.init` (aún sin build)**: `LightingJNI.init()` (bytecode `/tmp/lighting.txt` 70-135) hace
  `System.loadLibrary("Lighting64")`; el catch de `UnsatisfiedLinkError` loguea, `Thread.sleep(3000)` y `System.exit(1)`. Sin librería el
  MainThread muere ahí y el `RenderThread` termina el loop con `System.exit(0)`, por eso `swapCount` seguía en 2 y la captura en 95 % negro.
  Camino honesto elegido (no stub): módulo propio `port/src/main/java/port/natives/Lighting64.java` que **implementa de verdad** los entradas
  que el boot toca antes de existir un mundo — `configure(F)V` (valida cell size > 0), `WaitingForMain()Z` (nuestro pass es síncrono ⇒ `false`),
  `DoLightingUpdateNew(JZ)V` (no hay nada registrado ⇒ sin trabajo; si el juego abrió updates por chunk lanza con su identidad) y `destroy()V` —
  y registra el nombre `Lighting64` en `pzRuntime.loadedLibraries`, que es lo que `JdkSupport.loadLibrary` exige. Las otras 46 natives de la clase
  conservan el fail-fast con su nombre JNI (`FailFastNatives`), así una sesión in-world falla en vez de iluminar mal.
  Nuevo transformer `port/compiler/LightingNativeCompatibility.java` (mismo patrón que `BulletNativeCompatibility`: reenvía sólo natives inventariadas
  de `zombie.iso.LightingJNI`, 4 forwards) registrado en `BuildCompiler` y `port.GameBoot.main` llama `Lighting64.install()`.
  `mvn -f port/pom.xml compile` OK (1 error intermedio: `org.teavm.model.MethodNode` no existe → `MethodHolder`, anotado en AGENT_LEARNINGS).
- 05:09 build **`build-round42g-game.log`** lanzado con los 4 forwards corregidos. El intento anterior
  `build-round42f-game.log` **falló** (`BUILD FAILURE`) por mi descriptor: `MethodDescriptor("configure",
  ValueType.VOID)` significa `()V` (los primeros N-1 son parámetros), y TeaVM reportó
  `ERROR: zombie.iso.LightingJNI.configure(F)V: Method port.natives.Lighting64.configure()V was not found`
  y lo mismo para `DoLightingUpdateNew(JZ)V`. Corregido a `(FLOAT,VOID)` y `(LONG,BOOLEAN,VOID)`;
  `mvn -f port/pom.xml compile` vuelve a pasar. Causa anotada en `AGENT_LEARNINGS.md`.
- 05:15 build **`build-round42g-game.log`** (con el módulo Lighting64 + los 4 forwards): `BUILD SUCCESS` (6:04 min,
  peak RSS 6.58 GiB, 94958 métodos), `wasm_bytes:143142946`, gate PASS (`fiberops: False`),
  `probe8 'java.lang.Thread::<clinit>'` → `no seed`. `scripts/build.sh` dejó `Lighting64` compilado en el módulo
  y copió `port/runtime/window.mjs` (25.039 B) a `port/target/web/runtime/`.
- 05:20 Chrome (`port/target/diag115.mjs`, 110 s, dev server 8092, log `diag116.log`, `errs: []`):
  **desaparece el `System.exit(1)` de LightingJNI** — `exits` tiene **un solo** elemento, `{status:0, swapCount:2,
  windowCreated:false, contextCurrent:false}`; `loadedLibraries:["PZBullet64","PZBullet","Lighting64"]` y
  `missingLibraries:["RakNet64","PZPopMan64"]`. O sea: el MainThread sobrevive a `LightingJNI.init` y ahora muere
  en el paso siguiente, `ZombiePopulationManager.init → loadLibrary("PZPopMan64")` (bytecode `/tmp/zombie.popman…txt`:
  ese `init` **no** tiene try/catch ni `exit`, así que lanza `UnsatisfiedLinkError` dentro de `GameWindow.init`,
  que sí lo captura y lo reenvuelve en `RuntimeException`). `swapCount` sigue en 2 y `glTop: []` en esta sonda:
  ningún frame del menú todavía.

## Ronda 43 (05:26 inicio) — avances incrementales

- 05:26 Inicio. Bloqueo heredado de la ronda 42: `ZombiePopulationManager.init → loadLibrary("PZPopMan64")`
  mata el MainThread; después están `PZSQLUtils.init` (Class.forName org.sqlite.JDBC → `System.exit(1)`),
  `Clipper.init` (`PZClipper64` + `n_init()V`), `PathfindNative.init` (`PZPathFind64`), `WorldMapJNI.init` (sin natives).
- Verificado con `javap` sobre la copia fijada: `GameWindow.init()` llama en orden
  `initGlobalShader → invokeOnRenderContext → initFonts → checkRequiredLibraries → SteamUtils/ServerBrowser/SteamFriends/SteamWorkshop
  → resetModFolders → RakNetPeerInterface.init → LightingJNI.init → ZombiePopulationManager.init → PZSQLUtils.init
  → Clipper.init → PathfindNative.init → WorldMapJNI.init`. Las natives de población sólo se llaman con un
  `IsoWorld` (IngameState/IsoWorld/IsoChunk/...), no en el boot.
- Cambios (compilan): **nuevos** `port/natives/NativeRegistry.java`, `port/natives/PopMan64.java`,
  `port/natives/Clipper2.java`, `port/natives/PathFind64.java`, `port/compiler/NativeModuleCompatibility.java`,
  `port/runtime/jdbc/SqliteBootstrap.java`, `port/compiler/SqliteCompatibility.java`;
  `port/GameBoot.java` instala los 3 módulos nuevos; `port/compiler/BuildCompiler.java` registra los 2 transformers nuevos.
- `mvn -f port/pom.xml compile` **BUILD SUCCESS** (dos errores intermedios anotados en AGENT_LEARNINGS: paréntesis
  que faltaba en `Map.ofEntries` y un tipo duplicado al intentar un `Driver` propio).
- Build `build-round43-game2.log` **SUCCESS** (06:13): `wasm_bytes` 143.111.836, gate `fiberops: False`,
  `probe8 'java.lang.Thread::<clinit>'` ⇒ `no seed`.
- Chrome (`diag119/120/121`, dev 8092): **desaparecieron los dos bloqueos de la ronda 42**. `loadedLibraries`
  ahora es `["PZBullet64","PZBullet","Lighting64","PZPopMan64","PZClipper64","PZPathFind64"]`, `missingLibraries`
  sólo `["RakNet64"]`, y `pzRuntime.sqlitedb = {version:"3.54.1", initCalls:1}` ⇒ `PZSQLUtils.init` ya no hace
  `System.exit(1)`.
- Bloqueo nuevo encontrado con el stack resuelto (`scripts/wasm_stack.py`): `zombie.GameWindow::init →
  zombie.vehicles.Clipper::n_init → port.natives.Clipper2::init` lanzaba
  `TypeError: Cannot read properties of undefined (reading 'init')`. Causa: `NativeRegistry.register` guardaba
  `r.natives[library] = true` (booleano) y `publish` hacía `s.calls[counter]` sobre ese booleano. Arreglado
  (registro y publish ahora garantizan un objeto `{stage,calls}`; mismo arreglo en `Lighting64`).

## Cierre de la Ronda 42 (05:25)

- **Estado**: el menú principal real de PZ **no se ve** en Chrome. `MENU_REACHED` no existe y no se creó.
- **Archivos cambiados en la ronda** (sin commit, `.git` intocado): `port/runtime/window.mjs` (contexto actual
  GLFW: `contextCurrent`, `detachContext()`, `getCurrentContext()`), `port/shim/BrowserWindow.java`
  (`glfwMakeContextCurrent(0)` = detach), `port/runtime/JdkSupport.java` (`recordMissingLibrary`/`recordExit`),
  **nuevos** `port/natives/Lighting64.java` y `port/compiler/LightingNativeCompatibility.java`,
  `port/GameBoot.java` (`Lighting64.install()`), `port/compiler/BuildCompiler.java` (registra el transformer),
  `tests/browser/window.spec.mjs` (test de attach/detach), `port/target/diag113/114/115.mjs` (ignorados),
  `AGENT_LEARNINGS.md` (4 errores nuevos), `STATUS.md`.
- **Hasta dónde llega hoy el arranque**: `GameWindow.init()` avanza `initGlobalShader → invokeOnRenderContext →
  initFonts (returned, 66 texturas subidas vía el puente GL11) → checkRequiredLibraries → SteamUtils/ServerBrowser/
  SteamFriends/SteamWorkshop.init → resetModFolders → RakNetPeerInterface.init` (falla `RakNet64`, el juego lo
  loguea y sigue) → **`LightingJNI.init` ahora tiene su librería** → `ZombiePopulationManager.init` lanza
  `UnsatisfiedLinkError: System.loadLibrary: browser implementation is missing for PZPopMan64`, que
  `GameWindow.mainThreadInit` reenvuelve en `RuntimeException` → `GameThread exited..` → el `RenderThread` cierra
  el loop y hace `System.exit(0)` (registrado: `{status:0, swapCount:2}`).
- **Bloqueo actual**: nativas de `ZombiePopulationManager` (`PZPopMan64`, 28 natives) sin implementación. Después
  de ese punto `GameWindow.init` sigue con `PZSQLUtils.init` (que necesita `org.sqlite.JDBC` en el classpath y
  también hace `System.exit(1)` si `Class.forName` falla), `Clipper.init` (`PZClipper64`, 13), `PathfindNative.init`
  (`PZPathFind64`, 11) y `WorldMapJNI.init` (sin natives).
- **Siguientes pasos, en orden**:
  1. Módulo `port/natives/PopMan64.java` + transformer, mismo patrón que Lighting64: implementar las entradas que el
     boot toca antes del mundo (el `init` del juego sólo hace `loadLibrary` y registra un `PredicatedFileWatcher`;
     las natives de población se llaman con un `IsoWorld` existente ⇒ deben conservar el fail-fast con su nombre JNI).
     Verificar con `javap` cuáles se llaman desde `GameWindow.init`/`mainThreadInit` y no inventar semántica.
  2. `PZSQLUtils.init`: decide el camino honesto — el jar pinneado trae `org/sqlite/*.class` en
     `projectzomboid.jar` pero TeaVM rechaza `org.sqlite.JDBC implements java.sql.Driver` (missing in classpath;
     ver `port/target/reports/task4-baseline/missing-apis.md`). Nuestrapuente JDBC está en `port/runtime/jdbc`
     (`BrowserConnection`/`Sqlite`) y sirve SQLite Wasm 3.54.1: hace falta un transformer que reenvíe
     `PZSQLUtils.getConnection` a ese puente y un `Class.forName("org.sqlite.JDBC")` que resuelva, o registrar una
     sustitución propia. **No** dejar que `System.exit(1)` vuelva a matar el MainThread.
  3. Repetir con `Clipper` (`clipper2-java`, Boost según `docs/ROADMAP-source.txt`) y `PathfindNative`
     (`PZPathFind64`) cuando el menú ya dependa de ellas.
  4. Re-correr `diag113.mjs`/`diag115.mjs` y recién ahí una captura con histograma muy por debajo de 95 % negro;
     sólo con menú visible + captura + prueba Playwright verde crear `MENU_REACHED`
     (`tests/browser/game-main.spec.mjs` con `playwright.game.config.mjs`, `PZ_REAL_TEST_PORT=8093`).
- 05:26 verificación fina (`port/target/diag117.mjs` → `diag117.log`, y `diag113.mjs` re-corrido → `diag118.log`,
  150 s, `errs: []`): el DebugLog pasa de 13.741 B a **11.267 B** y el histograma de excepciones es ahora
  `{oshi "Unsupported platform: UNSUPPORTED":1, "missing for RakNet64":1, "thrown by thread MainThread..":1,
  "missing for PZPopMan64":1, "thrown by thread MainThread.":1, "System.exit(0): a browser tab is not an OS process":1}`
  — **ningún `System.exit(1)`** y ninguna textura GL que lance (`glThrown: []`). El `RenderThread` trabajó más que
  nunca: `glTotal 801` con `createBuffer 256`, `clearColor 3`, `clear 2`, `createProgram/useProgram/deleteProgram 2`,
  `blendFunc 1` (shader global + borrado de pantalla), `SpriteRenderer#waitForReadyState` 1484 veces y
  `RenderThread#acquireContextReentrant` 4. `pzRuntime.natives = {Lighting64:true}`. `swapCount` sigue en 2:
  el loop muere antes del primer `glfwSwapBuffers` del menú por el `UnsatisfiedLinkError` de `PZPopMan64`.
- **Pruebas de esta ronda**: build JVM `mvn package` 45 pass; `npm test` **25 pass / 0 fail**;
  `npx playwright test --config playwright.config.mjs` **18 pass**; `python3 -m unittest discover -s tests/python` (con `scripts/env.sh`) **OK**; suite del juego `playwright.game.config.mjs` **no corrida**
  (sigue asegurando `phase:'starting'`/`menuRendered:false`, no relajar).
- **Avisar al dueño** (no tocamos `.git`): el commit `4865c76` de la ronda 41 incluyó por error el directorio
  `-jnounc=0`, creado por un typo de extracción de jzlib (tercero Apache-2.0, no código del juego). Conviene
  eliminarlo del árbol y del índice en la próxima revisión del vigilante.
- 06:50 (ronda 43) Dos fixes nuevos, `mvn -q -f port/pom.xml compile` OK:
  1. `port/web/game.mjs` monta `/stdlib.lua` y `/stdlib.lbc` en la VFS antes de arrancar wasm, fetch
     del recurso del classpath que el dev server ya sirve desde `PZ_HOME` (`/classpath/resource/0/...`,
     sha256 `583c2949…31eb` para stdlib.lua). Causa del `FileNotFoundException` de
     `KahluaUtil.setupLibraryText` (`new File("stdlib.lua").getAbsoluteFile()` ⇒ raíz VFS). Nada se copia al repo.
  2. `port/shim/GL15.java`: `nglMapBuffer`/`glUnmapBuffer` implementados de verdad — mapa = bloque real
     del arena rellenado con `getBufferSubData` y devuelto con `bufferSubData` en el unmap; la dirección
     se conserva mientras está mapeado (LWJGL `apiGetMappedBuffer` compara `memAddress`+capacity), y el
     tamaño sale de `BUFFER_SIZE` (0x8764). Antes lanzaba `UnsupportedOperationException` y mató el
     RenderThread en `RingBuffer.next → GLVertexBufferObject.map`.
- 07:35 (ronda 43) Corregido el orden de parámetros de las natives de buffers, verificado contra el bytecode
  pinneado (`GL15C.glBufferData(int,long,int)` ⇒ `nglBufferData(target, size, 0L, usage)`;
  `glBufferData(int,ByteBuffer,int)` ⇒ `(target, bb.remaining(), memAddress(bb), usage)`; igual
  `glBufferSubData`/`glGetBufferSubData` ⇒ `(target, offset, remaining, address)`). Antes el shim leía
  `bytes=0` y el `address` como tamaño ⇒ `gl.bufferData(target, Uint8Array(0))` ⇒ `BUFFER_SIZE 0` ⇒
  `nglMapBuffer` devolvía 0 ⇒ `OpenGLException: Failed to map a buffer 65536 bytes long` en
  `SpriteRenderer$RingBuffer.next → GLVertexBufferObject.map()`. Descriptores `(IJJI)V`/`(IJJJ)V` intactos,
  así que `gl-overrides.tsv` no cambia. `mvn -q -f port/pom.xml compile` OK.
- 07:40 (ronda 43) `JdkTask4Support.getGenericSuperclass` deja de lanzar `UnsupportedOperationException`:
  devuelve `cls.getSuperclass()` (TeaVM 0.16.0 no conserva la firma genérica del superclase — verificado:
  ningún `.class` de `teavm-classlib-0.16.0.jar` contiene "genericSuperclass"; sólo hay `getGenericType`,
  `getGenericReturnType`, `getGenericParameterTypes`). `exposeLikeJavaByClass` de Kahlua usa ese Type sólo
  para recursar, así que el raw superclass es la respuesta fiel (null para Object/interfaces/primitivos).
  `getGenericExceptionTypes` ahora devuelve `method.getExceptionTypes()` (las excepciones checked no llevan
  argumentos genéricos). Con esto el MainThread no muere en `exposeAll` al exponer `java.io.BufferedReader`.

## Ronda 44 (07:44 inicio) — avances incrementales

- 07:44 Inicio. Heredado de la ronda 43 (`diag129.log`, build `build-round43-game5.log`): el MainThread muere en
  `LuaJavaClassExposer.exposeLikeJava` con `UnsupportedOperationException: Class.isAnonymousClass: inner-class
  metadata was not retained by this AOT build: java.io.BufferedReader`; el RenderThread lanza 2×
  `Error: Failed to execute 'getBufferSubData' on 'WebGL2RenderingContext': Cannot convert a BigInt value to a number`
  en `SpriteRenderer$RingBuffer.next → GLVertexBufferObject.map`. `swapCount` 2, canvas negro 95 %.
- 07:50 Fuente verificada del primer bloqueo: `grep -rl isAnonymousClass` sobre el jar pinneado (extraído a
  `/tmp/anonscan2`) da **un solo** consumidor: `se.krka.kahlua.integration.expose.LuaJavaClassExposer`
  (`exposeLikeJava`, offset 48, usado sólo para saltear `exposeStatics` en clases anónimas).
  Fix en `port/runtime/JdkTask4Support.isAnonymousClass`: nombre `"<enclosing>$<n>"` con segmento final
  puramente numérico ⇒ true; clases miembro/locale conservan simple name ⇒ false. Ya no lanza.
- 07:52 BigInt→number en WebGL2 (mismo patrón tres veces): `GL15.bufferSubNative`, `GL15.getSubNative` y
  `GL20.attribPointerNative` pasan de parámetro `long` a `int` (`offsetBytes`/`checked` verifican el rango).
  Los descriptores de las natives `(IJJJ)V`/`(IJJI)V` no cambian, así que `gl-overrides.tsv` sigue igual.
- 07:53 `mvn -B -Dmaven.repo.local=$PZ_WEB_ROOT/.toolchain/m2 -f port/pom.xml compile` **OK** (nota: `mvn -f port/pom.xml
  compile` sin `-Dmaven.repo.local` falla con `Could not create local repository at /home/agente47/.m2/repository`).
- ~08:20 (ronda 44) Fix de `java.util.Currency` en Wasm: transformer nuevo `port/compiler/CurrencyCompatibility.java`
  reescribe la única llamada `Currency.getInstance(Locale)` dentro de `java.text.NumberFormat::<init>()V`
  (TeaVM la usa con su CLDR horneada: "en" resuelve Chipre y CYP no está en su `iso4217.bin` ⇒ throw en
  `FluidContainer::<clinit>`). El destino es `port.runtime.JdkSupport.currencyForLocale(Locale)`, que reproduce
  la respuesta real del JDK 25 medida en `/tmp/CurrCheck.java` (`DecimalFormatSymbols.getInstance(Locale.ENGLISH)`
  ⇒ moneda `XXX`, sin excepción) y registra cada fallback en `pzRuntime.currencyFallbacks` en vez de ocultarlo.
  `mvn -q -f port/pom.xml compile` OK.
- ~08:40 (ronda 44) `glAlphaFunc(IF)V` puente completo: el shim `GL11.glAlphaFunc` → `gl.mjs alphaTest(func,ref)`
  (func validado 512..519 según `javap -constants`, ref clamado [0,1]); `glEnable/glDisable(GL_ALPHA_TEST=0x0bc0)`
  ahora **aplica** el estado además de registrarlo; `useProgram` sube `pzAlphaTestFunc`/`pzAlphaTestRef` al programa
  actual cuando el fragmento recibió la inyección `pzAlphaTestDiscard()` de `glsl.mjs`. Los programas sin inyección
  se cuentan en `capabilityState().alphaTest.gaps` (nunca se ignora en silencio). Campos renombrados
  `alphaTestState`/`activeUnit` porque chocan con los métodos homónimos de la clase.
- ~09:00 (ronda 44) Natives de estado ausentes mapeadas de verdad (no registradas): `GL11.glColorMask(ZZZZ)`,
  `glStencilFunc(III)`, `glStencilOp(III)`, `glDepthRange(DD)`, `glClearDepth(D)`, `GL13C.glActiveTexture(I)`,
  `GL14C.glBlendEquation(I)` (WebGL2: `colorMask/stencilFunc/stencilOp/depthRange/clearDepthf/blendEquation`,
  tokens validados contra las constantes reales del navegador; un token desconocido lanza con su identidad).
  `glActiveTexture` trae unidades de textura reales (`textureUnits[]` por unidad + límite
  `MAX_COMBINED_TEXTURE_IMAGE_UNITS`), así `glBindTexture`/`glTexImage2D`/`glGetTexImage` actúan sobre la unidad activa.
  Nuevo `port/shim/GL30.java` para 16 natives de `GL30C` (VAO `nglGenVertexArrays/nglDeleteVertexArrays/glIsVertexArray/glBindVertexArray`,
  FBO `nglGenFramebuffers/nglDeleteFramebuffers/glIsFramebuffer/glBindFramebuffer/glCheckFramebufferStatus/glFramebufferTexture2D/glFramebufferRenderbuffer`,
  RBO `nglGenRenderbuffers/nglDeleteRenderbuffers/glIsRenderbuffer/glBindRenderbuffer/glRenderbufferStorage`) sobre objetos WebGL2 reales.
  `tools/glgen/generate.py`: +34 líneas de `gl-overrides.tsv` (177 mappings, antes 146). Las desktop-only
  (`glBlitFramebuffer`, `glFramebufferTexture1D/3D`, `glRenderbufferStorageMultisample`, `*Parameteriv`) siguen sin mapear y lanzan.
  Pruebas: `npm test` **30 pass / 0 fail** (nuevo `tests/node/gl-state.test.mjs` con 5 casos de estado/alpha test/FBO).
- ~09:30 (ronda 44) `glPushAttrib/glPopAttrib/glPushClientAttrib/glPopClientAttrib` implementados de verdad.
  WebGL2 no tiene pila de atributos, así que `port/runtime/gl.mjs` mantiene la suya: `call()` registra el
  último valor de cada estado que este puente restaura (`recordAttrib`), `pushAttrib(mask)` snapshot de las
  claves cubiertas por el mask y `popAttrib()` las reenvía al contexto. Bits cuyo estado desktop no existe
  acá (`POINT/CURRENT/POLYGON_STIPPLE/LIGHTING/FOG/ACCUM_BUFFER/TRANSFORM/HINT/EVAL/LIST/TEXTURE`) se cuentan
  en `attribGaps` y se exponen en `attribState()`, nunca se ignoran en silencio. `ENABLE` snapshot de
  `capabilities/fixedFunctionCaps/alphaTestState` + scissor, `PIXEL_MODE` de `pixelStorei`, `TEXTURE` de las
  unidades activas, `VIEWPORT/SCISSOR` de sus cajas. Pila llena (64) y `popAttrib` con pila vacía lanzan.
  Máscaras verificadas en bytecode del jar: `GL_VIEWPORT_BIT` 2048 (9 sitios) y 24576 = ENABLE|COLOR_BUFFER
  (1 sitio, `zombie.core.Core`); valores de bits con `javap -constants` (GL_TEXTURE_BIT 262144, GL_SCISSOR_BIT
  524288, GL_ALL_ATTRIB_BITS 1048575 — la tabla anterior estaba corrida 1 bit, corregido).
  `GL_ATTRIB_STACK_DEPTH/GL_CLIENT_ATTRIB_STACK_DEPTH/GL_MAX_(CLIENT_)ATTRIB_STACK_DEPTH` responden desde
  esta pila. `port/shim/GL11.java` +4 wrappers/JSBodies, `tools/glgen/generate.py` +4 entradas
  (`gl-overrides.tsv` 177→181 mappings). 5 tests nuevos en `tests/node/gl-state.test.mjs`: **npm test 35 pass / 0 fail**.
