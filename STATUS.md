# Estado del port personal

Actualizado: **2026-10-26, ronda 5**, Linux x86_64. Repositorio: `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad). HEAD del dueño: **054512f**.

**El main real mantiene cero APIs ausentes y cero diagnósticos, y Chrome ejecuta el Wasm de 144.676.333 bytes. `Display.init` y `LoggerManager.init` terminan; el arranque se detiene en `Bullet.init` al cargar `PZBullet64`. El menú todavía no se renderiza. `MENU_REACHED` no existe.**

F0 conserva su inventario y el build contrasta los hashes externos. F1 mantiene la compilación y ejecución inicial del main con reflexión Lua completa. F2 avanza hasta el logger real, con salida de consola corregida. F3 inicializa la plataforma de ventana del navegador; crear la ventana del juego e integrar su renderer siguen pendientes. La carga temprana de Bullet requiere trabajo de F5 antes de continuar el arranque.

## Copia e inventario reales

- `PZ_HOME` canónico: `/home/agente47/pz-pinned/25485521/projectzomboid`. Los scripts aceptan también el padre `25485521`. El log inicial fallaba porque buscaba el launcher un nivel más arriba.
- Steam appid **108600** y build id **25485521** confirmados en `/datos/SteamLibrary/steamapps/appmanifest_108600.acf`. El SHA-256 del jar instalado coincide con el de la copia fijada. Esta verificación adicional cubre el jar principal; el inventario determinista de la copia conserva como procedencia del build id el nombre de su directorio.
- Versión **42.21**, obtenida de los argumentos literales de `GameVersion` en `Core.<clinit>` sin ejecutar clases del juego; revisión `4a0e9546ec` en `GitVersion.REVISION`.
- Launcher real: `zombie.gameStates.MainScreenState`, classpath `.` y `projectzomboid.jar`. La clave del JSON es `classpath`, en minúsculas.
- Fat jar: **65.682.033 bytes**, **23.829 entradas de clase**, de ellas 6.462 con major 69 (Java 25). SHA-256: `e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33`. El runtime incluido es Zulu 25.0.1; el compilador del proyecto usa el JDK 25 local de `.toolchain`.
- **LWJGL 3.4.1** integrado en el fat jar: core, GLFW, OpenGL, STB, system y util. La versión se obtiene de `org.lwjgl.Version`, ya que no hay jars LWJGL separados con manifests propios.
- **88 clases Kahlua** integradas. Su clase `Version` contiene placeholders; no se inventó una versión de release.
- **8.284 declaraciones native / 7.799 firmas distintas** en los tres jars inventariados: 8.281 declaraciones en el fat jar, 2 en pzexe y 1 en jrt-fs. Se distinguen variantes multi-release, duplicados, JRE y plataformas. Quedan 88 declaraciones sin export identificado; `nm` no resuelve registro JNI dinámico.
- Se inspeccionan también ELF empaquetados dentro del jar mediante archivos anónimos en memoria, sin extraer binarios al repositorio. Dos inventarios consecutivos produjeron JSON idéntico. Salidas locales: `inventory/report.md`, `inventory/inventory.json`, `inventory-run.log`.

Exports JNI Linux relevantes: Lighting 50; Bullet 98 (variante NoOpenGL 93); Clipper 19; PathFind 13; PopMan 59; ZNetJNI 139; Bink 11; FMOD integration 437; jassimp 10; ImGui 2.129; LWJGL core 2.150; OpenGL 2.236; STB 224; tinyfd 11; SQLite 61. Son exports, no implementaciones del port ni conteos sumables entre variantes.

## Implementación de la ronda 5

- `Display.init` consulta el canvas, WebGL2, el teclado DOM y las métricas del navegador; conserva la inicialización original del teclado y su guarda idempotente. No se modificó `os.name`. Las funciones GLFW sin soporte fallan explícitamente; las nativas conservan su nombre JNI.
- Etiquetas de teclas desde KeyboardLayoutMap cuando está disponible, con rechazo de teclas no imprimibles y scancodes virtuales propios del backend. No hay una ventana GLFW del juego creada ni callbacks del renderer conectados.
- Corrección local de la consola de TeaVM: escrituras largas con offsets correctos, UTF-8 fragmentado, flush repetido y escrituras posteriores. La fixture propia falla antes del parche y pasa después; también comprueba double y slices de caracteres.
- Registros de entrada/retorno de inicializadores, traza independiente del logger del juego y mayor profundidad de stack en Chrome. Los reportes de ejecución anteriores se invalidan al recompilar; la prueba guarda automáticamente la evidencia nueva.

Alcance y fuentes en [docs/round5-compatibility.md](docs/round5-compatibility.md). Las implementaciones y límites anteriores se conservan en [docs/task4-compatibility.md](docs/task4-compatibility.md) y [docs/task3-compatibility.md](docs/task3-compatibility.md). No se cambiaron reglas del juego.

## Compilación y arranque observados

| Compilación | Clases / métodos alcanzados | Resultado |
|---|---:|---|
| Final tarea 4, Lua completo | 10.291 / 73.142 | 0 APIs, 0 diagnósticos, 144.799.524 bytes Wasm; detenía en LWJGLUtil. |
| **Final ronda 5, Lua completo** | **10.279 / 73.003** | **0 APIs, 0 diagnósticos, 144.676.333 bytes Wasm; llega a Bullet.init.** |
| Plataforma de prueba | 937 / 4.341 | 1.735.621 bytes Wasm, cero diagnósticos. |

TeaVM del main: **345.614 ms**; heap máximo **20 GiB**, pila **16 MiB**, RSS pico de Maven **10.245.228 KiB (9,77 GiB)**. La plataforma tarda 5.353 ms con RSS pico 1.174.168 KiB. Son métricas de compilación, no del rendimiento del juego.

Se mantienen **1.043 registros literales, 1.040 clases expuestas distintas, 1.123 clases del jar con jerarquías y 35 tipos externos**, sin registros no resueltos. No se recortó la exposición Lua. El cambio de alcance corresponde a los puentes de plataforma y guardas GLFW. Resolver firmas no implica implementar todo GLFW ni ejecutar Lua.

La última prueba real registra estos límites, en orden:

```text
org.lwjglx.opengl.Display#init()V:entered
org.lwjglx.opengl.Display#init()V:returned
zombie.core.logger.LoggerManager#init()V:entered
zombie.core.logger.LoggerManager#init()V:returned
zombie.core.physics.Bullet#init()V:entered
```

El estado final es:

```text
phase: failed
error: java.lang.UnsatisfiedLinkError: System.loadLibrary: browser implementation is missing for PZBullet64
menuRendered: false
```

La traza resuelta confirma `MainScreenState.main → Bullet.init → Bullet.loadLibrary → System.loadLibrary`. La consulta de hardware de OSHI también registra `Unsupported platform: UNSUPPORTED`; el juego la captura y continúa. No se inventa hardware del anfitrión. No hubo pageerror sin capturar en la prueba.

La captura `test-results/game-main-round5.png` fue inspeccionada: muestra canvas negro y la detención de Bullet, sin menú. No hay registros de entrada/retorno de LuaManager.init ni del render del menú. `game_initialized:false` sigue siendo un campo del reporte de compilación; los límites ejecutados se registran aparte en `chrome-startup.json`.

## Pruebas y evidencia

**94 pruebas aprobadas: 45 JVM, 10 Python, 17 Node, 17 Chrome de plataforma y 5 Chrome con la copia real.** Cero fallos, omitidos o flaky en los reportes finales. La prueba de arranque exige la detención concreta de Bullet y las etapas previas terminadas; no cuenta eso como un menú funcionando.

- Build real: `port/target/web/game/boot.wasm`, `port/target/reports/game-main-lua/`, `build-round5-pass3.log`.
- Plataforma: `port/target/web/boot.wasm`, `port/target/reports/platform-smoke-minimal/`, `build-round5-platform-final.log`.
- Verificación: `docs/verification.md`, XML de Surefire, `verification-round5-python.log`, `verification-round5-node.log`, `verification-round5-browser.log`, `verification-round5-real-final.log`.
- Navegador: `test-results/browser-results.json`, `test-results/real-game-results.json`, `test-results/game-main-round5.png`, `port/target/reports/game-main-lua/chrome-startup.json` con traza, etapas, consola y hashes de módulo/captura.
- Regresión de consola: `verification-round5-console-before.log` conserva el fallo previo en flush; `verification-round5-console-fixed.log` y la suite final verifican la corrección.
- Se mantienen la lectura Java de WorkshopTags, persistencia bajo /Zomboid, comparación de los 4.194.304 bytes RGBA de Title.png y compilación/enlace de tres pares de shaders originales. Esos recursos no demuestran el renderer del juego.

## Bloqueo y siguiente paso concreto

1. **Resolver PZBullet64 con una implementación real.** La clase Bullet declara 93 métodos native en esta copia; el inventario y `build/round5/bullet-signatures.json` conservan sus firmas. No hay aún módulo wasm32, especificación del wrapper ni grabaciones de referencia bajo docs/re. El siguiente trabajo debe obtener/verificar primero el contrato de inicialización y versionado, identificar la versión de Bullet y construir el módulo separado con Emscripten. Las firmas solas no describen buffers, unidades, ownership ni efectos de cada llamada. No convertir System.loadLibrary en un no-op ni registrar una biblioteca vacía.
2. Repetir el main real tras esa integración para descubrir la siguiente detención. Conservar errores JNI de las operaciones aún ausentes. Para implementar comportamiento de física se necesitan las notas y capturas locales previstas por el roadmap; no se generaron en esta ronda y no se ejecutó el juego nativo.
3. Continuar `Display.create`, GLFW/callbacks, GL20, pipeline fijo y loop cooperativo del renderer. LWJGLUtil sigue original y deberá adaptarse con esas rutas; el puente de esta ronda cubre la inicialización, no todos los usos de LWJGL.
4. Resolver el include `util/math` de `screen.frag` según las lecturas originales y completar las demás nativas, incluyendo FMOD. JDBC con SQLite Wasm no demuestra compatibilidad con partidas reales.

También siguen pendientes FileChannel.map, durabilidad automática rws/rwd, invalidación de vistas ByteBuffer al cerrar arenas y traducción de trampas unsigned por cero a ArithmeticException Java. Los límites de reflexión/StackWalker, JMX y serialización están documentados en compatibilidad.

No se necesita reinstalar la copia ni tocar Strata para reproducir el estado actual. Para continuar la física falta información de interoperabilidad y pruebas de referencia; no es un problema de instalación de Steam ni de memoria disponible.

## Reproducción y trabajo local

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
bash scripts/test.sh
bash scripts/build.sh --with-game
python3 scripts/report_game.py --reports port/target/reports/game-main-lua
npx playwright test --config playwright.game.config.mjs
```

El entorno fija por defecto `MAVEN_OPTS="-Xmx20g -Xss16m"`. No ejecutar el juego nativo ni pruebas pesadas de navegador durante el build completo. `/` conserva la demo de plataforma; `/game.html` ejecuta el main real y muestra su detención.

`.git` es de solo lectura; HEAD sigue en **054512f** y el dueño commitea. No se crearon commits, remotos ni publicaciones, no se usó sudo ni se tocaron aplicaciones del dueño. La auditoría de **277 archivos rastreados/candidatos** no encontró binarios ni recursos prohibidos. Los inventarios, fuentes públicas generadas, Wasm, capturas y recursos permanecen ignorados. `git diff --check` pasó.

Los servidores de prueba se gestionaron exclusivamente en **127.0.0.1:8091/8092**. Para abrirlo: `PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid" bash scripts/dev.sh`, exclusivamente en **127.0.0.1:8090**. No se afirma que quede un servidor activo de esta sesión.
