# Manejar el proyecto en marcha

Lo que está en vivo (dev server 8098, servidor PZ, túnel) **no se reinicia ni se toca sin OK del dueño** ([HANDOFF-T3.md](../HANDOFF-T3.md)). Antes de cualquier cosa que corte la partida, mirar quién está jugando.

## Estado de un vistazo

```bash
bash scripts/dev_server_8098.sh status       # dev server en vivo
bash scripts/mp-server.sh status             # servidor PZ: corre/detenido y último evento del log (sale 1 si está caído)
bash scripts/public-tunnel.sh status         # túnel, modo público y clave
bash scripts/dev_web_8199.sh status          # web de pruebas
tail -20 /tmp/pzweb-access.log               # últimos accesos: quién está jugando
```

## Dev server (8098)

| Acción | Comando |
|---|---|
| Arrancar (si no corre) | `bash scripts/dev_server_8098.sh start` |
| Reiniciar | `bash scripts/dev_server_8098.sh restart` (exige `chrome.lock` libre; con el túnel abierto, usar `public-tunnel.sh` para no perder el modo público) |
| Parar | `bash scripts/dev_server_8098.sh stop` |
| Log | `/tmp/pz80-server.log` |

Lee cada archivo en cada pedido (`Cache-Control: no-cache`): un JS o un `boot.wasm` nuevo se ven al recargar la página **sin reiniciar**. Solo hace falta reiniciar si cambió `dev-server/*.mjs` o el manifiesto de media (se calcula al arrancar). Si el túnel está abierto, un `restart` directo levanta el server **sin la clave**: pasar por `public-tunnel.sh stop/start` (cambia la URL) o arrancar con `PZWEB_PUBLIC_KEY="$(cat /tmp/pzweb-public.key)" bash scripts/dev_server_8098.sh restart`.

Variables: `PORT`, `PZ_HOME`, `PZ_PACKS` (por defecto `build/real-browser/packs`), `PZWEB_WEB_ROOT` (raíz web alternativa), `PZWEB_PUBLIC_KEY` (modo público), `PZWEB_ACCESS_LOG`, `PZWEB_WARM_FILE`.

## Servidor PZ

| Acción | Comando |
|---|---|
| Arrancar | `bash scripts/mp-server.sh start`; esperar `SERVER STARTED` en `/tmp/pz82/server.log` |
| Parar | `bash scripts/mp-server.sh stop` (corta a todos los jugadores) |
| Borrar el mundo | `bash scripts/mp-server.sh stop && bash scripts/mp-server.sh reset-world` (borra la partida guardada en `/tmp/pz82/zomboid`) |
| Admin | usuario `admin`, contraseña en `/tmp/pz82/admin.pw` |

Arranca sin Steam, sin UPnP, sin voz y **sin vehículos** (`Bullet.addVehicle` todavía no existe en el navegador). La configuración del mundo se aplica en `scripts/mp-server.sh` (`apply_config`). Para probar cambios de mundo sin tocar la partida en vivo, la instancia de prueba:

```bash
PZ_MP_DATA=/tmp/pz86/zomboid PZ_MP_PORT=16271 PZ_MP_UDPPORT=16272 PZ_MP_LOG=/tmp/pz86/server.log bash scripts/mp-server.sh start
```

## Túnel público

Abre **solo** el dev server 8098 a internet por Cloudflare (`trycloudflare.com`), protegido con una clave (`?k=` o cookie). Quien no la tenga recibe 403. El servidor PZ y los puentes siguen en loopback.

| Acción | Comando |
|---|---|
| Abrir | `bash scripts/public-tunnel.sh start`: genera clave nueva, reinicia 8098 en modo público e imprime `https://…trycloudflare.com/play.html?k=…` |
| Estado | `bash scripts/public-tunnel.sh status` |
| Cerrar | `bash scripts/public-tunnel.sh stop`: mata cloudflared, borra la clave y reinicia 8098 solo local |

Cada `start` cambia la URL y la clave: los jugadores necesitan el link nuevo. Por eso, con gente jugando, **el túnel no se toca**. La URL completa se comparte por privado, nunca en PRs, issues ni commits. `cloudflared` se busca en `~/.local/bin/cloudflared` (o `CLOUDFLARED`).

## Web de pruebas (8199)

Para probar cambios sin tocar lo que juegan los demás: raíz propia en `/tmp/pz86/web`, sin clave ni túnel, log y accesos aparte.

```bash
bash scripts/dev_web_8199.sh start           # copia la web y arranca en 127.0.0.1:8199
bash scripts/dev_web_8199.sh sync            # vuelve a copiar port/web y port/runtime después de editar
bash scripts/dev_web_8199.sh stop
```

## Chrome de prueba y mediciones

Un Chrome de prueba a la vez (`/tmp/pz80-locks/chrome.lock`). `scripts/live_chrome.sh start|cmd|status|stop` mantiene uno controlado por comandos; `scripts/bench.sh` mide arranque y fps con el candado tomado. Los perfiles de prueba en `/tmp` ocupan 1–2 GB cada uno: borrarlos al terminar (`df -h /tmp`).

## URLs útiles

| URL | Qué |
|---|---|
| `/play.html` | Modo directo multijugador (versión rápida): entra solo al servidor local |
| `/play-dev.html` | Lo mismo con el build dev |
| `/fast.html` | Versión rápida en solitario (`game.html?build=release-minpulse&persist=1&texcache=1`) |
| `/game.html` | Build dev, con diagnóstico del arranque |
| `/` | Demo de plataforma (no es el juego) |

Parámetros que ayudan a diagnosticar: `?tos=1` (muestra los términos), `?nocache=1` (sin caché de texturas), `?prefetch=0`, `?earlycompile=0`, `?zerotimer=0`, `?timerdiag=1`.

## Problemas comunes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| Edité JS y no cambia nada | No se corrió `sync_web.sh` (8098 sirve `port/target/web`, no `port/web`) | `bash scripts/sync_web.sh && grep -c '<cambio>' port/target/web/<archivo>` |
| `chrome.lock ocupado` | Otro Chrome de prueba corriendo | `bash scripts/live_chrome.sh status`; no forzar el candado |
| Un jugador recibe 403 | Link sin `?k=` o clave vieja (el túnel se reabrió) | Pasarle el link que imprime `public-tunnel.sh start` |
| "Load failed" en Safari | Error de red genérico de `fetch`; la puerta ya pasó | Mirar `/tmp/pz80-server.log` y el log de accesos |
| No conecta al servidor | Servidor PZ caído o sin `SERVER STARTED` | `bash scripts/mp-server.sh status`; `tail /tmp/pz82/server.log` |
| "Script version mismatch" o expulsión por Lua | El Lua del cliente difiere del servidor | Ver `AGENT_LEARNINGS.md` (R82/R83) |
| `/tmp` lleno, errores de cuota | Perfiles de Chrome de prueba acumulados | Borrar perfiles viejos en `/tmp`; `df -h /tmp` |
| La partida desapareció tras reiniciar la máquina | Los datos del servidor viven en `/tmp` (tmpfs) | No hay recuperación; avisar antes de reiniciar la máquina |

Para matar procesos, filtrar por pid concreto (`ps -eo pid,args | grep -v grep`): un `pkill -f` con un patrón que aparece en la propia línea de comandos mata la shell que lo corre (`AGENT_LEARNINGS.md`, R81/R83).
