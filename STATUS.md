# Estado del port personal

Actualizado: **2026-10-27 09:11 (-03), ronda 19**, Linux x86_64. Repositorio real `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad). HEAD del vigilante al empezar la ronda: **d64ddfd** ("Ronda 18"). Esta ronda **no hizo commits** (regla: el vigilante commitea al cerrar la ronda).

⚠️ **Advertencia de estado**: el corte de 4 horas de la ronda (inicio 05:12, límite 09:12) llegó antes de poder compilar y probar el código de esta ronda. Este archivo se actualiza primero, como exige `docs/CODEX_TASK_CONTINUE.md`. Nada de lo de abajo está verificado en Chrome todavía; el avance real verificado sigue siendo el de la ronda 14/18.

## Cambios de esta ronda (sin commit, sin build completado)

Archivos modificados:
- `port/src/main/java/port/compiler/WindowCompatibility.java` — reescritado con ramas nuevas:
  - `org.lwjglx.LWJGLUtil.<clinit>`: `DEBUG=false`, `CHECKS=true`, `PLATFORM=1` (Linux) → evita el `LinkageError` por `os.name="TeaVM"`.
  - `org.lwjgl.system.Checks.<clinit>`: `CHECKS=true`, `DEBUG=false`, `DEBUG_FUNCTIONS=false`.
  - `org.lwjgl.system.libffi.LibFFI.<clinit>`: `FFI_TYPE_LONGDOUBLE=(short)3` (igual que el bytecode real, coincide con `FFI_TYPE_DOUBLE`) y los 21 `ffi_type_X` como `FFIType` construidos sobre registros del arena de `BrowserLibFFI`; los 15 enteros de ABI en 0.
  - `FFICIF.<clinit>`: constantes del `ffi_cif` x86_64 (`SIZEOF=40, ALIGNOF=8, ABI=0, NARGS=4, ARG_TYPES=16, RTYPE=8, BYTES=24, FLAGS=28`). **Duda registrada**: nadie las lee en este backend y no se verificaron contra un `ffi_cif` real.
  - `APIUtil`: ambos `apiCreateCIF` devuelven un `FFICIF` sobre un registro zeroed del arena; ambos `apiCreateCIFVar` lanzan honestamente; `apiLog` queda original (no-op porque `Checks.DEBUG=false`).
  - `Upcalls`: `<clinit>` sin allocator ni closure registry; `upcallCreate` → handle propio, `upcallGet` → instancia cast a `CallbackI`, `upcallFree` → olvido. No se toca el path nativo `ffi_get_closure_alloc`.
  - `MemoryStack`: `<clinit>` con `DEFAULT_STACK_SIZE=65536`, `DEFAULT_STACK_FRAMES=8`, `TLS=new ThreadLocal()`; `stackGet` crea el stack perezosamente (arriba usa un lambda `invokedynamic` que el IR no expresa); ctor y `nmalloc(II)` con la misma aritmética del bytecode (`(address+pointer-bytes) & ~(align-1)`, `OutOfMemoryError "Out of stack space."` con CHECKS). `malloc/calloc/mallocInt/callocInt/nmalloc(I)/ncalloc` quedan originales.
  - `GLFW`: `glfwInit()Z`→true; `glfwInitHint` sale, `glfwPlatformSupported`→false; **11 setters de callback** (key, button, cursor, scroll, char, windowpos, windowsize, refresh, framebuffer, iconify, error) validan la ventana, registran el handler y devuelven `null` como callback previo (el backend nunca instaló uno nativo); `glfwSetWindowIcon` sale si `icons` es null y si no lanza honestamente (falta el puente de píxeles STB); `glfwPollEvents` reescrito como bucle `pollEvent/eventCallback/eventArguments` → `CallbackI.callback(0,args)` (dispatch `VIRTUAL`). Todo lo demás conserva el forward genérico al bridge o el raise con el nombre exacto de la operación.
  - `org.lwjgl.opengl.GL`: `<clinit>` solo crea `capabilitiesTLS`; las 4 variantes de `createCapabilities` lanzan `IllegalStateException("Browser WebGL2 GL capability bridge is not implemented: org.lwjgl.opengl.GL#createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;")`. `setCapabilities/getCapabilities` quedan originales.
- `port/src/main/java/port/shim/BrowserCallbacks.java` — reescrito: registros `IdentityHashMap` de instancia↔handle, `newHandle/instanceOf/forget/setHandler(kind,instance)`, y `pollEvent/eventKind/eventCallback/eventArguments` que exponen las celdas de argumentos estilo libffi (`void**`; la celda 0 apunta a la celda que contiene el handle de ventana, leída por los cuerpos de interfaz con dos lecturas de puntero).
- `port/src/main/java/port/shim/BrowserWindow.java` — puente `glfwCreateWindow(IILjava/lang/CharSequence;JJ)J` y `checkWindow(long)` (valida handle==1).
- `port/src/main/java/port/shim/BrowserLibFFI.java` (nuevo) — 21 registros `ffi_type` en el arena (`size@0, align@8, type@10, elements@12`, códigos libffi void=0…pointer=14 obtenidos de `javap` de LWJGL 3.4.1), `typeAddress(kind)`, `newCIFAddress()`. **Duda**: el código de `FFI_TYPE_LONGDOUBLE` (4) no se pudo contrastar con libffi; nadie lo usa en este camino.
- `port/runtime/window.mjs` — backend con forma de GLFW sobre el canvas: `hint()` solo lanza para `GLFW_FULLSCREEN` (los hints que usa `Display.create` pasan), `getPlatform()=393221`, `poll()` serializa `kind<TAB>handle(1)<TAB>campos` (key 4 campos con scancode duplicado, button 3, cursor/scroll dos doubles, char 1), `registerCallback(kind,handle)` emite `callback_registered`, y eventos `created`, `shown`, `context_current`, `initialized`. `node --check` OK.
- `tests/browser/game-main.spec.mjs` — las aserciones de ventana pasan del stop viejo de ronda 14 (`windowSystem` longitud 1 + `glfwWindowHint` no implementado) al nuevo stop esperado: `created` con `handle:1`, ≥10 `callback_registered`, `shown`, `context_current`, y la traza debe contener el raise de `GL#createCapabilities`. Captura nueva: `test-results/game-main-round19.png`. Las aserciones de Bullet y de progreso se conservan (verificaban bien).

## Estado REAL verificado del arranque en Chrome (lo único confirmado hoy)

Desde la última prueba verde (rondas previas, build `d64ddfd`):
- Build del main real: `success`, 10.246 clases / 72.764 métodos alcanzados, 0 diagnósticos, `boot.wasm` **144.409.580 bytes**.
- Chrome: `phase:'main-returned'`, `error:''`, `menuRendered:false`. `Display.init` y `LoggerManager.init` terminan; `Bullet.init` entra y retorna cargando el wasm32 de Bullet 2.83; el stop es `Browser GLFW operation unavailable: org.lwjgl.glfw.GLFW#glfwWindowHint(II)V`, capturado por `MainScreenState.main`. `windowSystem` = 1 evento (`initialized`). `oshi` reporta "Unsupported platform: UNSUPPORTED" (capturado) y la traza muestra `Library::loadSystem`.
- Pruebas completas verdes antes de esta ronda: **94 tests** (45 JVM, 10 Python, 17 Node, 17 Chrome plataforma, 5 Chrome real). En esta ronda los **45 tests JVM vuelven a pasar** con el código nuevo (`mvn compile` también BUILD SUCCESS).

**El menú principal real sigue sin verse. `MENU_REACHED` no existe y no se creó.**

## Bloqueo actual

El `scripts/build.sh --with-game` de esta ronda **falló** en `compile-wasm-gc`:

```
org.teavm.model.emit.EmitException: Argument 0 of type I is not compatible with method
port.shim.BrowserCallbacks.setHandler(VI)Ljava/lang/Object;
  at org.teavm.dependency.DependencyClassSource.lambda$transformClass$0 (DependencyClassSource.java:135)
```

Causa raíz (diagnosticada y verificada con un programa mínimo contra `teavm-core-0.16.0.jar`): los varargs de `MethodDescriptor`/`MethodReference` son `nombre, parámetros…, resultado`, y yo los había escrito al revés en ~26 sitios. Corregido en `WindowCompatibility.java`; `mvn -f port/pom.xml compile` da **BUILD SUCCESS** a las 09:10. El build Wasm del main completo **no llegó a ejecutarse** por el corte de la ronda.

## Siguientes pasos concretos, en orden (ronda 20)

1. Verificar la convención de varargs en todos los demás transformadores propios (`grep -n 'new MethodReference\|new MethodDescriptor' port/src/main/java/port/compiler/*.java`) — el mismo error puede existir en otros archivos y el analizador de dependencias solo reporta el primero.
2. Correr el build completo secuencial, sin Chrome en paralelo:
   ```bash
   cd /home/agente47/Projects/pz-web && source scripts/env.sh
   export MAVEN_OPTS="-Xmx8g -Xss16m"
   export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
   bash scripts/build.sh --with-game > build-round20-game.log 2>&1   # correr en la sesión en primer plano, NO con nohup
   ```
   Revisar `port/target/reports/game-main-lua/build-report.json` (success) y que `teavm-diagnostics.txt` quede vacío; anotar los bytes del wasm. Si aparece otro `EmitException`, corregir la identidad de la IR nombrada.
3. `npx playwright test --config playwright.game.config.mjs`; leer `port/target/reports/game-main-lua/chrome-startup.json` y **adaptar las aserciones de `tests/browser/game-main.spec.mjs` a lo que realmente observe** (los eventos y el raise exactos). Inspeccionar visualmente `test-results/game-main-round19.png` y recién entonces poner `screenshot_visually_checked:true`.
4. `bash scripts/test.sh` y registrar el conteo.
5. Después de eso, el camino hacia el menú, en orden probable: **puente real WebGL2 para `GL#createCapabilities()` → `GLCapabilities`** (el stop actual), luego el subconjunto GL11 que usa el renderer (los overrides existentes en `port/target/config/gl-overrides.tsv` mapean `glClear/glClearColor/glGetError/glViewport` a `port.shim.GL11`), puente de píxeles STB para los iconos de `Display.create`, cursor/pointer lock, `LuaManager.init`, loop de render y el menú.

## Notas de operación

- Servidores solo en `127.0.0.1`. **Strata y el puerto 8083 no se tocaron** (es este agente). No se corrió el juego nativo ni Chrome en paralelo con el build.
- RAM: 31 GiB total; el build usa `MAVEN_OPTS=-Xmx8g -Xss16m` y `source scripts/env.sh` en **cada** comando (mvn/javap/node solo están en el PATH después de sourcear).
- Sin `sudo`, sin cambios en herramientas globales, sin archivos del juego en git, sin stubs que simulen éxito: cada operación no implementada lanza con su identidad exacta.
