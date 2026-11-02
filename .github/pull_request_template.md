<!--
Plantilla de PR (docs/procesos/ramas-y-prs.md). Vale para personas y agentes.
- Base: `dev` para todo lo nuevo; `main` solo para una publicación desde `dev` o un hotfix.
- Título en Conventional Commits: `<tipo>(<ámbito>): <descripción en español>`. Con squash es el commit que queda.
- Marcá [x] lo que revisaste y tachá con ~~…~~ lo que no aplica, diciendo por qué. No borres ítems.
- Un agente completa cada punto mirando el diff (`git diff origin/dev...HEAD`) y corriendo los comandos, no de memoria.
-->

## Qué cambia y por qué

<!-- Dos o tres líneas para alguien que no vio la conversación. Ronda y brief si hay (`docs/briefs/…`). -->

## Reglas del port

- [ ] El diff no trae archivos del juego, `.jar`, `.class`, `.so`, `media/`, `.bank`, mapas, packs, código decompilado ni derivado del juego (`bash scripts/check-forbidden.sh origin/dev`).
- [ ] No hay claves, contraseñas, URLs del túnel ni rutas a datos privados (`/tmp/pzweb-public.key`, `admin.pw`…).
- [ ] Las nativas nuevas o tocadas que faltan lanzan una excepción con su nombre JNI; nada simula éxito.
- [ ] No cambia reglas del juego: solo plataforma, archivos, gráficos, input, audio, red o nativas.
- [ ] El servidor sigue escuchando solo en `127.0.0.1` (o el cambio de exposición está aprobado por el dueño y anotado en `docs/decisions.md`).

## Alcance

- [ ] La rama sale de `dev`, está al día (`git fetch && git rebase origin/dev` antes del push) y el diff tiene solo lo de esta tarea.
- [ ] Si entraron cambios en `AGENTS.md`, `docs/procesos/` o `.claude/skills/` con el rebase, los leí y el cambio los cumple.

## Documentación

- [ ] `STATUS.md`: ronda, evidencia, faltantes, bloqueos y pasos manuales.
- [ ] `docs/decisions.md`: una línea por decisión nueva, con motivo y fuente.
- [ ] `AGENT_LEARNINGS.md`: los errores de esta tarea, con causa, patrón a evitar y mejor enfoque.
- [ ] Si cambia un proceso: su documento en `docs/procesos/` y su skill en `.claude/skills/`, en este mismo PR.

## Cómo se verificó

<!-- Cada comando y su resultado. Si no se corrió, decir por qué (sin PZ_HOME, chrome.lock ocupado, sin RAM…). -->

| Nivel | Comando | Resultado |
|---|---|---|
| Node | `npm test` | |
| JVM | `mvn -f port/pom.xml test` o el test puntual | |
| Chrome (plataforma) | `npm run test:browser` | |
| Build real | `bash scripts/build.sh --with-game` | |
| Chrome con el juego | sonda / `scripts/bench.sh` / capturas locales (no se suben) | |

<!-- Una demo sintética no demuestra que el juego funcione: no marcar un hito del juego sin haberlo ejecutado. -->

## Riesgos y pendientes

<!-- Qué podría romperse (modo directo, túnel, partida en vivo), si hay que recargar o reiniciar algo, qué quedó afuera. -->
