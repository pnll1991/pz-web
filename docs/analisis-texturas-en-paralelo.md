# Análisis: decodificar texturas fuera del hilo principal en la 1.ª carga

Fecha: 2026-11-08. Estudio de factibilidad sin build, sin Chrome y sin decompilar: se basa en el código propio del repo, en lo que ya está documentado (`STATUS.md`, `docs/decisions.md`, `docs/notes-from-code.md`, `docs/analisis-carga-mundo.md`) y en el perfil de CPU del 8/11. Brief de la tarea de implementación: `docs/briefs/r92-texturas-en-paralelo.md`.

## Punto de partida (medido el 8/11, una corrida, loopback)

- 1.ª carga 288–301 s hasta la partida. De los ~191 s entre elegir el personaje y entrar al mundo, ~110 s son CPU de wasm en texturas.
- Self time del perfil: `ImageData::getPixelClamped` 31 s, `setPixel` 21 s, `performPreMultipliedAlpha` 9,6 s, `NativeImage::read` 9 s, `TilesetDepthTexture::load` 8,7 s, `PNGDecoder::decode` 7,7 s, jzlib `InfBlocks::proc` 8 s, `generateMipMaps` 4,4 s.
- Agrupado por camino:
  - **Páginas de `.pack`** (`FileTask_LoadPackImage.call`: PNG, jzlib, `ImageData`, máscara, mipmaps y premultiplicado): ~82 s de self (31 + 21 + 9,6 + 7,7 + 8 + 4,4). Hay una cota independiente: con la caché de texturas llena y sin descartes, la 2.ª carga bajó de 288 a 170 s (−118 s). Así que casi todo el trabajo de `ImageData` pasa por este camino.
  - **Camino stb** (`NativeImage.read` y `TilesetDepthTexture.load`, que llegan a `stbi_load`): ~17,7 s de self y ~41 s inclusivos según `STATUS.md` (~15 + ~26).
- La caché `texcache` solo ayuda desde la 2.ª carga. El camino stb no pasa por ninguna caché.

## Cómo fluye hoy una página de textura

1. **Bytes.** El `.pack` llega de la red, de la Cache API o de OPFS (`packs.mjs`). Tiles2x se baja por tramos perezosos con sha256 (`lazypack.mjs`). Se monta en el VFS del navegador (`vfs.mjs`).
2. **Pedido.** El gestor de assets del juego encola un `zombie.asset.FileTask_LoadPackImage` en el pool de `FileSystemImpl`, un `newFixedThreadPool(availableProcessors)`. En el navegador esos hilos son fibras de TeaVM en el único hilo principal, y `availableProcessors` vale `navigator.hardwareConcurrency` (`BrowserPlatform.java`).
3. **Intercepción actual.** `PackImageCacheCompatibility` mueve el `call()` original intacto a `port$uncachedCall()`, y `call()` pasa a ser `PackImageCache.call(this)`, que hace esto:
   1. `waitFileTask()`, igual que el original;
   2. `openPage` + `readPng`: los bytes exactos del PNG, de la firma a IEND;
   3. `lookup`, que es `@Async`: SHA-256 (WebCrypto) + `get` de IndexedDB. **Suspende solo la fibra de ese FileTask**, mientras las demás fibras y el resto del juego siguen;
   4. si hay acierto, `PackImageCodec.restore` arma el `ImageData` con `newMip` + memcpy por nivel. Si no hay, corre `port$uncachedCall()` (todo el trabajo de CPU, sin suspender) y después `store`, que copia meta y niveles a JS (`begin`/`addLevel`) y encola la escritura (`commit`).
4. **GPU.** El `ImageData` vuelve al gestor de assets. Más tarde el juego crea la textura y sube cada nivel con `glTexImage2D`, que pasa por `gl.mjs` y llega a WebGL2. Ese tramo no se toca en este análisis.

**Dónde se puede interceptar sin tocar el juego:** en el punto 3, que ya es nuestro. La espera `@Async` del `lookup` demuestra que una fibra puede esperar a un resultado de JS sin bloquear al resto. Un Worker es otra fuente de resultado igual que IndexedDB: llega como un "acierto" con `{meta, levels}` y lo arma el mismo `PackImageCodec.restore`.

## Qué habría que replicar exactamente

Lo que guarda la caché, y por lo tanto lo que un Worker tendría que producir byte a byte, es el formato `PackImageCodec` (FORMAT 1, magic `PIC1`, little-endian):

- **meta:** ancho y alto; `widthHw` y `heightHw` (tamaño en la GPU); `mipMapCount`; `id`; flags (`solid`, `alphaPaddingDone`, `preMultipliedAlphaDone`, `preserveTransparentColor`); el índice de `data`; los índices de `mipMaps`, conservando qué niveles son el mismo objeto; por nivel distinto, `w`, `h`, `position`, `limit` y `capacity` (= w·h·4); y la `BooleanGrid` de la máscara (`bitWidth`, `height` y los `int` de `value`).
- **levels:** los bytes RGBA de cada `MipMapLevel` distinto, después de todo lo que hace el `call()` original: decodificar el PNG; con el flag 64, alfa premultiplicado y mipmaps; y lo que `alphaPadding` deje marcado.
- **Clave:** `c<CODEC_VERSION>|m<mask>:f<flags>:c<FORMAT>|sha256(png)`.

Qué se puede saber sin leer el juego:

- La **decodificación PNG** la define la especificación (inflate y desfiltrado), siempre que el decodificador del juego no aplique gamma ni perfil de color. Eso no está documentado en el repo.
- **Máscara, premultiplicado, padding y mipmaps** son algoritmos del juego. El repo solo documenta que existen, sus nombres y que dependen del flag 64. No hay forma legítima de reimplementarlos idénticos: habría que leer el bytecode, que está prohibido como fuente, o ajustarlos a ciegas contra las salidas, lo que equivale a inventar comportamiento.
- Campos que pueden depender de **estado global**, como `id`: la caché los guarda tal como salieron. Un productor fuera del hilo tendría que dar el mismo valor, y `verify` lo detectaría si no.

## Riesgos de no ser idéntico y cómo se detecta

- Una textura con un byte distinto puede verse como bordes con halo, transparencias o mipmaps lejanos distintos. Peor todavía: una máscara distinta cambia qué píxeles son "clickeables" u opacos para el juego. Eso ya roza las reglas del juego.
- Con `texcache`, además, lo distinto **se persiste** y se sirve en todas las cargas siguientes hasta que se suba `CODEC_VERSION`.
- Defensa existente: `?texcache=verify` (modo 2). Ante un acierto corre igual el original, compara `sameMeta` y `sameLevel` byte a byte, cuenta `verifyOk`/`verifyBad` y **siempre devuelve el original**. Un resultado de Worker con forma de acierto se compara con ese mismo código sin cambios. Precedente: 174/174 idénticas en la ronda 81.
- Regla para cualquier opción: arranca opt-in (`?texworker=1`), se valida con un modo verify sobre una 1.ª carga completa (`verifyBad = 0` en todas las páginas, no en una muestra) y solo después pasa a ser la opción por defecto. Si cambia algo de lo que produce, se sube `CODEC_VERSION`.

## Opciones

### A. Decodificador propio en JS o wasm32 dentro de Workers

- **A1, páginas de `.pack`:** reimplementar el `call()` completo, incluidos máscara, premultiplicado y mipmaps. **Se descarta.** No se puede garantizar la identidad sin conocer los algoritmos del juego. `createImageBitmap` y `OffscreenCanvas` tampoco sirven ni siquiera para decodificar: hacen conversión de color y el canvas 2D guarda premultiplicado, con pérdida.
- **A1', solo el PNG de las páginas:** sustituir `PNGDecoder.decode` o el inflate por bytes ya decodificados en un Worker. El techo es ~16 s (PNGDecoder + jzlib), y obliga a reescribir llamadas dentro de un método del juego. Mal cociente entre ganancia y riesgo.
- **A2, camino stb (recomendable aparte):** `NativeImage.read` y `TilesetDepthTexture.load` llegan a **`port.shim.BrowserStb.stbiLoad`, que es código nuestro**: un decodificador PNG propio que imita `stb_image` con `req_channels=4`. Acá la identidad es fácil de asegurar, porque se compara nuestro decodificador Java contra su versión JS sobre fixtures propias, en JVM y en Node. El plan:
  - un Worker decodifica por ruta los PNG que se van a pedir (depthmaps y lo que liste el warm-list), con `DecompressionStream('deflate')` nativo más el desfiltrado, y deja `{w, h, rgba}` transferido en un mapa del hilo principal;
  - `stbiLoad` consulta ese mapa con un `@JSBody` y, si encuentra la ruta, copia de una vez al arena;
  - si no, sigue con el decodificador Java;
  - esos resultados también se pueden guardar en IndexedDB, en el mismo almacén con otra sal (`stb:f<flip>`).
  - De paso: hoy `stbiLoad` copia el archivo **byte a byte con `Uint8Array.get`**, una llamada a JS por byte. Cambiarlo por una copia en bloque es trivial (requiere build).
  - **Ganancia:** hasta ~18 s de self y ~41 s inclusivos si los Workers terminan antes de que el juego pida la imagen. Sin medir cuánto de ese self es nuestro `decodePng` inlinado y cuánto es código del juego que procesa los píxeles después.
  - **Costo:** chico o mediano. Necesita build, porque cambia `BrowserStb.java`.

### B. El propio `boot.wasm` en N Workers como productor de páginas (recomendada)

Idea: **no reimplementar nada.** Cada Worker instancia el **mismo** `boot.wasm` (el `WebAssembly.Module` ya compilado se le pasa por `postMessage`, sin recompilar) con su propio heap, y llama a un `@JSExport` nuevo, por ejemplo `PackImageWorker.decode(png, mask, flags)`. Ese método ejecuta el **mismo bytecode del `call()` del juego** y devuelve `PackImageCodec.capture`. Es idéntico por construcción: mismo binario, mismo compilador y semántica Java determinista.

- **Cómo se ejecuta el `call()` sin el FileSystem del juego.** Un transformer, en la línea de `PackImageCacheCompatibility` y `SpinWaitCompatibility`, clona el programa de `call()` en `port$workerCall(InputStream)`. En el clon, reemplaza la única invocación a `FileSystem.openStream(...)` por el parámetro (un `ByteArrayInputStream` con el PNG) y deja la de `waitFileTask` sin efecto. El build falla si no encuentra exactamente una de cada una. El objeto del task se crea con `new` sin constructor y con `mask`/`flags` asignados por IR. Nada de esto cambia lo que calcula el juego.
- **Integración en el hilo principal.** En `PackImageCache.call`, cuando el `lookup` falla y está `?texworker=1`, se hace `@Async await pool.decode(png, salt)`, que devuelve `{hit:true, meta, levels}`. Después van el `restore` ya existente y un `commit` a IndexedDB con esos mismos buffers: no hace falta capturar de nuevo desde la memoria lineal. `?texworker=verify` reutiliza `verify()`: corre el original y compara.
- **Por qué paraleliza.** El pool de `FileSystemImpl` tiene `hardwareConcurrency` fibras. Si cada una se suspende esperando al Worker, puede haber N páginas decodificándose a la vez y el hilo principal queda libre para el resto de la carga (`MetaGridLoader`, Lua, GL).
- **Ganancia estimada** sobre los ~82 s de páginas: el hilo principal pasa a pagar solo el `restore` (memcpy; en la ronda 81 los aciertos llevaron ToS de 106 a 55 s) y la copia a IDB.
  - Si el juego pide páginas **de a muchas**, con 4 Workers el tiempo de pared de las páginas se acerca a ~82/4 ≈ 20 s, solapado con otro trabajo: **ahorro esperado 50–75 s**.
  - Si las pide **de a una** (cada pedido espera al anterior), solo se gana el solapamiento con el resto de la carga: **0–30 s**.
  - **No se sabe cuál de los dos casos es** (ver "Medición previa sin build").
- **Costo y riesgos:**
  - Memoria: cada instancia reserva el heap de buffers directos (`setMinDirectBuffersSize` = 256 MiB en el build del juego) más su heap GC. Con 4 Workers, ~1–1,5 GB más mientras dura la carga. Los Workers se cierran al terminar. No sirve para móviles.
  - El cargador de TeaVM (`TeaVM.wasmGC.load`) y los imports de `boot.wasm` tienen que poder instanciarse en un Worker sin `document`. Los imports son funciones que solo fallan si se llaman, pero hay que comprobarlo.
  - Estado estático: si la decodificación lee estáticos que el hilo principal ya inicializó con las opciones del jugador (por ejemplo calidad de texturas o compresión), el Worker los vería con su valor por defecto. `verify` lo detecta. Para encontrarlos se usa `javap` local, sin copiar nada, y el Java propio los sincroniza.
  - Campos globales como `id`: `verify` los detecta, y el hilo principal puede fijarlos al restaurar.
  - El `<clinit>` que arrastre `ImageData` en el Worker puede ser caro o tocar cosas del navegador. Se mide.
  - Hace falta un build: un transformer nuevo, `PackImageWorker` y la espera en `PackImageCache`. Es un solo artefacto y no hay un segundo build de TeaVM.
- **B-bis, segundo módulo TeaVM solo con texturas:** mismo resultado con un wasm más chico, pero duplica el build (~20 GB de RAM, uno a la vez) y puede arrastrar medio juego por dependencias igual. Queda como plan B si instanciar `boot.wasm` entero en un Worker resulta demasiado pesado.

### C. Semilla precalculada de la caché (sin decodificar en el cliente)

- Generar en la máquina del dueño los registros de `texcache` (meta + levels) para todas las páginas y servirlos como un pack más, bajado y volcado a IndexedDB en la 1.ª carga. Así la 1.ª carga se comporta como la 2.ª: **−118 s medidos** de CPU, 288 → 170 s.
- Se pueden producir con un navegador después de una 1.ª carga (exportando IDB) o con la JVM, que ya ejecuta las clases del juego en `PackImageCacheTest`. Habría que comprobar que la JVM da lo mismo que el wasm; `verify` lo hace.
- **Costo:** ancho de banda. Hoy hay ~3 GB decodificados en IDB. Comprimidos (RGBA con mucha transparencia, más ~33 % de mipmaps) se estiman en **0,5–1 GB más de descarga** (sin medir). Por el túnel, a 25 Mbps, son ~160–320 s: más que lo que se ahorra. Sirve en LAN o loopback, no para jugadores remotos. Además son datos del juego servidos por nuestro servidor: igual que los packs, nunca van a git.
- **Veredicto:** opt-in posible para la red local. No resuelve el caso que importa.

### Resumen

| Opción | Qué ahorra (sobre ~110 s) | Identidad | Costo | Build |
|---|---|---|---|---|
| A1 (reimplementar páginas) | ~82 s teóricos | **no garantizable** | alto | sí |
| A1' (solo PNG de páginas) | ≤ 16 s | spec, riesgo medio | medio | sí |
| **A2 (stb en Workers + caché)** | ~18 s de self, hasta ~41 s inclusivos | fácil (código nuestro) | chico/medio | sí |
| **B (boot.wasm en Workers)** | 50–75 s si hay concurrencia; 0–30 s si no | por construcción + `verify` | medio/alto, +1–1,5 GB de RAM en la carga | sí, uno |
| C (semilla) | ~118 s de CPU, pero +0,5–1 GB de red | por construcción | ancho de banda | no para usarla; sí una herramienta |

## Algo chico y seguro sin build (propuesto, no implementado)

Nada de lo que hay en `port/runtime/*.mjs` puede bajar los ~82 s, porque esa CPU es Java del juego. Hay dos cambios chicos que sí valen.

1. **Diagnóstico que decide entre B y A2** (`texcache.mjs`):
   - en `lookup`, llevar `inflight` y `maxInflight` (las fibras dentro de `PackImageCache.call` a la vez) y anotar `entry.t1 = performance.now()` al resolver;
   - en `begin()`, sumar `decodeMs += now - entry.t1`. Entre que la fibra se reanuda y `begin` solo corren `port$uncachedCall()` y `capture`, sin suspender, así que da el **tiempo exacto de CPU de páginas por carga y por página**;
   - si los aciertos suman `restoreMs` (de la resolución del lookup a la siguiente llamada a `note('hit')`), queda medido también el costo del lado principal en B;
   - todo sale en la línea `caches` que ya existe. No cambia ningún byte.
   - Si `maxInflight` llega a ~`hardwareConcurrency`, B paraleliza de verdad. Si se queda en 1, B solo solapa.
2. **Escritura de IndexedDB desde un Worker** (`texcache.mjs`). Hoy `commit` hace `put` desde el hilo principal, y el clonado estructurado de ~3 GB de niveles se copia en ese hilo. Pasar `{meta, levels}` a un Worker con transferencia (sin copia), y que el `put` lo haga el Worker sobre la misma base, saca esa copia del hilo principal.
   - Ganancia **estimada en 1–5 s, sin medir**.
   - Cuidados: el Worker no debe volver a correr el vaciado por esquema, que lo sigue haciendo solo `open()` en la página, y `pendingBytes`/`dropped` tienen que seguir contando igual.
   - Se prueba en Node con un IndexedDB falso, como `texcache.test.mjs`, y con `stats.stored` en Chrome.

## Recomendación y plan probable

1. **Medir (sin build):** el diagnóstico 1 en una 1.ª carga fría en la instancia de prueba (8199 + PZ de prueba). Sale `decodeMs` total, `maxInflight` y el reparto por página. Con eso se decide B.
2. **A2 primero, por riesgo y costo:**
   - decodificador JS del camino stb en un Worker, con prefetch por ruta y caché en IDB;
   - copia en bloque en `stbiLoad`;
   - prueba de identidad: Node contra las fixtures de `StbImageWriteTest`/PNG propios, y JVM con `BrowserStb.decodePng` sobre las mismas fixtures;
   - medición: `NativeImage.read` y `TilesetDepthTexture.load` en el perfil, antes y después.
3. **B como prototipo opt-in** (`?texworker=1`), si `maxInflight > 1`:
   - (a) instanciar `boot.wasm` en un Worker y llamar a un `@JSExport` trivial, para medir memoria y tiempo de instanciación;
   - (b) transformer `port$workerCall` más `PackImageWorker.decode`, comparado contra `call()` en JVM con `PackImageCacheTest`;
   - (c) pool de N Workers (`?texworkers=N`, por defecto `min(4, hardwareConcurrency-1)`) y `@Async` en `PackImageCache`;
   - (d) `?texworker=verify` en una 1.ª carga completa: `verifyBad = 0`;
   - (e) medición.
4. **Cómo se mide**, con el mismo protocolo que `docs/analisis-carga-mundo.md`:
   - perfil de Chrome nuevo y 1.ª carga fría, 3 corridas por configuración (`?texworker=0`, `1` y `verify`);
   - marcas `world-load-start` → `world-load-end` y `ingame-enter`;
   - la línea `caches` (`decodeMs`, `maxInflight`, `verifyOk/Bad`, `stored`, `dropped`);
   - el pico de memoria del proceso de la pestaña;
   - un perfil `PZ_CPUPROF` para ver que `getPixelClamped`/`setPixel` salieron del hilo principal.
   - Éxito: menos tiempo hasta `ingame` en la 1.ª carga, `verifyBad = 0` y la 2.ª carga igual o mejor.

## Qué no se pudo determinar sin medir

- Si el juego pide las páginas de a muchas o de a una (`maxInflight`). Es lo que decide si B ahorra 50–75 s o 0–30 s.
- Cuánto de los ~110 s es exactamente `FileTask_LoadPackImage.call`: no había un `.cpuprofile` guardado para sacar el tiempo inclusivo. El −118 s de la 2.ª carga es una cota indirecta.
- Cuánto del self de `NativeImage::read` y `TilesetDepthTexture::load` es nuestro `decodePng` inlinado (wasm-opt) y cuánto es código del juego. Un perfil del build dev, sin inlining, lo separa.
- Si `boot.wasm` y el cargador de TeaVM se instancian en un Worker sin `document`, cuánta memoria cuesta cada instancia y qué estáticos lee la decodificación.
- La compresibilidad real de la semilla de la opción C.
- La ganancia de escribir IndexedDB desde un Worker.
