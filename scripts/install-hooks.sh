#!/usr/bin/env bash
# Activa los hooks versionados en .githooks/ para este clon (no toca la configuración global de git).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
chmod +x .githooks/* scripts/check-forbidden.sh scripts/check-commit-title.sh
echo "Hooks activos: $(ls .githooks | tr '\n' ' ')"
