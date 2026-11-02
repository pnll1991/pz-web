# Updates sin build

Un **update sin build** es un cambio que llega a la partida en vivo sin compilar Java/TeaVM ni reempaquetar: se mergea, se publica en `main`, se copia con `scripts/sync_web.sh` y los jugadores lo ven **al recargar la página**. La URL no cambia, no se reinicia el dev server, el servidor PZ ni el túnel. Sirve para mandar mejoras chicas y frecuentes que se noten mientras el build pesado sigue su propio ritmo.

## Qué califica

Lo decide la ruta de cada archivo del diff. `scripts/publicar_web.sh --dry-run` hace esta misma clasificación.

| Ruta | Efecto en vivo | ¿Update sin build? |
|---|---|---|
| `port/web/*` (primer nivel: `game.mjs`, `*.html`, `style.css`, `support.js`, `keyshim.js`, `play.lua`…) | Se ve al recargar | Sí |
| `port/runtime/*.mjs` (`input.mjs`, `gl.mjs`, `net.mjs`, `autojoin.mjs`…) | Se ve al recargar | Sí |
| `docs/`, `*.md`, `.claude/`, `.github/`, `tests/`, `tools/diag/` | Ninguno | Sí (acompañan) |
| `dev-server/*`, `scripts/mp-server.sh`, `scripts/mp-bridge.sh`, `scripts/public-tunnel.sh` | Hace falta reiniciar algo en vivo | No: pide OK del dueño ([HANDOFF-T3.md](../HANDOFF-T3.md)) |
| `port/src`, `port/config`, `port/pom.xml`, `port/pzbullet`, `tools/pack`, `package*.json`, el resto | Hace falta `scripts/build.sh` o reempaquetar | No |

Un PR sin build **no mezcla** archivos de las dos últimas filas: si la tarea los necesita, va como PR normal y se publica con el build.

## Que se note

Cada update sin build tiene que poder verse en la partida en vivo. En el PR, la sección "Qué cambia y por qué" dice **qué ve el jugador y dónde** (pantalla, tecla, mensaje), y si hace falta algo más que recargar. Buenos candidatos: entrada (teclas, mouse, texto, táctil), mensajes y pantallas de carga, avisos de compatibilidad, el modo directo (`play.html`, `autojoin.mjs`, `play.lua`), rendimiento del lado JS (`gl.mjs`, `packs.mjs`, `prefetch.mjs`) y textos.

## Cómo se trabaja

1. Rama `<tipo>/<tema>` desde `origin/dev`, como siempre ([ramas-y-prs.md](ramas-y-prs.md)). En el cuerpo del PR, la etiqueta **`sin build`** en "Riesgos y pendientes".
2. Probar sin build:
   - `npm test` (y una prueba nueva en `tests/node/` si el cambio es de entrada, red o GL).
   - Contra el `boot.wasm` ya compilado, en un puerto temporal (8199, `scripts/dev_web_8199.sh`), nunca en 8098: `bash scripts/sync_web.sh && grep -c '<cambio>' port/target/web/<archivo>` antes de abrir Chrome.
   - Si toca `play.lua`, probar en la instancia de prueba del servidor (`PZ_MP_DATA`/`PZ_MP_PORT`): el servidor compara el Lua del cliente.
3. PR a `dev`. En la tabla "Cómo se verificó", "Build real" queda tachado con el motivo "update sin build".

## Publicar en vivo

El dueño decide cuándo pasa `dev → main`, y ese pedido ya incluye publicar en vivo: quien mergea a `main` publica enseguida ([ramas-y-prs.md](ramas-y-prs.md#publicar-de-dev-a-main)). Un update mergeado y no publicado no lo ve nadie.

1. Mirar `/tmp/pzweb-access.log`: quién está jugando ahora.
2. En la máquina en vivo, en el checkout que sirve 8098 (rama `main`, sin cambios locales):

   ```bash
   bash scripts/publicar_web.sh --dry-run   # qué entra y si es sin build
   bash scripts/publicar_web.sh             # fast-forward de main + sync_web.sh + comprobación de lo servido
   ```

   El script se niega (sale con 3 o 4) si lo que entra necesita build o reiniciar algo; en ese caso no se publica en caliente.
3. Avisar a los jugadores que recarguen y qué van a notar. La URL sigue igual.

Un archivo borrado de `port/web/` sigue servido en `port/target/web/` (`sync_web.sh` no borra): si importa, borrarlo a mano al publicar.

## Si algo sale mal

`git revert` del merge en una rama `hotfix/<tema>` desde `main`, PR a `main` y otra vez `scripts/publicar_web.sh`. Mientras tanto, el estado anterior se puede volver a servir en el checkout en vivo con `git checkout <sha-anterior> -- port/web port/runtime && bash scripts/sync_web.sh` (y después `git checkout main -- port/web port/runtime` para no dejar el árbol sucio).
