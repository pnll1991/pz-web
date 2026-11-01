#!/usr/bin/env bash
# Ronda 81 (frente 1): lanzador de la versión rápida (ADVANCED + wasm-opt -O2 acotado + pulsos mínimos + caché de texturas).
#   scripts/fast.sh            asegura el dev server en 127.0.0.1:8098, comprueba el build y imprime la URL
#   scripts/fast.sh build      además construye el release si falta (build.lock; ~8 min; PZ_HOME fijado)
#   scripts/fast.sh rebuild    lo reconstruye siempre
# Marcador: http://127.0.0.1:8098/fast.html  (= game.html?build=release-minpulse&persist=1&texcache=1).
# game.html sin parámetros sigue siendo el build dev (port/target/web/game).
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
DIR=port/target/web/release-minpulse/game
export PZ_HOME=${PZ_HOME:-/home/agente47/pz-pinned/25485521/projectzomboid}
if [ "${1:-}" = rebuild ] || { [ "${1:-}" = build ] && [ ! -f "$DIR/boot.wasm" ]; }; then
  flock /tmp/pz80-locks/build.lock bash scripts/build.sh --with-game --release --pulse=min
fi
bash scripts/sync_web.sh >/dev/null
bash scripts/dev_server_8098.sh start
if [ -f "$DIR/boot.wasm" ] && grep -q '"success":true' "$DIR/build-state.json"; then
  echo "build rápido: $(stat -c '%s B, %y' "$DIR/boot.wasm" | cut -c1-40)"
  echo "abrir: http://127.0.0.1:8098/fast.html"
else
  echo "falta el build rápido: scripts/fast.sh build" >&2; exit 1
fi
