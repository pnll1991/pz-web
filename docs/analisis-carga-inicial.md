# Análisis: caché y carga inicial en el navegador

Fecha: 2026-11-02. Rama `perf/cache-carga-inicial`. Sigue a la ronda 88 (`STATUS.md`), que dejó pendiente "reducir los ~2150 XHR restantes".

## Qué se repetía en cada recarga

Antes de este cambio ya se reutilizaban entre recargas: los packs de menos de 64 MB (Cache API), las páginas de texturas decodificadas (IndexedDB, `texcache.mjs`) y el sha256 por archivo de los packs (memo en `localStorage`).

| Recurso | Cómo se pedía | Costo en cada recarga |
|---|---|---|
| Lecturas de `/media` fuera de los packs (lua, scripts, lotheaders, rangos de mapas) | XHR síncrono con `Cache-Control: no-store` (`vfs.fetchRange`, `vfs.bundled`) | ~2150 pedidos por carga. Por el túnel, cada uno es un round trip (~0,5 s según la ronda 84) |
| Precarga del warm-list (`/warm-media.json`, ~4720 rangos, ~167 MiB) | `fetch` con `no-store` | Vuelve a bajar lo mismo en cada carga |
| Packs de más de 64 MB (`Tiles2x.pack`, 321 MB) | Fuera de la Cache API a propósito (riesgo de cuota) | Descarga completa en cada carga |
| Packs de menos de 64 MB | Se leían de la Cache API… | …y se volvían a escribir enteros en cada carga |
| Packs de índices viejos | Nunca se borraban | Cada reempaquetado dejaba una copia vieja en la Cache API |
| `/media-manifest.json` | Sin ETag | Respuesta completa en cada carga |
| `boot.wasm` (~145 MB) | `compileStreaming` recién al evaluarse `game.mjs` | Arrancaba después de toda la cascada de imports (31 pedidos, incluido el parser GLSL) |
| `sqlite3.js` + `sqlite3.wasm`, `pzbullet` (manifest + glue + wasm), apertura de la caché de texturas (HEAD de `boot.wasm` + IndexedDB), `pulse-map.json` | Esperas en serie, después de cargar los packs | ~8 round trips en serie detrás de la parte más lenta |

## Qué se implementó

1. **Caché persistente de `/media`** (`port/runtime/mediacache.mjs`). Cada respuesta que la VFS o la precarga traen de la red se guarda en la Cache API (`pz-web-media-v1`), en segundo plano, con la misma clave que `vfs.prefetched` (`url|Range`).
   - En la carga siguiente, `restore()` la vuelca a `vfs.prefetched` mientras se cargan los packs. `fetchRange()` y `bundled()` la encuentran ahí y no salen a la red. La precarga del warm-list saltea lo restaurado.
   - Versión: el campo `version` del manifest (sha256 de nombres, tamaños y mtimes de `PZ_HOME/media`), o el sha256 del manifest si el server todavía no la manda. Si la versión cambia, se borra todo antes de usarla.
   - Una entrada inválida nunca llega al juego: `fetchRange()` exige el largo exacto y `bundled()` valida cada archivo contra el tamaño del manifest. Si no coincide, se lee de la red.
   - Tope de 320 MiB, tanto guardado como restaurado en memoria (la precarga ya usaba 300 MiB). Lo no usado se descarta a los 10 min, como la precarga.
   - `?mediacache=0` o `?nocache=1` la apagan.
2. **Packs grandes en OPFS** (`port/runtime/packs.mjs`).
   - Los packs de más de 64 MB se guardan como archivo en el Origin Private File System, después de verificados y en segundo plano. `createWritable` solo reemplaza el archivo al cerrar, así que un reload a mitad de escritura no deja un pack parcial. Sin OPFS, el comportamiento es el de antes.
   - Solo se escribe lo que se bajó. Lo leído de una caché ya no se reescribe.
   - Un pack guardado que no verifica se borra y se baja una vez de la red (antes fallaba el arranque).
   - Los packs que no están en el índice actual se borran de la Cache API y de OPFS.
   - Cada pack se sigue verificando entero (tamaño + sha256) antes de montarse.
3. **Dev server** (`dev-server/server.mjs`):
   - `/media-manifest.json` lleva `version`, ETag fuerte y responde 304. Las entradas van ordenadas por nombre, para que la versión sea determinista.
   - `/packs/<sha256>.bin` sale con `Cache-Control: public, max-age=31536000, immutable`: el nombre es el hash, así que nunca cambia de bytes.
   - `index.json` y lo demás siguen con `no-cache`.
4. **Arranque** (`port/web/game.html`, `port/web/game.mjs`):
   - `boot.wasm` empieza a bajar y compilarse en un script clásico al principio de `game.html` (`globalThis.pzEarlyWasm`). Medido en Chrome con un servidor sintético: pasa de ser el pedido 35 de 37 al 19, antes de la cascada del parser GLSL.
   - `modulepreload` de los módulos del runtime. El importmap va antes que ellos: si va después, Chrome lo ignora.
   - SQLite, Bullet, la apertura de la caché de texturas y `pulse-map.json` arrancan al principio y se esperan en el mismo punto que antes, así que un error se lanza donde se lanzaba.
   - El id del build para la caché de texturas sale del ETag de la respuesta temprana de `boot.wasm` (sin HEAD extra).

## Cómo se mide

En la consola y en globals:

- `mediacache restore {...}` al arrancar y `mediacache t=…s {...}` cada 30 s. Incluyen `restored`, `stored`, `prefetchHits`, `remote.requests` (XHR reales).
- `pzPackStats.sources` (`network` / `cache` / `opfs`), `pzPackStats.corrupt`, `removed`, `opfsWrites`.
- `prefetch-bg {...cached...}`: rangos del warm-list que ya venían de la caché.
- `pzEarlyWasm`, `pzEarlyCompile` (`used` / `fallback`).

Lo esperado en la segunda carga con el mismo build y la misma copia: `pzPackStats.sources.network = 0` y `remote.requests` cerca de 0 durante el menú y la carga del mundo, salvo lo que no entre en los 320 MiB. Comparar con `scripts/bench.sh --page fast.html` en frío (perfil nuevo) y en caliente.

## Despliegue

- `port/web/*` y `port/runtime/*.mjs` alcanzan con un reload. La caché de media funciona sin reiniciar el dev server: usa el sha256 del manifest como versión.
- La versión con mtimes, el 304 del manifest y el `immutable` de los packs necesitan reiniciar el dev server, con OK del dueño (`docs/HANDOFF-T3.md`). Por eso este PR **no** es "sin build".
- Uso de disco del perfil: hasta 320 MiB de media + los packs grandes (~321 MB) en OPFS, además de la caché de texturas. `navigator.storage.persist()` ya se pide desde la ronda 88.

## Pendiente (no entra en este PR)

- **Memo de la traducción GLSL** (`gl.mjs` → `glsl.mjs#translateShader`, preprocess + parse de cada shader en cada carga). Es una función pura de (fuente, etapa). Se podría cargar un `Map` desde IndexedDB al arrancar, versionado por el sha256 de `glsl.mjs` y la versión del parser. Sin medir; conviene perfilar antes.
- **sha256 completo de cada pack en cada carga** (~500 MB). El nombre del pack es su hash y solo se guarda lo verificado, así que se podría saltear al leer de la caché. Es una política explícita de la ronda 81 ("cada pack se sigue verificando completo"): lo decide el dueño.
- **Caché de texturas por build**: se borra con cada `boot.wasm` nuevo (~945 MB). Guardarla con una versión del códec en lugar del ETag del build evitaría arrancar en frío, pero si el códec cambia sin cambiar la versión reutiliza datos viejos.
- **URL con hash e `immutable` para `boot.wasm` y `/vendor/*`**. Ahorra las revalidaciones 304, pero necesita cambios en el build (`scripts/build.sh`, `copy_web_vendor.py`).
