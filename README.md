# PZ Browser Lab

Base local para un port personal de Project Zomboid a **TeaVM 0.16.0 / WebAssembly GC**. La demo ejecuta Java compilado en el navegador; no usa streaming. **Todavía no arranca Project Zomboid.** El estado y los bloqueos están en [STATUS.md](STATUS.md).

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

## Continuar cuando esté instalada la copia Linux

1. Instalar **Project Zomboid, Steam appid 108600**, abrirlo una vez y comprobar que funciona nativamente.
2. Fijar una copia **fuera de este repositorio**, por ejemplo `~/pz-pinned/<build>`, antes de compilar. Conservar el build id de Steam junto a la copia. No trabajar directamente sobre una instalación que Steam pueda actualizar.
3. Apuntar a la carpeta que contiene `ProjectZomboid64.json`:

```bash
export PZ_HOME="$HOME/pz-pinned/REEMPLAZAR-POR-BUILD"
source scripts/env.sh
tools/inventory/run.sh "$PZ_HOME"
bash scripts/build.sh --with-game
bash scripts/dev.sh
```

Para buscar instalaciones existentes: `tools/inventory/run.sh --find`. Devuelve `[]` y código 2 si no encuentra ninguna en las bibliotecas de Steam declaradas, Flatpak o `~/pz-pinned`.

El inventario es offline e idempotente y produce `inventory/report.md` e `inventory/inventory.json`, ambos locales e ignorados. Examina jars, clases sueltas, versiones de classfile, manifests, firmas JNI, exports `Java_*`, debug ELF, Kahlua y módulos LWJGL. Si el número de versión del juego no está en metadatos conocidos, informa **desconocido**. La inscripción dinámica de JNI no se puede resolver solamente con `nm`.

`--with-game` verifica los hashes del inventario, referencia el classpath externo y genera guardas nativas y el mapa GL. **Sigue usando `port.Boot`, que es una prueba de plataforma: no equivale a compilar el juego completo.** El siguiente trabajo requiere estudiar el inventario real, establecer el entrypoint del juego y resolver las primeras APIs ausentes.

## Paquetes de recursos

Preparar una lista explícita de rutas relativas, una por línea, como `media/...`, obtenida de las lecturas reales al arrancar. El generador no adivina qué archivos necesita el juego:

```bash
python3 tools/pack/pack.py --files /ruta/local/startup-files.txt
```

Lee `PZ_HOME`, genera `packs/index.json` y blobs identificados por SHA-256. Publica el índice al final y conserva blobs anteriores para no invalidar clientes abiertos. El tamaño objetivo por blob es 32 MiB; un archivo individual no se divide. No versionar la lista si revela datos que se quieren mantener privados.

El servidor monta `/packs` y `/media` en modo lectura, sirve ETag y rangos, y escucha exclusivamente en `127.0.0.1`. No monta el resto de `PZ_HOME`. El runtime `preloadPacks(vfs)` valida hashes, usa Cache Storage y prepara lecturas sincrónicas en memoria. `/Zomboid` admite escrituras con persistencia explícita mediante `await vfs.flush()`; **cerrar la pestaña no garantiza guardar cambios sin flush**. Un flush usa una sola transacción IndexedDB y conserva el snapshot anterior si se aborta.

## Compilador y límites actuales

- `port.compiler.BuildCompiler` usa la API de TeaVM desde Maven para referenciar el classpath externo y registrar clases/métodos alcanzados, diagnósticos y tamaño Wasm. `MAVEN_OPTS` vale `-Xmx20g` por defecto, sin reservar 20 GiB al iniciar.
- `FailFastNatives` genera cuerpos que lanzan `UnsupportedOperationException` con el símbolo JNI largo. Solo transforma firmas del inventario y fixtures propias; no sustituye nativas internas de TeaVM indiscriminadamente.
- `PlatformSubstitutions` prueba la API `SubstitutionPolicy` con nuestra API gráfica sintética. `tools/glgen/generate.py` clasifica las firmas reales y genera cuatro mapeos escalares exactos para GL11/GL11C. El resto conserva su wrapper o falla explícitamente si es nativo.
- `port/config/reflection.txt` limita la reflexión con `SimpleReflectionPolicy`. El manifiesto Lua/Kahlua todavía requiere el bytecode del juego.
- `BrowserFiles` conecta Java con el VFS. No es una sustitución completa de `java.io` / `java.nio`.
- Matrices y triangulación de quads tienen pruebas compartidas JVM/Wasm; aún no forman un pipeline fijo compatible con PZ. Tampoco hay traductor GLSL, MemoryUtil completo, física, SQLite, audio FMOD ni multijugador.

Reportes locales: `port/target/build-report.json`, `port/target/teavm-diagnostics.txt`, `port/target/jdk-api-report.txt`, `.toolchain/doctor.json`, `test-results/browser-results.json`. El reporte JDK corresponde únicamente al código alcanzado en la prueba; no es una auditoría completa de compatibilidad del juego.

## Reglas de colaboración

Leer [AGENTS.md](AGENTS.md) y [AGENT_LEARNINGS.md](AGENT_LEARNINGS.md). No publicar ni agregar archivos del juego, fuentes derivadas, SDKs privados, resultados de builds o capturas privadas. No hay repositorios remotos configurados por este trabajo. En la sesión inicial `.git` estaba en solo lectura: ver `docs/local-commits.md` para los commits pendientes.
