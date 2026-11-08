# Ronda 92 — Warm-list persistente

<!-- Plantilla de brief (docs/procesos/trabajo-con-agentes.md). Copiar a docs/briefs/r<N>-<tema>.md. -->

**Rama:** `fix/warm-list-persistente` desde `dev` · **PR a:** `dev` · **Quién lo pide:** Paolo (sesión principal)

## Objetivo

Que la warm-list del dev server en vivo (8098) no se pierda al reiniciar la máquina, para que los jugadores del túnel sigan teniendo precarga en lotes después de un reinicio.

## Contexto

- El dev server guarda la warm-list en `PZWEB_WARM_FILE`, por defecto `/tmp/pzweb-warm.json` (`dev-server/warm-list.mjs`, `dev-server/server.mjs`). Es la lista de rangos de `/media` y `/packs` que las sesiones necesitaron hasta entrar al mundo; el cliente la precarga con `/media-ranges`.
- `/tmp` se borra en cada reinicio. El 8/11/2026, tras reiniciar, un jugador por el túnel de Cloudflare (~0,7 s por pedido) hizo más de 1300 lecturas síncronas sueltas de `/media` en 11 min sin entrar. Con la lista reconstruida (4328 entradas) la precarga fueron 40 pedidos en 14 s y entró en ~6 min.
- Hay una copia buena en `/tmp/pzweb-warm.json`: no se borra ni se modifica.

## Alcance

- Se toca: `scripts/dev_server_8098.sh` (ruta por defecto `~/.local/share/pz-web/warm.json`, respetando un `PZWEB_WARM_FILE` exportado, migración única desde `/tmp`, `export` para `node`), `dev-server/warm-list.mjs` (crear la carpeta al guardar; la escritura ya era tmp + rename), `tests/node/prefetch.test.mjs`, `docs/operacion/manejar.md`, `docs/decisions.md`, `STATUS.md`, `AGENT_LEARNINGS.md`.
- `scripts/public-tunnel.sh` no cambia: reinicia con `dev_server_8098.sh restart` y hereda la misma ruta.
- No se toca: nada en vivo (8098, servidor PZ 16261, túnel, 8199), `package-lock.json`, `port/pom.xml`.

## Recursos compartidos

Ninguno: sin build de Maven, sin Chrome y sin puertos. La lógica del script se prueba en subshells, sin lanzar el servidor.

## Cómo se prueba

- `npm test` (Node).
- `bash -n scripts/dev_server_8098.sh scripts/public-tunnel.sh`.
- Extraer `warm_file_default` del script y correrla en subshells con un `HOME` de prueba. Casos: migra desde `/tmp`, no pisa un archivo existente, respeta una variable propia sin migrar y el hijo hereda la variable. `/tmp/pzweb-warm.json` no cambia (sha256 antes y después).

## Terminado cuando

- [ ] `dev_server_8098.sh` exporta la ruta persistente y migra una vez
- [ ] `warm-list.mjs` crea la carpeta y la prueba Node lo cubre
- [ ] PR a `dev` con la plantilla completa y la CI en verde
- [ ] `STATUS.md`, `docs/decisions.md` y `AGENT_LEARNINGS.md` al día

## Entrega

Cambios sin commitear en el worktree del subagente; la sesión principal revisa, commitea y abre el PR. Para que aplique en vivo hay que reiniciar 8098, y eso corta a los jugadores un momento: hace falta el OK del dueño. Si el túnel está abierto, reiniciar con la clave (`PZWEB_PUBLIC_KEY="$(cat /tmp/pzweb-public.key)" bash scripts/dev_server_8098.sh restart`).
