# Ronda 92 — texturas fuera del hilo principal en la 1.ª carga

**Rama:** `perf/texturas-en-paralelo` desde `dev` (el análisis va en `docs/analisis-texturas-paralelo`) · **PR a:** `dev` · **Quién lo pide:** dueño

## Objetivo

Bajar los ~110 s de CPU de texturas de la 1.ª carga (de elegir el personaje a `ingame`) sacando el trabajo del hilo principal. Las texturas que recibe el juego tienen que ser idénticas byte a byte a las de hoy, y no se cambia ninguna regla del juego.

## Contexto

- Análisis completo: `docs/analisis-texturas-en-paralelo.md` (opciones A, B y C, riesgos y plan).
- Perfil del 8/11 (`STATUS.md`, sección "perfil de la carga y caché de texturas"). El camino de páginas de `.pack` suma ~82 s de self. El camino stb (`NativeImage.read` y `TilesetDepthTexture.load`, que llegan a `port.shim.BrowserStb`) suma ~18 s de self y ~41 s inclusivos.
- `texcache` (IndexedDB, `PackImageCache` + `PackImageCodec`) solo ayuda desde la 2.ª carga. `?texcache=verify` compara contra el original.
- Los hilos Java del juego no pueden ir a Workers, porque Wasm GC no comparte el heap (`docs/notes-from-code.md`, ronda 81, frente 4). Sí puede ir un heap separado con su propia instancia.

## Pasos (en orden; cada uno se puede cortar ahí)

1. **Medición sin build** (`port/runtime/texcache.mjs`):
   - `inflight`/`maxInflight` en `lookup`;
   - `decodeMs`, medido desde que se resuelve el lookup hasta `begin()`;
   - `restoreMs` en los aciertos;
   - todo en la línea `caches`.
   - Prueba en Node en `tests/texcache.test.mjs`.
   - Una 1.ª carga fría en la instancia de prueba.
2. **A2, camino stb** (necesita build):
   - un Worker decodifica por ruta los PNG que van a pasar por `stbi_load` (`DecompressionStream` + desfiltrado, igual que `BrowserStb.decodePng`);
   - un mapa de resultados en `pzRuntime`;
   - `BrowserStb.stbiLoad` lo consulta antes de decodificar en Java;
   - copia en bloque del archivo en lugar de `Uint8Array.get` byte a byte;
   - caché en IDB con la sal `stb:f<flip>`.
   - Opt-in `?stbworker=1`.
3. **B, prototipo opt-in** `?texworker=1`, solo si `maxInflight > 1` en el paso 1:
   - instanciar el mismo `boot.wasm` (Module por `postMessage`) en N Workers;
   - un transformer que clona `FileTask_LoadPackImage.call()` en `port$workerCall(InputStream)`, reemplazando solo `openStream` y `waitFileTask` (el build falla si no los encuentra exactamente una vez);
   - `@JSExport PackImageWorker.decode`, que devuelve `PackImageCodec.capture`;
   - `@Async` en `PackImageCache` cuando falla el lookup;
   - `?texworker=verify`.

## Alcance

- Se toca: `port/runtime/texcache.mjs`, un `port/runtime/texworker.mjs` nuevo, `port/src/main/java/port/runtime/PackImageCache.java`, un `PackImageWorker.java` nuevo, un `port/src/main/java/port/compiler/` transformer nuevo y su registro, `port/src/main/java/port/shim/BrowserStb.java`, el bloque de texcache de `port/web/game.mjs`, tests y docs.
- No se toca:
  - el bytecode del juego más allá de clonar `call()` y reemplazar esas dos invocaciones;
  - ningún algoritmo de máscara, premultiplicado o mipmaps (no se reimplementan);
  - 8098, el servidor PZ en vivo y el túnel;
  - `package-lock.json`, `port/pom.xml` y `scripts/*-lock.json`.
- Si cambian los bytes guardados o el layout, se sube `CODEC_VERSION` (`docs/operacion/actualizar.md`).

## Recursos compartidos

- Build de Maven/TeaVM: uno a la vez (`build.lock`), ~20 GB.
- Chrome de prueba: uno a la vez, con `chrome.lock` y un perfil propio en `/tmp`.
- Instancia de prueba: web 8199 + PZ de prueba (`PZ_MP_DATA`/`PZ_MP_PORT`). Nada en vivo sin OK del dueño.
- Ojo con la RAM: B suma ~1–1,5 GB a la pestaña durante la carga. No correrlo a la vez que un build.

## Cómo se prueba

- **Node:** `npm ci --ignore-scripts && npm test`. Pruebas nuevas para:
  - los contadores de texcache;
  - el pool de Workers con un Worker falso;
  - el decodificador JS del camino stb contra PNG sintéticos propios (paleta, gris+alfa, RGB, RGBA y los 5 filtros, con y sin flip).
- **JVM:**
  - `BrowserStb.decodePng` contra las mismas fixtures (mismos bytes que en Node);
  - `PackImageCacheTest` extendido para que `port$workerCall` con un `ByteArrayInputStream` dé lo mismo que `call()`, con `PZ_HOME`.
- **Build real:** `bash scripts/build.sh --with-game --release` (`scripts/fast.sh build`).
- **Chrome con el juego**, 1.ª carga fría y 3 corridas por caso:
  - casos `?texworker=0`, `?texworker=1` y `?texworker=verify` (y `?stbworker=` igual);
  - se miden `world-load-start` → `world-load-end` → `ingame-enter`, la línea `caches` y el pico de memoria de la pestaña;
  - perfil `PZ_CPUPROF` para comprobar que `getPixelClamped`/`setPixel` ya no están en el hilo principal.

## Terminado cuando

- [ ] Paso 1 medido: `decodeMs` y `maxInflight` anotados en `STATUS.md`
- [ ] `verifyBad = 0` en una 1.ª carga completa con cada opción activada
- [ ] Menos tiempo hasta `ingame` en la 1.ª carga (3 corridas), y la 2.ª carga igual o mejor
- [ ] Opt-in con interruptor para apagarlo, y `CODEC_VERSION` subida si corresponde
- [ ] PR a `dev` con la plantilla completa y la CI en verde
- [ ] `STATUS.md`, `docs/decisions.md` y `AGENT_LEARNINGS.md` al día

## Entrega

Link del PR, nivel de evidencia alcanzado y la tabla de tiempos (sin opción, con cada opción, verify) más el pico de memoria. Aclarar si alcanza con recargar (cambios solo en `.mjs`) o si hace falta un `boot.wasm` nuevo, que se publica con `scripts/publicar_web.sh` en la máquina en vivo cuando el dueño lo pida.
