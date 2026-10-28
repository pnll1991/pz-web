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

## Ronda 26 (en curso) — iniciada 21:05:38 (-03)

- Estado de partida: el build `--with-game` del cierre de la ronda 25 **falló** (`build-report.json` 21:05: `success:false`, `wasm_bytes:0`). Diagnóstico: `port.shim.GL15.nglDeleteBuffers(IJ)V` y `nglDeleteQueries(IJ)V` usaban `Int32Array.fromJavaArray([I)`, cuyo parámetro está marcado `@JSByRef` y **no está soportado en Wasm GC**.
- Fix aplicado: `GL15.nglDeleteBuffers`/`nglDeleteQueries` construyen ahora `Int32Array.create(count)` y cargan los handles con `handles.set(i, NativeMemory.memGetInt(...))`. Ningún otro shim usa `fromJavaArray`.
- Build `--with-game` 21:14 **EXIT=0**, `success:true`, `wasm_bytes:144320110`, `process_peak_rss_kib:6724324` (< límite de RAM). El bloque de GL15 queda compilado en Wasm GC.
- Chrome 21:15 (build nuevo): el boot avanzó mucho. `Display#create()V:returned` y el juego registra "OpenGL 1.5 buffer objects supported" (GL15 real). Stop nuevo exacto: `java.lang.UnsupportedOperationException: Java_org_lwjgl_opengl_GL20C_glEnableVertexAttribArray__I`, levantado en `org.lwjgl.opengl.GL20C::glEnableVertexAttribArray` ← `zombie.core.SpriteRenderer$RingBuffer::create` ← `SpriteRenderer::create` ← `GameWindow::InitDisplay` ← `RenderThread::init`. Sin pageErrors. La prueba `game-main.spec.mjs` falló solo porque sus aserciones de symbols/trace siguen apuntando al stop de la ronda 25 (`glfwSetWindowAttrib`), que ya no aparece.
- Ronda 27 (21:59): `port/src/main/java/port/shim/GL20.java` nuevo (natives GL20C sobre WebGL2 real: shaders/programs/uniforms/atributos/locations; introspection ausente en WebGL2 lanza con su identidad). `port/runtime/gl.mjs`: `uniformfv/uniform1iv/uniformMatrix` redirigidos por nombre+count, `shaderSourceJoined` (bloques de arena contiguos copiados en Java), `PORT_PROVIDED` suma los nombres GL20C que este backend cubre o lanza. `tools/glgen/generate.py`: `GL20_DIRECT` (55 natives) → 127 filas en `gl-overrides.tsv` (antes 71). `mvn -f port/pom.xml compile` OK; python 10 OK; npm 19 OK. Próximo: build `--with-game` en primer plano y prueba Chrome.

## Ronda 27 (continuación, 22:25) — joystick/Gamepad API
- `port/compiler/WindowCompatibility.java`: nuevo kind `KIND_JOYSTICK = 11` y `case "glfwSetJoystickCallback" -> KIND_JOYSTICK` en `callbackSetterKind` (el setter devuelve la callback previa ⇒ `constantNull`, como los otros 11 setters).
- `port/shim/BrowserCallbacks.java`: `KIND_JOYSTICK = 11` + `eventArguments`: kind 11 usa 2 slots y escribe en cada slot un **int propio** (índice de joystick y flag conectado), porque `GLFWJoystickCallbackI.callback` lee `args[0]` con un solo `memGetAddress`+`memGetInt` (sin la doble deref de la ventana). Serialización window.mjs: `kind\tslot\tconnected`.
- `port/shim/BrowserWindow.java`: puente `glfwJoystickPresent(I)Z`, `glfwGetJoystickName(I)String`, `glfwJoystickIsGamepad(I)Z`, `glfwGetGamepadName(I)String` (los tres primeros coinciden con los descriptores de `org.lwjgl.glfw.GLFW`, así `WindowCompatibility` los reenvía al bridge). Axes/buttons/hats/GUID/gamepad state siguen con el raise honesto.
- `port/runtime/window.mjs`: espejo de la Gamepad API (`syncGamepads`, `refreshGamepads`, `joystickPresent/joystickName/joystickIsGamepad/gamepadName`), cola de eventos joystick kind 11 en `poll()`, listeners `gamepadconnected/gamepaddisconnected` que sólo piden re-sinc (`navigator.getGamepads()` está prohibido dentro de esos callbacks). Sin interacción del usuario Chrome reporta 0 gamepads: estado real, no stub. Slot ≥16 se registra una vez como `joystick_unrepresentable`.
- Verificado: `mvn -f port/pom.xml compile` OK, `node --check port/runtime/window.mjs` OK, python 10 OK, npm 19 OK. Build `--with-game` en curso (`build-round27b-game.log`).

## Ronda 28 (en curso) — iniciada 22:45:35 (-03)

Cambios verificados (se actualiza durante la ronda):

- `port/runtime/window.mjs`: **corrige el mapeo invertido de modos de input GLFW** (stop de la ronda 27b:
  `Unsupported browser input mode: 208897`). `GLFW_CURSOR=208897` es el MODE y sus valores son
  `212993` NORMAL / `212994` HIDDEN / `212995` DISABLED / `212996` CAPTURED; `208898`/`208899` son
  STICKY_KEYS/STICKY_MOUSE_BUTTONS y `208901` RAW_MOUSE_MOTION. DISABLED pide pointer lock real
  (nunca afirma que obtuvo el lock); CAPTURED lanza con su identidad. Eventos nuevos
  `input_mode` y estado `pointerLock/pointerLockPending/pointerLockError`.
- `port/web/game.mjs`: `main(['-nosound'])` (camino documentado del propio juego: `Core.soundDisabled`
  ⇒ `GameWindow.mainThreadInit` instala `DummySoundManager`/`DummyAmbientStreamManager`) y
  `Error.stackTraceLimit = Infinity`. Verificado: el `UnsatisfiedLinkError` de FMOD desaparece y la
  traza real del RangeError quedó visible.
- Diagnóstico confirmado con la traza completa (10134 líneas, 3370 ciclos
  `Fiber::reversePush/Fiber::push/Arrays::copyOf` bajo `Thread::switchContext <- Thread::yield <-
  zombie.core.opengl.RenderThread::renderLoop`): TeaVM 0.16.0 reanade la cadena de pending calls de
  la fibra en el stack de JavaScript en cada suspend/resume, así que un loop que suspende en cada
  iteración desborda V8. `--js-flags=--stack-size` **crashea** Chrome (medido), no es opción.
- `port/src/main/java/port/runtime/JdkSupport.java` (nuevo método `yield()` cooperativo rate-limited,
  `YIELD_GRANT = 64`, suspende vía `Thread.sleep(0)`) + `JdkCompatibility` sustituye ahora también
  `java.lang.Thread#yield()V` (era el punto de suspensión real del render loop; `onSpinWait` ya
  estaba forward). `mvn -f port/pom.xml compile` OK.
- `port/runtime/gl.mjs` + `port/shim/GL11.java`: `glEnable/glDisable` traducen tokens de escritorio a
  la capability WebGL2 real (`CAPS`: SCISSOR_TEST/CULL_FACE/DEPTH_TEST/STENCIL_TEST/BLEND/
  POLYGON_OFFSET_FILL/RASTERIZER_DISCARD/SAMPLE_ALPHA_TO_COVERAGE/SAMPLE_MASK) y registran el estado;
  un token sin capability WebGL2 **lanza con su identidad** en vez de ignorarse (elimina
  `WebGL: INVALID_ENUM: enable: invalid capability` sin simular éxito). `node --check` y `mvn compile` OK.

## Ronda 29 (en curso) — iniciada 00:45:26 (-03), límite de corte 03:45

Estado de partida verificado:
- HEAD vigilante `775e558` (Ronda 28). `git status` limpio al arrancar.
- La Ronda 28 quedó cortada a las 4 h **durante** su último build: `build-round28f-game.log` se
  truncó en `TeaVM progress=3990` y por eso `port/target/web/game/` solo tiene
  `build-state.json = {"success":false,"status":"running"}` (sin `boot.wasm`). Su última prueba
  Chrome real sí quedó registrada (`verification-round28d-real.log`, 00:42):
  `phase:'main-returned'`, `error:''`, stop exacto
  `WebGL2 has no capability for GL token 0xde1 (glEnable)` en
  `zombie.core.Core::init → GL11.glEnable` con `port.shim.GL11::glEnable` en la traza.
- El parche posterior de la Ronda 28 ya está en el repo: `port/runtime/gl.mjs` traduce tokens de
  capability de escritorio a WebGL2 (`CAPS`/`FIXED_FUNCTION_CAPS` con valores verificados por
  `javap -constants`: SCISSOR_TEST 0x0c11, CULL_FACE 0x0b44, DEPTH_TEST 0x0b71, STENCIL_TEST 0x0b90,
  BLEND 0x0be2, POLYGON_OFFSET_FILL 0x8037, RASTERIZER_DISCARD 0x8c89, SAMPLE_ALPHA_TO_COVERAGE 0x809e,
  SAMPLE_COVERAGE 0x80a0, SAMPLE_MASK 0x80b0) y **registra** las fixed-function sin equivalencia
  WebGL2 (`TEXTURE_2D` 0x0de1, FOG, LIGHTING, etc.) en `fixedFunctionCaps`. `node --check` OK.

Acciones de esta ronda:
1. Build `--with-game` en primer plano (`build-round29-game.log`), único camino posible porque no
   hay `boot.wasm` utilizable. En curso desde el primer comando.

2. **Bug encontrado y corregido (causa del `loader-failed` de las 5 pruebas al empezar la ronda)**:
   el parche final de la Ronda 28 dejó dos líneas de `this.capabilities/this.fixedFunctionCaps`
   escritas **después** de la llave de cierre de `class GLBridge` en `port/runtime/gl.mjs`, así que
   el módulo lanzaba `TypeError: Cannot set properties of undefined (setting 'capabilities')` al
   importar (`node -e import(...)` lo confirma; `node --check` NO lo detecta porque es error de
   runtime, no de sintaxis). Fix: las dos líneas van ahora en el constructor y se eliminó el bloque
   huérfano del final del archivo (764 líneas). Copiado a `port/target/web/runtime/gl.mjs`.
3. Build `--with-game` en primer plano terminado 01:02: `EXIT=0`, `success:true`,
   `wasm_bytes:144335949`, `reachable_classes:10235`, `reachable_methods:72919`,
   `process_peak_rss_kib:7636636` (bajo el límite), `compile_ms:796352`.
4. **Causa REAL del RangeError corregida (no era "replay por iteración")**: la traza de 10133 líneas
   muestra 3369 ciclos `java.util.Arrays::copyOf → Fiber::push → Fiber::reversePush`, es decir
   **recursión mutua infinita** del crecimiento del stack de la fibra. `TArrays.copyOf(T[],int)` de
   TeaVM asigna con `Array.newInstance(componentType,n)` y ese `getClass()` es asíncrono, así que
   TeaVM convierte `copyOf` en corutina cuyo prólogo llama `Fiber::reversePush → Fiber::push`; como
   se llama justo cuando el array está lleno, vuelve a crecer el mismo array → recursión hasta el
   límite de V8. Fix en la fuente fijada vía `scripts/prepare_teavm.py::patch_fiber()`: `Fiber.push`
   usa un helper propio `growObjectStack` (allocación concreta `new Object[n*3/2]` + copia manual),
   que es un intrinsic de asignación y nunca suspende. `python3 scripts/prepare_teavm.py` OK.
5. `python3 scripts/prepare_teavm.py` OK: imprime las 5 líneas de TeaVM + la del fiber + la del
   generator. Dos arreglos necesarios: (a) el `package.read` del generator quedaba fuera del `with`
   del source jar (`ValueError: Attempt to use ZIP archive that was already closed`) -> se abre un
   segundo `ZipFile`; (b) el patrón de reemplazo del generator debe ir **sin** la indentación
   original (los dos call sites tienen 4 y 8 espacios) -> se busca `functionType.getSupertypes().add(
   classInfoType.newArrayFunctionType());` y se reemplaza con el guard `declared != null` conservando
   el prefijo de indentación. Verificado en `port/target/generated-sources/teavm/...` (2 sitios).
6. `mvn -f port/pom.xml test -Dtest=CompilerRegressionTest -o`: **6 tests OK** (antes 2 NPE en
   `prepareTypes` por `newArrayFunctionType()==null`). Los otros 45 tests JVM ya pasaban.
   Nota: al insertar esa función consumí por error la línea `def main():` (NameError en el build);
   restaurada y verificada con `python3 -c "import ast; ast.parse(...)"`.
7. Build `--with-game` Ronda 29c terminado 02:25 (`build-round29c-game.log`): `EXIT=0`,
   `success:true`, `wasm_bytes:144335411`, `reachable_classes:10235`, `reachable_methods:72920`,
   `compile_ms:346888`, `process_peak_rss_kib:7114668`.
8. **El `RangeError: Maximum call stack size exceeded` DESAPARECIÓ** (el fix de `Fiber.push`
   funciona). Chrome (`verification-round29c-real.log`, `observedAt 2026-10-28T05:26:47Z`): 4 pass
   en `real-game.spec.mjs`; `game-main.spec.mjs` falla ahora con `phase:'starting'` (nunca llega a
   un estado final en 60 s), `pageErrors:[]`. Avance nuevo del boot: además de Display/Logger/
   Bullet/Display#create aparece `zombie.GameWindow#InitGameThread()V:entered/returned`; GL 267
   calls, swapCount 2, 39 eventos de ventana (joystick kind 11 registrado, input_mode 208897).
9. Stop visible nuevo: `java.lang.UnsupportedOperationException: Unsupported platform: UNSUPPORTED`
   lanzado por `oshi.SystemInfoFFM::createHardware` (desde `MainScreenState::printSpecs` en
   `MainScreenState::main`), resuelto con `scripts/wasm_stack.py` sobre los offsets de
   `Throwable.java:` (6219430 fillInStackTrace, 15268115 Throwable::<init>, 6300801 Exception::<init>,
   6192791 RuntimeException::<init>, 6179357 UnsupportedOperationException::<init>_0, 73390099
   oshi.SystemInfoFFM::createHardware, 72055835/62739231/62739275 lambda get, 63142302/63142704
   Memoizer$1::get, 62365006 SystemInfoFFM::getHardware, 27774642 printSpecs, 9925543 main,
   74419148 port.GameBoot::launch). **El juego lo captura yloguea y el boot sigue**: no es el bloqueo.
10. **Bloqueo actual**: tras `InitGameThread` la fibra principal queda suspendida para siempre.
    Verificado con diagnósticos Playwright propios (`port/target/diag29.mjs`, `port/target/diag30.mjs`
    sobre el dev-server 8092): 120 s esperando, `phase:'starting'`, contadores GL/swap/eventos
    congelados (267/2/39), `pageErrors:[]`, 432 requestAnimationFrame (el JS sigue libre), y un único
    `setTimeout` creado por `org.teavm.runtime.EventQueue::offerWasmGC` en t=4823 que **sí fireó**
    (4845). No hay ningún timer pendiente => la fibra está parked en una espera de monitor/join que
    nadie notificará (no es un loop ni un sleep).
11. Cierre de Ronda 29 (03:20-03:23): la suite real `PZ_REAL_TEST_PORT=8093 npx playwright test
    --config playwright.game.config.mjs` pasa **5/5** (`verification-round29f-real.log`), con las
    aserciones adaptadas al park real (`phase:'starting'`, `mainThreadInit:entered` sin `returned`,
    `DebugOptions#initMessaging:entered`, `RenderThread#renderStep:returned`, `pageErrors:[]`).
    `npm test` 19/19 y `python3 -m unittest discover -s tests/python` OK (10 pruebas, requiere
    `source scripts/env.sh` para tener `javac` en PATH; sin env fallan 2 por `FileNotFoundError`).
12. Instrumentación ampliada para hallar el park (build en curso `build-round29h-game.log`):
    `StartupProgress.CLASSES` suma `zombie.PredicatedFileWatcher`, `zombie.debug.DebugOptionsXml`,
    `zombie.util.PZXmlUtil`, `zombie.XMLDocument`, `java.io.File`, `java.io.FileOutputStream`,
    `java.io.BufferedWriter`, `java.io.OutputStreamWriter`, `java.io.FileWriter` y
    `java.lang.Thread` (así un `sleep`/`yield`/`wait` se ve como frontera y no se infiere);
    `port/runtime/vfs.mjs` agrega `ringTrace()` sobre el mismo anillo `pzGameTrace` para
    `store()` de rutas `/Zomboid` escribibles, `mkdir()` y `flush():entered` (el preload de
    `/media` no inunda el anillo). La spec lee ahora las fronteras de `pzGameProgress` (no rota)
    y guarda el anillo crudo en el reporte (`parkRing`) + adjunto `park-ring-tail`.
13. Build `build-round29h-game.log` (03:29:34, 6:27 min): `EXIT=0`, `success:true`,
    `wasm_bytes:144501580`, peak RSS 7.1 GiB con la instrumentación ampliada. Chrome con el anillo
    nuevo (`port/target/diag33.mjs`, `diag34.mjs`, puerto 8092) acota el park: la última frontera
    del juego es `zombie.util.PZXmlUtil#createNewDocument()Lorg/w3c/dom/Document;:returned` dentro
    de `PZXmlUtil#write()` (tras `tryWrite`, `DebugOptionsXml#<init>`, `PZXmlUtil#<clinit>`,
    `PredicatedFileWatcher#<init>` y `DebugFileWatcher#add` ya retornados). No hay ninguna frontera
    `java.io.FileOutputStream`/`vfs#store`: el park está **antes** de abrir el archivo, dentro de
    nuestro código compat `javax.xml`/`org.w3c.dom`/`port.shim.BrowserDom`. Además `java.lang.Thread`
    muestra que el render thread sí arranca y corre (`Thread#start`, `lambda$start$0`, `yield`,
    `sleep`, `getStackTrace`), por lo que el `Thread.yield` de `mainThreadInit` no es el bloqueo.
14. Suite real tras el build nuevo: `verification-round29h/i/j` fallan y `verification-round29k-real.log`
    (03:49) pasa **5/5**. Las causas fueron nuestras, no del juego: (a) `page.screenshot` excedía el
    timeout de 90 s porque resolver símbolos de una pila de 10 000+ frames con el classlib instrumentado
    tardaba más; (b) el poll del anillo esperaba `pzGameTrace` pero Chrome tarda ~115 s en instanciar
    los 144 MB de wasm bajo presión de RAM. Ajustado a `test.setTimeout(330_000)` y poll
    `{timeout:190_000}`. Reporte `port/target/reports/game-main-lua/chrome-startup.json` ahora incluye
    `parkRing` (400 eventos) y deja `state {phase:'starting',error:'',menuRendered:false}`,
    `pageErrors:[]`.

## Ronda 30 (cerrada 07:00) — iniciada 04:04:44 (-03), límite de corte 08:04

Estado de partida verificado:
- HEAD vigilante `9c0e144` (Ronda 29), `git status` limpio. `port/target/web/game/boot.wasm` presente
  (144 556 110 B, 03:56) con `build-state.json` `success:true`, `reachable_classes:10235`,
  `reachable_methods:72922`, peak RSS 7.03 GiB. Suite real previa: 5/5 (`verification-round29k-real.log`).
- Diagnóstico 29k confirmado (`chrome-startup.json`, `observedAt 2026-10-28T07:03:49Z`): la última
  frontera del juego es `PZXmlUtil#createNewDocument():returned` (t≈114796.3) dentro de
  `PZXmlUtil#write()`; el fiber del hilo de juego nunca retorna de `mainThreadInit`, y el bootstrap
  `Thread#lambda$start$0(Z)V` sí llega a su `returned` (pasa por el catch/handler), es decir el
  bloque `tryWrite` se suspende dentro de `javax.xml.bind` y no se reanuda.

Cambios de esta ronda:
1. `port/compiler/StartupProgress.java`: corregido el bug de instrumentación de la Ronda 29 — la
   sustitución de `port.compat.*` ocurre ANTES del transformer, así que `OWN_PREFIXES=
   "port.compat.javax.xml."` producía **0 fronteras**. Ahora se instrumentan los nombres
   post-sustitución `javax.xml.`, `org.w3c.dom.`, `org.xml.sax.` (más `port.shim.BrowserDom`).
2. Sumado a la observación (solo entrada/retorno, sin cambiar lógica): las clases
   `PZXmlUtil$MarshallerAllocator`, `PZXmlUtil$MarshallerAllocator$1`, `PZXmlUtil$UnmarshallerAllocator`,
   y los métodos `ThreadLocal#get`/`initialValue`, `ServiceLoader#load`/`loadIterator`,
   `Class#forName`, `Class#getMethod`, `Method#invoke`, `ClassLoader#getResource`,
   `ExceptionLogger#logException(Throwable,String)`.
   Motivo: `PZXmlUtil.write(T,File)` = `createNewDocument()` → `MarshallerAllocator.get(obj)` →
   `JAXBContext.newInstance(Class[])` → `ContextFinder.find` (jaxb.properties → sysprops →
   ServiceLoader → "platform default provider" `com.sun.xml.internal.bind.v2.ContextFactory`), todo
   reflexión/ServiceLoader que en TeaVM es asíncrono y puede parkear el fiber. Verificado con
   `javap`: el jar pinneado trae 119 entradas `javax/xml/bind/*` y 788 `com/sun/xml/bind/*`, pero en
   `boot.wasm` hay **0** ocurrencias de `com.sun.xml.bind` (el RI del jar no es alcanzable), y
   `tryWrite` captura `JAXBException` y llama `ExceptionLogger.logException` (método `synchronized`).
3. `mvn -f port/pom.xml compile` BUILD SUCCESS con los cambios de instrumentación. Build `--with-game`
   en primer plano (`build-round30-game.log`) en curso desde 04:44.
4. Puente JAXB propio (esta ronda, la corrección del park): el RI del jar (`com.sun.xml.bind.v2.ContextFactory`)
   tiene **0** ocurrencias en `boot.wasm`, así que `JAXBContext.newInstance` → `ContextFinder.find` →
   `getResourceUrl("META-INF/services/javax.xml.bind.JAXBContext")` parkeaba el fiber del juego
   (confirmado con `port/target/diag38.mjs`: última frontera `ContextFinder#getResourceUrl entered` y luego
   `Thread#lambda$start$0(Z)V:returned`, o sea el catch de `runThread`, sin frames de `ExceptionLogger`).
   Nuevos archivos (sustitución vía `port/compat/...` + `PlatformSubstitutions`):
   `port/compat/javax/xml/bind/{JAXBContext,JAXBException,PropertyException,Marshaller,Unmarshaller}.java`,
   `port/compat/javax/xml/bind/annotation/{XmlElement,XmlElements,XmlAttribute,XmlRootElement,XmlType,XmlTransient,XmlEnum,XmlEnumValue,XmlID,XmlIDREF}.java`
   (firmas verificadas con `javap` del jar; `DEFAULT="$default$"`), y el motor
   `port/runtime/PortXmlBeans.java` (bean↔DOM por reflexión, solo campos públicos/anotados, nunca inventa:
   fuera del subset lanza `JAXBException`), `port/runtime/PortMarshaller.java` (DOM + `BrowserDom.serialize`,
   propiedades `jaxb.encoding`/`jaxb.formatted.output`/`jaxb.fragment`/schemaLocation, `Listener`),
   `port/runtime/PortUnmarshaller.java` (`BrowserDom.parse` + BOM/`<?xml encoding`).
   `port/config/reflection.txt`: +58 clases bean con anotaciones `javax.xml.bind.annotation.*` (nombres del scan
   de la copia fijada, incluido nested con `$`).
5. Errores corregidos al compilar: `beanClasses()`/`resolve()` package-private entre paquetes; ctors de
   `PortMarshaller`/`PortUnmarshaller` no públicos; `linked=cause==null?message:cause` (String→Throwable);
   `rootOf(...)` devolvía `Document` donde se esperaba `Element`; `indexOf(int,String)` invertido;
   `charset()` dentro de `setProperty` sin capturar `JAXBException`. `mvn -f port/pom.xml compile` BUILD SUCCESS.
6. Build `--with-game` en primer plano hacia `build-round30b-game.log` (en curso).
7. Build `build-round30b-game.log` (06:01): `EXIT=0`, `success:true`, `reachable_classes:10218`,
   `reachable_methods:72042`, `wasm_bytes:143445840`, compile 366 s, peak RSS 6.39 GiB,
   `teavm-diagnostics.txt` vacío. En `boot.wasm`: `com.sun.xml.bind` = 0, `ContextFinder` = 0,
   `javax.xml.bind.JAXBContext` = 24, `PortXmlBeans` = 32, y las anotaciones reflectables sí se
   emiten (`javax.xml.bind.annotation.Xml{Element,Type,RootElement,Attribute,Transient,EnumValue}$$_impl`).
8. Diagnóstico Chrome con el puente JAXB (5 corridas: `port/target/diag38b/39/40/41/42/43.mjs`,
   dev-server propio en 8092, ~100–140 s de instanciación): **el park de JAXB se resolvió**.
   `pzGameProgress` pasa de 400 a 446 eventos y ahora se ven `BrowserDom#serialize():returned`,
   `PZXmlUtil#elementToPrettyString():returned`, `PZXmlUtil#write/tryWrite():returned`,
   `DebugOptions#initMessaging():returned`, `DebugOptions#init():returned`, `GameProfiler#init():returned`,
   `SoundManager/<clinit>`, `AmbientStreamManager`, `VoiceManager#loadConfig():returned`.
9. **Stop nuevo (nada simulado)**: `RenderThread#invokeQueryOnRenderContext():entered` →
   `invokeOnRenderContext():entered` → `queueInvokeOnRenderContext():returned` →
   `lambda$invokeOnRenderContext$0():returned` (el supplier llama `notifyRenderStateQueue`) →
   `Thread#interrupted():returned` → el fiber del hilo de juego termina su `runThread` sin avanzar.
   Verificado con `javap` del jar: `RenderContextQueueItem.waitUntilFinished(BooleanSupplier)` hace
   `synchronized (waitLock) { if (!isFinished()) waitLock.wait(); }` y ningún fiber vuelve a
   `flushInvokeQueue()`, por lo que el `wait()` nunca recibe `notify`.
   (Corrección de la Ronda 30, ver ítem 13: la afirmación inicial de que `renderLoop()` suspende su fiber en
   `Thread.yield()` no se sostiene — `renderLoop()` tiene 0 `Thread.sleep` y la instrumentación deduplicada
   no permite localizar la suspensión del fiber de render.)
   Contadores tras 170 s: `swapCount:2`, GL calls 267, `dispatched:0`; y la prueba nueva comprueba que
   `progress` y `swapCount` **no crecen** en 12 s adicionales (no es un boot lento, es un interbloqueo
   cooperativo entre los dos fibers).
10. Suite real Ronda 30 (`verification-round30-real.log`, 06:36, `PZ_REAL_TEST_PORT=8093`): **5/5 passed**
    (2.5 m). La spec nueva de esta ronda pasó sin relajar asserts: `DebugOptions#init()/initMessaging():returned`,
    `PZXmlUtil#tryWrite():returned`, `GameProfiler#init():returned`, `VoiceManager#loadConfig():returned`,
    `JAXBContext#newInstance([Class):returned`, `BrowserDom#serialize():returned`,
    `invokeQueryOnRenderContext/invokeOnRenderContext` **entered y no returned**, `not MainScreenState#enter():entered`,
    y `frozen-counters` (`progress` y `swapCount` idénticos tras 12 s extra). Bullet/GL/ventana/ícono/oshi siguen asserts.
    Reporte: `port/target/reports/game-main-lua/chrome-startup.json` (progress 446, parkRing 400,
    `bridge.glCallTotal:267`, `dispatched:0`, `swapCount:2`, `state.phase:"starting"`, `menuRendered:false`).
    Captura `test-results/game-main-round30.png` (1458x1323): 77,08 % rgb(0,0,0) + 18,70 % rgb(11,17,22) +
    2,45 % rgb(16,27,32); `screenshot_visually_checked:false` (no existe `view_image` en este entorno).
11. Otras suites: `npm test` 19/19 OK; `python3 -m unittest discover -s tests/python` 10 OK (requiere
    `source scripts/env.sh`). No se corrió `scripts/test.sh` (no quedaba ventana y no debe correrse junto con Chrome).
12. **Diagnóstico decisivo del interbloqueo** (`port/target/diag44.mjs`, dev-server 8092, 170 s + 25 s):
    el anillo sin dedup `pzGameTrace` (400 eventos) contiene **0** entradas de `Thread#yield`,
    `Thread#sleep`, `flushInvokeQueue`, `renderStep` o `#wait`, y es byte-idéntico entre la muestra A
    (t≈170 s) y la muestra B (t≈195 s), con `swapCount:2` en ambas. Conclusión: **no hay ningún fiber
    avanzando**; no es un boot lento ni un rate-limit del yield. El fiber del hilo de juego está parkado
    en `Object.wait()` de `RenderContextQueueItem.waitLock` y el fiber de render ya no está en su loop.
13. Hechos medidos (sin inferencias):
    - `pzGameProgress` **deduplica por label**, así que no permite contar iteraciones del loop de render;
      el anillo sin dedup `pzGameTrace` (cap 400) quedó saturado por las escrituras de `FileOutputStream`
      del hilo de juego. Por lo tanto **no** se puede afirmar en qué llamada exacta quedó suspendido el
      fiber de render: hace falta un contador por label (ver paso a de abajo).
    - `renderLoop()` tiene **0** llamadas a `Thread.sleep` (los 2 `Thread.sleep` de `RenderThread.class`
      están ambos en `lambda$uncaughtException$0()`, el hilo ForceCloseThread) y ningún otro llamador del
      boot las usa (`Input`, `Mouse`, `Clipboard`, `GameProfiler`: 0 llamadas verificadas con `javap`).
      Aun así hay un `Thread#sleep(J)V:entered` (t=134352.8) **sin** `returned`, y ningún
      `Thread#sleep(J)V:returned` en toda la traza.
    - Fronteras del boot en orden temporal: `Thread#start()` (134298.3) → `renderLoop():entered` (134298.7)
      → `acquireContextReentrant():entered/returned` → `renderStep():entered/returned` (134316.4/134317.7)
      → `flushInvokeQueue():entered/returned` (134318.0) → `DebugOptions#testThreadCrash():returned` (134319.0)
      → `Thread#yield():entered/returned` (134319.0/134319.1) → `Thread#sleep(J):entered` (134352.8, sin retorno).
      El fiber que corre `mainThreadStart`/`mainThreadInit` entra en 134360.8 y su último evento es
      `Thread#interrupted():returned` (134740.6) seguido de `Thread#lambda$start$0(Z)V:returned` (134740.7),
      inmediatamente después de `invokeQueryOnRenderContext():entered`/`invokeOnRenderContext():entered`/
      `queueInvokeOnRenderContext():returned`/`lambda$invokeOnRenderContext$0():returned`.
    - `notifyRenderStateQueue()` sólo reenvía a `SpriteRenderer.instance` (instance null en boot) ⇒ nunca
      existe `notifyAll` del `waitLock` de `RenderContextQueueItem`, que es donde el bytecode del juego hace
      `synchronized (waitLock) { if (!isFinished()) waitLock.wait(); }`.
14. Bloqueo actual (Ronda 30, nada simulado): **un fiber queda parkado y ninguno avanza**. El hilo de juego
    llama `RenderThread.invokeQueryOnRenderContext()` y espera `RenderContextQueueItem.waitLock`; con
    `swapCount:2` fijo, `dispatched:0` y contadores idénticos tras 12 s extra, no hay progreso de ningún
    fiber (`state.phase:"starting"`, sin excepción nueva ni timeout). El menú sigue sin verse, así que **no**
    se crea ninguna señal de menú. La causa exacta de la suspensión del fiber de render sigue **sin resolver**
    (ver 13); la hipótesis principal es que quedó suspendido dentro de una llamada asíncrona no instrumentada
    después de `Thread.yield()`, o en nuestro propio `JdkSupport.yield()`, y por eso nunca vuelve a
    `flushInvokeQueue()` para liberar el `waitLock`.
15. Siguientes pasos concretos (Ronda 31), en orden:
    a. **Contar, no deduplicar**: agregar a `port.shim.BrowserProgress` un contador por label
       (`globalThis.pzGameCounts`, `counts[label]++` en cada entrada) y exponerlo en los diags. Esto resuelve
       la limitación de 13 y permite decidir con una sola corrida de ~250 s.
    b. Instrumentar (solo entrada/retorno) `org.lwjglx.opengl.Display` (`processMessages`, `update`,
       `isCloseRequested`, `swapBuffers`), `zombie.core.SpriteRenderer` y `java.lang.Object`
       (`wait`, `wait(J)`, `notify`, `notifyAll`). Son clase TeaVM propia en el caso de `Object`, así que se
       observa sin tocar el juego.
    c. Con los contadores, distinguir los dos casos: si `Thread#yield():entered` crece ⇒ el render fiber
       itera y el problema es que `flushInvokeQueue` no invoca el item pendiente (mirar el `synchronized` de
       `flushInvokeQueue`/`hasContext`); si no crece ⇒ el fiber está suspendido y hay que encontrar la última
       entrada sin retorno (`Display.processMessages()` es el candidato principal).
    d. Sólo después de eso, evaluar un puente propio de `Display.processMessages()`/`Display.sync()` si el
       park resulta estar en código de plataforma (LWJGL/ GLFW), que es terreno del port. **No** modificar
       `RenderContextQueueItem.waitUntilFinished()`: es concurrencia real del juego; si algún día se puentea,
       documentarlo en `docs/decisions.md` y nunca como un stub que simule éxito.
    e. Build `--with-game` en primer plano (~7 min) y suite real con `PZ_REAL_TEST_PORT=8093`; actualizar los
       asserts de la spec según el nuevo stop real (nunca relajar Bullet/GL/ventana/ícono).
