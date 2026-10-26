# Evidencia de verificación — 2026-10-26

La suite general usa fixtures propias. Una suite separada lee recursos reales de la copia fijada y conserva solamente metadatos y resultados locales. **Ninguna prueba ejecutó el menú, LuaManager.init ni una partida.**

## Comandos reproducibles

```bash
source scripts/env.sh
export PZ_HOME="$HOME/pz-pinned/25485521"
tools/inventory/run.sh "$PZ_HOME"
MAVEN_OPTS=-Xmx2g bash scripts/test.sh
npx playwright test --config playwright.game.config.mjs
```

**37 pruebas aprobadas:** 6 JVM, 10 Python, 10 Node, 8 Chrome de plataforma y 3 Chrome de copia real. La decodificación de referencia de PNG usa Pillow 12.3.0 instalado en este entorno. Los tests gráficos usan Chrome separado con SwiftShader; no miden la RTX 3090 ni fps del juego.

| Capa | Evidencia |
|---|---|
| JVM | Matrices/quads/reflexión originales de la base; transferTo con Unicode y streams abiertos; resolución de Paths comparada con el JDK; bytes 0, 128, 255 y EOF. |
| Inventario | Launcher Linux anidado y clave `classpath`, modified UTF-8 con surrogates aislados, JNI/overloads, fallos claros, generación determinista y sin copia de clases. Dos inventarios reales consecutivos tuvieron el mismo SHA-256 de salida. |
| Exposición | Fixture propia verifica registros de clases literales, seguimiento de helper, jerarquía, miembros públicos, reporte de clase dinámica sin adivinarla, determinismo y rechazo si cambia el hash del jar. |
| HTTP | Loopback, COOP/COEP, MIME Wasm, GET/HEAD, Range/ETag, recursos solo lectura y controles de origen/ruta existentes. El montaje de la copia real aceptó el directorio padre del launcher. |
| VFS | Directorios, rename de subárbol, rechazo de borrado no vacío, offsets/ceros, cuota y permisos. Contenido y metadata se persisten en una transacción; aborto preserva el snapshot anterior. |
| Java en Chrome | Instancia WebAssembly real, main de plataforma, guarda JNI, checksum 170 de buffer directo. File/FileInputStream/RandomAccessFile/Files/Paths operan sobre el VFS JS; texto, directorios vacíos y timestamps sobreviven a recarga. |
| Gráficos/input | Shader sintético y píxel `[64,217,166,255]`, error de shader inválido y glGetError real. Loop de prueba, teclado/blur/Unicode y cache de paquetes conservan sus tests. |
| Texto real | `media/WorkshopTags.txt`, paquete local precargado y lectura Java NIO. Longitud y SHA-256 coinciden con el archivo original. Escritura Java persistida bajo `/Zomboid/real-copy-test`. |
| Textura real | `media/ui/Title.png`, 1024×1024: PNG → ImageBitmap → textura WebGL2 → framebuffer → readPixels. SHA-256 de **todos** los 4.194.304 bytes RGBA igual al de Pillow. Sin error GL. No es una captura del menú. |
| Interfaz del juego | Ante un build fallido, `/game.html` informa el bloqueo y no solicita ningún módulo Wasm ni reemplaza el juego por la demo. |

## Compilaciones

| Modo | Resultado |
|---|---|
| Plataforma | Éxito; 251 clases, 1.084 métodos alcanzados, 232.283 bytes Wasm; TeaVM 2.846 ms en la corrida registrada. Heap máximo 2 GiB, RSS pico del proceso Maven 548.892 KiB. |
| Main real / reflexión mínima | Falla; 3.173 clases, 13.867 métodos, 232 diagnósticos, 52 APIs distintas ausentes, 0 bytes Wasm. TeaVM 12.449 ms; heap máximo 2 GiB, RSS pico de Maven 1.420.160 KiB. |
| Main real / reflexión Lua | Falla por `OutOfMemoryError` con heap de 2 GiB y también con 3 GiB. No hay conteo final ni Wasm válido. El reporte final conserva el fallo, no las cifras de otro build. |

Los tiempos son de compilación de TeaVM, excluyen preparación, pruebas y Maven; los RSS son del proceso completo. No son un benchmark de PZ. El límite normal de `scripts/build.sh` sigue siendo `MAVEN_OPTS=-Xmx20g`; en esta sesión se redujo explícitamente por la RAM disponible.

Para reproducir el análisis parcial (se espera salida distinta de cero mientras falten APIs):

```bash
MAVEN_OPTS=-Xmx2g bash scripts/build.sh --with-game --reflection minimal
python3 scripts/report_game.py
```

Para la reflexión Lua, después de liberar memoria:

```bash
bash scripts/build.sh --with-game
```

`port/target/reports/<modo>-<reflexión>/` conserva los resultados; los archivos directamente bajo `port/target/` corresponden al último build. `port/target/web/game/boot.wasm` y `port/target/web/boot.wasm` son salidas independientes. Solo el segundo existe y es ejecutable en este estado.

## Artefactos y límites

- Logs: `verification-task2.log`, `build-game-minimal.log`, `build-game-lua-3g.log`, `real-browser-tests.log`.
- Servicio dejado activo: `http://127.0.0.1:8093`, copia real montada; health, recurso real y aislamiento verificados, más pantalla de bloqueo real y plataforma en Chrome. Captura de diagnóstico inspeccionada: `test-results/game-blocked.png`.
- Detalles de navegador: `test-results/browser-results.json`, `test-results/real-game-results.json` y `test-results/platform.png`.
- Inventario: `inventory/inventory.json` y `inventory/report.md`; exposición: `port/target/config/exposure.json` y `reflection-lua.txt`.
- APIs faltantes: `port/target/reports/game-main-minimal/missing-apis.json` y `.md`, con ubicaciones de uso. Corregir APIs alcanzadas puede revelar otras nuevas; no se promete que las 52 sean el total del juego.
- No se probaron shaders GLSL reales, callbacks GLFW del juego, JNI implementada, loop real, física, sonido ni guardado de partidas. `Thread.onSpinWait` con yield no reemplaza una auditoría de todos los busy-waits.
- La persistencia requiere flush asíncrono explícito. No se afirma durabilidad de FileChannel ni de modos RandomAccessFile `rws/rwd`.
- La auditoría de archivos rastreados y candidatos a git no encontró binarios, recursos ni código derivado del juego. Los assets, paquetes de prueba y resultados permanecen ignorados.

Las verificaciones de toolchains auxiliares (Emscripten, Ghidra y Vineflower) pertenecen a la base del 2026-10-25; no se repitieron porque esta tarea no las modificó.
