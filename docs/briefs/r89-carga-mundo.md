# Ronda 89 — carga del mundo

**Rama:** `perf/carga-mundo` desde `dev` · **PR a:** `dev` · **Quién lo pide:** Jona (dueño)

## Objetivo

Bajar el tiempo entre "entrar a una partida" (CONTINUE o modo directo `/play.html`) y poder jugar, sobre todo en la segunda entrada y para quien juega por el túnel, sin cambiar reglas del juego.

## Contexto

- Ronda 81: carga del mundo 74 s (release, caché caliente, local). El último perfil de la carga (186 s, antes de la caché de texturas) era CPU de texturas (`ImageData.getPixelClamped`, `setPixel`, alfa premultiplicado, PNG/jzlib), `getError` 3,1 % (~5,7 s) y ~2150 XHR síncronos de `/media` (5 s en local, pero ~0,5 s cada uno por el túnel). Fuente: `docs/notes-from-code.md` (ronda 81, frentes 2–4).
- La caché de texturas (`port/runtime/texcache.mjs` + `port.runtime.PackImageCache`) se vacía con cada `boot.wasm` nuevo.
- `docs/analisis-carga-inicial.md` dejó como decisiones abiertas: la caché de texturas por build y el sha256 completo de cada pack en cada carga.
- La caché de media (#9) solo ayuda desde la segunda carga. La precarga (`/warm-media.json`) corre en segundo plano y la carga del mundo no la espera.
- En macOS no se compila ni se levanta nada (`docs/operacion`): la evidencia de este PR llega a Node; la medición con el juego se hace en la máquina Linux con el protocolo de `docs/analisis-carga-mundo.md`.

## Frentes (un subagente cada uno, archivos disjuntos)

| Frente | Qué | Archivos |
|---|---|---|
| A | Caché de texturas con versión del códec en vez del build | `port/runtime/texcache.mjs`, `port/src/main/java/port/runtime/PackImageCache.java`, bloque de texcache de `port/web/game.mjs`, tests |
| B | Menos viajes de red en la carga del mundo (precarga en lote, la carga la usa) | `port/runtime/vfs.mjs`, `prefetch.mjs`, `mediacache.mjs`, `dev-server/*`, bloque de precarga de `port/web/game.mjs`, tests |
| C | `glGetError` más barato durante la carga sin cambiar lo que el juego lee | `port/runtime/gl.mjs`, tests |
| D | No repetir el sha256 completo de un pack que viene de la caché o de OPFS | `port/runtime/packs.mjs`, tests |
| E | Medición de la carga del mundo + cartel del modo directo | `tools/diag/`, marcas en `port/web/game.mjs`, `docs/analisis-carga-mundo.md` (sesión principal) |

## Alcance

- Se toca: lo de la tabla.
- No se toca: reglas del juego (radio de chunks, población, etc.), el dev server 8098, el servidor PZ ni el túnel en vivo; `package-lock.json`, `port/pom.xml`, `scripts/*-lock.json`.

## Recursos compartidos

Ninguno: en esta máquina (macOS) no se compila ni se abre Chrome con el juego. Solo `node --check` y `npm test`.

## Cómo se prueba

- `npm test` (salvo `pzbullet.test.mjs`, como la CI) y `node --check` de los módulos tocados.
- Java tocado: debe compilar con el resto (se verifica en la máquina Linux; aquí, revisión).
- Con el juego: protocolo de `docs/analisis-carga-mundo.md` en la máquina Linux.

## Terminado cuando

- [ ] Cada frente tiene su interruptor por URL para volver al comportamiento anterior y pruebas Node nuevas.
- [ ] PR a `dev` con la plantilla completa y la CI en verde
- [ ] `STATUS.md`, `docs/decisions.md` y `AGENT_LEARNINGS.md` al día

## Entrega

Link del PR, qué hay que recargar o reiniciar (dev server si cambia `dev-server/`), y el protocolo de medición pendiente.
