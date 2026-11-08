# Manejar el proyecto en marcha

Lo que está en vivo (dev server 8098, servidor PZ, túnel) **no se reinicia ni se toca sin OK del dueño** ([HANDOFF-T3.md](../HANDOFF-T3.md)). Antes de cualquier cosa que corte la partida, mirar quién está jugando.

## Estado de un vistazo

```bash
bash scripts/dev_server_8098.sh status       # dev server en vivo
bash scripts/mp-server.sh status             # servidor PZ: corre/detenido y último evento del log (sale 1 si está caído)
bash scripts/public-tunnel.sh status         # túnel, modo público y clave
bash scripts/dev_web_8199.sh status          # web de pruebas
tail -20 /tmp/pzweb-access.log               # últimos accesos: quién está jugando
curl -s http://127.0.0.1:8098/perf           # fps, ping y memoria de cada jugador + carga de la máquina (en el navegador: /jugadores.html)
```

## Dev server (8098)

| Acción | Comando |
|---|---|
| Arrancar (si no corre) | `bash scripts/dev_server_8098.sh start` |
| Reiniciar | `bash scripts/dev_server_8098.sh restart` (exige `chrome.lock` libre; con el túnel abierto, usar `public-tunnel.sh` para no perder el modo público) |
| Parar | `bash scripts/dev_server_8098.sh stop` |
| Log | `/tmp/pz80-server.log` |

Lee cada archivo en cada pedido (`Cache-Control: no-cache`): un JS o un `boot.wasm` nuevo se ven al recargar la página **sin reiniciar**. Solo hace falta reiniciar si cambió `dev-server/*.mjs` o el manifiesto de media (se calcula al arrancar). Si el túnel está abierto, un `restart` directo levanta el server **sin la clave**: pasar por `public-tunnel.sh stop/start` (cambia la URL) o arrancar con `PZWEB_PUBLIC_KEY="$(cat /tmp/pzweb-public.key)" bash scripts/dev_server_8098.sh restart`.

Variables: `PORT`, `PZ_HOME`, `PZ_PACKS` (por defecto `build/real-browser/packs`), `PZWEB_WEB_ROOT` (raíz web alternativa), `PZWEB_PUBLIC_KEY` (modo público), `PZWEB_ACCESS_LOG`, `PZWEB_WARM_FILE` (ver abajo), `PZWEB_MEDIA_CACHE_MB` (caché de tramos de `/media` comprimidos), `PZWEB_RANGES_CACHE_MB` (caché de lotes de `/media-ranges` comprimidos).

Warm-list (la lista de rangos de `/media` y `/packs` que las sesiones necesitaron hasta entrar al mundo; el cliente la precarga en lotes con `/media-ranges`): `dev_server_8098.sh` la guarda en `~/.local/share/pz-web/warm.json`, que sobrevive a los reinicios de la máquina. Un `PZWEB_WARM_FILE` ya exportado manda. Si el archivo no existe y queda el viejo `/tmp/pzweb-warm.json`, el script lo copia una vez. `public-tunnel.sh` reinicia el server con el mismo script, así que usa la misma ruta. Cuántas entradas tiene: `curl -s http://127.0.0.1:8098/warm-media.json | node -e 'console.log(JSON.parse(require("fs").readFileSync(0)).length)'`. Vacía = el primer jugador por el túnel hace cientos de lecturas sueltas (~0,7 s cada una) hasta que se reconstruye.

## Servidor PZ

| Acción | Comando |
|---|---|
| Arrancar | `bash scripts/mp-server.sh start`; esperar `SERVER STARTED` en `/tmp/pz82/server.log` |
| Parar | `bash scripts/mp-server.sh stop` (corta a todos los jugadores) |
| Borrar el mundo | `bash scripts/mp-server.sh stop && bash scripts/mp-server.sh reset-world` (borra la partida guardada en `/tmp/pz82/zomboid`) |
| Admin | usuario `admin`; contraseña en `~/.config/pz-web/admin-pass` (600, persiste a reinicios; si no existe, el viejo `/tmp/pz82/admin.pw`). `bash scripts/mp-server.sh admin-pass-file` dice cuál usará `start`; nunca imprime la clave |
| Rotar la clave admin | `bash scripts/mp-server.sh gen-admin-pass --force` escribe una nueva sin mostrarla. **No cambia nada hasta el próximo `start`**: el servidor en marcha sigue con la anterior. Se reaplica sobre el mundo existente (verificado), así que basta con parar y arrancar; el reinicio corta la partida y va agrupado con otros (OK del dueño) |

Memoria: heap de 6 GB por defecto (`PZWEB_SERVER_XMX`, `PZWEB_SERVER_XMS`=2g; formato de Java, ej. `8g`); con 2 GB ya usaba 2,8 GB de RSS sin jugadores. Cambiarla necesita reiniciar el servidor.

Arranca sin Steam, sin UPnP, sin voz y **sin vehículos** (`Bullet.addVehicle` todavía no existe en el navegador). La configuración del mundo se aplica en `scripts/mp-server.sh` (`apply_config`). Para probar cambios de mundo sin tocar la partida en vivo, la instancia de prueba:

```bash
PZ_MP_DATA=/tmp/pz86/zomboid PZ_MP_PORT=16271 PZ_MP_UDPPORT=16272 PZ_MP_LOG=/tmp/pz86/server.log bash scripts/mp-server.sh start
```

### Idioma (español por defecto)

El juego sale en **español (ES)** en el menú y en la partida. Lo decide cada navegador antes de arrancar el juego (`port/runtime/language.mjs`): reescribe la línea `language=` del `/Zomboid/options.ini` del pack (de solo lectura) y el juego la lee en `Translator.getLanguage()`. Las traducciones (`media/lua/shared/Translate/<CÓDIGO>/`) se leen bajo demanda de `PZ_HOME/media` como el resto de `/media`: no se empaquetan ni se suben.

| Quién | Cómo se cambia | Por defecto |
|---|---|---|
| Un jugador | `play.html?lang=EN` (cualquier carpeta de `Translate`: `EN`, `ES`, `ES_MX`, `PTBR`…). Queda guardado en ese navegador (`localStorage` `pzweb.language`); `?lang=auto` lo borra | lo que diga el dev server |
| El dev server (todos los que no eligieron) | `PZWEB_LANGUAGE=EN` al arrancarlo; va en `/media-manifest.json` (`language`). Uno inválido corta el arranque. **Necesita reiniciar el dev server** | `ES` (también con un dev server viejo que no lo anuncia) |
| El servidor PZ | `PZWEB_LANGUAGE=EN bash scripts/mp-server.sh start` (o `apply-config`): reescribe `language=` en `$PZ_MP_DATA/options.ini`. Se valida contra `PZ_HOME`. **Necesita reiniciar el servidor PZ** | `ES` |

Opciones guardadas de un jugador: el port nunca guarda `options.ini` (el juego intenta y recibe EROFS), así que no hay un idioma "guardado" por el juego que respetar: todos pasan a ES al recargar, salvo quien eligió otro con `?lang=`. Cambiar el idioma en Opciones dentro del juego dura hasta recargar. Si falta la carpeta del idioma elegido no se toca nada y la consola dice `language {"applied":false,…}`.

El idioma del servidor solo afecta lo que el servidor traduce por su cuenta; los textos de la interfaz de cada jugador salen de su cliente. El mensaje de bienvenida del chat (`ServerWelcomeMessage` de `pzweb.ini`) es texto de configuración, no una traducción: con `ES` y el mensaje por defecto del juego, `start`/`apply-config` lo cambian por una versión propia en español; `PZWEB_WELCOME_MESSAGE='…'` (una línea, `<LINE>` separa renglones) fija otro, y uno ya personalizado no se toca.

### Calidad gráfica (mínima por defecto)

Cada navegador baja las opciones gráficas antes de arrancar el juego (`port/runtime/graphics.mjs`), con el mismo mecanismo que el idioma: reescribe 16 líneas del `/Zomboid/options.ini` del pack (niebla Legacy, agua y charcos bajos, sin charcos, sin cielo, sin reflejos, iluminación a 10 fps, interfaz a 30 fps, sin sombras de cadáveres, sin sangre, objetos del suelo en 2D, ropa simplificada, sin lluvia ni nieve dibujadas, sin efectos de puertas ni contorno de contenedores). La lista con los valores del juego está en `MINIMUM_OPTIONS`.

| Quién | Cómo se cambia | Por defecto |
|---|---|---|
| Un jugador | `play.html?calidad=juego` deja los valores del juego; `?calidad=minima` vuelve a la mínima; `?calidad=auto` borra la elección. Queda guardado en ese navegador (`localStorage` `pzweb.quality`) | `minima` |

Medido (instancia de prueba, Chrome con GPU, mismo lugar del mapa): quieto 50,1 → 60,0 fps (tope de 60), caminando 41,3 → 45,0. No arregla la lentitud de fondo; solo saca lo que el juego dibuja de más.

### Población de zombis (pocos) y cómo cambiarla

`scripts/mp-server.sh` reescribe la población de `pzweb_SandboxVars.lua` en cada `start` (y con `apply-config`, que solo escribe los archivos sin arrancar). Por defecto deja **pocos zombis y sin reaparición**:

| Clave | Variable | Por defecto | Qué es (enum del archivo que genera el servidor fijado) |
|---|---|---|---|
| `Zombies` | `PZWEB_ZOMBIES` | `5` | 1 Insane, 2 Very High, 3 High, 4 Normal, **5 Low**, 6 None (no hay "Very Low") |
| `Distribution` | `PZWEB_ZOMBIE_DISTRIBUTION` | `2` | 1 urbana, 2 uniforme |
| `ZombieRespawn` | `PZWEB_ZOMBIE_RESPAWN` | `4` | 1 High, 2 Normal, 3 Low, **4 None** |
| `PlayerSpawnZombieRemoval` | `PZWEB_SPAWN_ZOMBIE_REMOVAL` | `1` | **1 sin zombis en el edificio de aparición y alrededor** (valor por defecto del juego), 2 edificio, 3 habitación, 4 en cualquier lado |
| `ZombieConfig.PopulationMultiplier` | `PZWEB_POP_MULT` | `0.15` | el valor de "Low" según el archivo (Normal 0.65, High 1.2) |
| `ZombieConfig.PopulationStartMultiplier` | `PZWEB_POP_START_MULT` | `0.5` | multiplicador del día 1 ("Low") |
| `ZombieConfig.PopulationPeakMultiplier` | `PZWEB_POP_PEAK_MULT` | `1.0` | multiplicador del día pico (28) |
| `ZombieConfig.RespawnHours` / `RespawnUnseenHours` / `RespawnMultiplier` | `PZWEB_RESPAWN_HOURS` / `PZWEB_RESPAWN_UNSEEN_HOURS` / `PZWEB_RESPAWN_MULT` | `0.0` | `RespawnHours=0` apaga la reaparición |
| `ZombieConfig.RedistributeHours` | `PZWEB_REDISTRIBUTE_HOURS` | `12.0` | migración dentro de la celda (por defecto del juego) |

Los valores se validan (entero o decimal con punto, dentro del rango que documenta el archivo) antes de escribir nada; uno inválido corta el `start`. Ojo: el servidor dedicado **no** deriva `ZombieConfig` del preset `Zombies` (el mundo en vivo tenía `Zombies=3` con `PopulationMultiplier=0.65`, el de Normal), por eso se fijan las dos cosas. Ejemplo, un poco más que el defecto:

```bash
PZWEB_POP_MULT=0.3 bash scripts/mp-server.sh start
```

En un `PZ_MP_DATA` nuevo el primer `start` todavía no tiene `pzweb_SandboxVars.lua` (lo crea el servidor): arranca con los valores del juego. Para un mundo de prueba con estos valores: `start`, esperar `SERVER STARTED`, `stop`, `reset-world`, `start`.

**Cambiar la población no repuebla lo que ya está guardado**: la población inicial de cada celda del mapa se genera la primera vez que alguien la visita y queda en `Saves/Multiplayer/pzweb/zpop/zpop_<x>_<y>.bin`. En el mundo en vivo (`/tmp/pz82/zomboid`) las celdas ya visitadas conservan la densidad alta y la nueva solo se nota en celdas que nadie pisó (y, al no haber reaparición, los zombis que se maten no vuelven). Para tener la densidad nueva en todo el mapa hay que crear un mundo nuevo:

```bash
bash scripts/mp-server.sh stop && bash scripts/mp-server.sh reset-world && bash scripts/mp-server.sh start
```

Alternativa que conserva casas, inventarios y personajes (probada solo en una copia de prueba, no en el mundo en vivo): con el servidor **parado**, sacar la carpeta `Saves/Multiplayer/pzweb/zpop/` (moverla a un respaldo fuera del mundo, no borrarla) y arrancar. El servidor la vuelve a crear con la densidad nueva. En la prueba, un mundo creado con la densidad vieja bajó de 24 a 5 zombis cargados alrededor del bar; uno nuevo con los valores nuevos tuvo 3. Es un corte del servidor como cualquier reinicio y pide el mismo OK. Los zombis guardados dentro de los chunks visitados podrían seguir ahí: con un mundo visitado durante días puede quedar más densidad que en la prueba.

`reset-world` **borra la partida guardada** (casas, inventarios, personajes): es el mismo corte que cualquier reinicio del servidor más la pérdida del progreso. Necesita OK explícito del dueño y mirar antes `/tmp/pzweb-access.log`; no se hace en vivo por iniciativa de un agente. Probar la densidad sin tocar la partida: instancia de prueba con otros puertos y otra carpeta (`PZ_MP_DATA`, `PZ_MP_PORT`, `PZ_MP_UDPPORT`, `PZ_MP_LOG`); un `reset-world` con esas variables solo borra la carpeta de prueba.

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
| Tras reiniciar la máquina, la carga por el túnel tarda muchísimo (cientos de `/media` sueltos en el log de accesos) | Warm-list vacía: un dev server arrancado sin `dev_server_8098.sh` o anterior a la ronda 92 la guardaba en `/tmp` | Arrancar con `dev_server_8098.sh` (usa `~/.local/share/pz-web/warm.json`); la lista se reconstruye con la primera sesión que entra |
| La partida desapareció tras reiniciar la máquina | Los datos del servidor viven en `/tmp` (tmpfs) | No hay recuperación; avisar antes de reiniciar la máquina |

Para matar procesos, filtrar por pid concreto (`ps -eo pid,args | grep -v grep`): un `pkill -f` con un patrón que aparece en la propia línea de comandos mata la shell que lo corre (`AGENT_LEARNINGS.md`, R81/R83).
