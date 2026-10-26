# Estado del port personal

Actualizado: **2026-10-26, tarea 3**, Linux x86_64. Repositorio: `/datos/Projects/pz-web` (`~/Projects/pz-web` es el enlace de compatibilidad).

**El menú principal todavía no se ejecuta ni se renderiza.** El análisis del main real con reflexión Lua completa ahora termina dentro del límite de memoria, pero devuelve APIs/dependencias ausentes y no genera Wasm. F0 sigue inventariado; F1 pendiente, F2 con FileChannel y persistencia verificados, F3 con tres pares de shaders reales compilados/enlazados por separado. La demo de plataforma continúa siendo una prueba independiente.

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

## Implementación de la tarea 3

| Área | Resultado comprobado |
|---|---|
| Concurrencia | ConcurrentLinkedQueue/Deque, CopyOnWriteArraySet, newKeySet, FutureTask y pools cooperativos de ExecutorService; ReentrantLock/Condition con equidad e interrupción. Pruebas JVM y uso de APIs estándar sustituidas en Chrome. |
| Hilos | ThreadGroup, herencia del grupo, membresía y handlers; separación de creado/iniciado/terminado, join antes de start, plazos y rechazo de un segundo start. No se auditaron todos los busy-waits del juego. |
| Archivos F2 | FileChannel comparte el VirtualFileAccessor de streams y RandomAccessFile. Posición, transferencias parciales, IO posicional, lectura/escritura y cierre. `force` espera la transacción IndexedDB y propaga su rechazo; bytes conservados después de recargar. |
| APIs concretas | Math.fma exacto con redondeo único; UUID con bits altos; PrintStream(File); String con high byte; Matcher.replaceAll(Function); CRC32 con ByteBuffer; GregorianCalendar.toInstant; carga AOT sin inicialización y consulta de cuota/heap del navegador. |
| Errores explícitos | Carga de bibliotecas con UnsatisfiedLinkError, procesos del SO con IOException, salida/shutdown hooks y locks de archivos con error explícito. Las nativas inventariadas conservan el nombre JNI en su guarda. No se simula disponibilidad. |
| Shaders F3 | Parser AST fijado y local, traducción parcial de GLSL de escritorio a GLSL ES, defaults de uniforms y promociones escalares. Tres pares originales (`default`, `blit`, `vboRenderer_PositionColorUV`) compilan y enlazan en WebGL2. Una fixture propia verifica uniforms y píxel `[64,128,191,255]`. No están conectados al renderer del juego. |
| Memoria de TeaVM | Parche local de tres cachés de reflexión: BitSet por índice internado, manteniendo la policy Lua. Fuente pública Apache-2.0 de TeaVM 0.16.0 fijada por SHA-256, generada solamente bajo target ignorado; jar de Maven intacto. El compilador comprueba el parche activo. |
| Diagnóstico | Estados y artefactos separados por modo; al comenzar BuildCompiler se invalidan el Wasm y los reportes derivados viejos. Las fallas internas registran tiempo, heap y RSS. El agrupador rechaza un análisis incompleto para no presentar cero APIs falsamente. |

Alcance exacto y límites de cada implementación en [docs/task3-compatibility.md](docs/task3-compatibility.md). No se implementaron clases completas del JDK por el solo hecho de resolver algunos de sus métodos.

## Compilaciones y pruebas

**60 pruebas aprobadas: 21 JVM, 10 Python, 15 Node, 10 Chrome de plataforma y 4 Chrome con la copia real.** Cero fallos y cero casos omitidos en la corrida final. Comandos, métricas y artefactos en [docs/verification.md](docs/verification.md).

| Compilación | Clases / métodos alcanzados | Resultado |
|---|---:|---|
| Referencia inicial, Lua completo, 20 GiB | 7.778 / 53.546 | 80 APIs ausentes, 647 diagnósticos; 0 bytes Wasm. |
| Pasada intermedia, Lua completo, 20 GiB | 8.563 / 57.716 | 62 APIs ausentes, 497 diagnósticos; 0 bytes Wasm. |
| Siguiente pasada sin parche de memoria | No completado | OutOfMemoryError al poblar la caché de reflexión, con heap máximo de 20 GiB. |
| **Final, Lua completo y caché compacta** | **10.021 / 71.178** | **82 APIs ausentes, 825 diagnósticos; 0 bytes Wasm.** TeaVM 282.236 ms; RSS pico Maven 6.282.064 KiB (5,99 GiB), heap máximo 20 GiB. |
| Plataforma final, caché compacta | 571 / 2.503 | Éxito; 754.331 bytes Wasm, cero diagnósticos. TeaVM 3.539 ms; RSS pico Maven 804.608 KiB. |

Las **52 APIs** de la tarea 2 correspondían a reflexión mínima. La referencia de esta tarea con Lua completo tenía 80. En el reporte final dejaron de aparecer 41 de esas 80 y se alcanzaron 43 ausencias nuevas, quedando 82; no es una regresión medida contra el mismo conjunto de código alcanzado ni demuestra cobertura total del JDK. El reporte incluye símbolos externos al JDK y cuatro diagnósticos adicionales de interfaces/superclases ausentes. Son conteos del análisis de compilación, no del juego ejecutándose.

El manifiesto mantiene 1.043 registros literales, 1.040 clases expuestas distintas, 1.123 clases del jar con jerarquías y 35 tipos externos. No se recortó para bajar la memoria. Esa exposición estática no demuestra LuaManager.init ni garantiza toda la exposición dinámica.

## Bloqueos actuales y siguiente trabajo

- F1: Unsafe/MemoryUtil y Foreign Memory API, MethodHandles/VarHandle, recursos del classpath, watchers, JMX/management, XML/DOM, AWT geométrico, digest/normalización, SQL/JDBC y más concurrencia (blocking queues, read/write locks, scheduled executors, CompletableFuture). El reporte también alcanza tipos opcionales de OpenTelemetry ausentes. No se ocultaron esos errores.
- F2: mapeo de archivos, watcher del VFS y recursos del classpath. Los modos RandomAccessFile `rws/rwd` aún no fuerzan cada escritura. `FileChannel.force` ya tiene persistencia comprobada; no equivale a durabilidad implícita de toda la IO.
- F3: conectar GL20 y el pipeline fijo real a WebGL2, resolver la arena de memoria nativa, GLFW y callbacks, loop y threading del renderer. Solo cuatro funciones escalares GL11/GL11C del inventario tienen mapping real. Compilar tres pares de shaders por separado no prueba que el menú los utilice ni valida el pipeline completo.
- `screen.frag` contiene un include propio (`util/math`) cuya resolución y orden no se inventaron. El traductor rechaza features que todavía no soporta.
- Las nativas de PZ, física, mundo, SQLite y FMOD siguen pendientes. No se ejecutaron el menú, LuaManager.init ni una partida. No se modificaron las reglas del juego.

El próximo paso técnico es partir de `port/target/reports/game-main-lua/missing-apis.md`, implementar las familias anteriores con pruebas y volver a analizar el main real. No hace falta reinstalar el juego ni liberar más RAM para reproducir este análisis con la caché compacta. Obtener un Wasm sin errores y ejecutar su arranque sigue pendiente; las capturas/logs locales del original harán falta para validar equivalencia cuando el renderer pueda ejecutarse.

## Reproducción y evidencia local

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
MAVEN_OPTS=-Xmx20g bash scripts/build.sh --with-game
# El build todavía termina con error por las APIs ausentes.
python3 scripts/report_game.py --reports port/target/reports/game-main-lua
```

No ejecutar el juego nativo ni pruebas pesadas de navegador durante ese build. Para la plataforma y las pruebas, después de que termine:

```bash
bash scripts/test.sh
npx playwright test --config playwright.game.config.mjs
```

- Build real final: `port/target/reports/game-main-lua/`, `build-task3-lua-final-tree.log`; sin `port/target/web/game/boot.wasm`.
- Referencias anteriores: `task3-baseline-lua/`, `task3-intermediate-lua/`, `task3-oom-lua/` bajo `port/target/reports/`.
- Plataforma final: `port/target/reports/platform-smoke-minimal/`, `build-task3-platform-final.log`.
- Pruebas: `verification-task3-python.log`, `verification-task3-node.log`, `verification-task3-browser.log`, `real-browser-task3.log` y XML de Surefire.
- Navegador: `test-results/browser-results.json`, `test-results/real-game-results.json`; ambas suites conservan sus resultados en directorios distintos.
- Capturas inspeccionadas: `test-results/task3-shader-compatibility.png` (fixture propia) y `test-results/game-blocked-task3.png` (estado real del build fallido). Ninguna muestra el menú.
- Se preservan las comprobaciones reales de lectura Java de WorkshopTags, escritura persistida y comparación de todos los 4.194.304 bytes RGBA de Title.png con Pillow.

## Git y ejecución local

HEAD al comenzar: **649b950**. `.git` es de solo lectura en esta sesión; no se crearon commits ni remotos. Los cambios están en disco y los grupos sugeridos figuran en [docs/local-commits.md](docs/local-commits.md). `docs/CODEX_TASK_3.md` y `run-task3.sh` ya estaban sin seguimiento y se conservaron sin modificaciones. No se ejecutó el script del dueño ni se detuvieron/reiniciaron sus aplicaciones.

La auditoría de los 115 archivos rastreados/candidatos no encontró extensiones binarias ni directorios de recursos prohibidos; inventarios, shaders reales leídos, resultados y fuentes públicas generadas permanecen ignorados. Se revisó que no se agregaran fuentes derivadas del juego.

Las pruebas levantaron y cerraron sus propios servidores en **127.0.0.1:8091** y **127.0.0.1:8092**. No se afirma que un servidor de esta sesión quede activo. Para abrir la plataforma: `PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid" bash scripts/dev.sh`, siempre en **127.0.0.1:8090**; si está ocupado, elegir otro puerto local con `PORT=8093`. `/` muestra la demo y `/game.html` informa el bloqueo del build real.
