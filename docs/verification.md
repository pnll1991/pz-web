# Evidencia de verificación — 2026-10-26, tarea 3

**60 pruebas aprobadas:** 21 JVM, 10 Python, 15 Node, 10 Chrome de plataforma y 4 Chrome con la copia real. La corrida final no tuvo fallos ni casos omitidos. **Ninguna prueba ejecutó el menú, LuaManager.init ni una partida.**

## Comandos reproducibles

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
bash scripts/test.sh
npx playwright test --config playwright.game.config.mjs
```

La validación final se ejecutó por capas: `scripts/build.sh` (incluye JUnit), `python3 -m unittest discover -s tests/python -v`, `npm test`, `npm run test:browser` y la suite real del comando anterior. No hubo navegador ni juego nativo durante los builds completos. Chrome usa una instancia separada y SwiftShader; los resultados no miden la RTX 3090 ni fps de PZ. La referencia de decodificación PNG usa Pillow 12.3.0, ya disponible en el entorno.

| Capa | Evidencia |
|---|---|
| JVM: núcleo y reflexión | Matrices/quads, dispatch virtual reflectivo en una subclase y lectura/escritura de campos de objeto y primitivos. TransferTo Unicode, Paths y bytes 0/128/255/EOF. |
| JVM: caché del compilador | CompactTypeSet compara las respuestas de inserción con HashSet para 20.000 tipos internados, 100.000 inserciones repetidas y consumidores independientes. El build comprueba que estén activas las tres cachés parcheadas. |
| JVM: concurrencia | Colas, snapshots y eliminación por identidad, incluido retiro concurrente durante equals; futures, resultados, excepciones, timeout/cancelación, pools y terminación; Condition, propiedad del lock y conservación del turno justo tras interrupción. |
| JVM: FMA | Comparación de bits con JDK 25 para 5.000 ternas aleatorias de float y otras 5.000 de double, más ceros con signo, subnormales, infinitos y cancelación de overflow. |
| JVM: archivos | FileChannel: transferencias parciales, IO posicional, buffers, posición compartida, truncado, permisos y cierre. No valida mmap, locks del SO ni watchers. |
| Python | Inventario y exposición: fixtures propias, metadatos de launcher/JNI, modified UTF-8, seguimiento de registros y helpers, jerarquías, determinismo y rechazo de hashes cambiados. |
| Node | HTTP local y controles existentes, VFS/IndexedDB y traducción GLSL por AST. Se verifican macros/comentarios, scopes, defaults y rechazo de features sin soporte. |
| Java en Chrome | Wasm GC real, sustituciones estándar, colas/futures/pools/locks cooperativos, grupos e herencia de Threads, join antes de start y rechazo del segundo start. JNI ausente produce error explícito. |
| FileChannel en Chrome | Bytes escritos por Java sobreviven a la recarga después de force. Una falla inyectada de flush llega a Java como IOException. Streams/RandomAccessFile/NIO comparten el VFS. No se afirma durabilidad por cada escritura rws/rwd. |
| Más APIs en Chrome | UUID con bits altos, String con high byte, FMA en casos límite, PrintStream(File), Matcher, CRC32 y errores explícitos de procesos/bibliotecas. |
| Gráficos sintéticos | Shader básico y píxel `[64,217,166,255]`; shader de escritorio traducido, uniform `[0.25,0.5,0.75,1]` y píxel `[64,128,191,255]`; shaders inválidos fallan. Input, pausa/yield y Cache Storage conservan sus pruebas. |
| Shaders reales | Fetch local de los pares `default`, `blit`, `vboRenderer_PositionColorUV`: traducción, compilación y enlace reales en WebGL2; glGetError = 0. Reportes con hashes/metadatos, sin fuentes del juego versionadas. No se demostró su uso por el menú. |
| Texto y textura reales | WorkshopTags leído por Java NIO con longitud/hash coincidentes; escritura bajo /Zomboid persistida. Title.png 1024×1024 pasa PNG → ImageBitmap → WebGL2 → framebuffer → readPixels; todos los 4.194.304 bytes RGBA coinciden con Pillow y glGetError = 0. |
| Pantalla del juego | El estado real servido es game-main fallido: el canvas permanece oculto y no se solicita Wasm. Además se conserva la prueba aislada de una respuesta fallida. Captura inspeccionada visualmente. |

## Compilaciones con reflexión Lua completa

| Corrida | Clases / métodos | APIs ausentes / diagnósticos | Tiempo TeaVM | Heap máximo / RSS pico de Maven |
|---|---:|---:|---:|---:|
| Referencia inicial | 7.778 / 53.546 | 80 / 647 | 202.232 ms | 20 GiB / 21.630.724 KiB |
| Intermedia | 8.563 / 57.716 | 62 / 497 | 251.428 ms | 20 GiB / 21.744.484 KiB |
| Ampliada, antes del parche | No completado | No contabilizable | No registrado por la versión anterior del reporte | 20 GiB / no registrado; OutOfMemoryError en caché de reflexión |
| **Final, caché compacta** | **10.021 / 71.178** | **82 / 825** | **282.236 ms** | **20 GiB / 6.282.064 KiB** |

Todas esas compilaciones produjeron **0 bytes Wasm del juego**. El reporte final incluye 82 símbolos ausentes distintos y, dentro de los 825 diagnósticos totales, cuatro mensajes adicionales de interfaces/superclases faltantes. 41 de los 80 símbolos iniciales ya no aparecen; se alcanzaron 43 nuevas ausencias. La lista de 52 de la tarea 2 venía del análisis mínimo y no es directamente comparable.

El parche local usa el source jar público Apache-2.0 de TeaVM 0.16.0 fijado por SHA-256, sin modificar Maven global ni el jar de dependencias. Cambia tres conjuntos de tipos vistos por BitSet; conserva la política y el manifiesto Lua. Su mecanismo y límites están en [task3-compatibility.md](task3-compatibility.md). El reporte identifica `compiler_patch: compact-reflection-caches-v1`.

**Plataforma final:** 571 clases, 2.503 métodos alcanzados, **754.331 bytes Wasm**, cero diagnósticos; 3.539 ms de TeaVM, heap máximo 20 GiB y RSS pico de Maven 804.608 KiB. Ejecutada en Chrome con las 14 pruebas mencionadas. Estas cifras no describen el juego.

Los tiempos excluyen preparación, tests y el resto de Maven; RSS pertenece al proceso completo. Las corridas alcanzan distinto código y no son un benchmark controlado de rendimiento.

```bash
# Sin juego nativo ni navegador pesado en paralelo:
MAVEN_OPTS=-Xmx20g bash scripts/build.sh --with-game
# Se espera fallo por APIs ausentes; luego:
python3 scripts/report_game.py --reports port/target/reports/game-main-lua
```

El agrupador rechaza estados `running` o `compiler-failed`, porque allí no hay conteo final. BuildCompiler invalida reportes derivados al iniciar y borra un Wasm anterior de ese mismo modo al fallar.

## Artefactos conservados

- `port/target/reports/game-main-lua/`: reporte final, diagnósticos y `missing-apis.json`/`.md` con call sites. Log: `build-task3-lua-final-tree.log`.
- `port/target/reports/task3-baseline-lua/`, `task3-intermediate-lua/`, `task3-oom-lua/`: referencias anteriores. El último conserva solo el fallo de memoria, no una lista de APIs vieja.
- `port/target/reports/platform-smoke-minimal/`, `build-task3-platform-final.log` y `port/target/surefire-reports/`: plataforma y 21 JUnit.
- `verification-task3-python.log`, `verification-task3-node.log`, `verification-task3-browser.log`, `real-browser-task3.log`: suites finales por capa.
- `test-results/browser-results.json` y `test-results/real-game-results.json`: 10 y 4 aprobados, cero omitidos/fallidos/flaky. Las carpetas de salida `test-results/platform/` y `test-results/real/` evitan que una suite borre la evidencia de la otra.
- Capturas inspeccionadas: `test-results/task3-shader-compatibility.png` y `test-results/game-blocked-task3.png`. Ninguna es el menú. La prueba del píxel traducido es una aserción readPixels, independiente de los indicadores iniciales de la demo visibles en la captura.
- El Wasm de plataforma existe; `port/target/web/game/boot.wasm` no existe. Los archivos genéricos de reporte bajo `port/target/` corresponden al último build (juego con Lua); los directorios por modo conservan también el de plataforma.
- Inventario: `inventory/inventory.json` y `inventory/report.md`. Exposición: `port/target/config/exposure.json` y `reflection-lua.txt`; el archivo activo `reflection.txt` corresponde al último modo construido, en esta corrida al juego con Lua completo.

## Límites y auditoría

Persisten los bloqueos de Unsafe/FFM/MethodHandles, XML/DOM, JDBC/SQLite, management, concurrencia adicional, recursos, watchers y otras APIs detalladas en el reporte. GLFW, arena nativa y GL20 no están conectados al renderer. Solo cuatro mappings escalares GL11/GL11C están confirmados; no hay implementación de las demás nativas de PZ ni equivalencia de física/audio/guardado comprobada.

La auditoría de los **115 archivos rastreados/candidatos** no encontró binarios ni directorios de recursos prohibidos; también se revisaron las fuentes nuevas para no incluir código derivado del juego. Los assets y resultados continúan ignorados. `git diff --check` no informó errores. No hubo commits porque `.git` permanece de solo lectura, ni publicación, remotos o cambios de herramientas globales.

Los servidores de tests se limitaron a 127.0.0.1:8091/8092 y terminaron con Playwright. No se detuvieron servidores ajenos. La herramienta nativa del juego y las toolchains auxiliares no se ejecutaron durante esta tarea.
