# Estado del port personal

Actualizado: **2026-10-26, tarea 4**, Linux x86_64. Repositorio: `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad).

**El main real compila con reflexión Lua completa: cero APIs ausentes, cero diagnósticos y un Wasm de 144.799.524 bytes. Chrome lo valida y ejecuta su arranque. El menú todavía no se renderiza.** La traza llega a `zombie.gameStates.MainScreenState.main → org.lwjglx.opengl.Display.init → org.lwjglx.LWJGLUtil.<clinit>`, donde falla con LinkageError: Unknown platform: TeaVM.

F0 conserva su inventario y el build vuelve a contrastar los hashes externos. F1 alcanza la compilación y ejecución inicial del main real. F2 suma recursos del classpath, watchers y SQLite/JDBC con persistencia verificada. F3 continúa pendiente de integrar el renderer; los shaders y la textura originales siguen probados por separado.

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

## Implementación de la tarea 4

| Familia | Resultado y límite principal |
|---|---|
| Unsafe / MemoryUtil / Foreign Memory | Arena de direcciones virtuales comprobadas, buffers, alineación, copia/realloc/free, campos por tokens y segmentos con vida/hilo. No son punteros del heap GC ni direcciones directamente utilizables por Bullet u otros módulos. |
| MethodHandles / VarHandle | Lookup con clase llamadora, métodos y campos, firmas polimórficas tipadas, boxing y compare-and-exchange por identidad. No cubre todos los combinadores del JDK. |
| Classpath y archivos | Recursos originales por índice ZIP local, CRC, orden y duplicados, sin extraer el jar. WatchService recibe cambios reales del VFS. Se conserva FileChannel.force y su transacción IndexedDB. |
| XML / DOM | DOMParser/XMLSerializer del navegador, identidad de nodos, namespaces y transformación de identidad. DTD, entidades externas, validación y XSLT pendiente se rechazan. |
| Digest / texto / AWT geométrico | MD5, SHA-1, SHA-256, HexFormat, normalización, colación, Rectangle/Line2D y conversiones RGB/HSB, con comparaciones contra JDK 25. No implementa ventanas AWT. |
| SQL / JDBC | SQLite Wasm oficial 3.54.1; SQL real, prepared statements, binds, resultados, claves, transacciones y persistencia tras recargar. Una conexión activa por ruta persistente; métodos JDBC pendientes lanzan SQLFeatureNotSupportedException. |
| Concurrencia | BlockingQueue, SynchronousQueue de capacidad cero, Semaphore, ReadWriteLock, adders, ScheduledExecutor, CompletableFuture y pools con límites/rechazo. Hilos cooperativos; no se afirma paralelismo de CPU. |
| Management y APIs ajenas al navegador | Datos reales del agente navegador donde corresponde. JMX, procesos, TCP/DNS del SO, carga nativa y serialización pendiente fallan explícitamente. Los adaptadores opcionales OpenTelemetry tampoco simulan telemetría. |
| Compilador/runtime | Correcciones locales fijadas de TeaVM: metadata, interfaces marker, intrinsics reflectivas, inicializadores por clase, tipos nullable de corrutinas y monitores. Registro del hilo inicial antes de adquirir monitores. Se conserva íntegra la exposición Lua. |

Alcance, comprobaciones y limitaciones en [docs/task4-compatibility.md](docs/task4-compatibility.md); funcionalidades previas en [docs/task3-compatibility.md](docs/task3-compatibility.md). Resolver firmas no equivale a implementar clases completas del JDK.

## Compilación y arranque observados

| Compilación | Clases / métodos alcanzados | Resultado |
|---|---:|---|
| Base de la tarea 3, Lua completo | 10.021 / 71.178 | 82 APIs ausentes, 825 diagnósticos, 0 bytes Wasm. |
| **Final de la tarea 4, Lua completo** | **10.291 / 73.142** | **0 APIs ausentes, 0 diagnósticos, 144.799.524 bytes Wasm.** |
| Plataforma de prueba | 936 / 4.331 | 1.734.303 bytes Wasm, cero diagnósticos. |

TeaVM del main: **340.793 ms**; heap máximo **20 GiB**, pila **16 MiB**, RSS pico de Maven **8.543.984 KiB (8.15 GiB)**. Los tiempos excluyen preparación y pruebas; no son un benchmark de rendimiento del juego. La plataforma tarda 5.405 ms con RSS pico 1.132.840 KiB.

El manifiesto mantiene **1.043 registros literales, 1.040 clases expuestas distintas, 1.123 clases del jar con jerarquías y 35 tipos externos**, sin registros no resueltos. El filtro de firmas públicas java/javax elimina extensiones internas de TeaVM inexistentes en JDK 25; no elimina clases ni miembros del juego. La exposición estática no demuestra que LuaManager.init se haya ejecutado.

Chrome carga `/game/boot.wasm`; el estado final observado es:

```text
phase: failed
error: java.lang.LinkageError: Unknown platform: TeaVM
menuRendered: false
```

La traza original de TeaVM usa offsets FakeClass. Se resolvieron contra nombres y límites del Wasm, sin desensamblar: `MainScreenState.main`, `Display.init` y `LWJGLUtil.<clinit>` están presentes. El error está atrapado por GameBoot; no hubo pageerror sin capturar. La detección de plataforma de LWJGLx requiere un puente real para navegador antes de continuar con GLFW/GL20; no se cambió os.name para simular un sistema soportado.

La captura `test-results/game-main-task4.png` muestra ese estado y fue inspeccionada. `game_initialized:false` en el reporte de compilación se conserva: ese campo no afirma ejecución. El resultado de navegador está separado en `chrome-startup.json`. No se ejecutaron LuaManager.init, el menú ni una partida de manera demostrada.

## Pruebas y evidencia

**90 pruebas aprobadas: 44 JVM, 10 Python, 17 Node, 14 Chrome de plataforma y 5 Chrome con la copia real.** Cero fallos, omitidos o flaky en los reportes finales. Las pruebas reales comprueban la detención conocida del arranque; no cuentan esa detención como un menú funcionando.

- Build real: `port/target/web/game/boot.wasm`, `port/target/reports/game-main-lua/`, `build-task4-pass16.log`.
- Plataforma: `port/target/web/boot.wasm`, `port/target/reports/platform-smoke-minimal/`, `build-task4-platform-final.log`.
- Verificación: `docs/verification.md`, XML de Surefire, `verification-task4-python.log`, `verification-task4-node.log`, `verification-task4-browser.log`, `verification-task4-real-final.log`.
- Navegador: `test-results/browser-results.json`, `test-results/real-game-results.json`, `test-results/task4-platform.png` y `test-results/game-main-task4.png`. Las suites conservan directorios separados.
- Se mantienen la lectura Java de WorkshopTags, la persistencia bajo /Zomboid, la comparación completa de los 4.194.304 bytes RGBA de Title.png y la compilación/enlace de tres pares de shaders originales.
- Referencias y fallos intermedios en `port/target/reports/task4-*`; un fallo interno no se contabiliza como cero APIs. El compilador elimina el módulo también cuando TeaVM escribe Wasm pero termina con diagnósticos severos.

## Siguiente trabajo y límites

1. Integrar `org.lwjglx.LWJGLUtil` y `Display.init` con una plataforma real de navegador, conservando errores explícitos y verificando la siguiente detención con el main real. Cambiar `os.name` a Linux solo para pasar la detección no implementa ese soporte.
2. Integrar GLFW/callbacks, GL20, pipeline fijo y loop cooperativo del renderer. Solo cuatro mappings escalares GL11/GL11C están confirmados; la arena virtual requiere traducción de direcciones al conectar esos puentes.
3. Resolver el include `util/math` de `screen.frag` según las lecturas originales. Tres pares de shaders enlazados por separado no validan ese include ni el menú.
4. Completar nativas de mundo, física y FMOD; Bullet sigue como módulo wasm32 separado. JDBC con SQLite Wasm no demuestra compatibilidad con partidas reales ni sustituye todos los JNI de SQLite.

También siguen pendientes FileChannel.map, durabilidad automática rws/rwd, invalidación de vistas ByteBuffer al cerrar arenas y traducción de trampas unsigned por cero a ArithmeticException Java. Los límites restantes de reflexión/StackWalker, JMX y serialización están documentados en compatibilidad. No se cambiaron reglas del juego.

## Reproducción y trabajo local

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
bash scripts/test.sh
bash scripts/build.sh --with-game
python3 scripts/report_game.py --reports port/target/reports/game-main-lua
npx playwright test --config playwright.game.config.mjs
```

El entorno fija por defecto `MAVEN_OPTS="-Xmx20g -Xss16m"`. No ejecutar el juego nativo ni pruebas pesadas de navegador durante el build completo. No se necesita reinstalar la copia para reproducirlo.

HEAD al comenzar y terminar: **a1d4963**. `.git` es de solo lectura; no se crearon commits, remotos ni publicaciones. Los grupos para revisión están en [docs/local-commits.md](docs/local-commits.md). Los archivos del dueño y su supervisor se preservaron; no se encendieron ni apagaron sus aplicaciones.

La auditoría de **269 archivos rastreados/candidatos** no encontró binarios ni directorios de recursos prohibidos. No se agregó código derivado del juego. Inventarios, Wasm, capturas, recursos y fuentes públicas generadas permanecen ignorados. `git diff --check` no informó errores.

Las pruebas levantaron y cerraron sus propios servidores en **127.0.0.1:8091/8092**. Para abrirlo: `PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid" bash scripts/dev.sh`, exclusivamente en **127.0.0.1:8090**. `/` es la demo; `/game.html` ejecuta el main real y muestra su detención. No se afirma que quede un servidor activo de esta sesión.
