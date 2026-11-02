# Análisis: carga del mundo

Fecha: 2026-11-02. Rama `perf/carga-mundo` (ronda 89, brief `docs/briefs/r89-carga-mundo.md`). Sigue a `docs/analisis-carga-inicial.md`: el arranque hasta el menú ya está optimizado, y ahora lo que más se espera es entrar a la partida.

## Qué se sabía

- **Ronda 81:** la carga del mundo tardaba 74 s (build release, caché de texturas caliente, en local). El último perfil completo es de antes de la caché de texturas: 186 s, con el 99 % del tiempo en CPU (`docs/notes-from-code.md`, "Carga (CONTINUE …)"). El reparto era:
  - decodificación de texturas: `ImageData.getPixelClamped` 16,8 %, `setPixel` 11 %, `performPreMultipliedAlpha` 22 %, PNG y jzlib;
  - `getError`: 3,1 % (~5,7 s);
  - lecturas síncronas de `/media`: ~2150 XHR, 155 MB, 5 s en local.
- **Caché de texturas:** se borraba con cada `boot.wasm` nuevo. Con builds frecuentes, la primera entrada después de cada build volvía a ser la de 186 s.
- **Por el túnel** cada XHR síncrono es un viaje de ida y vuelta (~0,5 s, ronda 84). Mil lecturas fuera de caché pesan más que toda la CPU.
- **Precarga:** traía ~4720 rangos con un `fetch` cada uno, así que por el túnel eran ~4720 viajes.
- **Packs:** cada carga calculaba el sha256 completo de cada pack, aunque viniera de la caché local. Desde #27 Tiles2x (306 MiB) es perezoso y se verifica por tramo (WebCrypto en `ChunkStore.restore`, `sha256Hex` en las lecturas síncronas), y D no lo toca: lo que D se ahorra es el sha256 de los packs no perezosos, ~245 MiB de los ~551 MiB del índice local (`packs-new/index.json`). Sin medir con el juego.
- **Modo directo:** el cartel decía "Preparando…" durante toda la carga del mundo, porque nada lo actualizaba entre la precarga y "game loading took".

## Qué se implementó

| Frente | Cambio | Interruptor | Qué se espera |
|---|---|---|---|
| A | La caché de texturas se llavea con `CODEC_VERSION` (`port/runtime/texcache.mjs`) y no con el ETag de `boot.wasm`. Una base vieja, llaveada por build, se vacía una vez al abrirla. | `?texcache=build` vuelve a llavear por build; `?texcache=verify` y `?texcache=0` siguen igual | Un build nuevo ya no deja la carga en frío; se ahorra la parte de CPU de texturas (la mayor) |
| B | Precarga en lotes: la ruta `GET /media-ranges?i=…` del dev server sirve hasta 512 entradas del warm-list en una respuesta (como mucho ~24 MiB), con bytes idénticos a cada pedido suelto. La precarga se lanza también por https, y el modo directo espera esa misma promesa en lugar de lanzar otra. | `?prefetchbatch=0` vuelve a un fetch por rango; `?prefetch=0` y `?nocache=1` la apagan | ~48 pedidos en vez de ~4720 |
| C | `glGetError`: las llamadas que GLES 3.0 define sin condición de error (clear*, máscaras, viewport/scissor no negativos, flush/finish…) ya no fuerzan un `getError` real. Contador nuevo `gl.realGetErrors`. | `?glerrors=strict` | Ganancia chica; el grueso viene de texParameteri y bindTexture, que sí pueden fallar |
| D | Un pack leído de la Cache API o de OPFS se valida solo por tamaño. Lo bajado de la red se sigue verificando completo. Si la pasada por archivo encuentra un archivo corrupto en un pack salteado, se borra y se reintenta una vez con verificación completa. | `?packverify=1` o `?nocache=1` | sha256 de ~245 MiB menos por carga (los packs no perezosos; Tiles2x ya se verifica por tramo desde #27). Sin medir: ver `pzPackStats.digestMs` |
| E | Marcas `world-load-start`, `world-load-end` e `ingame-enter` en `pzBootTimings`, tomadas de los pulsos de `GameLoadingState` e `IngameState`, que también existen en `release-minpulse`. Al terminar la carga del mundo sale una segunda línea `caches`, con `gl.realGetErrors`. El modo directo muestra "Cargando el mundo…". | — | Poder medir |

Lo que se descartó, y por qué:

- **Hilos del juego en Workers:** con Wasm GC no se puede compartir el heap (ronda 81, frente 4).
- **Reducir el radio de chunks u otros ajustes del juego:** cambian reglas del juego.
- **Quitar el pre-drain de `texImage2D` o atribuir los errores por rango:** cambian cuándo lanza la subida, o pueden perder o duplicar errores (frente C).
- **Esperar la precarga en local antes de conectar:** serializaría ~167 MiB contra lecturas de ~2 ms. Sigue solo en https o con `?prefetch=1`.

## Riesgos

- **Frente A:** si la decodificación cambia sin subir `CODEC_VERSION` (PackImageCodec/PackImageCache, `PackImageCodec.FORMAT`, la copia fijada del juego), se sirven páginas viejas. Para detectarlo está `?texcache=verify`. La identidad de la copia del juego no entra en la clave, así que al cambiar la copia fijada hay que subir la versión a mano.
- **Frente B:** el cliente pide por índice del warm-list. Si el dev server se reinicia con otra lista, la cabecera de la respuesta no coincide y esas entradas cuentan como fallidas: nunca se sirven bytes equivocados. Hasta reiniciar el dev server, el cliente recibe 404 y vuelve solo al modo anterior.
- **Frente D:** con el memo del índice activo, que es lo normal en la segunda carga, no corre ninguna verificación de contenido de un pack cacheado. Si el disco se corrompe, `?packverify=1` lo diagnostica.

## Protocolo de medición (máquina Linux, no en macOS)

1. Build release con este PR: `scripts/fast.sh build`. Cambia `boot.wasm`, así que es la primera prueba del frente A. Reiniciar el dev server de pruebas (8199) para tener `/media-ranges`.
2. **Línea base:** con la URL `?texcache=build&prefetchbatch=0&glerrors=strict&packverify=1`, perfil nuevo y después el mismo perfil. Anotar las marcas `world-load-start` → `world-load-end` de `boot-timings`, y las dos líneas `caches`.
3. **Con los cambios:** misma secuencia sin esos parámetros. Lo esperado en la segunda carga:
   - `caches.tex.hit > 0` y `miss ≈ 0`;
   - `caches.packs.digestSkipped` con todos los packs;
   - `prefetch-bg.mode = "batch"` y `requests` ≈ 50;
   - `caches.media.remote.requests` cerca de 0;
   - `gl.realGetErrors` menor que en la línea base.
4. **Recompilar sin cambiar nada** (otro `boot.wasm`) y entrar: `tex.cleared` tiene que ser 0. Si la caché sobrevive al build, el frente A funciona.
5. **Fidelidad:** `?texcache=verify` tiene que dar `verifyBad = 0`.
6. **Túnel:** repetir el paso 3 por la URL pública y comparar `world-load-start → world-load-end`.
7. **Perfil, si la carga sigue larga:** `node tools/diag/profile.mjs ingame --query 'build=release-minpulse&persist=1'` deja `load.cpuprofile`; `profile.mjs report` lo resume. Pasar los números a `STATUS.md`.

## Despliegue

- `port/web/*` y `port/runtime/*.mjs` alcanzan con recargar.
- `/media-ranges` necesita reiniciar el dev server, con OK del dueño (`docs/HANDOFF-T3.md`). Sin reinicio no rompe nada: el cliente vuelve a un fetch por rango.
- `PackImageCache.java` solo cambió en un comentario: no hace falta un build para el frente A. Sí hace falta para medir el paso 4.
