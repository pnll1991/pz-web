# Commits locales sugeridos — tarea 2

La base ya está registrada en **ba29078** (`Base F0-F3: toolchain, inventario, TeaVM Wasm GC, WebGL2, VFS`). En esta sesión `.git` está montado en solo lectura. Se conservaron los cambios en disco sin intentar eludir esa restricción y sin crear remotos.

Desde una terminal del dueño con escritura en `.git`, revisar `git status` y registrar estos grupos. No usar `git add -f`: `inventory/`, `build/`, `target/`, paquetes, binarios, recursos y capturas reales deben permanecer ignorados.

```bash
git add tools/inventory tools/exposure tools/pack tests/python AGENT_LEARNINGS.md
git commit -m "fix: inventory the pinned Linux fat jar and generate Lua exposure metadata"

git add port scripts/prepare_build.py scripts/report_game.py
git commit -m "feat: link the real game main and bridge TeaVM file APIs to browser storage"

git add dev-server/server.mjs playwright.config.mjs playwright.game.config.mjs tests/browser tests/node tests/prepare_real.py
git commit -m "test: verify persistent Java IO and exact WebGL texture transfer from the local copy"

git add README.md STATUS.md docs/decisions.md docs/local-commits.md docs/verification.md docs/CODEX_TASK_2.md
git commit -m "docs: record real inventory, build failures and verified F2-F3 progress"
```

La secuencia completa corresponde al estado verificado. No se ejecutó ningún commit en esta sesión. `docs/CODEX_TASK_2.md` es el documento de tarea aportado por el dueño, que ya estaba sin seguimiento al comenzar.
