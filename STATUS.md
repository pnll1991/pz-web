# Estado del port personal

Actualizado: **2026-10-29 01:30 (-03), Ronda 40** (inicio 21:45:04 según `supervisor.log`, corte duro 01:45).
Repositorio `/home/agente47/Projects/pz-web`. Esta ronda **no hizo commits y no tocó `.git`**.
Artefacto analizado: build `build-round40c-game.log` (SUCCESS, gate PASS, `probe8 'java.lang.Thread::<clinit>'` ⇒ `no seed`),
`port/target/web/game/boot.wasm` de 143.128.474 B del 2026-10-29 00:04. Dev server **8092** (no reiniciado) sirviendo `port/target/`.

## Resumen del estado real hoy

- **El boot llega al juego y el hilo principal sigue vivo.** Ya no hay `pageerror`, ni `RuntimeError: unreachable`,
  ni excepción fatal: `phase:'starting'` (antes `main-returned`, hilo de juego muerto).
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
