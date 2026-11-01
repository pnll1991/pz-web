#!/usr/bin/env bash
# Ronda 81 (frente 0): benchmark repetible (tools/diag/bench.mjs) con chrome.lock tomado durante toda la corrida.
#   scripts/bench.sh [--mode menu|ingame|create-save] [--front F --change C --notes "..."]
#                    [--page game.html] [--query persist=1] [--extra probe=1] [--url URL]
#                    [--profile /tmp/pz81/profile] [--fps-seconds 30] [--walk-seconds 10] [--no-shots]
# Ejemplos:
#   scripts/bench.sh --front 0 --change baseline-dev                 # arranque -> ToS -> menú (~6 min)
#   scripts/bench.sh --mode ingame --front 0 --change baseline-dev   # + CONTINUE -> en juego -> fps quieto y con W
#   scripts/bench.sh --mode create-save                              # una vez por perfil: partida nueva + QUIT
# Filas en /tmp/pz81/bench.tsv (front, change, metric, value, notes); corrida en /tmp/pz81/bench-runs/<ts>-<modo>/
# (log.txt, console.txt, summary.json, capturas). Sólo mata el Chrome que lanzó.
# BENCH_LOCK_WAIT=N segundos de espera por el candado (por defecto 7200). PZ_GPU / PZ_CHROME_HEADED como en live_chrome.sh.
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
LOCK=${BENCH_LOCK:-/tmp/pz80-locks/chrome.lock}
mkdir -p /tmp/pz81
curl -fsS -m 5 "http://127.0.0.1:${PZ_DIAG_PORT:-8098}/health" >/dev/null || { echo "dev server ${PZ_DIAG_PORT:-8098} caído: scripts/dev_server_8098.sh start" >&2; exit 1; }
# No heredar otros flocks del llamador (build.lock/git.lock) al Chrome de prueba.
for fd in $(ls /proc/$$/fd); do (( fd > 2 )) && eval "exec $fd>&-" 2>/dev/null; done
echo "esperando $LOCK (hasta ${BENCH_LOCK_WAIT:-7200} s)..." >&2
exec flock -w "${BENCH_LOCK_WAIT:-7200}" -E 75 "$LOCK" node tools/diag/bench.mjs "$@"
