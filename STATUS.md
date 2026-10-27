# Estado del port personal

Actualizado: **2026-10-27 12:12 (-03), ronda 21**, Linux x86_64. Repositorio real `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad). HEAD del vigilante al empezar la ronda: **f68aef9** ("Ronda 20"). Esta ronda **no hizo commits** (regla: el vigilante commitea al cerrar la ronda) y **no tocó `.git`**.

La ronda 20 quedó sin build ni prueba (corte de 4 h). Esta ronda completó ese trabajo: el build Wasm del main real **compila** y el arranque en Chrome **avanza más allá del icono**. El stop real de hoy está verificado con Playwright y captura.

## Cambios de esta ronda (sin commit)

Objetivo: eliminar el stop de `Display.create` en `loadIcons` (lectura del ícono vía libSTB nativo) y subir el ícono real al DOM del navegador.

Archivos nuevos:
- `port/src/main/java/port/shim/BrowserStb.java` — puente STB en el navegador:
  - `stbiLoad(...)`: lee el archivo desde el filesystem persistente del navegador (`readOptional` devuelve `Uint8Array`; se copia a `byte[]` con `getLength()/get(i)`), decodifica **PNG propio** (colortype 2/6, bit depth 8, interlace 0) con `java.util.zip.Inflater` (existe en TeaVM: el classlib tiene `TInflater`), aplica el flip vertical y entrega un buffer RGBA nativo del arena.
  - `setFlip(...)`, `failureReason()`, `stbiImageFree(...)`.
  - `resize(...)`: reescala sRGB con muestreo bilineal de 4 esquinas. Índices corregidos: destino `to[y*destStride + x*channels + channel]` con `destStride = destW*channels`, origen con `sourceStride`, lecturas `& 0xFF`.
  - `uploadIcon(entries,count,size)`: copia el RGBA desde el arena y lo manda a JS.
- `port/src/main/java/port/compiler/StbCompatibility.java` — `ClassHolderTransformer` nuevo, registrado en `BuildCompiler` después de `WindowCompatibility`. Sustituye solo lo necesario y deja el resto en FailFast honesto:
  - `org.lwjgl.system.libstb.LibSTB.<clinit>` → no-op (ya no carga la librería nativa).
  - `STBImage.stbi_load(CharSequence,int[],int[],int[],int)`, `stbi_set_flip_vertically_on_load(Z)V`, `nstbi_set_flip_vertically_on_load(I)V`, `stbi_failure_reason()String`, `stbi_image_free(ByteBuffer)`.
  - `STBImageResize.stbir_resize_uint8_srgb(ByteBuffer,IIILByteBuffer,IIII)ByteBuffer`.
  - Helper `argument()` convierte `BOOLEAN`→`INTEGER` para el flip.

Archivos modificados:
- `port/src/main/java/port/compiler/WindowCompatibility.java` — el bloque `glfwSetWindowIcon` **ya no lanza**: lee `count` con `CustomBuffer.remaining()I` y `entries` con `CustomBuffer.address()J`, llama `BrowserStb.uploadIcon(entries,count,16)` y termina con `e.exit()` explícito.
- `port/src/main/java/port/compiler/BuildCompiler.java` — agrega `StbCompatibility.class.getName()` a los transformers.
- `port/src/main/java/port/shim/BrowserWindow.java` — `glfwGetWindowFrameSize(J,IntBuffer,IntBuffer,IntBuffer,IntBuffer)` valida cada buffer (acepta `null`) en lugar de desreferenciarlo.
- `port/runtime/window.mjs` — método `setIcon(bytes,width,height)`: valida `Uint8Array`, dimensiones y `bytes.length === w*h*4`, rasteriza en un canvas 2D y crea/actualiza un `<link rel="icon">` con data URL PNG; guarda `lastIcon` y emite `icon_set`. `node --check` OK.
- `tests/prepare_real.py` — agrega `media/ui/zomboidIcon32.png` a `startup.txt` y su metadata (`url,width,height,rgba_sha256`) a `assets.json`.
- `tests/browser/game-main.spec.mjs` — aserciones adaptadas al stop real nuevo (ver abajo) y captura `test-results/game-main-round21b.png`.

## Estado REAL verificado del arranque en Chrome (hoy, build 21d)

Build: `port/target/reports/game-main-lua/build-report.json` → `success:true`, 10.228 clases / 72.540 métodos, `teavm-diagnostics.txt` **vacío**, `boot.wasm` **143.584.379 bytes**, pico RSS 7,02 GiB, `compile_ms` 361.984. Log: `build-round21d-game.log` (EXIT=0, BUILD SUCCESS).

Chrome (`port/target/reports/game-main-lua/chrome-startup.json`, `observedAt` 2026-10-27T15:09:14Z):
- `phase:'main-returned'`, `menuRendered:false`, `error:''`, sin errores de página no capturados.
- Etapas alcanzadas: `Display#init` entra y retorna → `LoggerManager#init` entra y retorna → `Bullet#init` entra y retorna (carga el wasm32 de Bullet) → `Display#create` entra.
- Eventos de ventana (backend `browser`): `initialized` (1365×1100, renderer `WebGL 2.0 (OpenGL ES 3.0 Chromium)`), `created` (640×480, título `Project Zomboid`), **`icon_set` width 32 / height 32 / bytes 4096**, `callback_registered` **11** (10 kinds de `initCallbacks` + 1 kind 10), `shown`, `context_current`.
- El ícono real quedó en el DOM: `link[rel=icon]` con data URL `image/png`. **Ya no aparece** `failed to read image`.
- Excepciones capturadas por el propio PZ, en orden:
  1. `java.lang.UnsupportedOperationException: Unsupported platform: UNSUPPORTED` (oshi, harmless, capturado).
  2. **Stop actual**: `java.lang.IllegalStateException: Browser WebGL2 GL capability bridge is not implemented: org.lwjgl.opengl.GL#createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;` con `Message: Exception thrown during MainScreenState.main.`

`test-results/game-main-round21b.png` (1365×1100) existe y se analizó por histograma: `rgb(0,0,0)` sobre el área del canvas (640×480 = 307.200 px, exactamente el `created`) y el resto en `rgb(11,17,22)` con texto de consola en `rgb(233,238,236)`. **No hay tool de inspección de imágenes en este entorno** (`view_image` deshabilitado), por eso `screenshot_visually_checked` sigue en `false`: no se puede afirmar una revisión visual humana, solo el análisis por píxeles.

**El menú principal real sigue sin verse. `MENU_REACHED` no existe y no se creó.**

## Pruebas de esta ronda

- `bash scripts/test.sh` (suite completa, `verification-round21-full.log`, EXIT=0): **94 tests verdes** → 45 JVM (surefire), 10 Python (unittest), 19 Node (`npm test`; el conteo histórico decía 17: la suite Node creció a 19), 17 Chrome plataforma (`npm run test:browser`). El `build.sh` de esta suite es el de plataforma, sin `--with-game`.
- `npx playwright test --config playwright.game.config.mjs` con `PZ_HOME` exportado (`verification-round21-real.log`): **5 passed (10.1s)**, incluido el spec del main real con el nuevo stop y la prueba del PNG real subido a WebGL2.
- `mvn -f port/pom.xml compile` (chequeo JVM): BUILD SUCCESS.

## Bloqueo actual

`Display.create` necesita capacidades GL de LWJGL. `org.lwjgl.opengl.GL#createCapabilities()` (y sus 3 variantes delegadas) sigue lanzando el raise honesto porque falta el puente `GLCapabilities` sobre WebGL2. Todo lo posterior (GL11, shaders del renderer, `LuaManager.init`, loop, menú) está detrás de ese paso.

## Siguientes pasos concretos, en orden (ronda 22)

1. **Puente `GLCapabilities`.** Verificar **antes** de escribir IR el body de `GL#createCapabilities(boolean,IntFunction)` y las 4 variantes delegadas con `javap -p -c -cp "$PZ_HOME/projectzomboid.jar" org.lwjgl.opengl.GL` (el body exige `GL.functionProvider != null`). Implementación prevista:
   - un `FunctionProvider` propio: `getFunctionAddress(name)` → dirección no nula estable en el arena, `destroy()` no-op;
   - `IntFunction` → `PointerBuffer` sobre `memAlloc(2236*8)` (una entrada por enum GL);
   - conjunto de extensiones obtenido de WebGL2 en JS (o vacío, anotando la duda en `docs/decisions.md`);
   - `forwardCompatible=false`.
2. GL11 sobre WebGL2: el subset ya mapeado en `port/target/config/gl-overrides.tsv` (`glClear`, `glClearColor`, `glGetError`, `glViewport` → `port.shim.GL11`) y ampliar según el stop siguiente. Los shaders del juego ya compilan/linkan en WebGL2 (prueba verde).
3. Cursor/pointer lock y el resto del loop de `Display`.
4. `LuaManager.init`, loop de render y el menú. `MENU_REACHED` **solo** cuando el menú principal real se vea en Chrome, con captura en `test-results/` y su prueba Playwright verde.
5. Cada ronda: `bash scripts/test.sh`, `npx playwright test --config playwright.game.config.mjs` y `STATUS.md` actualizado antes del cierre.

## Notas de operación

- `source scripts/env.sh` en **cada** comando (mvn/javap/node/playwright solo están en el PATH después de sourcear). `MAVEN_OPTS="-Xmx8g -Xss16m"`; mvn necesita `-Dmaven.repo.local="$PZ_WEB_ROOT/.toolchain/m2"`.
- `bash scripts/build.sh --with-game` se corrió **en primer plano** (~7 min), nunca con `nohup`, y nunca con Chrome en paralelo. Pico RSS medido: 7,02 GiB.
- **Strata y el puerto 8083 no se tocaron** (es este agente). No se corrió el juego nativo. Servidores solo en `127.0.0.1`.
- Sin `sudo`, sin herramientas globales modificadas, sin archivos del juego ni código decompilado en git, sin stubs que simulen éxito: cada operación no implementada lanza con su identidad exacta.
- `rg` **no existe** en este entorno (usar `grep` o Python). `view_image` está deshabilitado: las capturas solo pueden analizarse programáticamente.
