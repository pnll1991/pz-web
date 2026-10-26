# Commits locales pendientes

La tarea pide commits chicos. El sandbox de la sesión inicial permite editar el proyecto pero monta su `.git` en solo lectura. El intento real de `git add` falló con:

```text
fatal: Unable to create '/datos/Projects/pz-web/.git/index.lock': Read-only file system
```

No se cambió esa restricción, no se creó otra base git ni se publicó nada. Desde una terminal del dueño con escritura normal en `.git`, después de revisar `git status`, se pueden registrar los grupos siguientes. No usar `git add -f` ni agregar `.toolchain`, `build`, `target`, `inventory`, `packs` o archivos del juego.

```bash
git add .gitignore AGENTS.md AGENT_LEARNINGS.md docs/CODEX_TASK.md docs/ROADMAP-source.txt docs/decisions.md
git commit -m "docs: establish personal port scope and repository rules"

git add scripts/bootstrap.py scripts/toolchain-lock.json scripts/env.sh scripts/doctor.py scripts/check-toolchains.sh tools/inventory tools/pack tools/glgen tests/python
git commit -m "build: add pinned user toolchains and offline game inventory"

git add port scripts/build.sh scripts/prepare_build.py
git commit -m "feat: compile platform probes to TeaVM Wasm GC with explicit native failures"

git add dev-server package.json package-lock.json playwright.config.mjs scripts/dev.sh scripts/test.sh tests/browser tests/node tests/prepare_browser.py
git commit -m "test: verify local serving graphics input and persistent browser files"

git add README.md STATUS.md docs/local-commits.md docs/verification.md
git commit -m "docs: record validation and next steps requiring the game"
```

Los primeros commits organizan el trabajo; la secuencia completa constituye la base ejecutable. Si falta identidad git, el dueño debe elegirla; no se inventó nombre ni email.
