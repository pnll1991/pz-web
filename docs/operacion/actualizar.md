# Actualizar el proyecto

Todo cambio entra por PR a `dev` y llega a lo que está en vivo cuando el dueño publica `dev → main` ([ramas-y-prs.md](../procesos/ramas-y-prs.md#publicar-de-dev-a-main)). Cada merge a `main` se publica enseguida siguiendo esta guía: un `main` sin publicar es un pendiente. El checkout que sirve 8098 en la máquina en vivo está en `main`. Lo que hay que hacer después de traer `main` depende de **qué archivos cambiaron**:

| Cambió | Qué hay que hacer en la máquina en vivo | ¿Corta la partida? |
|---|---|---|
| `port/web/*`, `port/runtime/*.mjs` | `bash scripts/publicar_web.sh` (o `sync_web.sh` tras traer `main`) y que los jugadores recarguen | No |
| `docs/`, `*.md`, `tests/`, `.github/`, `.claude/` | Nada | No |
| `port/src`, `port/config`, `port/pom.xml`, `tools/compatibility`, `tools/exposure`, `tools/glgen` (Java, TeaVM, transformers) | Build: `bash scripts/fast.sh rebuild` (versión rápida) y/o `bash scripts/build.sh --with-game` (dev); después recargar | No: el `boot.wasm` se reemplaza de forma atómica al final |
| `dev-server/*.mjs` | `bash scripts/dev_server_8098.sh restart` (con el túnel abierto, ver [manejar.md](manejar.md#dev-server-8098)) | Sí, unos segundos; los jugadores recargan |
| `/media` comprimido (ronda 90: `dev-server/server.mjs` + `port/runtime/vfs.mjs`, `prefetch.mjs`, `port/web/game.mjs`) | Las dos filas de arriba, en cualquier orden: `sync_web.sh` y reiniciar el dev server. Una página vieja contra el server nuevo sigue con Range (sin comprimir) y una página nueva contra el server viejo también (no ve `slices` en el manifest); comprime recién con los dos y una recarga. El warm-list y la caché de media del navegador siguen valiendo | Sí, por el reinicio del dev server |
| `scripts/mp-server.sh` o la configuración del mundo | `bash scripts/mp-server.sh stop && bash scripts/mp-server.sh start` (y `reset-world` si cambia algo que solo se aplica al crear el mundo, p. ej. la densidad de zombis: [manejar.md](manejar.md#densidad-de-zombis-necesita-un-mundo-nuevo); `reset-world` borra la partida) | Sí: todos se desconectan |
| `tools/mp-bridge/` | `bash scripts/mp-bridge.sh build`; cada conexión nueva usa el puente nuevo | Las conexiones abiertas siguen con el viejo |
| `scripts/precompress.mjs`, `scripts/lazy-packs.mjs` | Correrlos sobre `build/real-browser/packs` ([levantar.md](levantar.md#paquetes-comprimidos-y-tiles2x-perezoso-ronda-89)); el `index.json` perezoso se ve al recargar. Para que el dev server sirva lo comprimido y anote los tramos en el warm-list hay que reiniciarlo (fila `dev-server/*.mjs`) | No, salvo el reinicio del dev server |
| `tools/pack/` o recursos empaquetados | Rehacer o ampliar los paquetes ([levantar.md](levantar.md#6-paquetes-de-recursos)); conservar el `index.json` anterior como respaldo | No; la primera carga siguiente baja los paquetes nuevos |
| `package.json` / `package-lock.json` | `npm ci --ignore-scripts --no-audit --no-fund` | No |
| `scripts/toolchain-lock.json` | `python3 scripts/bootstrap.py --extras` y un build completo | No |

Todo lo que corta la partida necesita OK del dueño y mirar antes `/tmp/pzweb-access.log`.

## Traer `main` a la máquina en vivo

```bash
cd <checkout en vivo> && git switch main && git status --short    # tiene que estar limpio
git fetch origin && git log --oneline HEAD..origin/main            # qué entra
git diff --stat HEAD origin/main                                   # qué archivos: ver la tabla de arriba
git merge --ff-only origin/main
```

Después, los pasos de la tabla para cada tipo de archivo que entró, y avisar a los jugadores si tienen que recargar o reconectar. La URL del túnel no cambia mientras no se toque el túnel.

## Cambios que se ven con solo recargar

Los cambios solo de `port/web/*` y `port/runtime/*.mjs` no necesitan build ni reinicio. Son la forma más rápida de mandar mejoras que se noten online. Se publican con `bash scripts/publicar_web.sh --dry-run` y después sin `--dry-run`: hace el fast-forward de `main`, corre `sync_web.sh`, comprueba lo servido y se niega si lo que entra necesita build o reinicio. Proceso completo: [updates-sin-build.md](../procesos/updates-sin-build.md).

## Build: cuál y cuánto tarda

| Comando | Qué produce | Lo usan |
|---|---|---|
| `bash scripts/fast.sh rebuild` | Versión rápida: release ADVANCED + `wasm-opt -O2` acotado + pulsos mínimos, en `port/target/web/release-minpulse/game` (~8 min) | `/play.html`, `/fast.html` |
| `bash scripts/build.sh --with-game --dev` | Build dev incremental (cachés, `mvn -o`, sin tests JUnit) con swap atómico | `/game.html`, `/play-dev.html` |
| `bash scripts/build.sh --with-game` | Build dev completo | `/game.html`, `/play-dev.html` |
| `bash scripts/build.sh` | Demo de plataforma | `/` |

Un build a la vez (`/tmp/pz80-locks/build.lock`). Con `PZ_NO_SWAP=1` el resultado queda en staging y se publica después con `bash scripts/swap_staged.sh <staging> <destino>`. El `boot.wasm` servido no desaparece durante el build.

## Volver atrás

- Web: `git checkout <sha-anterior> -- port/web port/runtime && bash scripts/sync_web.sh`, y después revertir por PR (`hotfix/<tema>` desde `main`).
- Build: rehacer el build en el commit anterior; no hay copia del `boot.wasm` previo salvo que se haya guardado a mano.
- Paquetes: restaurar el `index.json` de respaldo (los blobs anteriores se conservan).

## Caché de texturas: cuándo subir `CODEC_VERSION`

Desde la ronda 89 las páginas decodificadas en IndexedDB se llavean por `CODEC_VERSION` (`port/runtime/texcache.mjs`) y no por el build, así que sobreviven a un `boot.wasm` nuevo. La clave tiene el PNG de entrada, pero no el código que lo decodifica: si ese código cambia y nadie sube la versión, los navegadores siguen usando páginas viejas sin aviso. Hay que subir `CODEC_VERSION` en el mismo PR cuando:

- cambia la copia fijada del juego (`PZ_HOME`, `~/pz-pinned/<build>`), aunque los PNG parezcan iguales;
- cambia el decode o la serialización: `PackImageCodec`/`PackImageCache` (incluido `PackImageCodec.FORMAT`), la máscara, los mipmaps, el alfa premultiplicado, o un shim/transformer que altere `FileTask_LoadPackImage.call()`;
- hay duda. Antes de publicar se puede comparar con `?texcache=verify`.

Al subirla, cada jugador vacía su caché una vez y tiene una carga del mundo en frío (~945 MB se vuelven a decodificar). Conviene avisar.

## Actualizar la versión del juego

Una versión nueva de PZ es un proyecto aparte, no una actualización: copia fijada nueva en `~/pz-pinned/<build>`, inventario (`tools/inventory/run.sh`), revisar transformers y nativas contra las firmas nuevas, paquetes nuevos, subir `CODEC_VERSION` (ver arriba) y un build completo. El servidor y todos los clientes tienen que usar la misma versión. Se planifica con un brief en `docs/briefs/` y nunca sobre la copia en uso.
