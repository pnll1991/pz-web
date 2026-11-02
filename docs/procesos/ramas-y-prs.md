# Ramas y PRs

Hasta la ronda 88 todo entraba directo en `main` (o en ramas locales como `code/menu` que después se pasaban a `main`). Con una sola persona y un agente por vez funcionaba; con varias personas y varios agentes en paralelo, no: nadie revisa nada antes de que llegue a `main` y dos sesiones pueden pisarse. Desde ahora lo nuevo entra por `dev` mediante PRs y llega a `main` en una **publicación**.

## Las ramas

| Rama | Qué es | Quién escribe |
|---|---|---|
| `main` | Lo que funciona y se juega (lo que corre el dev server en 8098 y el modo directo). | Solo publicaciones desde `dev` y hotfixes |
| `dev` | Integración: lo que va a entrar en la próxima publicación. | PRs de las ramas de trabajo |
| `<tipo>/<tema>` | Una rama por tarea, que sale de `dev`: `fix/clic-derecho`, `feat/vehiculos-bullet`, `perf/carga-inicial`. | Quien hace la tarea |
| `hotfix/<tema>` | Arreglo urgente de algo que está roto en `main` (por ejemplo, la partida en vivo). Sale de `main`. | Quien arregla |

Nombres cortos, en `kebab-case`, en inglés o en español sin tildes. Las ramas locales del dueño (`code/menu`, `code/sw`) pueden seguir existiendo, pero lo que se quiere integrar se manda como PR a `dev`.

## Trabajo de todos los días

```bash
git fetch origin
git switch -c fix/mi-cambio origin/dev
# … commits …
git fetch origin && git rebase origin/dev
git push -u origin fix/mi-cambio
gh pr create --base dev --title "fix(runtime): …"   # usa .github/pull_request_template.md
```

1. **Toda tarea termina en un PR a `dev`**, con la plantilla completa y la CI en verde. Lo que se pide después en la misma conversación va a la misma rama y al mismo PR.
2. **Ningún agente mergea un PR, ni a `dev` ni a `main`, salvo que una persona se lo pida explícitamente para ese PR.** Que la tarea esté terminada o la CI en verde no es permiso. Mergear un PR no autoriza a mergear el siguiente ni a publicar en `main`.
3. **Se mergea con squash**: un commit por tarea en `dev`, con el título del PR como mensaje.
4. **Antes de cada push, rebase sobre `origin/dev`.** Si el rebase trajo cambios en `AGENTS.md`, `docs/procesos/` o `.claude/skills/` (`git diff --stat HEAD@{1} HEAD -- AGENTS.md docs/procesos .claude/skills`), se leen antes de pushear.
5. **Un PR, una tarea.** Si aparece otra cosa en el camino, se anota en `STATUS.md` como faltante o se abre otra rama; no se mezcla.

## Publicar de `dev` a `main`

Lo decide el dueño. Se abre un PR `dev → main` con la lista de PRs que entran y se mergea con **merge commit** (no squash), para que las dos ramas compartan la historia.

**Pasar a `main` y publicar en vivo son un solo paso.** Mergear no cambia lo que se juega: la máquina en vivo sigue sirviendo su checkout hasta que alguien lo actualiza. El objetivo es que el juego en vivo sea siempre `origin/main`. Por eso, el pedido del dueño de pasar `dev → main` incluye publicar ese merge, y quien mergea lo publica enseguida:

1. Mirar `/tmp/pzweb-access.log`: quién está jugando.
2. En la máquina en vivo (Linux, ver [docs/operacion/README.md](../operacion/README.md)), en el checkout que sirve 8098:

   ```bash
   bash scripts/publicar_web.sh --dry-run   # qué entra y qué necesita
   bash scripts/publicar_web.sh             # si salió 0: fast-forward de main + sync_web.sh + comprobación
   ```

3. Si sale con **3** (necesita build o reempaquetar): `git merge --ff-only origin/main`, `bash scripts/sync_web.sh` y el build o los paquetes que pida la tabla de [actualizar.md](../operacion/actualizar.md). No corta la partida, así que entra en el mismo pedido.
4. Si sale con **4** (reiniciar dev server, servidor PZ o túnel): eso corta la partida y **necesita un OK aparte del dueño** ([HANDOFF-T3.md](../HANDOFF-T3.md)). Mientras no llegue, se publica lo que no necesita reinicio (`git merge --ff-only origin/main && bash scripts/sync_web.sh`) y se avisa qué quedó pendiente.
5. Comprobar: `git rev-parse --short HEAD` en el checkout en vivo es igual a `git rev-parse --short origin/main`.
6. Comentar en el PR `dev → main` qué se publicó (sha), qué hizo falta (recarga, build, reinicio) y qué quedó pendiente. Avisar a los jugadores si tienen que recargar o reconectar.

Un `main` que no está publicado es un pendiente: va a `STATUS.md` con el motivo hasta que se publique. Si el agente no tiene acceso a la máquina en vivo, lo dice en el PR y en la respuesta; no da la tarea por terminada.

Un `hotfix/*` se mergea en `main`, se publica igual y después se lleva a `dev` (`git switch dev && git merge origin/main`) para que `dev` nunca quede atrás.

## Mensajes de commit y títulos de PR

[Conventional Commits 1.0.0](https://www.conventionalcommits.org/es/v1.0.0/). El historial viejo (`Ronda 88: …`) no se reescribe; la ronda se puede seguir nombrando en el cuerpo.

```
<tipo>(<ámbito>): <descripción en español, en minúscula y sin punto final>

<cuerpo opcional: qué cambia, por qué y en qué ronda>
```

| Tipo | Cuándo |
|---|---|
| `feat` | Algo nuevo que funciona: una nativa, un modo, un script |
| `fix` | Arreglo de algo que andaba mal |
| `perf` | Mejora de velocidad o memoria sin cambiar el comportamiento |
| `refactor` | Reorganización sin cambio de comportamiento |
| `test` | Pruebas o sondas nuevas |
| `docs` | Solo documentación (`STATUS.md`, `docs/`, `AGENTS.md`) |
| `build` / `ci` | Toolchain, Maven, TeaVM, Emscripten / workflows de GitHub |
| `chore` | Mantenimiento que no entra en los anteriores |

Ámbitos sugeridos, según la carpeta: `port`, `compiler`, `natives`, `runtime`, `web`, `dev-server`, `mp`, `bullet`, `pack`, `inventory`, `diag`, `tools`, `tests`, `scripts`, `docs`, `skills`, `ci`. Se omite si el cambio cruza todo el repo. `!` después del ámbito marca un cambio que rompe algo (un formato de pack, un parámetro de URL).

## Qué revisa la CI

`.github/workflows/ci.yml` corre en cada PR y en cada push a `main` y `dev`, sin la copia del juego:

- **Archivos prohibidos** (`scripts/check-forbidden.sh`): extensiones y rutas del juego, binarios, claves y archivos de más de 2 MiB.
- **Título del PR** (`scripts/check-commit-title.sh`).
- **Pruebas Node** (`npm test` salvo `pzbullet.test.mjs`, que necesita el módulo compilado) y `node --check` del runtime y el dev server.

El build real, las pruebas JVM con TeaVM y Chrome con el juego **siguen siendo locales**: necesitan `PZ_HOME`, la toolchain de `.toolchain/` y mucha RAM. Su resultado va en la tabla "Cómo se verificó" del PR, y lo que no se pudo correr se dice con el motivo.

## Hooks locales

Opcionales, por clon y sin tocar la configuración global: `bash scripts/install-hooks.sh`. Activan `.githooks/pre-commit` (guarda de archivos prohibidos sobre lo que está en el índice) y `.githooks/commit-msg` (formato del título).

## Protección de ramas (lo hace el dueño, una vez)

En GitHub, Settings → Rules → Rulesets, para `main` y `dev`: pedir PR antes de mergear, pedir que pasen `Archivos prohibidos` y `Pruebas Node`, y bloquear force push. Sin eso, un push directo a `main` entra sin control.
