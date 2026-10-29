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
16. Ronda 31 (2026-10-28 07:2x-07:3x -03), paso a+ (cambio 1): `port/src/main/java/port/shim/BrowserProgress.java`
    — el JSBody de `record` ahora además mantiene `globalThis.pzGameCounts`: `counts[label]++` y
    `counts[label+'#last']=t`. La lista deduplicada `pzGameProgress` sigue intacta (el reporte/suite la lee),
    así que el cambio es observación pura y el storage queda acotado por el conjunto de labels.
17. Ronda 31, paso b (cambio 2): `port/src/main/java/port/compiler/StartupProgress.java` — agregados
    `EXTRA_CLASSES` (`org.lwjglx.opengl.Sync`, `zombie.core.opengl.RenderContextQueueItem`) y
    `EXTRA_METHODS` con entradas/salidas de: ciclo de plataforma del render loop (`Display.processMessages/
    update/update(Z)/sync(I)/swapBuffers/isCloseRequested/getWidth/getHeight`), el handoff atascado
    (`RenderContextQueueItem.invoke/waitUntilFinished/notifyWaitingListeners`), `SpriteRenderer`
    (`notifyRenderStateQueue/renderQueued/NewFrame/isWaitingForRenderState/waitForReadyState/waitForReadySlotToOpen`),
    los monitores de TeaVM post-`@Rename` (`java.lang.Object#wait/#wait(J)/#wait(JI)/#notify/#notifyAll`),
    nuestro `JdkSupport#yield/onSpinWait`, y el scheduler propio de TeaVM (`Fiber.suspend/start/resume`,
    `EventQueue.offer/processSingle/stop`). Descriptores verificados con `javap` sobre el jar fijo y con las
    sources de TeaVM 0.16.0 (`TObject` usa `@Rename("wait")` sobre `wait0`, y `waitImpl(JI,AsyncCallback)` es
    la que suspende; `TThread.sleep` es `@Async` vía `EventQueue.offer(SleepHandler, now+millis)`).
    `mvn -f port/pom.xml compile` con `-Xmx8g` y `-Dmaven.repo.local=$PZ_WEB_ROOT/.toolchain/m2`: **OK** (rc=0).
    Nota de entorno: `mvn` sin `-Dmaven.repo.local` falla con "Could not create local repository at
    /home/agente47/.m2/repository" (la sandbox no permite escribir ahí); siempre usar el repo de `.toolchain/m2`.
18. Ronda 31, paso b2 (cambio 3): se agregaron además a `EXTRA_METHODS` las fronteras del scheduler propio de
    TeaVM (`org.teavm.runtime.Fiber#suspend/start/resume`, `EventQueue#offer(Event)/offer(Event,J)/
    processSingle/stop`) porque el stop medido es un `Thread#sleep(J)V:entered` que ningún timer reactiva.
    Diag nuevo `port/target/diag45.mjs`: muestrea a los 170 s y 25 s después y reporta **contadores** por
    familia de labels (`Thread#yield`, `JdkSupport#yield`, `Thread#sleep`, `Sync#sync`, `Display#*`,
    `flushInvokeQueue`, `RenderContextQueueItem#invoke/waitUntilFinished`, `Object#wait/notify`,
    `Fiber#*`, `EventQueue#*`) + delta, `swapCount`, GL total, `dispatchedEvents`, phase y cola del anillo.
19. Ronda 31, **error registrado en AGENT_LEARNINGS.md**: `scripts/build.sh --with-game` con
    `MAVEN_OPTS=-Xmx8g` (sin `-Xss16m`) dio `BUILD FAILURE ... compile-wasm-gc ... StackOverflowError`
    a los 6:07 min y dejó `port/target/web/game/build-state.json` en `success:false`, `status:
    "compiler-failed"`, `wasm_bytes:0` y **sin** `boot.wasm` (el round30 quedó borrado). Causa: env.sh
    pone `-Xmx20g -Xss16m` y asignar `MAVEN_OPTS` reemplaza ambos. Ahora se usa `MAVEN_OPTS="-Xmx8g -Xss16m"`.
    Segundo intento en curso: `build-round31b-game.log`.
20. Ronda 31, build y diag (cambio 4): `build-round31b-game.log` con `MAVEN_OPTS="-Xmx8g -Xss16m"` → **BUILD
    SUCCESS** en 6:43 min; `build-state.json success:true`, `boot.wasm` 143,455,368 B, classes 10218, methods
    72042, pico RSS 6.97 GiB. `diag45.mjs` (dev-server 8092, muestra A t≈170 s, B +25 s) dio el stop exacto,
    con **contadores** (todo idéntico A vs B, `phase:"starting"`, `swap:2`, `gl:267`, `dispatched:0`):
    - `RenderThread#renderLoop():entered` = **1** (nunca retornó).
    - El loop de render iteró **exactamente 64 veces** y paró ahí: `renderStep`, `flushInvokeQueue`,
      `Display#processMessages`, `Display#isCloseRequested`, `SpriteRenderer#waitForReadyState` y
      `Thread#yield`/`JdkSupport#yield` = **64 entradas/64 (o 63) retornos**. 64 = `YIELD_GRANT`: nuestra
      cedencia rate-limitada suspende en la llamada 64.
    - `port.runtime.JdkSupport#yield():entered` 64 → en la 64 llama `Thread.sleep(0L)`:
      `Thread#sleep(J)V:entered` = **1** y **ningún `returned`**. El fiber de render quedó suspendido en ese
      `sleep(0)` y **nunca fue reanudado** (`Fiber#suspend:entered`=2 con 0 retornos; `Fiber#start():returned`=2
      ⇒ los dos fibers devolvieron al event loop de JS; `EventQueue#offer(Event,J)` = 1; `Fiber#resume` = 0).
    - El fiber del juego, después, llegó a `RenderContextQueueItem#waitUntilFinished():entered`=1 y
      `Object#wait/#wait(J)/#wait(JI):entered`=1 (0 retornos) → parkado en `waitLock`. `RenderContextQueueItem#invoke`
      = **0**: el item encolado jamás se ejecutó, así que nunca hay `notifyAll` del `waitLock`.
    - `SpriteRenderer#notifyRenderStateQueue()` 65 y `java.lang.Object#notifyAll()` = 4,227,205 llamadas
      (todas antes del park): existe un `notifyAll` masivo pero **no** del `waitLock` del item pendiente.
    Conclusión (sin inferencias): **el port se detiene en la primera suspensión real del fiber de render**,
    que es nuestro propio `JdkSupport.yield()` → `Thread.sleep(0L)`. Ningún timer de TeaVM (`teavmAsync.offer`
    = `setTimeout`) lo reanudó, por eso `flushInvokeQueue` nunca vuelve a correr y el hilo de juego queda
    parkado en `waitLock`. El interbloqueo de la ronda 30 era este síntoma, no el `waitLock` en sí.
21. Ronda 31, paso 1 (cambio 5): `port/web/game.mjs` — envoltorio **de observación** del import
    `teavmAsync.offer/kill` usando el hook documentado `installImports` del loader de TeaVM
    (`TeaVM.wasmGC.load(url, {installImports})`). Cuenta ofertas, timers que disparan, errores del
    callback rethrown y `kill`s, en `globalThis.pzAsync`. Además se envolvió `globalThis.setTimeout`
    para registrar **todos** los timers de la página con su stack de origen (`globalThis.pzTimers`).
    Diags nuevos `port/target/diag46..49.mjs` (misma muestra de 170 s + 25 s).
22. Ronda 31, **medición decisiva** (diag49): la página crea **exactamente UN `setTimeout`** en toda
    su vida — `delay 0`, stack `org.teavm.runtime.EventQueue::offerWasmGC` → `offer` (game.mjs) →
    `setTimeout` — y ese es el `EventQueue.offer` de `TThread.start()` del renderThread
    (`EventQueue#offer(Event,J)` = 1 entrada/1 retorno, t≈160.479 s). `pzAsync`: offers=1, fired=1,
    errors=0, kills=0. Después: el renderLoop itera 64 veces, en la 64 `JdkSupport.yield()` llama
    `Thread#sleep(J)V` (entered=1, **nunca** retorna) y **no se crea ningún otro timer**
    (`timers.calls` sigue en 1). `Fiber#suspend:entered`=2 con 0 retornos y `Fiber#resume`=0.
    Conclusión: **la suspensión de `Thread.sleep(0L)` no registra ningún timer**, por eso ningún
    `setTimeout` la reanuda. No es un `setTimeout` perdido ni un error del callback: la llamada que
    debería ofrecer el `SleepHandler` nunca llega a `EventQueue.offerWasmGC`.
23. Ronda 31, paso 2 (cambio 6): `port/src/main/java/port/compiler/StartupProgress.java` — para ver
    qué rama corre, se instrumentan las clases propias de TeaVM `java.lang.Thread$SleepHandler`
    (`org.teavm.classlib.java.lang.TThread$SleepHandler` post-`@Rename`) y
    `org.teavm.runtime.Fiber$AsyncCallbackImpl` (clases completas, sin adivinar descriptores), más
    los métodos `Thread#sleep(JLorg/teavm/interop/AsyncCallback;)V`, `Platform#schedule(...)` y
    `Platform#postpone(...)`. Descriptores verificados con `javap` sobre
    `.toolchain/m2/org/teavm/teavm-classlib/0.16.0/teavm-classlib-0.16.0.jar` y `teavm-core-0.16.0.jar`
    (`TThread$SleepHandler(TThread,AsyncCallback)`, `Fiber$AsyncCallbackImpl.complete(Object)/error(Throwable)`,
    `Platform.schedule(PlatformRunnable,I)`, `EventQueue.offer(Event)` **existe** y por eso
    `Platform.postpone` (que la llama) también).
    **Error registrado en AGENT_LEARNINGS.md**: incluí `EventQueue#offer(Lorg/teavm/runtime/EventQueue$Event;)I`
    dos veces en `Set.of` (una ya estaba del paso b) y el build falló en 16 s con
    `ExceptionInInitializerError: duplicate element: ...` (falla temprano, no 6 min). Arreglado
    eliminando el duplicado; rebuild `build-round31c-game.log`.
24. Ronda 31, paso 3 (cambio 7) `diag50.mjs` + corrida 170 s (dev-server 8092): **confirmado con contadores**
    que la instrumentación de `Thread$SleepHandler` y `Fiber$AsyncCallbackImpl` (ítem 23) produce **cero
    fronteras**, mientras `boot.wasm` sí contiene los strings de esas etiquetas (`Thread$SleepHandler#<init>`,
    `#run`, `#interrupted`, `Fiber$AsyncCallbackImpl#complete/error/removePendingCall`) ⇒ el transformer las
    compiló pero **nunca se ejecutaron**: TeaVM 0.16.0 no llega a esa implementación en este `sleep`. Mismo
    sample: `timers.calls=1`, `async.offers=1/fired=1/errors=0/kills=0`, `JdkSupport#yield` 64 entradas/63
    retornos, `Thread#sleep(J)V:entered`=1 sin retorno, `Fiber#suspend:entered`=2 con 0 retornos,
    `Fiber#resume`=0, `Object#wait*`=1 sin retorno, `RenderContextQueueItem#invoke`=0, `Object#notifyAll`
    4,227,205, `totalLabels=483`, `totalEvents=51,374,774`, `phase:"starting"`, `swap:2`, `gl:267`.
25. Ronda 31, paso 4 (cambio 8, **arreglo real, no stub**): `port/src/main/java/port/shim/BrowserYield.java`
    nuevo — `@Async pause(int millis)` cuyo `AsyncCallback` se completa desde un `setTimeout` real del
    navegador (`@JSBody`). `port/runtime/JdkSupport.yield()` ahora suspende con `BrowserYield.pause(0)` en vez
    de `Thread.sleep(0L)`, que era la suspensión que **no registraba ningún timer**. No simula éxito: si el
    timer no dispara, el fiber queda parkado y los diags lo muestran igual que antes. `mvn -f port/pom.xml
    compile` con `-Xmx8g -Xss16m` y `-Dmaven.repo.local=$PZ_WEB_ROOT/.toolchain/m2`: **OK** (rc=0, ~5 s).
26. Ronda 31, paso 5 (cambio 9): `StartupProgress.EXTRA_CLASSES` incluye ahora `port.shim.BrowserYield` para
    contar suspensiones/reanudaciones del handoff propio. Build `--with-game` `build-round31d-game.log` en curso.
27. Ronda 31, paso 6 (build + diag): `build-round31e-game.log` **BUILD SUCCESS** (5:47 min, rc=0), `boot.wasm`
    143,461,083 B, classes 10222, methods 72052, pico RSS 6.97 GiB. `diag52.log` (170 s + 25 s, dev-server 8092)
    muestra el **avance real** del handoff nuevo: `port.shim.BrowserYield#pause(I)V:entered=1`,
    `BrowserYield#lambda$pause$0:entered=1`, `Fiber$AsyncCallbackImpl#complete:entered=1`,
    `Fiber$AsyncCallbackImpl#removePendingCall:entered/returned=1`, `Thread#setCurrentThread:entered/returned=1`
    y **`org.teavm.runtime.Fiber#resume():entered=1` + `Fiber#start():entered=3`** ⇒ el fiber de render **sí es
    reanudado** por el timer (antes: `Fiber#resume=0`, `Fiber#start=2`). `timers.calls=2/fired=2`.
28. **Error registrado en AGENT_LEARNINGS.md**: el timer del functor llamaba `resume.complete()` →
    `TypeError: resume.complete is not a function` (pageerror). Un `@JSFunctor` de TeaVM es la función JS en sí;
    corregido a `resume()` (misma convención que `BrowserPersistence`).
29. Ronda 31, paso 7 (crash nuevo, medido con `diag53.mjs`): tras el fix del functor aparece
    `RuntimeError: dereferencing a null pointer` con stack
    `java.lang.String::length <- org.teavm.jso.impl.wasmgc.WasmGCJSRuntime::stringToJs <- port.shim.BrowserProgress::record
    <- port.shim.BrowserYield::pause <- JdkSupport::yield <- Thread::yield <- RenderThread::renderLoop <-
    MainScreenState::main <- Fiber::runMain <- Fiber::resume <- Fiber$AsyncCallbackImpl::complete <-
    BrowserYield$pause$lambda$_2_0::complete$exported$0 <- game.mjs setTimeout`.
    El anillo muestra `null` en 3 entradas: **al reanudarse el fiber, TeaVM re-ejecuta el cuerpo instrumentado
    desde el medio y las cadenas que el transformer inyectó todavía no están materializadas** (son `null`), y
    `record` las pasaba a la conversión JS. Arreglado en `BrowserProgress`: `record`/`trace` convierten `null`
    en el centinela `"(resumed-frame)"` (observación propia del port, no cambia el juego). `mvn compile` OK.
30. Ronda 31, build `build-round31f-game.log` en curso con el fix del `null`. Suite real previa
    (`verification-round31-real.log`, 10:23-10:26, `PZ_REAL_TEST_PORT=8093`): **4/5 pasó**; `game-main` falló
    **sólo** por `expect(pageErrors).toEqual([])` con `["dereferencing a null pointer"]` (ítem 29). Los demás
    asserts de esta ronda siguen vigentes (DebugOptions/PZXmlUtil/GameProfiler/VoiceManager/JAXB/BrowserDom,
    `invokeQueryOnRenderContext` entered y no returned, ventana/ícono/GL, oshi `Unsupported platform`, Bullet).
31. Ronda 31, paso 8 (dos fixes finales, `mvn compile` OK rc=0):
    a. `port/shim/BrowserProgress.java`: `record`/`trace` ahora **omiten** la frontera cuando `label == null`
       (`if (label != null) recordNative(label)`). La variante anterior (sustituir por la constante
       `"(resumed-frame)"`) **no arregló nada**: build `build-round31f-game.log` (BUILD SUCCESS, boot.wasm
       143,461,172 B) dio el mismo `RuntimeError: dereferencing a null pointer` en `String::length <-
       WasmGCJSRuntime::stringToJs <- record` (diag54), porque una constante String nueva se materializa con
       el mismo mecanismo diferido. Por eso ahora se salta, sin crear cadenas.
    b. `port/shim/BrowserYield.java`: se **desinstrumentó** el método que suspende (quitado de
       `StartupProgress.EXTRA_CLASSES`) y el conteo pasa al lado JS dentro del propio `@JSBody`:
       `globalThis.pzYield = {pauses, resumes}`. Un contador JS no puede crashear el módulo.
32. Ronda 31, cierre (build `build-round31g-game.log` con ambos fixes; en curso al cortar la ronda).
    **No** se creó `MENU_REACHED`: el menú sigue sin verse (`phase:"starting"`, `swapCount:2`, `gl:267`,
    `dispatched:0`, `menuRendered:false`).

## Ronda 31 (cierre 10:50) — iniciada 06:57:04 (-03), límite de corte 10:57

**Archivos cambiados en esta ronda** (ningún commit propio; el vigilante commitea):
- `port/src/main/java/port/shim/BrowserProgress.java` — contadores por label (`globalThis.pzGameCounts`)
  y, al cierre, `record`/`trace` omiten las fronteras con `label == null` (crash WasmGC en fiber reanudado).
- `port/src/main/java/port/compiler/StartupProgress.java` — `EXTRA_CLASSES`/`EXTRA_METHODS` nuevos
  (ciclo de plataforma del render loop, `RenderContextQueueItem`, `SpriteRenderer`, monitores TeaVM
  post-`@Rename`, `Fiber.suspend/start/resume`, `EventQueue.offer/processSingle/stop`, `Thread$SleepHandler`,
  `Fiber$AsyncCallbackImpl`, `Platform.schedule/postpone`); al cierre se **retiró** `port.shim.BrowserYield`
  de la instrumentación (es el método que suspende y ahí las cadenas del transformer son `null`).
- `port/src/main/java/port/shim/BrowserYield.java` — **nuevo**: `@Async pause(int)` cuyo `AsyncCallback`
  completa un `setTimeout` real del navegador; `globalThis.pzYield = {pauses,resumes}` en el propio `@JSBody`.
- `port/src/main/java/port/runtime/JdkSupport.java` — `yield()` suspende con `BrowserYield.pause(0)`
  en lugar de `Thread.sleep(0L)`.
- `port/web/game.mjs` — wrapper observacional del import `teavmAsync.offer/kill` (`installImports`) y de
  `globalThis.setTimeout` (`globalThis.pzTimers` con stack de origen).
- Diags nuevos `port/target/diag45..53.mjs` + logs `diag45..54.log`, builds `build-round31{b..g}-game.log`.

**Última etapa alcanzada hoy (verificada con diag52/diag53 y suite real):** el arranque llega a
`MainScreenState#main` → `Display.init/create` → `RenderThread#renderLoop()` con **64 iteraciones reales**
(`Display.processMessages/isCloseRequested/getWidth/getHeight`, `flushInvokeQueue`, `notifyRenderStateQueue` = 64/65),
luego el fiber de render se suspende en el handoff propio y **por primera vez es reanudado**:
`BrowserYield#lambda$pause$0:entered=1` → `Fiber$AsyncCallbackImpl#complete:entered=1` →
`removePendingCall:entered/returned=1` → `Thread#setCurrentThread:entered/returned=1` →
**`Fiber#resume():entered=1`** y `Fiber#start():entered=3` (ronda 30: `resume=0`, `start=2`), con
`timers.calls=2/fired=2`. El error exacto donde se detiene hoy es:
`RuntimeError: dereferencing a null pointer` en `java.lang.String::length <-
org.teavm.jso.impl.wasmgc.WasmGCJSRuntime::stringToJs <- port.shim.BrowserProgress::record <-
port.shim.BrowserYield::pause <- JdkSupport::yield <- Thread::yield <- RenderThread::renderLoop`,
disparado **durante la reanudación** (pila `Fiber::resume <- Fiber$AsyncCallbackImpl::complete <-
BrowserYield$pause$lambda$_2_0::complete$exported$0 <- game.mjs setTimeout`). Fix aplicado al cierre
(ítem 31): no registrar fronteras con `label == null` + desinstrumentar `BrowserYield`.

**Estado del juego tras la reanudación:** todavía `state.phase:"starting"`, `swapCount:2`, `gl:267`,
`dispatched:0`, `menuRendered:false`; `RenderContextQueueItem#invoke=0` y el hilo del juego sigue parkado en
`Object#wait` de `waitLock` (`waitUntilFinished():entered=1`). **No** existe `MENU_REACHED`.

**Bloqueo actual:** la reanudación del fiber de render funciona, pero el `pageerror` nulo del
instrumentador abortaba la corrida; con el fix del cierre queda por comprobar si el loop vuelve a iterar
y si `flushInvokeQueue()` ejecuta el item pendiente (lo que liberaría el `waitLock` del hilo de juego).

**Siguientes pasos concretos (ronda 32), en orden:**
1. Confirmar `build-round31g-game.log` (BUILD SUCCESS + `build-state.json success:true`) y servirlo en 8092.
2. `PZ_DIAG_PORT=8092 node port/target/diag53.mjs` (200 s): exigir `pageErrors` vacío,
   `pzYield.pauses/resumes > 1` y `timers.calls > 2`; si `JdkSupport#yield():entered` crece > 64, el loop
   re-itera. Ahí mirar `RenderContextQueueItem#invoke` y `Object#notifyAll` del `waitLock`.
3. Si el loop re-itera pero `invoke` sigue en 0: el `synchronized` de `RenderThread.flushInvokeQueue` /
   `hasContext` es el siguiente candidato (observar `Sync#sync`, `Display#sync/update`, `hasContext`).
   **No** modificar `RenderContextQueueItem.waitUntilFinished()` (concurrencia real del juego).
4. Suite real `PZ_HOME=... PZ_REAL_TEST_PORT=8093 npx playwright test --config playwright.game.config.mjs`
   y actualizar `tests/browser/game-main.spec.mjs`: quitar los asserts de contadores congelados (`frozen.after
   .progress === frozen.before.progress`) si el loop vuelve a avanzar, y reubicar la captura
   (`test-results/game-main-round31.png`) + histograma de píxeles con python; `screenshot_visually_checked:false`
   salvo que se inspeccione la imagen. Nunca relajar Bullet/GL/ventana/ícono/oshi.
5. Recordar límite de la spec: `test.setTimeout(330_000)` y que el `webServer` de `playwright.game.config.mjs`
   usa `reuseExistingServer:false` (puerto 8093 propio, no reutilizar 8092).

**Pruebas de esta ronda:**
- `mvn -f port/pom.xml compile` con `-Xmx8g -Xss16m`: OK (rc=0, ~4-5 s) tras cada cambio.
- Builds `--with-game`: round31b/c/e/f **BUILD SUCCESS**; round31a falló por StackOverflow (ver AGENT_LEARNINGS);
  `build-round31g-game.log` en curso al cerrar.
- `npm test` **19/19 OK**; `python3 -m unittest discover -s tests/python` **OK** (10).
- Suite real `verification-round31-real.log` (10:23-10:26, puerto 8093): **4/5**; `game-main` falló sólo por
  `expect(pageErrors).toEqual([])` con `["dereferencing a null pointer"]` (fix en ítem 31).
33. Ronda 31, cierre real (10:50 -03): `build-round31g-game.log` **BUILD SUCCESS** (rc=0, 6:33 min),
    `build-state.json success:true`, `boot.wasm` **143,460,420 B**, classes 10222, methods 72052, pico RSS
    6.67 GiB. El dev-server 8092 ya lo sirve (ETag nuevo `W/"88d0844-1a182c72be9.f0a"`). Intento de verificación
    rápida con `diag53.mjs` (espera 45 s) quedó cortado por el límite de la ronda (`timeout 120`, rc=124):
    **quede pendiente confirmar que el `pageerror: dereferencing a null pointer` desapareció** con el fix del
    ítem 31. Suites rápidas del cierre: `npm test` 19/19 OK y `python3 -m unittest discover -s tests/python` OK.

## Ronda 32 — iniciada 10:53:37 (-03), límite de corte 13:53
1. Paso 0 (verificación pendiente de la ronda 31): `build-round31g-game.log` **BUILD SUCCESS** ya confirmado
   (rc=0, 6:38 min, `build-state.json success:true`, `boot.wasm` 143,460,420 B, classes 10222, methods 72052,
   pico RSS 6.67 GiB) y el dev-server 8092 lo sirve (ETag `W/"88d0844-1a182c72be9.f0a"`).
2. `diag56.log` (150 s, dev-server 8092): el `pageerror` **SIGUE** presente. Stack nuevo:
   `RuntimeError: dereferencing a null pointer` en `java.lang.String::length <-
   org.teavm.jso.impl.wasmgc.WasmGCJSRuntime::stringToJs <- port.shim.BrowserProgress::record <-
   org.teavm.runtime.Fiber::suspend <- port.shim.BrowserYield::pause <- JdkSupport::yield <-
   Thread::yield <- RenderThread::renderLoop <- MainScreenState::main <- Fiber::runMain <- Fiber::resume <-
   Fiber$AsyncCallbackImpl::complete <- BrowserYield$pause$lambda$_2_0::complete$exported$0`.
   Conclusión (diagnóstico correcto del fix de la ronda 31): la guarda `label != null` **no alcanza** y el
   problema no es sólo `BrowserYield`: **toda cadena inyectada por el transformer en una frame que se
   reanuda después de un suspend** (ahí: la inyección `:returned` de `Fiber#suspend`) llega sin
   materializar y crashea el módulo. Por eso el crash sólo subió un frame al desinstrumentar `BrowserYield`.
3. Cambio de enfoque (código, no más análisis): la instrumentación de fronteras pasa de cadenas a
   **enteros**. `port.shim.BrowserProgress.pulse(int bucket)` (contador JS por bucket, sin conversión de
   String); `StartupProgress` emite `pulse(id)` en lugar de `record/trace(String)` y registra el mapa
   id→label en `port/target/reports/pulse-map.json` para que los diags decodifiquen. Un `int` no puede ser
   null ni depender de materialización diferida, así que las fronteras del ciclo suspend/resume dejan de
   crashear.
4. Paso 1 (cambio 1, `mvn -f port/pom.xml compile` **OK** rc=0, 3.6 s): la instrumentación de fronteras deja de
   pasar Strings al JS. `port/shim/BrowserProgress.java` reemplaza `record(String)`/`trace(String)` por un único
   `pulse(int bucket)` (`@JSBody`) que resuelve la etiqueta en JS desde `globalThis.pzPulseLabels` y mantiene la
   misma forma de `pzGameCounts`, `pzGameProgress` (dedup) y `pzGameTrace` (anillo 400). `StartupProgress.record()`
   ahora inyecta `IntegerConstantInstruction` + `InvokeInstruction(pulse, ValueType.INTEGER, VOID)`; se agregó
   `LABELS`/`idOf()`/`pulseMapJson()`. `BuildCompiler` escribe el mapa id→etiqueta en
   `target/web/game/pulse-map.json` (+ `reports/` y `target/`) incluso si el build falla. `port/web/game.mjs`
   hace `fetch('/game/pulse-map.json')` y setea `globalThis.pzPulseLabels` **antes** de cargar `boot.wasm`.
   Nota de API verificada con `javap` de teavm-core 0.16.0: `ValueType.INTEGER` (no `INT`) y
   `InvokeInstruction.setArguments(Variable...)`.
5. Paso 2 (fix, `mvn compile` **OK** rc=0): el primer `build-round32-game.log` falló en TeaVM con
   `ERROR: port.shim.BrowserProgress.pulse(I)V: Method is not annotated with org.teavm.interop.Import`.
   Causa: `pulse` era `@JSBody native` y el transformer le inyectaba llamadas (las llamadas agregadas
   tras la fase JSO no se retransforman). Fix: wrapper Java `pulse(int)` que llama `pulseNative(int)`
   (`@JSBody`). Registrado en AGENT_LEARNINGS.md. Nota: el mapa ya se escribió (`pulse-map.json`
   164,907 B con el mapa de etiquetas) antes del fallo, confirmando el wiring de `BuildCompiler`.
   Otro incidente de esta ronda: un `pkill -f 'scripts/build.sh'` mató mi propio build (patrón
   coincidente) y un `setsid ... &` dejó el build muerto al cerrar la sesión; el build se relanza
   siempre en primer plano. RAM disponible en este momento: ~0.2 GiB libre + swap 10 GiB (Strata
   ~19-20 GiB); el build pico 6.7 GiB puede ir a swap, por eso se corre solo, sin Chrome al lado.
6. Paso 3 (cambio, `mvn compile` **OK** rc=0, 3.6 s a 11:38): se elimina **toda** la instrumentación de la
   maquinaria suspend/resume de `StartupProgress`, que es la causa común de los dos crashes
   (ronda 31 `dereferencing a null pointer`, ronda 32 `array element access out of bounds` en
   `Fiber::popInt <- Thread::currentThread <- Fiber::suspend`): el transformer inyecta código en la
   frame que TeaVM reentra al reanudar y corrompe el stack de fibras WasmGC. Fuera: `java.lang.Thread`
   (CLASSES), `java.lang.Thread$SleepHandler`, `org.teavm.runtime.Fiber$AsyncCallbackImpl` (EXTRA_CLASSES),
   y de EXTRA_METHODS `org.teavm.runtime.Fiber#suspend/start/resume`, `EventQueue#offer/processSingle/stop`,
   `java.lang.Object#wait/notify/notifyAll`, `Thread#sleep(J,AsyncCallback)`, `Platform#schedule/postpone`.
   También fuera los métodos async del classlib que pueden suspender: `ServiceLoader#load/loadIterator`,
   `Class#forName/getMethod`, `Method#invoke`, `ClassLoader#getResource`, `ThreadLocal#get/initialValue`.
   `zombie.core.opengl.RenderContextQueueItem` deja de instrumentarse por clase (incluye
   `waitUntilFinished`, que llama `Object#wait` y suspende); se observan en su lugar sus vecinos no
   bloqueantes: `alloc/resetInternal/setWaiting/isFinished` + `invoke/notifyWaitingListeners`.
   Suspend/resume/timers siguen medidos desde JS (`pzYield`, `pzTimers`, `pzAsync`, `pzRuntime`), que no crashea.
7. Paso 4 (diagnóstico clave con build de control): `diag58.log` (build round32b ya sin maquinaria
   suspend/resume) sigue con `array element access out of bounds` en `Fiber::popInt <-
   Thread::currentThread <- Fiber::suspend <- BrowserYield::pause`; los contadores del render fiber
   llegan a 64 iteraciones reales y `RenderContextQueueItem#invoke = 0`. Para decidir si el corruptor
   era la instrumentación, se agregó `PZ_PULSE_OFF=1` a `StartupProgress.transformClass` (desactiva
   TODAS las pulses) y se construyó `build-round32e-game.log` **BUILD SUCCESS** (`boot.wasm`
   143,197,771 B, pulse-map vacío). `diag60.log`: **el mismo crash aparece sin ninguna pulse** →
   la instrumentación quedó descartada como causa; el corruptor es el propio handoff de enteros del
   `@Async` de `BrowserYield.pause(int)` (TeaVM guarda/reresta los `int` del método suspendido con
   `Fiber::pushInt`/`popInt` y el restore tras el resume lee de más).
8. Paso 5 (fix, `mvn compile` **OK** rc=0 a 12:22): `port.shim.BrowserYield` pasa a `@Async pause()`
   **sin parámetro** (misma forma que `BrowserPersistence.flush()`, el otro `@Async` que sí funciona
   aquí), y `JdkSupport.yield()` llama `pause()`. El delay queda en `0` dentro del `@JSBody`.
   Build `--with-game` `build-round32f-game.log` en curso; luego `diag58.mjs` de nuevo.
9. Paso 6 (causa raíz de TeaVM establecida con código de la librería, no adivinado): se decompiló
   `org.teavm.runtime.Fiber` (teavm-core 0.16.0, vineflower en `/tmp/fiberx`) y `TThread`
   (teavm-classlib 0.16.0). `Fiber.suspend()` enlaza contra **un único `static Fiber.lastPendingCall`**,
   y `AsyncCallbackImpl.complete()` lo desenlaza. Con dos fibras parkadas a la vez (render en
   `yield`, juego en `RenderContextQueueItem#waitUntilFinished -> Object#wait`), la continuación async
   de una reanuda con la cola corrupta y el restore de enteros del método `@Async` hace
   `popInt` fuera de rango → el `RuntimeError` de las rondas 31/32. Por eso el `pause()` sin
   parámetro tampoco lo arregló.
10. Paso 7 (fix, `mvn compile` **OK** rc=0 a 12:40): el yield deja de suspender por defecto.
    `BrowserYield.suspendDecision(int)` lee `globalThis.pzYieldSuspend` y `JdkSupport.yield()` sólo
    suspende si `?suspend=1` (diagnóstico). `Thread.yield` es una pista de scheduler: no cambia
    reglas del juego y el render fiber puede seguir iterando (y descargar el item de render cuando
    el game fiber lo espera). Build `--with-game` `build-round32g-game.log` en curso (~6.5 min).
11. Paso 8 (puerta del yield + verificación): las pulses ahora inyectan `BrowserYield.setRenderPending(1/0)`
    en los retornos de `RenderContextQueueItem#setWaiting()` y `#invoke()` (estado en
    `globalThis.pzRenderItemPending`), y `suspendDecision()` sólo suspende si no hay un item de render
    en vuelo. `build-round32h-game.log` **BUILD SUCCESS** (13:02). `diag63.log` con esa build quedó
    **sin salida y `timeout` rc=124**: la pestaña no devuelve el control al loop de JS (el fiber render
    ya no suspende y el handoff se corre anidado), por eso Playwright no pudo `evaluate` ni capturar.
    Se agrega `port/target/diag64.mjs` (evaluate/captura con `Promise.race` y volcado de console/pageerror)
    para medir si el loop de JS queda bloqueado o si el anidamiento de `fiber.resume()` termina en
    `RangeError` de pila.
12. Paso 9 (medición de la puerta con build nuevo, `build-round32i-game.log` **BUILD SUCCESS** 13:16):
    con `pzYieldSuspend=true` (default en `game.mjs`) y la puerta `pzRenderItemPending`, `diag64` sigue
    con **1 pageerror** `RuntimeError: array element access out of bounds`. La puerta no alcanza: el
    primer `BrowserYield.pause()` del render fiber ocurre en la iteración 64 del loop, **antes** de que
    el game fiber haga `alloc/setWaiting`, pero el game fiber ya está parkado en su propio
    `Object#wait`/`Fiber#suspend` anterior, así que hay dos fibras con `Fiber.lastPendingCall` enlazado
    y la continuación async vuelve a hacer `popInt` fuera de rango. Con `pzYieldSuspend=false`
    (`?suspend=0`, medido con la build round32h): **0 pageerrors** pero la pestaña no devuelve el
    control al event loop de JS (evaluate/screenshot en timeout, log congelado en el WARN de GPU
    memory), o sea el render fiber acapara la pila y no hay frame ni menú.
13. Estado real del arranque en Chrome al cierre de la ronda 32 (verificado 13:17 con
    `port/target/diag64.mjs` sobre `boot.wasm` 143,215,350 B de round32i): el juego llega a
    `Display.create()` retornado y 64 iteraciones reales de `RenderThread#renderLoop`
    (`Display#processMessages/isCloseRequested/getWidth/getHeight` = 64, `flushInvokeQueue` = 64,
    `SpriteRenderer#notifyRenderStateQueue` = 65), y después se detiene en el primer
    `Thread.yield -> BrowserYield.pause -> Fiber#suspend` con el `RuntimeError` de arriba.
    El menú **no** está renderizado (`menuRendered:false`, `swap:2`, `gl:267`), así que **no** se creó
    `MENU_REACHED`.
14. Bloqueo actual: el modelo de fibras de TeaVM 0.16.0 en WasmGC tiene **un único**
    `Fiber.lastPendingCall` estático; con dos fibras bloqueadas a la vez (game fiber en
    `RenderContextQueueItem#waitUntilFinished -> Object#wait`, render fiber en `Thread.yield`) la
    reanudación de una lee de más el stack de enteros guardados del método `@Async`
    (`Fiber::popInt` -> `array element access out of bounds`). Confirmado con el build de control
    `PZ_PULSE_OFF=1` (cero pulses) y con `pause()` sin parámetro, y leyendo `org.teavm.runtime.Fiber`
    decompilado de teavm-core 0.16.0.
15. Siguientes pasos concretos (orden sugerido para la ronda 33):
    a) Atacar el handoff de dos fibras: hacer que **una sola** fibra use async. Opción más simple y
       honesta: sustituir el `Object#wait`/`waitLock` de `RenderContextQueueItem` por un puente propio
       del puerto (`port.runtime.JdkSupport` / shim) que entregue el control al render fiber de forma
       cooperativa sin suspender el game fiber; documentar en `docs/decisions.md` como decisión de
       plataforma (no cambia reglas del juego) y **nunca** simular el éxito del item: el item debe
       ejecutarse de verdad en el render fiber. Verificar con `RenderContextQueueItem#invoke()V > 0`.
    b) Alternativa: suspender el game fiber y **no** suspender el render fiber, pero con un `await`
       real desde JS (no busy-loop), por ejemplo `requestAnimationFrame`/`await` en el host para
       reanudar el fiber render desde fuera; requiere que `JdkSupport.yield()` suspenda sólo cuando el
       game fiber está parkado y que el host llame la reanudación desde un rAF.
    c) Cuando el loop avance: `RenderContextQueueItem#invoke()V > 0`,
       `MainScreenState#enter/render` y `GameStateMachine#render` aparecen en las pulses, `swap` sube
       de 2, y ahí sí captura + spec + `MENU_REACHED`.
    d) La spec `tests/browser/game-main.spec.mjs` todavía afirma `frozen.after.progress ===
       frozen.before.progress` y `frozen.after.swap === frozen.before.swap` (líneas ~78-79): hay que
       quitarlas cuando el loop vuelva a avanzar, y mantener intactos los asserts de Bullet/GL/ventana/
       ícono/oshi y `expect(pageErrors).toEqual([])`.
16. Cierre de la ronda 32 (13:17 -03). Suites rápidas del cierre (con `source scripts/env.sh`):
    `npm test` **19/19 OK**, `python3 -m unittest discover -s tests/python` **OK (10)**.
    `mvn -f port/pom.xml compile` **OK**; builds `--with-game`: round32b/c/d/e/f/g/h/i **BUILD SUCCESS**
    (el round32a original falló por el `@JSBody` instrumentado; ver AGENT_LEARNINGS). Sin commits y sin
    cambios en `.git` (el vigilante commitea). Sin `MENU_REACHED`.
    Nota para la próxima ronda: el flag de experimento `PZ_PULSE_OFF=1` sigue disponible en
    `StartupProgress.transformClass` para compilar una build de control sin fronteras.

## Ronda 33 — iniciada 13:18:21 (-03), límite de corte 17:18
1. 14:00 — Parche de scheduler en `scripts/prepare_teavm.py` (`patch_fiber()`): `AsyncCallbackImpl.complete/error`
   reemplaza el `fiber.resume()` anidado por `EventQueue.offer(new ResumeEvent(thread, fiber))` (macrotask, tiempo 0).
   Verificado: `python3 scripts/prepare_teavm.py` OK; el Fiber.java generado tiene 2 offers, 1 `fiber.resume()` (dentro de
   ResumeEvent.run) y conserva `growObjectStack`. `mvn -f port/pom.xml compile` **BUILD SUCCESS** (270 archivos).
   Registrado en `docs/decisions.md`.
2. 14:07 — Build grande `build-round33-game.log` **BUILD SUCCESS** con el parche: `boot.wasm` 143,216,983 B,
   classes 10223, methods 72056, pico RSS 5.95 GiB, `build-state.json success:true`. El dev-server 8092 ya lo sirve
   (ETag nuevo). Diag nuevo `port/target/diag65.mjs` (counts + trace + async/timers + captura con `Promise.race`).
3. 14:12 — `diag65.log` con la build parchada (resume diferido por EventQueue): sigue **1 pageerror**
   `RuntimeError: array element access out of bounds` en `Fiber::popInt <- Thread::currentThread <-
   Fiber::suspend <- BrowserYield::pause <- RenderThread::renderLoop`, con `pzAsync {offers:2, fired:2,
   errors:1}` y `pzYield {pauses:1, resumes:1}`. O sea el resume diferido no elimina el desbalance del
   int-stack: el corruptor no es sólo el anidamiento de `start()`. Para identificarlo con números se
   instrumenta el Fiber parchado (diagnóstico honesto, no simula éxito): `popInt` lanza
   `IllegalStateException("Fiber int-stack underflow…")` con id/estado/intTop/push/pop/suspend/resume/
   doubleResume de la fiber y de `Fiber.current`, `lastSuspendFiber`, `lastResumeFiber`, `startDepth`,
   `maxStartDepth`, `nestedStarts`. Build de medición `build-round33b-game.log` en curso.
   Nota: un parche previo dejó basura al final de `scripts/prepare_teavm.py` (líneas fuera de Python);
   corregido recortando el archivo tras `main()` — ver AGENT_LEARNINGS.
## Ronda 34 — iniciada 14:40:59 (-03), límite de corte 18:40
1. 14:45 — Ronda 34: aplicado el fix de la nota de Code en `port/compat/java/lang/ThreadGroup.java`: `WeakHashMap`→`IdentityHashMap` (claves Thread y grupos; no llama `hashCode/equals` virtuales), eliminados todos los `synchronized` del camino de `Thread.<init>/start/isAlive/getThreadGroup` y de `threads()/stackTraces()`. Motivo registrado en el comentario del archivo (monitores y colecciones hasheadas hacían `registerDefault` async → `Thread.<clinit>` async → `currentThread/setCurrentThread` corrutinas → `Fiber::popInt` fuera de rango).

## Ronda 35 — iniciada 14:59:30 (-03), límite de corte 18:59
0. Contexto verificado al arrancar: HEAD `d50b3af` (Ronda 34) trae el fix de `docs/notes-from-code.md` en `port/compat/java/lang/ThreadGroup.java` (IdentityHashMap, sin `synchronized`), pero **no hay build nuevo**: `port/target/web/game/build-state.json = {"success":false,"status":"running"}` y **no existe `boot.wasm`** (el build 33c fue cortado en `DEPENDENCY_ANALYSIS` por el corte manual de la ronda 33). RAM disponible real: 5 GiB (`free -g`), no 10 GiB → uso `MAVEN_OPTS=-Xmx8g -Xss16m` y no corro Chrome en paralelo con el build.
1. 15:00 — Agregado el **gate de build** que propone `docs/notes-from-code.md`: `scripts/wasmfn.py` (copia de `tools/code-agent/wasmfn.py`) y bloque en `scripts/build.sh` que escribe `port/target/reports/thread-fiber-gate.txt` y `exit 1` si `Thread::currentThread`/`setCurrentThread` tienen `fiberops: True`. `bash -n scripts/build.sh` OK. Registrado en `docs/decisions.md`.
2. 15:01 — Lanzado build grande `build-round35-game.log` (`--with-game`, `MAVEN_OPTS=-Xmx8g -Xss16m`, `PZ_HOME` exportado) en primer plano para validar el fix de ThreadGroup de la ronda 34. En curso.

## Ronda 37 — iniciada 15:27:09 (-03), límite de corte 18:57
0. Contexto verificado al arrancar: HEAD `8e0c260` (Ronda 36) solo agregó `docs/notes-from-code.md` (segundo diagnóstico de Code) + `tools/code-agent/probe8.py`. `supervisor.log`: ronda 36 cortada manualmente a las 15:27. RAM disponible real `free -g`: **4 GiB** (no 10) → `MAVEN_OPTS=-Xmx8g -Xss16m`, sin Chrome en paralelo con el build. `boot.wasm` sigue sin existir (build 33c cortado).
1. 15:30 — Aplicado el fix de Code tal cual, sin stubs:
   - `port/src/main/java/port/compiler/JdkCompatibility.threads()`: eliminada la inyección `ThreadGroup::registerDefault(this)` en `Thread.<init>`; en su lugar se agrega el campo público `$pzCreator` (tipo Thread) al ClassHolder de `java.lang.Thread` y se inyecta `call Thread::currentThread` + `putfield $pzCreator` antes de cada `ExitInstruction` del constructor (asignación plana, sin llamadas de clase).
   - `JdkCompatibility.transformClass` caso nuevo `"java.lang.ThreadGroup"`: cuerpos generados con `ProgramEmitter` para `creatorOf(Thread)Thread` = `getfield $pzCreator` y `forgetCreator(Thread)V` = `putfield $pzCreator = null`; se quita `NATIVE` de ambos.
   - `port/src/main/java/port/compat/java/lang/ThreadGroup.java`: borrado `registerDefault`; nuevo `private static Member member(Thread)` perezoso (usa `creatorOf`/`forgetCreator`; creador null ⇒ ROOT y started=true, como el viejo camino main); `beforeStart`, `alive` y `groupOf` usan `member(thread)`; declarados los dos natives. Comentario de causa raíz actualizado.
   - `mvn -q -f port/pom.xml compile` (con `source scripts/env.sh`, `MAVEN_OPTS=-Xmx8g -Xss16m`, repo `.toolchain/m2`): **BUILD SUCCESS** (EXIT=0).
2. 15:33 — `tools/code-agent/probe8.py` ya resuelve `asyncchain.py` junto a su propio directorio (antes hardcodeaba `/tmp/code-agent`); copiada esa lib a `tools/code-agent/asyncchain.py`. Pendiente de corrida: requiere `boot.wasm`.
3. Próximo: UN solo `bash scripts/build.sh --with-game` (PZ_HOME exportado, primer plano). Esperado: gate `fiberops: False` y `probe8.py ... 'java.lang.Thread::<clinit>'` ⇒ `no seed`.
4. 15:38 — Build grande **UN solo** `bash scripts/build.sh --with-game` (`PZ_HOME` exportado, `MAVEN_OPTS=-Xmx8g -Xss16m`, primer plano): `build-round37-game.log` **BUILD SUCCESS / EXIT=0** en 6:08 min. `boot.wasm` 143.113.066 B, classes 10209, métodos 71966 (compilados 94898), pico RSS 6.20 GiB, `build-state.json success:true`.
5. 15:38 — **GATE PASS** (antes nunca pasó con el juego): `port/target/reports/thread-fiber-gate.txt`
   ```
   java.lang.Thread::currentThread idx 349 size 11 calls: [] fiberops: False
   java.lang.Thread::setCurrentThread idx 5204 size 36 calls: ['teavm@currentTimeMillis'] fiberops: False
   ```
6. 15:39 — Verificaciones de Code sobre el artefacto real:
   - `python3 tools/code-agent/probe8.py port/target/web/game/boot.wasm 'java.lang.Thread::<clinit>'` ⇒ **`no seed`** (slots 23944, entradas 52806; visitados 1). Ya no hay camino async desde `Thread.<clinit>` a `Fiber::suspend`.
   - `probe5.py ... 'java.lang.Thread::<init>_1' 'java.lang.Thread::<clinit>'`: `<init>_1` fiber False y solo llama `Object::<init>` (fiber False) y `Thread::currentThread` (fiber False) + `Thread@initializer`; `<clinit>` fiber False y solo llama `DefaultUncaughtExceptionHandler::<init>` (False) y `Thread::<init>_3` (False). El `registerDefault` desapareció del wasm.
   - Copiado `/tmp/code-agent/probe5.py` a `tools/code-agent/probe5.py` con la misma resolución local de `asyncchain.py`.
7. 15:43 — Primera corrida en Chrome con el fix (`port/target/diag68.mjs`, port 8092, espera 170 s, captura con `Promise.race`): **0 pageerrors**. Desapareció el crash `RuntimeError: array element access out of bounds` en `Fiber::popInt <- Thread::currentThread` (histórico de las rondas 30-33) y tampoco aparece el `RangeError: Maximum call stack` de la build de diagnóstico 33b (diag67). El log del juego ahora avanza y muestra: `version=42.21.0 4a0e9546ec`, `GraphicsCard: ... ANGLE (SwiftShader)`, `OpenGL version: 3.3 (WebGL 2.0)`, `Display mode changed to 640x480`, `OpenGL 1.5 buffer objects supported`, `VSync: OFF`. Captura: `test-results/game-main-round37.png`.
8. 15:46 — `port/target/diag69.mjs` (mismo build, volcado de `pzGameProgress` + logs + trace): **0 pageerrors**, `pzGame {phase:'starting', menu:false, error:''}`, `pzFiberDiag:null`, 65 iteraciones de `Display#processMessages`, `pzGameCounts`: `RenderThread#flushInvokeQueue entered 65 / returned 64`, `RenderContextQueueItem#invoke entered 1 / returned 0`, `pzYield {pauses:1,resumes:1}`, `pzAsync {offers:2,fired:2,errors:0}`, `swap:2`, `gl:267`. El último frame del trace termina en `RenderThread#flushInvokeQueue -> RenderContextQueueItem#invoke()V:entered` (sin `returned`).
9. 15:58 — Paso 1 del handoff (diagnóstico de park): `scripts/prepare_teavm.py::patch_fiber()` ahora registra cada park real de fibra en `globalThis.pzFiberSuspend` (`reportSuspend` en la rama de park de `Fiber.suspend`, después del `isResuming()` para no marcar como parkada la replay del resume; captura la traza de pila JS con `new Error().stack`) y `reportResume` en `Fiber.resume()` marca `parked=false` con `resumeFrom`. `pendingDepth()` cuenta la cola `lastPendingCall`. Sin APIs suspendibles ni `StringBuilder` (aprendizajes rondas 32/33). `python3 scripts/prepare_teavm.py` regenera `port/target/generated-sources/teavm/org/teavm/runtime/Fiber.java` OK (`ast.parse` previo pasa).
10. 16:06-16:27 — Instrumentación del monitor de TeaVM para identificar el deadlock: nuevo `port/src/main/java/port/shim/BrowserMonitor.java` (`record(...)` wrapper + `@JSBody` que acumula `globalThis.pzMonitorLog`), y en `scripts/prepare_teavm.py::patch_monitor()` 9 puntos de registro (`monitorEnterWait` con callback, las 2 ramas de grant, la cola `enteringThreads`, `waitImpl`, `monitorExit` con release, `waitForOtherThreads`, el re-ingreso del listener tras `wait`, `notify0`, `notifyAll0`). El `@JSBody` vive en `port.shim` y no en `TObject`: la build falló primero con `java.lang.Object.reportMonitor(IIIIIII)V: Method is not annotated with org.teavm.interop.Import` (misma causa que el aprendizaje de la ronda 25 sobre clases renombradas `java.*`). Con el wrapper: `bash scripts/build.sh --with-game` **EXIT=0** (`build-round37c-game.log`, 6:37 min, `boot.wasm` 143.123.817 B, gate PASS con fiberops False). Nuevo `port/target/diag71.mjs` lee `pzMonitorLog` + `pzFiberSuspend` (trazas Java resumidas).

## Ronda 38 — iniciada 17:54:23 (-03), límite de corte 21:54 (documentar desde 21:24)
0. Contexto verificado al arrancar (18:27): `supervisor.log` → ronda 38 a las 17:54:23. Artefacto actual
   `port/target/web/game/boot.wasm` 143.125.544 B (17:01) del build `build-round37f-game.log` con
   `build-state.json success:true`, GATE PASS (`thread-fiber-gate.txt`: currentThread/setCurrentThread
   `fiberops: False`) y `probe8.py ... 'java.lang.Thread::<clinit>'` ⇒ **`no seed`** (slots 23944, entries 52806).
   No existe `build-round38*.log` todavía (todavía no hay build de esta ronda). RAM disponible real
   `free -g`: **4 GiB** → `MAVEN_OPTS=-Xmx8g -Xss16m`, un solo build en primer plano, sin Chrome en paralelo.
   Dev server `127.0.0.1:8092` sirviendo el artefacto actual. Punto de bloqueo heredado (ronda 37, diag69/diag71):
   0 pageerrors, juego llega a `Display#processMessages` x65 / `swap:2` / `gl:267` pero `menu:false`; dos fibras
   parkadas: game/main espera `RenderContextQueueItem::waitUntilFinished` (monitor `waitLock`) y la render fiber
   está parkada en `Object::monitorEnter` de ese mismo `waitLock` dentro de `RenderContextQueueItem::invoke`.
1. 18:30 — Re-medición del artefacto round37f con `port/target/diag71.mjs` (espera corta) antes de editar código.
2. 18:55-19:15 — Re-medición del artefacto round37f con tres probes nuevos:
   - `port/target/diag75.mjs` (espera 120 s) y `port/target/diag76.mjs` (5 sondas de 4 s cada 15 s):
     **todos los `page.evaluate` dan timeout**, incluso el primero a los 15 s y una sonda mínima.
     `diag75.log` además muestra un 404 de recurso y 0 pageerrors. Coincide con `diag72.log` (16:53)
     y `diag72b.log` (17:04): desde el build round37d (16:42) la página no devuelve el loop de JS.
   - Conclusión: la build round37f NO es medible (no se puede distinguir "aún no cargó" de "loop bloqueado"),
     por eso la ronda 37 quedó sin evidencia de cierre. La única medición completa del artefacto
     sigue siendo `diag71.log` (16:30) sobre la build round37c: 0 pageerrors, fibras 1 y 2 parkadas,
     `flushInvokeQueue entered 65/returned 64`, `RenderContextQueueItem#invoke entered 1/returned 0`,
     `menu:false`, `swap:2`. Sospecha de la regresión: el `@JSBody` de `port.shim.BrowserMonitor`
     captura una traza JS (`new Error().stack` + `Error.stackTraceLimit`) en cada fase 4/5/8/9, o sea
     en cada `Object#wait`, `monitorExit` y `notifyAll` del arranque (cientos de capturas dentro de
     frames replayed de las corrutinas).
3. 19:20 — Análisis estructural del park (sin más javap): con `waitLock` de `RenderContextQueueItem`,
   el game fiber hace `wait` (listener en `notifyListeners` + `monitorExit` con `owner=null`), el render
   fiber entra al `monitorenter` de `invoke()` y, si ve `owner != null`, se encola en
   `enteringThreads` de TeaVM. `enteringThreads` **sólo** se drenó en `monitorExit` (cuando un dueño
   libera) o en `waitForOtherThreads` ofrecido por ese release: si el entrante se encola cuando ya no
   hay nadie que libere, nadie drena → las dos fibras quedan parkadas para siempre. Eso es exactamente
   el estado medido en `diag71.log` (game parkado en `waitImpl`, render parkado en `monitorEnterWait`,
   `invoke returned 0`). Fix de plataforma (no cambia reglas del juego): ofrecer un drenaje diferido
   de `enteringThreads` también cuando alguien se encola, y que el grant garantice ownership.
4. 19:25 — Editado `scripts/prepare_teavm.py::patch_monitor()` (verificado con `python3 -c "import ast;ast.parse(...)"`
   y `python3 scripts/prepare_teavm.py` OK, que regenera `TObject.java`):
   - Quitadas las fases 10/11/12 de `monitorEnter`/`monitorExit`/`createMonitor` (camino caliente de
     todo el arranque) para recuperar la medibilidad de la página. Quedan las fases 1-9 + dos nuevas.
   - Fix (a): `waitForOtherThreads` ya NO borra toda la cola `enteringThreads`; sólo la limpia si quedó
     vacía, y ofrece un drenaje diferido si quedan más solicitantes (`reportMonitor(13)` registra el grant).
   - Fix (b): en `monitorEnterWait`, al encolar un solicitante se ofrece `waitForOtherThreads(o)` diferido
     (macrotask), para que un entrante encolado sin dueño próximo también sea otorgado.
   - Fix (c): `NotifyListenerImpl.run()` readquire el monitor directamente cuando está libre
     (`owner == null` → `owner=currentThread; count += lockCount; callback.complete`) con
     `reportMonitor(14)`, en vez de encolarse como solicitante huérfano.
   Build grande `build-round38-game.log` en curso (único, primer plano, `MAVEN_OPTS=-Xmx8g`, PZ_HOME exportado).
5. 19:28 — Primer intento `build-round38-game.log` **falló** con `java.lang.StackOverflowError` en
   `WasmBinaryRenderingVisitor.render` (TeaVM COMPILING) porque pasé `MAVEN_OPTS=-Xmx8g` **sin `-Xss16m`**:
   `scripts/env.sh` sólo aplica su default `-Xmx20g -Xss16m` cuando MAVEN_OPTS viene vacío (línea 7).
   Registrado en AGENT_LEARNINGS. Relanzado con `MAVEN_OPTS="-Xmx8g -Xss16m"`.
6. 19:35 — `build-round38b-game.log` **BUILD SUCCESS / EXIT=0** (6:45 min): `boot.wasm` 143.127.094 B,
   classes 10212, métodos 71978, pico RSS 6.55 GiB, `build-state.json success:true`.
   **GATE PASS**: `thread-fiber-gate.txt` ⇒ currentThread `fiberops: False`, setCurrentThread `fiberops: False`.
   `python3 tools/code-agent/probe8.py port/target/web/game/boot.wasm 'java.lang.Thread::<clinit>'` ⇒ **`no seed`**
   (slots 23944, entries 52808). Con el fix del monitor aplicado (fases 10/11/12 fuera del camino caliente).
7. 20:06 — `diag80.mjs` (anillo con frames) sobre la build round38b confirma la forma del park pero NO
   permite concluir: el anillo de 160 estaba saturado de `phase 5` (`monitorExit` del camino caliente), así que
   las fases 3/6/7/8/13/14 fueron desplazadas. Además `wasmGCIdentity()` satura (`obj` siempre `-1` o
   `2147483647`), así que el id de objeto no identifica `waitLock`. Hecho útil medido: en el tail aparecen
   eventos `ph=5/9` con `thread=0` y `owner=0` a t≈166542, justo cuando el render fiber está en
   `RenderContextQueueItem#invoke` (entered 1 / returned 0): el monitor del `waitLock` es usado por el
   thread 0 con `notifyAll` dentro de `SpriteRenderer#notifyRenderStateQueue`.
8. 20:10-20:20 — Reescrito el JS de `port/shim/BrowserMonitor.java`: contadores acumulados por fase
   (`globalThis.pzMonitorCounts`, clave `fase:hilo`), último evento por fase (`pzMonitorLast`) y anillo
   `pzMonitorLog` que EXCLUYE la fase 5 (cap 60). Sin stubs: sólo observación. El capture de stack sigue
   fuera del camino caliente (fase 5 retorna antes de capturar).
9. 21:05-21:10 — (Segunda mitad) Parche verificado: anclas originales del classlib re-readas del jar y
   `count(...)==1`. Agregadas **fases 15 y 16** a `patch_monitor()`: 15 en la rama contendida de
   `monitorEnter(o,count)` (antes de llamar al `@Async` `monitorEnterWait`) y 16 en la primera línea del
   body `monitorEnterWait(o,count,callback)`. Sólo ints (`threadIdOr0/ownerIdOr0/enteringSize/notifySize`),
   sin captura de stack y sólo en el camino contendido: no reproducen la regresión de medibilidad de la
   ronda 37d. `ast.parse` OK; `python3 scripts/prepare_teavm.py` regeneró `TObject.java` con ambas fases
   (líneas 106 y 132). Build único en primer plano `build-round38d-game.log` (`MAVEN_OPTS="-Xmx8g -Xss16m"`).
10. 21:17 — `build-round38d-game.log` **BUILD SUCCESS / EXIT=0** (~6,6 min): `boot.wasm` 143.128.525 B,
    classes 10212, **métodos 71982** (+4 por las fases 15/16), pico RSS 6.54 GiB, `build-state.json success:true`.
    **GATE PASS** (`thread-fiber-gate.txt`: currentThread y setCurrentThread `fiberops: False`) y
    `probe8.py ... 'java.lang.Thread::<clinit>'` ⇒ **`no seed`** (slots 23944, entries 52808). Dev server
    `127.0.0.1:8092` sigue arriba (no reiniciado).
11. 21:18-21:23 — Medición sobre la build 38d: `diag84d.log`, `diag85d.log`, `diag86d.log`. **0 pageerrors**
    y la página SÍ responde `page.evaluate` (los primeros ~45 s no, por eso 6 sondas iniciales dan timeout;
    después todas responden). Avance real respecto de la ronda 37: `Display#processMessages` 65/65,
    `flushInvokeQueue entered 65 / returned 64`, `RenderContextQueueItem#invoke entered 1 / returned 0`,
    `swap` bloqueado en 2, `gl:267`, `phase:'starting'`, `menu:false`.
    Contadores monitor de todo el run: `{5:0:4283198, 9:0:4227205, 5:2:28, 9:2:1, 4:2:1, 15:0:1, 16:0:1}`
    y en el anillo, en t≈89457-89461 la secuencia exacta del bloqueo:
    `9 (owner=2,thread=2)` → `4 waitImpl (owner=2,thread=2)` → `9 (owner=0,thread=0)` →
    **`15 monitorEnter contendido (owner=2, thread=0)`** desde `RenderContextQueueItem::invoke` →
    **`16 monitorEnterWait_0 (owner=2, thread=0)`** desde `Object$monitorEnterWait$0::run`.
    `pzAsync` tuvo **2 offers / 2 fired** en todo el run (offer 1 = `Thread::start` de MainThread;
    offer 2 = el yield de `BrowserYield::pause`). Fibras: id1 park (suspends 3, resumes 1, pendingDepth 1),
    id2 park (suspends 1, resumes 0, pendingDepth 1).
12. 21:23 — **Causa raíz confirmada (ya no es una hipótesis)**: el `@Async` `monitorEnterWait(o,count)`
    suspende la fibra ANTES de correr su body, pero el body sí corre una vez (fase 16 = 1) y ahí ve
    `owner != null` y se encola en `enteringThreads`… sin embargo los contadores muestran
    `entering:0`/`notify:0` en fase 16 y **nunca** 3/6/13: el `EventQueue.offer(()->waitForOtherThreads(o))`
    que la fix (b) agregó al encolar **no aparece en `pzAsync`** (sólo 2 offers), porque el `offer` de
    TeaVM WasmGC pasa por el import `teavmAsync.offer` que sí medimos — por lo tanto ese offer nunca se
    ejecutó: la fibra quedó suspendida dentro del propio `suspend()` de `Fiber` (pendingDepth 1) y el
    resto del body jamás llegó a correr. Es decir: `monitorEnterWait_0` corre hasta `reportMonitor(16)`
    y el park ocurre en la llamada siguiente, de modo que ni el `add()` al `enteringThreads` ni el
    `EventQueue.offer` posteriores se ejecutan. Nadie puede grantearla: `monitorExit` (fase 5) y
    `notifyAll` (fase 9) siguen corriendo por el thread 0 pero no encuentran solicitante encolado.
    Nota de medición: el log del game fiber muestra el mismo `waitLock` con owner=2/thread=2 y luego
    owner=0/thread=0, así que el thread 0 (el de la render fiber según TeaVM) es quien hace el
    `monitorenter` contendido.
13. 21:24-21:27 — **Cierre de la ronda** (límite 21:54, documentar desde 21:24): no se tocó `.git`, sin
    commits. Pruebas de esta ronda con `source scripts/env.sh`: `npm test` **19/19 OK** y
    `python3 -m unittest discover -s tests/python` **10 OK**. No existe `MENU_REACHED` (el menú no se
    renderiza) y no se creó.
14. Bloqueo actual y siguientes pasos concretos, en orden:
    a) El `monitorenter` de `RenderContextQueueItem::invoke` se parka en `Fiber::suspend` y su body
       `monitorEnterWait_0` sólo ejecuta la primera línea (fase 16); el encolado en `enteringThreads` y
       el drenaje diferido nunca corren. Fix honesto a probar en la próxima ronda: mover el encolado +
       el `EventQueue.offer(()->waitForOtherThreads(o))` al **`monitorEnter(o,count)`** contenedor (que
       no es corrutina) antes de llamar al `@Async`, de forma que el solicitante exista en la cola antes
       de suspender; y que `waitForOtherThreads` grantee sólo si `owner == null` (ya lo hace), para que
       el grant sea real y no un resume falso. Duda anotada: si el grant llega mientras la fibra está en
       STATE_SUSPENDING, `Fiber$AsyncCallbackImpl.complete` re-entra por `EventQueue`, así que el
       `callback.complete` del runnable encolado debería reactivarla; verificarlo con las fases 3/6/13.
    b) Rebuild único (`MAVEN_OPTS="-Xmx8g -Xss16m"`, `PZ_HOME` exportado, primer plano) y medir con
       `port/target/diag85.mjs` (contadores + anillo con frames) y `diag84.mjs` (stacks de offers).
       Esperado avance: fases 3/6/13 > 0, `RenderContextQueueItem#invoke returned >= 1`,
       `flushInvokeQueue returned 65`, `swap > 2`, `MainScreenState#enter/render` con pulses, `menu:true`.
    c) Si el menú se renderiza: actualizar `tests/browser/game-main.spec.mjs` (quitar los asserts
       `frozen.after.progress === frozen.before.progress` e `swap` iguales, ~líneas 78-79; mantener
       Bullet/GL/ventana/ícono/oshi y `expect(pageErrors).toEqual([])`), correr Playwright con
       `PZ_REAL_TEST_PORT=8093`, captura en `test-results/`, y **sólo entonces** crear `MENU_REACHED`.
    d) Detalle secundario observado en el log de consola (no bloqueante): el boot registra
       `Unsupported platform: UNSUPPORTED` (`UnsupportedOperationException` con stack FakeClass) y luego
       sigue; `GraphicsCard` ANGLE/SwiftShader, `OpenGL version: 3.3 (WebGL 2.0)`, resolución inicial
       640x480 sobre canvas 1280x720. Queda para más adelante.
