# Operación de pz-web

Guías para levantar, manejar y actualizar el proyecto. Las reglas (`AGENTS.md`) y los procesos de trabajo (`docs/procesos/`) valen siempre; esto dice **qué comando correr y dónde**.

| Guía | Para qué |
|---|---|
| [levantar.md](levantar.md) | Instalar desde cero en una máquina nueva, fijar la copia del juego, primer build, paquetes y arrancar todo |
| [manejar.md](manejar.md) | Día a día: arrancar, parar y ver el estado de cada servicio, logs, quién juega, túnel, problemas comunes |
| [actualizar.md](actualizar.md) | Qué hacer según lo que cambió: web, Java, dev server, servidor PZ, paquetes, toolchain o versión del juego |

## Dónde corre cada cosa

El proyecto **corre en una sola máquina Linux x86_64** (la "máquina en vivo", usuario `agente47`, copia fijada en `~/pz-pinned/25485521/projectzomboid`). La toolchain de `scripts/toolchain-lock.json` es solo Linux x86_64 y los scripts de operación usan `ss`, `/proc`, `setsid`, `flock` y `stat -c`: **en macOS no se compila ni se levanta nada**. Desde otra máquina (por ejemplo una Mac) se edita, se prueban los tests Node y se abren PRs; el build y lo que está en vivo se manejan en la máquina Linux.

```
Navegador ──(túnel Cloudflare, ?k=clave)──> dev server 127.0.0.1:8098 ──> port/target/web  (HTML, JS, boot.wasm)
                                               │                      ──> PZ_HOME/media, build/real-browser/packs
                                               └── /ws ──> puente RakNet (una JVM por navegador) ──> servidor PZ 127.0.0.1:16261/udp
```

## Puertos

| Puerto | Qué | Notas |
|---|---|---|
| 8098 | Dev server **en vivo** (`scripts/dev_server_8098.sh`) | Lo que juegan los demás. No se reinicia sin OK del dueño |
| 8199 | Web de pruebas (`scripts/dev_web_8199.sh`), raíz propia en `/tmp/pz86/web` | Para probar sin tocar 8098 |
| 8090 | `scripts/dev.sh` / `npm run dev` (puerto por defecto de `server.mjs`) | Demo y desarrollo suelto |
| 8091, 8092 | Pruebas Playwright sintéticas y con el juego | 8092 figura como "no tocar" en `docs/HANDOFF-T3.md` |
| 8097 | Ajeno al proyecto | No tocar |
| 16261/udp, 16262/udp | Servidor PZ en vivo (`scripts/mp-server.sh`) | Solo `127.0.0.1` |
| 16271/udp, 16272/udp | Instancia de prueba del servidor PZ | `PZ_MP_DATA=/tmp/pz86/zomboid PZ_MP_PORT=16271 PZ_MP_UDPPORT=16272 PZ_MP_LOG=/tmp/pz86/server.log` |

Todo escucha en `127.0.0.1`. Lo único que sale a internet es 8098, por el túnel.

## Archivos fuera del repo

| Ruta | Qué |
|---|---|
| `~/pz-pinned/25485521/projectzomboid` | Copia fijada del juego (`PZ_HOME`). Solo lectura |
| `.toolchain/` (en el repo, ignorado) | JDK, Maven, Node, emsdk, Ghidra, Playwright, cachés |
| `port/target/web/` | Lo que sirve 8098: `game/` (build dev), `release-minpulse/game/` (versión rápida), JS copiado por `sync_web.sh` |
| `build/real-browser/packs/` | Paquetes de recursos servidos en `/packs` (`PZ_PACKS`) |
| `/tmp/pz80-server.log` | Log del dev server 8098 |
| `/tmp/pzweb-access.log` | Accesos al dev server: quién está jugando |
| `/tmp/pz82/server.log`, `/tmp/pz82/zomboid` | Log y datos del servidor PZ en vivo |
| `~/.config/pz-web/admin-pass` | Contraseña admin del servidor PZ (600). No se comparte. `/tmp/pz82/admin.pw` es el anterior, que `mp-server.sh` usa solo si falta este |
| `/tmp/pzweb-public.key`, `/tmp/pzweb-tunnel.{log,url,pid}` | Clave y estado del túnel. La clave no se comparte ni se versiona |
| `/tmp/pz80-locks/{chrome,build}.lock` | Candados: un Chrome de prueba y un build a la vez |
| `~/pzshots/` | Capturas y reportes de rondas (`CLAUDE_REPORT_R*.md`). No se suben |

`/tmp` es tmpfs (RAM, 16 GB): un reinicio de la máquina borra logs, la clave del túnel, la contraseña admin y **los datos del servidor PZ en `/tmp/pz82/zomboid`**.
