# Handoff a T3 Code — pz-web (2 nov 2026)

Claude Code (sesiones `claude -p` en terminal) queda reemplazado por **T3 Code** a pedido de Paolozky.
Este documento conserva las **reglas de lo que está en vivo** (sección Reglas), que se citan desde `AGENTS.md` y `docs/procesos/`. El estado de las rondas vive en `STATUS.md`; las ramas y PRs, en `docs/procesos/ramas-y-prs.md`.

## Proyecto
- Repo en la máquina en vivo (Linux): `/datos/Projects/02-Productos-propios/pz-web`; su checkout sirve `main`. El trabajo va en ramas `<tipo>/<tema>` desde `dev` (la rama `code/menu` ya no se usa).
- Qué es: Project Zomboid (build Steam 25485521, v42.21) corriendo en el navegador. El cliente Java se compila a wasm con TeaVM (`port/`, toolchain en `.toolchain/`); el servidor es `zombie.network.GameServer` del jar fijado en `~/pz-pinned/25485521/projectzomboid`, lanzado por `scripts/mp-server.sh` (datos en `/tmp/pz82/zomboid`, UDP 16261).
- Web: `node dev-server/server.mjs` en **127.0.0.1:8098**, sirve `port/target/web`. Modo directo multijugador: `/play.html`.
- Acceso público: túnel Cloudflare (`scripts/public-tunnel.sh`, clave en `/tmp/pzweb-public.key`). Lo usan Paolozky y su amigo.
- Leé primero: `AGENT_LEARNINGS.md`, `docs/decisions.md`, `docs/multiplayer-design.md`, reportes previos en `~/pzshots/CLAUDE_REPORT_R*.md`.

## Reglas (no negociables)
1. **Túnel intocable**: no reiniciar/tocar `scripts/public-tunnel.sh`, `/tmp/pzweb-public.key`, el proceso cloudflared ni la URL pública. La URL debe seguir igual.
2. Web solo en **8098**. Para desarrollo usar puerto temporal (p.ej. 8199) o build aparte.
3. No tocar: puertos 8097, 8092; los procesos anotados al traspaso (pids 446769/12149/12150, probablemente vencidos tras los reinicios del 2/11: identificarlos por puerto y rol, no por pid); el Chrome de escritorio de Paolozky; Steam; `~/pz-pinned`; Strata.
4. Un solo build Maven y un solo Chrome de prueba a la vez. Benchmarks cortos. Borrar perfiles de Chrome de prueba en `/tmp` al terminar (tmpfs 16 GB).
5. Sin sudo. Sin stubs que finjan éxito.
6. Commits en una rama propia desde `dev` y PR a `dev` (`docs/procesos/ramas-y-prs.md`). Nunca archivos del juego, media, packs, jars ni código decompilado; nunca claves.
7. **Nada en vivo sin OK de Paolozky** (server PZ en uso, web 8098, túnel). Antes de reiniciar el server PZ mirar `/tmp/pzweb-access.log` para ver si hay alguien jugando. Para cambios de mundo usar la instancia de prueba (`PZ_MP_DATA`/`PZ_MP_PORT` en `mp-server.sh`).
8. Tras editar `port/web/*` o `port/runtime/*.mjs`: `bash scripts/sync_web.sh && grep -c <cambio> port/target/web/<archivo>` ANTES de cualquier prueba.

## Estado
Las rondas 86 (zombis), 86b (clic derecho) y 87 (navegador) están terminadas y publicadas en `main` (la última publicación es #29, `f890e7f`). Qué PR cerró cada cosa, lo vigente y lo pendiente: [`STATUS.md`](../STATUS.md), sección "Pendientes vigentes" al final.
