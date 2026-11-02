# Handoff a T3 Code — pz-web (2 nov 2026)

Claude Code (sesiones `claude -p` en terminal) queda reemplazado por **T3 Code** a pedido de Paolozky.
La ronda 86 se cortó a las ~00:35 (-03) del 2/11/2026 en un punto limpio: árbol de trabajo limpio, todo commiteado.

## Proyecto
- Repo: `/datos/Projects/02-Productos-propios/pz-web`, rama de trabajo **`code/menu`**.
- Qué es: Project Zomboid (build Steam 25485521, v42.21) corriendo en el navegador. El cliente Java se compila a wasm con TeaVM (`port/`, toolchain en `.toolchain/`); el servidor es `zombie.network.GameServer` del jar fijado en `~/pz-pinned/25485521/projectzomboid`, lanzado por `scripts/mp-server.sh` (datos en `/tmp/pz82/zomboid`, UDP 16261).
- Web: `node dev-server/server.mjs` en **127.0.0.1:8098**, sirve `port/target/web`. Modo directo multijugador: `/play.html`.
- Acceso público: túnel Cloudflare (`scripts/public-tunnel.sh`, clave en `/tmp/pzweb-public.key`). Lo usan Paolozky y su amigo.
- Leé primero: `AGENT_LEARNINGS.md`, `docs/decisions.md`, `docs/multiplayer-design.md`, reportes previos en `~/pzshots/CLAUDE_REPORT_R*.md`.

## Reglas (no negociables)
1. **Túnel intocable**: no reiniciar/tocar `scripts/public-tunnel.sh`, `/tmp/pzweb-public.key`, el proceso cloudflared ni la URL pública. La URL debe seguir igual.
2. Web solo en **8098**. Para desarrollo usar puerto temporal (p.ej. 8199) o build aparte.
3. No tocar: puertos 8097, 8092; pids 446769/12149/12150; el Chrome de escritorio de Paolozky; Steam; `~/pz-pinned`; Strata.
4. Un solo build Maven y un solo Chrome de prueba a la vez. Benchmarks cortos. Borrar perfiles de Chrome de prueba en `/tmp` al terminar (tmpfs 16 GB).
5. Sin sudo. Sin stubs que finjan éxito.
6. Commits como Paolozky: `git -c user.name=Paolozky -c user.email=paolozky@local commit ...`, en `code/menu` (o rama aparte si arriesga el modo directo). Nunca archivos del juego, media, packs, jars ni código decompilado; nunca claves.
7. **Nada en vivo sin OK de Paolozky** (server PZ en uso, web 8098, túnel). Antes de reiniciar el server PZ mirar `/tmp/pzweb-access.log` para ver si hay alguien jugando. Para cambios de mundo usar la instancia de prueba (`PZ_MP_DATA`/`PZ_MP_PORT` en `mp-server.sh`).
8. Tras editar `port/web/*` o `port/runtime/*.mjs`: `bash scripts/sync_web.sh && grep -c <cambio> port/target/web/<archivo>` ANTES de cualquier prueba.

## Estado: zombies (Ronda 86) — EN CURSO
Brief: `docs/briefs/r86-zombies.md`.
Hecho y commiteado:
- `f820f9a`: clips de animación de zombi en el paquete (`tools/pack/stage_jassimp.py --zombie-anims`; antes faltaban los 185 clips de `anims_X/Zombie` → zombis invisibles e inmóviles), config de zombis y contraseña admin aleatoria en `scripts/mp-server.sh` (`/tmp/pz82/admin.pw`), instancia de prueba del server, comandos/diagnóstico de zombis en `port/web/play.lua`, pelea en `tools/diag/play-two.mjs`, `autojoin.mjs`.
- `d059fd4`: órdenes de diagnóstico (equip/attack/kill/path) en `play.lua`, retraso de pelea en `play-two`, aprendizajes.
Probado: dos clientes headless entraron al mundo (`admin` y un jugador), horda spawneada; capturas `~/pzshots/r86-zombies*.png`.
Falta:
- Confirmar con 2 jugadores que los zombis **se ven en ambos, persiguen, atacan y se pueden matar** (se estaba probando "muertes de zombi" con el ataque del propio juego cuando se cortó; antes se probó a puño y con bate sin resultado concluyente). Ver final de `~/claude-r86.log`.
- Escribir `~/pzshots/CLAUDE_REPORT_R86.md` (no llegó a crearse): qué quedó, commits, si hay que recargar/reconectar, que el link sigue igual, quién estaba jugando (ver `/tmp/pzweb-access.log` ~22:23).
- Aplicar en vivo solo con OK: probablemente requiere reiniciar el server PZ en vivo y que los jugadores recarguen la página (el link no cambia).

## Pendiente: clic derecho (Ronda 86b) — NO EMPEZADO
Brief: `docs/briefs/r86b-clic-derecho.md`. Bloquear `contextmenu` (preventDefault) en canvas/document de `play.html` (y `game.html` si comparte ruta) y que el botón 2 (mousedown/mouseup/pointer) llegue al juego. Probar en Chrome headless; Firefox no está instalado (sin sudo; portable en ~ solo si es viable). Sección en el reporte R86: qué cambió, si hay que recargar, túnel igual.

## Pendiente: Ronda 87 "todo en el navegador" — NO EMPEZADO
Brief: `docs/briefs/r87-navegador.md`. Orden: (1) service worker con versión/invalidación + carga de mapa/packs por partes, medir bytes 1ra/2da carga antes/después (hoy ~500 MB en 1ra carga: packs ~482 MB + boot.wasm 63 MB); (2) evaluar server PZ en wasm en el navegador del host (hilos, sockets, nativas, RAM); (3) evaluar WebRTC + señalización mínima (NAT/TURN). Rama `code/sw` desde `code/menu`, puerto temporal 8199. Reporte `~/pzshots/CLAUDE_REPORT_R87.md` diciendo qué afectaría la partida en vivo.

## Próximos pasos (en orden)
1. Zombies: terminar la prueba de pelea con 2 clientes en la instancia de prueba, arreglar lo que falle, reporte R86.
2. Clic derecho (r86b).
3. Ronda 87.
4. Pedir OK a Paolozky antes de aplicar cualquier cosa en vivo.
