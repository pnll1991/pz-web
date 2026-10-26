# Evidencia de verificación — 2026-10-25

Las entradas son sintéticas y de autoría del proyecto. No hay archivos, logs, shaders ni capturas reales de Project Zomboid en estas pruebas.

## Ejecución reproducible

```bash
source scripts/env.sh
python3 scripts/doctor.py
bash scripts/test.sh
bash scripts/check-toolchains.sh
```

Resultados: **3 tests JVM, 7 Python, 8 Node y 7 Playwright aprobados**. Los logs de la ejecución inicial están en `.toolchain/verification.log` y `.toolchain/toolchain-check.log` (ignorados).

## Qué demuestra cada capa

| Capa | Evidencia |
|---|---|
| JVM | Cálculo de matrices, orden de multiplicación, índices de quads, reflexión y errores de operaciones inválidas. Las mismas comprobaciones de núcleo se ejecutan dentro de Wasm. |
| Inventario | Compila una clase Java 25 y una biblioteca ELF propias. Detecta los 9 métodos nativos de las fixtures, resuelve JNI largo/overloads y distingue exports sin match. Dos ejecuciones producen los mismos reportes. Herramientas ausentes y destinos dentro del juego fallan claramente. |
| Generación GL | Examina bytecode sintético con namespace LWJGL de prueba, clasifica cuatro firmas exactas y conserva una función desconocida como nativa que debe fallar. No se copia código de LWJGL ni de PZ. |
| HTTP | Servidor real sobre loopback: COOP/COEP, MIME Wasm, GET/HEAD, rangos/sufijos/416, ETag/304/If-Range, rechazo de escritura, symlink externo y origen ajeno. |
| Wasm/Chrome | `WebAssembly.Instance` real, Java `main`, reflexión, `SubstitutionPolicy`, forwarding de métodos GL y guarda JNI. ByteBuffer directo devuelve checksum 170. Java lee y escribe UTF-8 a través del VFS. |
| WebGL2 | Compila shader, dibuja geometría sintética, `readPixels` devuelve `[64,217,166,255]`. Shader inválido falla; `glGetError` expuesto por Java devuelve 1280 ante enum inválido y luego 0, sin simular resultados. |
| Navegador | Loop Java por `requestAnimationFrame`, pausa/reanudación, teclado físico y liberación al perder foco, entrada Unicode. |
| Persistencia | Escritura → flush → recarga conserva la nota. Un archivo sin flush desaparece. Una transacción abortada conserva el snapshot previo. |
| Paquetes | Python genera blobs propios; Chrome verifica hashes y los carga. Tras recarga obtiene los blobs de Cache Storage sin requerir otra descarga. Un blob corrupto provoca error. |
| Emscripten | C propio → módulo Wasm separado → ejecución en Node: `add(20,22) = 42`. |
| Ghidra/Vineflower | Importación y análisis de `libprobe.so` propios completados; decompilación de `ReflectionProbe` propia. Salidas bajo `build/`, preferencias de Ghidra bajo `.toolchain/user-home`. |

## Reporte del build validado

```json
{
  "mode": "platform-smoke",
  "game_initialized": false,
  "success": true,
  "teavm": "0.16.0",
  "target": "WEBASSEMBLY_GC",
  "reachable_classes": 171,
  "reachable_methods": 647,
  "wasm_bytes": 126096,
  "external_classpath_entries": 0
}
```

El tiempo de compilación de TeaVM en esa ejecución fue aproximadamente 1,8 segundos; no incluye descargas, Maven ni las pruebas. No es un benchmark de PZ. El compilador también informa métodos emitidos, que no son la misma métrica que métodos alcanzados del reporte.

Artefactos locales: `port/target/build-report.json`, `port/target/teavm-diagnostics.txt`, `port/target/jdk-api-report.txt`, `.toolchain/doctor.json`, `test-results/browser-results.json` y `test-results/platform.png`. La captura de escritorio se inspeccionó visualmente: canvas, checks, campo de guardado y avisos de demo sintética visibles, sin errores de página.

## Límites

- Chrome 156 usa SwiftShader para la prueba; no se midió rendimiento de GPU, fps del juego ni estabilidad durante una hora.
- Ni el menú, ni Lua, ni un mundo, ni los bancos FMOD se probaron: falta la instalación Linux del juego.
- `--with-game`, los shaders reales, las clases propietarias, JNI dinámico y el mapeo de mandos del juego requieren evidencia adicional. Los checks sintéticos no los dan por compatibles.
- Se auditó `git ls-files --others --exclude-standard`: solo fuentes/configuración/documentación propias y los documentos de tarea del usuario; las rutas de activos, binarios y resultados están ignoradas.
- Los commits están pendientes por el montaje de `.git` en modo solo lectura; no hubo publicación ni cambios en remotos.
