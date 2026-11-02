---
name: ramas-y-prs
description: Cómo se crea una rama, se commitea, se pushea y se abre un PR en pz-web (ramas main/dev, Conventional Commits, plantilla de PR, CI sin la copia del juego, nunca mergear sin pedido explícito). Cargar antes de crear una rama, commitear, pushear, abrir o mergear un PR, o publicar dev en main.
---

# Ramas y PRs

Detalle completo: `docs/procesos/ramas-y-prs.md`. Checklist:

1. Rama `<tipo>/<tema>` desde `origin/dev` (hotfix: `hotfix/<tema>` desde `origin/main`). Nunca commitear en `main` ni en `dev`.
2. Commits con título `<tipo>(<ámbito>): <descripción en español>` (`bash scripts/check-commit-title.sh "<título>"`).
3. Antes de commitear: `bash scripts/check-forbidden.sh` (nada del juego, binarios, claves ni archivos de más de 2 MiB).
4. Antes del push: `git fetch origin && git rebase origin/dev`; si entraron cambios en `AGENTS.md`, `docs/procesos/` o `.claude/skills/`, leerlos.
5. PR a `dev` con `.github/pull_request_template.md` completa: cada ítem marcado o tachado con motivo, y la tabla "Cómo se verificó" con lo que se corrió de verdad.
6. Seguir la CI hasta el final. Avisar con el link del PR, el nivel de evidencia alcanzado y lo que no se pudo probar.
7. **No mergear** salvo pedido explícito para ese PR. Publicar `dev → main` solo si el dueño lo pide; merge commit, no squash.
