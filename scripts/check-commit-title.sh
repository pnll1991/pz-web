#!/usr/bin/env bash
# Valida un título de commit o de PR: `<tipo>(<ámbito>): <descripción>` o `<tipo>: <descripción>`.
# Lo usan el hook commit-msg y la CI (título del PR). Ver docs/procesos/ramas-y-prs.md.
set -euo pipefail
title="${1:-}"
types='feat|fix|perf|refactor|test|docs|build|ci|chore|revert'
# Los merges y reverts que genera git pasan sin validar.
if printf '%s' "$title" | grep -Eq '^(Merge |Revert ")'; then exit 0; fi
if printf '%s' "$title" | grep -Eq "^($types)(\([a-z0-9-]+\))?!?: .+[^.]$"; then exit 0; fi
cat >&2 <<MSG
Título inválido: "$title"
Formato: <tipo>(<ámbito>): <descripción en español, sin punto final>
Tipos: ${types//|/, }
Ejemplos: fix(runtime): el clic derecho llega al juego
          perf(web): compilar boot.wasm en paralelo con los packs
MSG
exit 1
