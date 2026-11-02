# Levantar el proyecto

Desde una máquina Linux x86_64 vacía hasta jugar en `http://127.0.0.1:8098/play.html`. Sin sudo: todo se instala en `.toolchain/` y nada toca la configuración global. Cada paso dice cuánto tarda y qué deja.

## 1. Requisitos de la máquina

- Linux x86_64 con Python 3, `curl`, `gcc`, `git`, `binutils`, `file`, `rsync`, `ss` (iproute2) y `flock` (util-linux).
- Chrome estable (o `CHROME_PATH` apuntando a otro ejecutable).
- **RAM**: el build del juego usa ~20 GB (`MAVEN_OPTS=-Xmx20g`); Chrome con el juego, varios GB más. 32 GB es lo razonable.
- Disco: la copia del juego, `.toolchain/` y los builds suman decenas de GB.
- Project Zomboid instalado por Steam (build 25485521, v42.21), solo para copiarlo.

## 2. Clonar e instalar la toolchain

```bash
git clone git@github.com:pnll1991/pz-web.git && cd pz-web
python3 scripts/bootstrap.py --extras      # JDK 25, Maven, Node 24, emsdk, Ghidra, Vineflower… con checksum (scripts/toolchain-lock.json)
source scripts/env.sh                      # PATH, JAVA_HOME, MAVEN_OPTS y cachés del repo; solo para esta shell
npm ci --ignore-scripts --no-audit --no-fund
bash scripts/install_clipper_libs.sh       # Clipper2-java y earcut4j en el repo Maven local (.toolchain/m2)
python3 scripts/doctor.py                  # informa lo que falte (.toolchain/doctor.json)
bash scripts/install-hooks.sh              # hooks de commit opcionales (docs/procesos/ramas-y-prs.md)
```

`source scripts/env.sh` hay que repetirlo en cada shell nueva; todos los scripts lo hacen solos.

## 3. Fijar la copia del juego

No se trabaja sobre la instalación de Steam (se actualiza sola). Se copia una vez, **fuera del repo**, con el build id en la ruta:

```bash
mkdir -p ~/pz-pinned/25485521
cp -a ~/.local/share/Steam/steamapps/common/ProjectZomboid/projectzomboid ~/pz-pinned/25485521/   # ajustar a la biblioteca de Steam
export PZ_HOME="$HOME/pz-pinned/25485521/projectzomboid"
tools/inventory/run.sh "$PZ_HOME"          # inventario offline: inventory/report.md e inventory.json (ignorados)
```

`tools/inventory/run.sh --find` busca instalaciones si no se sabe dónde está. Varios scripts asumen `PZ_HOME=/home/agente47/pz-pinned/25485521/projectzomboid` si la variable no está: en otra máquina, exportarla siempre.

## 4. Comprobar la plataforma sin el juego

```bash
bash scripts/test.sh      # build de la demo + pruebas JVM, Python, Node y Chrome (SwiftShader); captura en test-results/platform.png
```

Alcanza con `npm test` para lo que corre la CI. `scripts/dev.sh` levanta la demo en `http://127.0.0.1:8090`: un triángulo, comprobaciones de plataforma y una nota que persiste. No es el juego.

## 5. Primer build del juego

Un build a la vez en la máquina. El primero es el más largo.

```bash
bash scripts/build.sh --with-game            # build dev (TeaVM SIMPLE) -> port/target/web/game/boot.wasm
bash scripts/fast.sh build                   # versión rápida (release ADVANCED + wasm-opt, pulsos mínimos) -> port/target/web/release-minpulse/game; ~8 min
bash scripts/mp-bridge.sh build              # puente RakNet para el multijugador -> port/target/mp-bridge
```

- `build.sh` termina llamando a `sync_web.sh` (copia `port/web/*` y `port/runtime/*.mjs` a `port/target/web`).
- `/play.html` y `/fast.html` cargan la versión rápida; `/game.html` sin parámetros, el build dev.
- Reportes: `port/target/build-report.json`, `port/target/reports/`. Si un build falla, no deja un `boot.wasm` viejo de ese modo.

## 6. Paquetes de recursos

El juego lee los recursos de arranque desde paquetes en `build/real-browser/packs` (`/packs`) y el resto bajo demanda desde `PZ_HOME/media` (`/media`, sin extraer nada al repo). El paquete base se genera desde la lista de archivos que el juego lee al arrancar:

```bash
python3 tools/pack/pack.py --pz-home "$PZ_HOME" --files build/real-browser/startup.txt --out build/real-browser/packs --max-bytes 32000000
```

Sobre ese índice se agregan capas con `tools/pack/overlay.py --add` (fuentes, modelos `.x` preconvertidos con `tools/pack/stage_jassimp.py`, animaciones de zombi con `stage_jassimp.py --zombie-anims`, alias con `alias_index.py`). Qué capa resolvió qué problema está en `STATUS.md` (rondas 79–86) y `AGENT_LEARNINGS.md`. **Hoy no hay un script único que rehaga todos los paquetes desde cero**: en una máquina nueva, lo más seguro es copiar `build/real-browser/packs` de la máquina en vivo (es local e ignorado; nunca se sube).

### Paquetes comprimidos y Tiles2x perezoso (ronda 89)

Después de rehacer o ampliar los paquetes, dos pasos de solo lectura sobre el mismo directorio (todo queda en `build/`, ignorado por git):

```bash
node scripts/lazy-packs.mjs        # parte Tiles2x.pack (321 MB) en cabeceras (2 MB) + un tramo con sha256 por PNG (el navegador arranca sin esperarlo y lo baja en paralelo); reescribe index.json (respaldo: index.json.before-lazy)
node scripts/precompress.mjs       # deja .br y .gz al lado de cada pack comprimible y de cada .wasm grande (Tiles2x y otros PNG se saltan)
```

Cada repaquetado cambia los nombres por hash: hay que volver a correrlos (son idempotentes y rápidos si no cambió nada; `lazy-packs.mjs --undo` vuelve al índice anterior). Sin ellos todo sigue funcionando como antes: el índice sin `lazy` baja los packs enteros y el dev server sirve el original sin comprimir.

### `/media` comprimido (ronda 90)

No hay paso previo: el dev server comprime al vuelo los tramos de `PZ_HOME/media` (nunca escribe en `PZ_HOME` ni en `build/`) y guarda lo comprimido en memoria, con la clave ruta + tamaño + mtime + tramo. `PZWEB_MEDIA_CACHE_MB` (por defecto 256) acota esa caché. El navegador pide cada tramo como `/media/<archivo>?r=<inicio>-<fin>` **sin** cabecera Range (con Range, Chrome manda `Accept-Encoding: identity` y no se puede comprimir); lo hace solo si `/media-manifest.json` trae `"slices":1`, así que una página nueva contra un dev server viejo sigue usando Range. `?slices=0` en la URL de la página vuelve a Range. Los tipos ya comprimidos (png, pack, ogg, bank, bik, zip, glb…) se mandan tal cual; la lista y las razones medidas están en `dev-server/server.mjs` (`MEDIA_INCOMPRESSIBLE`) y en `docs/analisis-carga-inicial.md`.

Los lotes de la precarga (`/media-ranges`, ronda 91) también salen con br/gzip si el navegador lo acepta (`Vary: Accept-Encoding`, `Cache-Control: no-store`, sin ETag). Se comprime el cuerpo entero una vez y se guarda en memoria con la clave sha256 del cuerpo sin comprimir; `PZWEB_RANGES_CACHE_MB` (por defecto 128) acota esa caché. Un lote con menos del 10 % de bytes de tipos comprimibles (`RANGES_MIN_COMPRESSIBLE`) o que no baja al menos 10 % se manda tal cual. El cliente no cambia: `fetch` descomprime y `prefetch.mjs` mide el cuerpo ya descomprimido.

## 7. Arrancar todo

```bash
bash scripts/mp-server.sh start              # servidor PZ real en 127.0.0.1:16261; esperar "SERVER STARTED" en /tmp/pz82/server.log
bash scripts/dev_server_8098.sh start        # dev server en 127.0.0.1:8098 (idempotente)
bash scripts/fast.sh                         # comprueba el build rápido y deja el server arriba; imprime la URL
```

Abrir `http://127.0.0.1:8098/play.html`: acepta los términos, entra solo al servidor local con una cuenta por navegador y crea el personaje. La primera carga baja ~500 MB y llena la caché de texturas del navegador (~945 MB en IndexedDB); las siguientes son mucho más rápidas.

Para que juegue alguien de afuera, con OK del dueño: `bash scripts/public-tunnel.sh start` (ver [manejar.md](manejar.md#túnel-público)).
