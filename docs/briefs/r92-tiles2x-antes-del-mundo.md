# Ronda 92 — Tramos de Tiles2x en memoria antes de que el juego los lea

**Rama:** `perf/tiles2x-antes-del-mundo` desde `dev` · **PR a:** `dev` · **Quién lo pide:** dueño (sesión principal, 8/11/2026)

## Objetivo

Que en una carga típica por el túnel ningún tramo de `Tiles2x.pack` se lea con XHR síncrono (`lazyfill … "sync":0`), sin frenar el arranque más de lo necesario.

## Contexto

- R89 partió `Tiles2x.pack` (319 MB) en 369 tramos con sha256 (`port/runtime/lazypack.mjs`, `scripts/lazy-packs.mjs`) y los baja en segundo plano (`?fill=1`).
- Medido por el túnel el 8/11 (~0,7 s por pedido): `lazyfill {"filled":260,…,"sync":109,"syncBytes":80681944}`. Cada lectura síncrona congela el hilo principal una ida y vuelta.
- STATUS R89: el juego lee los 369 PNG, así que no se ahorran bytes; solo se puede solapar la descarga.

## Alcance

- Se toca: `port/runtime/lazypack.mjs`, `port/runtime/lazyfill-worker.mjs` (nuevo), `port/runtime/packs.mjs`, `port/web/game.mjs`, `tools/diag/load-bytes.mjs` (captura `lazy*` y lo trae del worktree `pzweb-tile1x`) y `tests/node/lazypack-shared.test.mjs` (nuevo).
- No se toca: Java, packs, dev server ni nada en vivo (8098, servidor 16261, túnel). Tampoco las reglas del juego.

## Recursos compartidos

- Web de pruebas 8199 (`/tmp/pz9x/web`) y servidor PZ de prueba 16293 (`/tmp/pz9y`), lanzados con `systemd-run --user`.
- Chrome de prueba bajo `chrome.lock`. Como máximo 4 cargas en frío.

## Cómo se prueba

- Node: `npm test` (`tests/node/lazypack-shared.test.mjs`: protocolo de estados, espera a otro hilo con `worker_threads`, robo por tiempo, caída del Worker).
- Chrome con el juego: `flock /tmp/pz80-locks/chrome.lock node tools/diag/load-bytes.mjs --label <x> --runs 1 --throttle 40 --latency 300 --url 'http://127.0.0.1:8199/play.html?port=16293'`, con `?lazyworker=0` para el comportamiento anterior. Restaurar `/tmp/pz86/warm.json` antes de cada corrida, porque las lecturas síncronas se anotan en el warm-list y cambian la corrida siguiente.

## Terminado cuando

- [x] `sync` = 0 en una carga en frío con latencia simulada
- [ ] PR a `dev` con la plantilla completa y la CI en verde (lo hace la sesión principal)
- [x] `STATUS.md`, `docs/decisions.md` y `AGENT_LEARNINGS.md` al día

## Entrega

Es un cambio sin build (`port/web/*`, `port/runtime/*.mjs`): se publica con `sync_web`/`publicar_web.sh`, sin reiniciar nada. Los números van en `STATUS.md` (sección R92).
