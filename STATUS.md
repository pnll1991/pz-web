# Estado del port personal

Actualizado: **2026-10-27 16:00 (-03), ronda 22**, Linux x86_64. Repositorio real `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad). HEAD del vigilante al empezar la ronda: **ec01a19** ("Ronda 21"). Esta ronda **no hizo commits** y **no tocó `.git`**.

Ronda 22 iniciada 12:10:28 (-03) según `supervisor.log`. Se cerró al llegar al límite de 4 h sin build del main real ni prueba Playwright nueva: el trabajo de esta ronda fue **diseño verificado + base del puente de capacidades GL**, y el diseño completo queda acá para que la ronda 23 lo implemente sin volver a leer bytecode.

## Cambios de esta ronda (sin commit)

- `port/runtime/gl.mjs` (modificado, `node --check` OK):
  - `apiVersion()` → `'3.3 (WebGL 2.0)'`. WebGL2 no expone versión de OpenGL de escritorio; se reporta ES 3.0 como 3.3 para habilitar el camino de shaders modernos del juego. Decisión registrada en `docs/decisions.md`.
  - `extensions()` → `gl.getSupportedExtensions()` real de Chrome (lista, sin inventar).
  - `hasEntrypoint(name)` → sonda honesta: `true` solo si WebGL2 tiene el método (`glXxx` → `xxx`) o si el nombre está en el conjunto explícito `PORT_PROVIDED` (glClearColor, glClear, glViewport, glGetError, glEnable, glDisable, glScissor, glDepthMask, glDepthFunc, glCullFace, glFrontFace, glPixelStorei, glLineWidth, glBlendFunc, glBlendFuncSeparate, glClearDepthf, glStencilMask, glClearStencil). Todo lo demás ⇒ address 0 ⇒ capability **false**.
- `port/src/main/java/port/shim/BrowserGL.java` (archivo nuevo): puente del provider de capacidades sobre `globalThis.pzRuntime.gl`.
  - `apiVersion()` / `extensionNames()` / `hasEntrypointRaw(name)` vía `@JSBody`.
  - `versionMajor()`/`versionMinor()` parsean la cadena reportada; `contextCurrent()`.
  - `tags()` construye el `Set<String>` con los nombres que usa LWJGL (`"OpenGL" + major + minor`, p. ej. `OpenGL11`, `OpenGL30`) más los nombres de extensión reales del contexto.
  - `functionAddress(name)` → `0` si el contexto no expone el entrypoint; si lo expone, un slot estable de 8 bytes del `NativeArena` (una entrada por nombre, cacheada en un `HashMap`). Nunca inventa direcciones.
  - Compila en JVM (`javac` aislado con `teavm-jso`) y `mvn -f port/pom.xml compile` BUILD SUCCESS.
- `docs/decisions.md`: una línea con la decisión de la versión reportada y la regla "extensión ausente ⇒ capability false".
- `AGENT_LEARNINGS.md`: sección Ronda 22 con el error de la ronda y los hechos verificados (ver "Hechos verificados" abajo).

**No se cambió todavía** `WindowCompatibility.rewriteGL`: `GL#createCapabilities` sigue lanzando el raise honesto, por eso el comportamiento en Chrome es idéntico al de la ronda 21.

## Estado REAL verificado del arranque en Chrome

No hubo build `--with-game` ni ejecución Playwright en esta ronda (corte de tiempo). El último estado verificado sigue siendo el de la ronda 21 (build 21d, `port/target/reports/game-main-lua/chrome-startup.json`, `observedAt` 2026-10-27T15:09:14Z, `build-round21d-game.log` EXIT=0):

- `phase:'main-returned'`, `menuRendered:false`, `error:''`, sin errores de página.
- Etapas: `Display#init` → `LoggerManager#init` → `Bullet#init` (wasm32 cargado) → `Display#create` → window events `initialized/created(640×480,'Project Zomboid')/icon_set(32×32,4096 B)/callback_registered×11/shown/context_current`, ícono PNG real en `<link rel=icon>`.
- **Stop exacto**: `java.lang.IllegalStateException: Browser WebGL2 GL capability bridge is not implemented: org.lwjgl.opengl.GL#createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;` (raised por `WindowCompatibility.rewriteGL`). Antes, oshi lanza `UnsupportedOperationException: Unsupported platform: UNSUPPORTED` y PZ lo captura (inofensivo).
- Captura rond 21: `test-results/game-main-round21b.png`, analizada solo por histograma (`screenshot_visually_checked:false`; `view_image` deshabilitado en este entorno).

**El menú principal real sigue sin verse. `MENU_REACHED` no existe y no se creó.**

## Pruebas de esta ronda

- `mvn -f port/pom.xml compile` (con `source scripts/env.sh`, `MAVEN_OPTS=-Xmx8g -Xss16m`, `-Dmaven.repo.local=$PZ_WEB_ROOT/.toolchain/m2`): BUILD SUCCESS (incluye el nuevo `BrowserGL.java`).
- `javac` aislado de `BrowserGL.java` + `NativeArena.java` contra `teavm-jso-0.16.0.jar`: sin errores.
- `node --check port/runtime/gl.mjs`: OK.
- `python3 -m unittest discover -s tests/python`: **10 OK** (`GL signatures: 5, verified scalar mappings: 4`).
- `npm test`: **19 pass / 0 fail**.
- **No corridos** por falta de tiempo: `scripts/build.sh --with-game`, `npx playwright test --config playwright.game.config.mjs`, `bash scripts/test.sh` (suite completa). Los cambios de `gl.mjs` no fueron ejercitados en Chrome en esta ronda.

## Bloqueo actual

`Display.create` → `GL.createCapabilities()` sigue sin puente. Lo posterior (GL11/GL11C reales, shaders del renderer, `LuaManager.init`, loop, menú) está detrás de ese paso. El puente necesita ~10 sustituciones coordinadas (ninguna hecha todavía); el diseño verificado está abajo y es suficiente para implementar sin más `javap`.

## Hechos verificados esta ronda (fuente: `javap -p -c` del jar pinneado; dumps en /tmp/gl.txt, /tmp/glc.txt, /tmp/gl11.txt, /tmp/gl11c.txt, /tmp/checks2.txt, /tmp/ms.txt, /tmp/apiutil2.txt, /tmp/ThreadLocalUtil.class)

1. `GL.createCapabilities(boolean,IntFunction)` (body real): exige `GL.functionProvider != null` (else ISE "OpenGL library has not been loaded."); llama `provider.getFunctionAddress("glGetError"/"glGetString"/"glGetIntegerv")` y exige los tres ≠ 0 (else ISE "Core OpenGL functions could not be found..."); después usa `JNI.callI`/`JNI.callPV`/`JNI.callP` + `MemoryUtil.memAddress(IntBuffer)`/`memUTF8Safe`/`memASCIISafe` para leer versión y extensiones; construye tags `"OpenGL"+i+j` en un `HashSet(512)` limitado por `MAX_VERSION` (nullable); llama `APIUtil.apiFilterExtensions(Set,Configuration.OPENGL_EXTENSION_FILTER)`; `new GLCapabilities(provider,set,forwardCompatible,intFunction ?: invokedynamic-default)`; `GL.setCapabilities(caps)`; retorna. **Todo ese body usa JNI: no se puede ejecutar en wasm.**
2. Las otras 3 variantes solo delegan a la de `(boolean,IntFunction)`. `Display.create` llama la variante sin argumentos (`display.txt` offset 299) y guarda el resultado en `Display.capabilities`.
3. `GLCapabilities.<init>(FunctionProvider,Set<String>,boolean,IntFunction<PointerBuffer>)`: setea `forwardCompatible`; `addresses = intFunction.apply(2236)`; llama **226** métodos `check_*` privados (`(provider,buffer,set)Z` ×220 y `(provider,buffer,set,boolean)Z` ×6); **después asigna los 2233 campos `long glXxx = buffer.get(i)`** para i=0..2232; termina `this.addresses = ThreadLocalUtil.setupAddressBuffer(buffer)`.
4. `Checks.checkFunctions(provider,PointerBuffer,int[],String...)`: por cada índice, si `buffer.get(idx) != 0` salta (ya resuelto); si es 0 pide `provider.getFunctionAddress(name)`: 0 ⇒ `result=false` (no escribe), ≠0 ⇒ `buffer.put(idx,address)`. `Checks.reportMissing(tag,name)` loguea y retorna `false`. `check_GL11` empieza con `if(!set.contains("OpenGL11")) return false;`. **Conclusión: un provider que devuelve address≠0 solo para entrypoints realmente disponibles produce capacidades honestas.**
5. `ThreadLocalUtil.<clinit>` usa JNI (`getThreadJNIEnv`, `memGetAddress`, `JNINativeInterface.GetVersion`); `setupAddressBuffer` y `areCapabilitiesDifferent` son Java puro pero leen el campo estático `FUNCTION_MISSING_ABORT`. Hay que sustituir `<clinit>` (campos `JNI_NATIVE_INTERFACE=0`, `CAPABILITIES_OFFSET=24`, `RESERVED_NULL=0`, `FUNCTION_MISSING_ABORT=0`, `FUNCTION_MISSING_ABORT_TABLE=0`) y volver no-op `setCapabilities(J)`/`setFunctionMissingAddresses(I)`/`setupAddressBuffer(buf)→buf`/`areCapabilitiesDifferent(...)→false`; sus 3 natives mantienen el raise JNI de `FailFastNatives`.
6. `GL.<clinit>` real: `capabilitiesTLS`, `icd = new GL$ICDStatic(null)`, `Library.loadSystem(...)` (raise), `MAX_VERSION = APIUtil.apiParseVersion(Configuration.OPENGL_MAXVERSION)`, `OPENGL_EXPLICIT_INIT`, `create()`. `GL.create()/create(String)/create(FunctionProvider)/destroy` cargan librerías o usan `ThreadLocalUtil`. `GL.setCapabilities` usa `MemoryUtil.memAddress(CustomBuffer)` (ausente en `NativeMemory` ⇒ raise) + `ThreadLocalUtil.setCapabilities(J)` + `icd.set(caps)`. `GL$ICDStatic.set/get` son Java puro; `GL$ICDStatic$WriteOnce.<clinit>` lanza ISE "No GLCapabilities instance has been set" si `tempCaps` es null → hay que inicializar `icd` y sustituir `setCapabilities`/`create(...)`.
7. `APIUtil.<clinit>` solo compila `API_VERSION_PATTERN` = `^[^\d\n\r]*(\d+)[.](\d+)(?:[.](\S+))?(?:\s+(.+?))?\s*$` con `Pattern.DOTALL`; `apiParseVersion` es Java puro (parsea grupo 1/2). `'3.3 (WebGL 2.0)'` lo parsea bien: major 3, minor 3, implementation "WebGL 2.0". `apiGetFunctionAddress(provider,name)` ⇒ si 0 llama `requiredFunctionMissing` (NPE), salvo `Configuration.DISABLE_FUNCTION_CHECKS`.
8. **`GL11.glXxx` reenvía a `GL11C.glXxx` (native)**: 274 natives en GL11C/GL11. Las mappings actuales de `gl-overrides.tsv` apuntan a `GL11` y son inútiles para el juego: deben apuntar a `org.lwjgl.opengl.GL11C` (existen `GL11C,GL12C,GL13C,GL20C,GL21C,GL30C`; `GL15` existe; verificar `GL15C` antes de mapear). `GL11C.<clinit>` solo llama `GL.initialize()`.
9. **No existe ninguna implementación concreta de `org.lwjgl.system.FunctionProvider`** en el jar pinneado (`SharedLibraryImpl` no está). Candidatos para convertirlos en provider en IR: `org.lwjgl.opengl.GL$1 extends SharedLibrary$Delegate` (única clase con `getFunctionAddress` fuera de las librerías de SO), `org.lwjgl.system.linux.LinuxLibrary extends SharedLibrary$Default` (sus natives `loadLibrary/getFunctionAddress/free` ya tienen raise JNI). `SharedLibrary$Default.<init>(String,long)` es Java puro.
10. `FunctionProvider.getFunctionAddress(CharSequence)` es default → `MemoryStack.ASCII(name)` + `getFunctionAddress(ByteBuffer)`; `MemoryStack` ya está puenteado y `NativeMemory.memAddress(ByteBuffer)` existe, así que basta implementar `getFunctionAddress(ByteBuffer)` leyendo el nombre ASCII desde el arena (hay helper `MemoryUtil.memASCII(long)` en `NativeMemory`).

## Siguientes pasos concretos, en orden (ronda 23)

Sub-hito A (≈60 min, debe terminar con build+Chrome):
1. En `WindowCompatibility`: agregar `case "org.lwjgl.system.ThreadLocalUtil"` (punto 5) y `case "org.lwjgl.opengl.GL$ICDStatic"` no-op si hace falta; reescribir `GL.<clinit>` = `capabilitiesTLS`, `icd = construct("org.lwjgl.opengl.GL$ICDStatic", null)`, `MAX_VERSION = null`, y dejar `functionProvider` listo en `create()`.
2. Reescribir `GL.create()/create(String)/create(FunctionProvider)/destroy()` y `GL.setCapabilities(GLCapabilities)`: usar `GLCapabilities.addresses` → `CustomBuffer.address()J` en lugar de `MemoryUtil.memAddress(CustomBuffer)`; `icd.set(caps)` conserva su body.
3. Reemplazar el raise de `GL.createCapabilities(boolean,IntFunction)` por el builder IR: `tags = BrowserGL.tags()`; `buffer = PointerBuffer.create(BrowserGL.addressBufferAddress(), 2236)` (allocar el arena desde JS o vía `NativeMemory.memAlloc(2236*8)` y envolver con `PointerBuffer.create(long,int)`); provider = instancia IR de `GL$1`/`LinuxLibrary` cuyo `getFunctionAddress(ByteBuffer)` llame `BrowserGL.functionAddress(memASCII(address))`; `new GLCapabilities(provider,tags,false,intFunction)` **sustituido** — la vía más simple es sustituir `GLCapabilities.<init>` para que haga exactamente forwardCompatible + addresses + los 226 `check_*` + los 2233 `get(i)` (son Java puro y ya existen: solo hay que dejar el `<init>` original y reemplazar `intFunction.apply(2236)` por el buffer propio). Verificar con `mvn compile` antes del build grande.
4. `tools/glgen/generate.py`: mapear natives escalares verificados sobre **GL11C** (y las clases `*C` que existan) a `port.shim.GL11`; extender `port/src/main/java/port/shim/GL11.java` con los wrappers JSO correspondientes a `PORT_PROVIDED`; ajustar `tests/python/test_inventory.py` (los conteos 5/4 cambian) manteniendo la aserción de que `glUnsupported` no se mapea.
5. Build en primer plano: `source scripts/env.sh; export PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid; MAVEN_OPTS="-Xmx8g -Xss16m" bash scripts/build.sh --with-game` (~7 min, nunca en paralelo con Chrome). Revisar `port/target/reports/game-main-lua/build-report.json` y `teavm-diagnostics.txt`.
6. Actualizar `tests/browser/game-main.spec.mjs` al stop real nuevo (cadena exacta de la excepción / nuevo avance), screenshot nueva `test-results/game-main-round23*.png` + histograma, y correr `npx playwright test --config playwright.game.config.mjs` con `PZ_HOME` exportado.
7. `bash scripts/test.sh` (debe quedar verde; mantener separados `test-results/platform` y `test-results/real`).
8. Reescribir `STATUS.md` y `AGENT_LEARNINGS.md`. `MENU_REACHED` solo cuando el menú se vea renderizado + captura + prueba Playwright verde.

Reglas que siguen vigentes: nada de archivos del juego en git, nunca stubs que simulen éxito, servidor solo en `127.0.0.1`, no tocar Strata ni el puerto 8083, sin `sudo`, `rg` ausente (usar `grep`/Python), `view_image` deshabilitado (verificar capturas por histograma), builds largos siempre en primer plano.

## Ronda 25 (en curso) — iniciada 18:27:59 (-03)

Cambios verificados parciales (se actualiza durante la ronda):

- `port/compiler/GLCompatibility.java` (nuevo, registrado en `BuildCompiler` tras `WindowCompatibility`): puente real de capacidades GL sobre WebGL2. `GL.<clinit>` instala `LinuxLibrary("WebGL2-browser", BrowserGL.providerHandle())`; `GL.createCapabilities(boolean,IntFunction)` sonda el contexto (`BrowserGL.contextCurrent()`, `versionMajor()!=0`, entrada real del provider vía `BrowserGL.addressPresent`) y construye `GLCapabilities` real; `ThreadLocalUtil` se vuelve constante y `GL$ICDStatic.set` guarda las caps. `WindowCompatibility.rewriteGL` se eliminó.
- `tools/glgen/generate.py`: `DIRECT` con 16 nombres GL11C verificados → 32 filas en `port/target/config/gl-overrides.tsv`.
- `BrowserGL`: `addressPresent(long)Z` y `providerHandle()` (identidad de arena, evita el NPE de `Pointer$Default` con address 0).
- Chrome tras ese build (20:05): **`org.lwjglx.opengl.Display#create()V:returned`** — el puente de capacidades funciona; el stop pasó a `glfwSetWindowAttrib(GLFW_DECORATED=131077)`.
- `port/runtime/window.mjs` `setAttrib`: DECORATED/FLOATING/MAXIMIZED/TRANSPARENT_FRAMEBUFFER se **registran** (`attribute_ignored`) en vez de lanzar; sigue el raise para atributos desconocidos. Chrome (20:15 build+test): el boot avanza a `Display.setBorderlessWindow`→`setDisplayModeAndFullscreenInternal`→**`glfwSetWindowMonitor(JJIIIII)V`** (`UnsupportedOperationException: Browser GLFW operation unavailable`).
- Implementado `BrowserWindow.glfwSetWindowMonitor` + `window.mjs setMonitorMode`: monitor handle validado, windowed usa x/y/size pedidos, monitor≠0 usa el work area de la pantalla, `refreshRate` se registra (`monitor_mode`) sin prometer cadencia. Build en curso.
- Stop siguiente tras `glfwSetWindowMonitor`: `GL11C.nglGetString(I)J` (raise fail-fast) llamado desde `zombie.core.Core#init` vía `GL11.glGetString`. Implementado: `port/runtime/gl.mjs` `stringParameter/stringParameterIndexed/intParameters/floatParameters/boolParameters` + `intParameter/floatParameter/boolParameter` (tokens de escritorio resueltos contra parámetros reales de WebGL2; token desconocido ⇒ raise honesto; `viewportBox`/`scissorBox` registrados en `call()`), wrappers en `port.shim.GL11` (`glGetString`, `glGetStringi`, `glGetInteger(i)`, `glGetIntegerv(int[]/IntBuffer)`, `glGetFloat/v`, `glGetBoolean/v`), y `tools/glgen/generate.py` con `DIRECT_CLASSES` GL11/GL11C/GL30C → **51 filas** en `gl-overrides.tsv`. Build 20:38 EXIT=0; `python3 -m unittest discover -s tests/python` OK.
- Stop siguiente: `zombie.core.VBO.GLVertexBufferObject::init` lanza `RuntimeException: Neither OpenGL 1.5 nor GL_ARB_vertex_buffer_object supported` porque `check_GL15`/`check_ARB_vertex_buffer_object` piden las 19 funciones GL15/GL15C (address ≠ 0) y `hasEntrypoint` las devolvía falsas. Implementado GL15 real sobre WebGL2: `port/runtime/gl.mjs` (`genBuffers/deleteBuffers/bindBuffer/isBuffer/bufferData/bufferSubData/getBufferSubData/bufferParameter/genQueries/deleteQueries/isQuery/beginQuery/endQuery/queryResult/queryActive`; mapa handle→WebGLBuffer/WebGLQuery con handles monótonos, 0 = none) + `port/src/main/java/port/shim/GL15.java` nuevo (natives GL15C: `nglGenBuffers(IJ)V`, `nglDeleteBuffers(IJ)V`, `nglBufferData(IJJI)V`, `nglBufferSubData(IJJJ)V`, `nglGetBufferSubData(IJJJ)V`, `nglGetBufferParameteriv(IIJ)V`, `nglGetBufferPointerv/nglMapBuffer/glUnmapBuffer` con raise honesto, queries). `PORT_PROVIDED` agrega los 19 nombres; `tools/glgen/generate.py` ahora clasifica también GL15C y elige target por tabla (`target_for`) → **71 filas** (51 GL11/GL11C/GL30C + 19 GL15C + 1 probe). `mvn -f port/pom.xml compile` OK; tests python OK.
