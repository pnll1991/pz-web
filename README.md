# PZ Browser Lab

Port personal de Project Zomboid (build 25485521, v42.21) al navegador con **TeaVM 0.16.0 / WebAssembly GC**. Hoy se juega en Chrome: menú, partida y multijugador contra el servidor dedicado real, por `/play.html` (vehículos y voz siguen pendientes). Estado, evidencia y bloqueos: [STATUS.md](STATUS.md).

**Para levantar, manejar o actualizar el proyecto: [docs/operacion/](docs/operacion/README.md).** Cómo se trabaja (ramas, PRs, agentes): [docs/procesos/](docs/procesos/README.md).

## Probar lo que ya funciona

Desde la raíz del repositorio:

```bash
source scripts/env.sh
npm ci --ignore-scripts --no-audit --no-fund
bash scripts/test.sh
bash scripts/dev.sh
```

Abrir **http://127.0.0.1:8090**. Se ve un triángulo de prueba, las comprobaciones de plataforma y un campo para guardar una nota en IndexedDB. La nota persiste al recargar. El canvas recibe input físico; al perder foco libera las teclas. El cuadro de texto prueba entrada de caracteres, incluido Unicode.

`test.sh` compila, ejecuta las pruebas JVM, Python, Node y Chrome, y deja una captura en `test-results/platform.png`. Playwright usa un perfil separado y Chrome local, con SwiftShader para que el test de píxeles sea reproducible. Sus tiempos **no miden rendimiento del juego ni de la GPU real**.

## Instalar el entorno desde cero

Linux x86_64; las versiones y checksums están en `scripts/toolchain-lock.json`.

```bash
python3 scripts/bootstrap.py --extras
source scripts/env.sh
npm ci --ignore-scripts --no-audit --no-fund
python3 scripts/doctor.py
bash scripts/test.sh
bash scripts/check-toolchains.sh
```

Los paquetes se descargan de sus distribuidores oficiales, se comprueba su checksum y se instalan en `.toolchain/`. No se usa sudo ni se modifica `.bashrc`. Se aprovechan Python 3, curl, gcc, git, binutils, `file` y Chrome del sistema; el doctor informa si falta alguno. `CHROME_PATH` permite usar otro ejecutable Chrome. Maven y npm usan caches locales ignoradas.

`check-toolchains.sh` compila una función C propia a Wasm y la ejecuta, analiza una biblioteca sintética con Ghidra y decompila una clase **nuestra** con Vineflower. Todos esos resultados van a `build/`. No usa archivos del juego.

## Compilar contra la copia Linux instalada

1. La copia actual está en `~/pz-pinned/25485521/projectzomboid`: PZ 42.21, LWJGL 3.4.1. El build id se contrastó con Steam y el hash del jar instalado.
2. Fijar una copia **fuera de este repositorio**, por ejemplo `~/pz-pinned/<build>`, antes de compilar. Conservar el build id de Steam junto a la copia. No trabajar directamente sobre una instalación que Steam pueda actualizar.
3. Apuntar a la copia fijada. Los scripts aceptan también el directorio padre cuando el launcher está en `projectzomboid/`:

```bash
export PZ_HOME="$HOME/pz-pinned/25485521"
source scripts/env.sh
tools/inventory/run.sh "$PZ_HOME"
bash scripts/build.sh --with-game
bash scripts/dev.sh
```

Para buscar instalaciones existentes: `tools/inventory/run.sh --find`. Devuelve `[]` y código 2 si no encuentra ninguna en las bibliotecas de Steam declaradas, Flatpak o `~/pz-pinned`.

El inventario es offline e idempotente y produce `inventory/report.md` e `inventory/inventory.json`, ambos locales e ignorados. Examina jars, clases sueltas, versiones de classfile, manifests, firmas JNI, exports `Java_*`, debug ELF, Kahlua y módulos LWJGL. Si el número de versión del juego no está en metadatos conocidos, informa **desconocido**. La inscripción dinámica de JNI no se puede resolver solamente con `nm`.

`--with-game` conecta `port.GameBoot` con el **main real del launcher**, verifica hashes y configuración y genera las guardas nativas, el mapa GL y el manifiesto Lua. La compilación completa usa reflexión Lua por defecto. El resultado comprobado está en [STATUS.md](STATUS.md). `--reflection minimal` permite obtener un diagnóstico parcial con menos memoria; no demuestra compatibilidad completa.

El módulo del juego usa `port/target/web/game/boot.wasm`; la demo conserva `port/target/web/boot.wasm`. Un build fallido no conserva un Wasm anterior de ese modo. `/game.html` comprueba el resultado real y muestra el bloqueo; `/` sigue siendo la demo. Los reportes se conservan por modo en `port/target/reports/`.

Pruebas con recursos de la copia real y observación de su arranque, después de compilar ambos módulos:

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521"
bash scripts/build.sh
bash scripts/build.sh --with-game
npx playwright test --config playwright.game.config.mjs
```

Estos tests leen `media/WorkshopTags.txt` mediante Java, comprueban persistencia y comparan todos los bytes RGBA de `media/ui/Title.png` contra su decodificación local con Pillow. También traducen, compilan y enlazan tres pares de shaders originales (`default`, `blit`, `vboRenderer_PositionColorUV`). Una prueba separada carga el Wasm del main real y registra su estado, consola y captura; no demuestra el menú. Usan el puerto 8092; los tests sintéticos usan 8091 y el servidor de desarrollo 8090. Los artefactos de ambas suites se guardan en subdirectorios separados de `test-results/`. No ejecutar estas pruebas ni el juego nativo mientras corre el build completo.

## Paquetes de recursos

Preparar una lista explícita de rutas relativas, una por línea, como `media/...`, obtenida de las lecturas reales al arrancar. El generador no adivina qué archivos necesita el juego:

```bash
python3 tools/pack/pack.py --files /ruta/local/startup-files.txt
```

Lee `PZ_HOME`, genera `packs/index.json` y blobs identificados por SHA-256. Publica el índice al final y conserva blobs anteriores para no invalidar clientes abiertos. El tamaño objetivo por blob es 32 MiB; un archivo individual no se divide. No versionar la lista si revela datos que se quieren mantener privados.

El servidor monta `/packs` y `/media` en modo lectura, sirve ETag y rangos, y escucha exclusivamente en `127.0.0.1`. Las rutas `/classpath/lookup` y `/classpath/resource/` leen recursos del classpath original sin extraer el jar. No monta el resto de `PZ_HOME`. El runtime `preloadPacks(vfs)` valida hashes, usa Cache Storage y prepara lecturas sincrónicas en memoria. `/Zomboid` admite escrituras con persistencia explícita mediante `await vfs.flush()`; **cerrar la pestaña no garantiza guardar cambios sin flush**. Un flush usa una sola transacción IndexedDB y conserva el snapshot anterior si se aborta.

## Compilador y límites actuales

- `port.compiler.BuildCompiler` usa la API de TeaVM desde Maven para referenciar el classpath externo y registrar clases/métodos alcanzados, diagnósticos y tamaño Wasm. `MAVEN_OPTS` vale `-Xmx20g -Xss16m` por defecto, sin reservar 20 GiB al iniciar. La pila ampliada permite serializar los bloques anidados del main completo.
- `scripts/prepare_teavm.py` aplica parches locales y fijados a las cachés de reflexión, monitores, metadata de campos y fusión de interfaces de TeaVM 0.16.0. Mantiene la exposición Lua completa. Fuente pública generada únicamente bajo `target/`, sin modificar herramientas globales; regresiones y alcance en [docs/task4-compatibility.md](docs/task4-compatibility.md).
- `FailFastNatives` genera cuerpos que lanzan `UnsupportedOperationException` con el símbolo JNI largo. Solo transforma firmas del inventario y fixtures propias; no sustituye nativas internas de TeaVM indiscriminadamente.
- `PlatformSubstitutions` registra las implementaciones propias de concurrencia, grupos de hilos, contratos de IO y FileChannel, incluyendo sus clases anidadas. `JdkCompatibility` agrega operaciones estándar concretas sin alterar el juego. `tools/glgen/generate.py` mantiene cuatro mapeos escalares exactos para GL11/GL11C; los métodos GL20 del juego aún no usan el traductor de shaders.
- `tools/exposure/generate.py` analiza el bytecode sin cargar clases del juego: registros literales, helpers, miembros públicos y jerarquías. Produce `exposure.json` y `reflection-lua.txt` en `port/target/config`. Los registros dinámicos que no se puedan resolver se informan explícitamente; no se afirma que `LuaManager.init` termine.
- `BrowserFileSystem` instala el SPI de archivos de TeaVM para que `File`, streams, `RandomAccessFile`, `Files` y `Paths` compartan el VFS de JS. FileChannel comparte posición/cierre con el stream, admite IO posicional y transferencias; `force()` suspende su corrutina hasta completar IndexedDB y propaga las fallas. WatchService observa cambios reales del VFS. Mapeo de archivos y sincronización automática `rws/rwd` siguen pendientes; los locks del SO fallan explícitamente.
- La tarea 4 agrega memoria virtual comprobada para MemoryUtil/Unsafe/Foreign Memory, handles, recursos del classpath, XML del navegador, digest, geometría, concurrencia y SQLite Wasm oficial con JDBC y persistencia. Las operaciones pendientes fallan explícitamente; resolver firmas no equivale a implementar el JDK completo. Ver los límites en [docs/task4-compatibility.md](docs/task4-compatibility.md).
- `glsl.mjs` usa `@shaderfrog/glsl-parser` 7.0.1 local para transformar ASTs, conservar defaults de uniforms e insertar promociones escalares de GLSL. Es un subconjunto comprobado; los includes propios de PZ, GLSL restante, pipeline fijo completo y callbacks GLFW siguen pendientes. Matrices y quads de la base todavía no están conectados al renderer del juego. Física, FMOD y los JNI originales de SQLite siguen pendientes; JDBC usa el motor SQLite Wasm separado.

Reportes locales: `port/target/build-report.json`, `port/target/teavm-diagnostics.txt`, `port/target/jdk-api-report.txt`, `.toolchain/doctor.json`, `test-results/browser-results.json`. Los reportes indican el modo y la reflexión: el diagnóstico mínimo del juego no cubre toda la exposición Lua. `python3 scripts/report_game.py --reports port/target/reports/game-main-lua` agrupa las APIs faltantes del análisis completo y conserva sus call sites.

## Reglas de colaboración

Leer [AGENTS.md](AGENTS.md) y [AGENT_LEARNINGS.md](AGENT_LEARNINGS.md). No publicar ni agregar archivos del juego, fuentes derivadas, SDKs privados, resultados de builds o capturas privadas. El único remoto es el repositorio privado `pnll1991/pz-web`; todo cambio entra por PR a `dev` ([docs/procesos/ramas-y-prs.md](docs/procesos/ramas-y-prs.md)).
