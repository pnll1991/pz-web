#!/usr/bin/env bash
# Ronda 80: un Chrome de prueba persistente controlado por tools/diag/live.mjs.
#   scripts/live_chrome.sh start [URL]     lanza (setsid+nohup) con /tmp/pz80-locks/chrome.lock tomado
#   scripts/live_chrome.sh stop            SIGTERM al node propio (cierra Chrome y libera el candado); -9 si no responde
#   scripts/live_chrome.sh status          pid, edad del latido y últimas líneas de out.txt
#   scripts/live_chrome.sh cmd "<comando>" [timeout_s]   agrega el comando y espera su OK/FAIL
# Candado: el proceso `flock` envuelve a node; node (y los Chrome que lanza) heredan el fd, así que el
# candado se libera sólo cuando el Chrome de prueba muere. Otros frentes que usan
# `flock /tmp/pz80-locks/chrome.lock <cmd>` esperan hasta el `stop`. LIVE_LOCK_WAIT=N espera N s
# por el candado al arrancar (por defecto 0: falla si otro Chrome de prueba lo tiene).
# Variables: PZ_LIVE (dir, /tmp/pzlive), PZ_GPU / PZ_CHROME_HEADED (ver tools/diag/chrome-flags.mjs).
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
D=${PZ_LIVE:-/tmp/pzlive}; export PZ_LIVE=$D
LOCK=${LIVE_LOCK:-/tmp/pz80-locks/chrome.lock}
mkdir -p "$D"

live_pid() { [[ -f "$D/live.pid" ]] || return 1; local p; p=$(<"$D/live.pid"); kill -0 "$p" 2>/dev/null && tr '\0' ' ' < "/proc/$p/cmdline" | grep -q 'tools/diag/live.mjs' && echo "$p"; }

cmd() { # agrega una línea y espera "#N OK|FAIL"; imprime las líneas #N
  local line=$1 wait=${2:-600} n
  live_pid >/dev/null || { echo "live Chrome no está corriendo" >&2; return 1; }
  n=$(( $(grep -c . "$D/cmd.txt" 2>/dev/null || true) + 1 ))
  printf '%s\n' "$line" >> "$D/cmd.txt"
  local end=$(( SECONDS + wait ))
  while (( SECONDS < end )); do
    if grep -qE "^\[[^]]*\] #$n (OK|FAIL) " "$D/out.txt"; then
      grep -E "^\[[^]]*\] #$n " "$D/out.txt"
      grep -qE "^\[[^]]*\] #$n OK " "$D/out.txt"; return
    fi
    live_pid >/dev/null || { [[ "$line" == quit ]] && return 0; echo "live Chrome murió durante #$n" >&2; tail -3 "$D/out.txt" >&2; return 1; }
    sleep 0.5
  done
  echo "timeout esperando #$n ($line); sigue en cola" >&2; return 124
}

case "${1:-status}" in
  start)
    if p=$(live_pid); then echo "ya corre (pid $p)"; exit 0; fi
    rm -f "$D/live.pid"
    # Subshell que cierra los fds heredados > 2 (otros flocks del llamador); sólo el fd de flock pasa a node/Chrome.
    ( for fd in $(ls /proc/$BASHPID/fd); do (( fd > 2 )) && eval "exec $fd>&-" 2>/dev/null; done
      LIVE_URL=${2:-} exec setsid nohup flock -w "${LIVE_LOCK_WAIT:-0}" -E 75 "$LOCK" node tools/diag/live.mjs >> "$D/live.log" 2>&1 < /dev/null ) &
    wrapper=$!
    for _ in $(seq 1 120); do
      if p=$(live_pid) && grep -q 'BROWSER ' "$D/out.txt" 2>/dev/null; then echo "live Chrome arriba (node pid $p)"; head -1 "$D/out.txt"; exit 0; fi
      if ! kill -0 "$wrapper" 2>/dev/null; then
        wait "$wrapper" && rc=0 || rc=$?
        if (( rc == 75 )); then echo "candado $LOCK ocupado por otro Chrome de prueba" >&2; else echo "live.mjs terminó (rc $rc); ver $D/live.log" >&2; tail -5 "$D/live.log" >&2; fi
        exit 1
      fi
      sleep 0.5
    done
    echo "live.mjs no arrancó en 60 s; ver $D/live.log" >&2; exit 1 ;;
  stop)
    p=$(live_pid) || { echo "no corre"; exit 0; }
    kill -TERM "$p" # live.mjs cierra el navegador en SIGTERM; no se encola `quit` detrás de un `until` largo
    for _ in $(seq 1 20); do kill -0 "$p" 2>/dev/null || { echo "detenido"; exit 0; }; sleep 0.5; done
    kill "$p"; sleep 2; kill -0 "$p" 2>/dev/null && kill -9 "$p"; echo "detenido (forzado)" ;;
  status)
    if p=$(live_pid); then
      age=$(( $(date +%s) - $(stat -c %Y "$D/heartbeat.json" 2>/dev/null || date +%s) ))
      echo "corre (node pid $p), latido hace ${age}s"; cat "$D/heartbeat.json" 2>/dev/null; tail -3 "$D/out.txt"
    else echo "no corre"; exit 1; fi ;;
  cmd) shift; cmd "$@" ;;
  *) echo "uso: $0 start [URL]|stop|status|cmd \"<comando>\" [timeout_s]" >&2; exit 2 ;;
esac
