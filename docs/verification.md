# Evidencia de verificación — 2026-10-26, tarea 4

**90 pruebas aprobadas:** 44 JVM, 10 Python, 17 Node, 14 Chrome de plataforma y 5 Chrome con la copia real. Cero fallos, omitidos o flaky en los reportes finales. **El Wasm del main real se ejecutó; el menú, LuaManager.init y una partida no están demostrados.** La prueba de arranque verifica la detención conocida, no éxito del juego.

## Reproducción

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
bash scripts/test.sh
bash scripts/build.sh --with-game
python3 scripts/report_game.py --reports port/target/reports/game-main-lua
npx playwright test --config playwright.game.config.mjs
```

Las corridas se separaron por capas, sin navegador pesado ni juego nativo durante los builds completos. `build.sh` ejecuta JUnit. La fixture de membresía del hilo hijo se volvió a compilar/probar después de mover su aserción al padre para que un error no quedara solo en el handler. El entorno usa JDK Temurin 25.0.5+8, Node 24.22.1, Chrome 156.0.8078.47 y TeaVM 0.16.0. Chrome usa un perfil independiente y SwiftShader: no es una medición de la RTX 3090 ni de fps del juego.

## Cobertura comprobada

| Capa | Evidencia |
|---|---|
| JVM: memoria y handles | Límites, alineación, copia solapada, realloc/free, rechazo de vistas retiradas y tokens de campo. Arenas confinadas y vida de segmentos. Firmas polimórficas y lookup se ejercitan además en Wasm. |
| JVM: concurrencia | Colas, futuros, interrupciones/plazos, equidad, ReadWriteLock, capacidad cero de SynchronousQueue, límites/rechazo/drenaje de ThreadPoolExecutor y tareas programadas. Conserva las pruebas anteriores de Condition y FMA. |
| JVM: digest/geometría/contratos | Comparaciones con JDK 25, límites de bloques de digest, geometría y color; ObjectName, Formatter, jerarquías de excepciones y serialización rechazada. |
| JVM: backend | Caché BitSet equivalente a HashSet, fusión de interfaces marker, compilación de fixtures reflectivas, tipos nullable para ramas/casts/bloques y módulo de grupos de hilos. |
| Python | Inventario offline y exposición Lua mediante fixtures propias: firmas, modified UTF-8, seguimiento de registros/helpers, jerarquías, determinismo y rechazo de hashes cambiados. |
| Node | Servidor local, ETag/Range/confinamiento, VFS y eventos WatchService, ZIP/classpath comprimido/vacío/duplicados, CRC y GLSL por AST. |
| Chrome: APIs de tarea 4 | Memoria y handles tipados, productores bloqueados, transferencia directa, futures, scheduling, locks, watchers, recursos, digest, normalización y XML DOM/streams. Casos no soportados fallan explícitamente. |
| Chrome: persistencia | SQL real con prepared statements, rollback, blobs, Unicode e int64 máximo; datos JDBC sobreviven a recarga. FileChannel.force conserva su prueba de transacción exitosa y rechazo propagado. |
| Chrome: runtime/compilador | Metadata de campos raw anotados y parametrizados; vtable Object, intrinsics reflectivas Integer/Long, metadata de Class/Field/Method/Constructor, cierre suspendible por interfaz, recurso nulo, membresía del main y lifecycle del hijo. División unsigned por cero conserva una trampa Wasm documentada. |
| Chrome: monitores | wait interrumpido recupera el monitor y limpia el flag; nanos submilisegundo terminan. La fixture pequeña de ThreadGroup no reprodujo el bootstrap asíncrono del juego; el módulo completo verificó esa corrección. |
| Gráficos sintéticos | Píxeles leídos del framebuffer, uniforms, rechazo de shaders inválidos, input, pausas/yield y Cache Storage. No equivalen al renderer del juego. |
| Recursos reales | WorkshopTags por Java NIO; Title.png 1024×1024 por PNG → ImageBitmap → WebGL2 → readPixels: los 4.194.304 bytes RGBA coinciden con Pillow. Pares default, blit y vboRenderer_PositionColorUV traducidos/compilados/enlazados con glGetError=0. |
| Main real | HTTP solicita el módulo, Chrome lo valida y ejecuta. Estado `failed`, error `java.lang.LinkageError: Unknown platform: TeaVM`, menuRendered=false; sin pageerror sin capturar. Captura y consola conservadas. |

## Build real con Lua completo

| Corrida | Clases / métodos | APIs ausentes / diagnósticos | Wasm |
|---|---:|---:|---:|
| Base a1d4963 | 10.021 / 71.178 | 82 / 825 | 0 bytes |
| Final tarea 4 | 10.291 / 73.142 | **0 / 0** | **144.799.524 bytes** |

Tiempo TeaVM: 340.793 ms; heap máximo 20 GiB; pila JVM 16 MiB; RSS pico de Maven 8.543.984 KiB. Los tiempos excluyen preparación/tests y las corridas alcanzan distinto código. El manifiesto conserva 1.043 registros, 1.040 clases expuestas, 1.123 clases del jar con jerarquías y 35 externas, sin registros no resueltos. No se recortó Lua.

La emisión requirió corregir fallos del backend público fijado de TeaVM, además de las APIs: campo anotado raw, fusión de vtables marker, referencias reflectivas a intrinsics, jerarquías solo usadas en catch, exposición de helpers internos y referencias nullable en corrutinas. La inicialización de reflexión se dividió por clase conservando el orden: Chrome rechazaba una función de 9.245.402 bytes. El módulo final tiene 11.484 funciones de inicialización de reflexión y su función más grande mide 2.712.632 bytes (`teavm@initializer`). Ninguna supera el límite observado de 7.654.321 bytes. No se desensambló código del juego: estos tamaños/nombres proceden de secciones de metadata y límites de cuerpos.

Las fuentes públicas Apache-2.0 se generan bajo target ignorado con hashes fijados; no se modifica el jar Maven ni herramientas globales. El campo `compiler_patch: compact-reflection-caches-v1` identifica la caché conservada de tarea 3; las correcciones adicionales están en prepare_teavm.py y [task4-compatibility.md](task4-compatibility.md).

**Arranque:** La traza llega a `zombie.gameStates.MainScreenState.main → org.lwjglx.opengl.Display.init → org.lwjglx.LWJGLUtil.<clinit>`, donde falla con LinkageError: Unknown platform: TeaVM. La traza original de TeaVM usa offsets FakeClass. Se resolvieron contra nombres y límites del Wasm, sin desensamblar: `MainScreenState.main`, `Display.init` y `LWJGLUtil.<clinit>` están presentes. El error está atrapado por GameBoot; no hubo pageerror sin capturar. La detección de plataforma de LWJGLx requiere un puente real para navegador antes de continuar con GLFW/GL20; no se cambió os.name para simular un sistema soportado.

**Plataforma:** 936 clases / 4.331 métodos, 1.734.303 bytes Wasm, cero diagnósticos, 5.405 ms y RSS pico 1.132.840 KiB. Sus resultados se conservan aparte del juego.

## Artefactos locales

- `port/target/reports/game-main-lua/`: build-report, missing-apis, diagnósticos vacíos, chrome-startup.json y wasm-functions.json; `build-task4-pass16.log`.
- `port/target/web/game/boot.wasm`: módulo real. `port/target/web/boot.wasm`: módulo de plataforma.
- `port/target/reports/task4-*`: referencias y errores intermedios, sin presentar fallos internos como conteo final. BuildCompiler invalida salidas al iniciar y elimina módulos con errores severos incluso si TeaVM alcanzó a escribirlos.
- `port/target/reports/platform-smoke-minimal/`, `build-task4-platform-final.log`, `verification-task4-jvm-final.log` y XML de Surefire.
- `verification-task4-python.log`, `verification-task4-node.log`, `verification-task4-browser.log`, `verification-task4-real-final.log`.
- `test-results/browser-results.json` (14 aprobados) y `test-results/real-game-results.json` (5 aprobados), con resultados separados en platform/ y real/.
- Capturas inspeccionadas: `test-results/task4-platform.png` y `test-results/game-main-task4.png`. La primera es la demo; la segunda muestra el error del arranque real. Ninguna muestra el menú.

## Límites y auditoría

No se demostró equivalencia del juego, carga de Lua, render del menú, física, audio ni guardado de partidas. Persisten GLFW/GL20/pipeline fijo/loop del renderer, el include util/math y nativas de PZ. Las limitaciones de memoria/FFM, reflexión, JMX, serialización, JDBC y concurrencia están detalladas en [task4-compatibility.md](task4-compatibility.md); un tipo presente no implica todas sus operaciones.

La auditoría examinó 269 archivos rastreados/candidatos: cero binarios y cero directorios/recursos prohibidos. Las fuentes son propias o parches sobre fuentes públicas fijadas; ningún código del juego decompilado/derivado está versionado. `git diff --check` pasó. HEAD sigue en a1d4963; `.git` es de solo lectura. No hubo commits, remotos, publicación, sudo ni cambios globales.

Los servidores de pruebas escucharon solo en 127.0.0.1:8091/8092 y terminaron con Playwright. No se alteraron aplicaciones del dueño ni se ejecutó el juego nativo.
