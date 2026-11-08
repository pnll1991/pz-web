#!/usr/bin/env bash
# Ronda 80: dev server de pruebas en 127.0.0.1:8098, desacoplado del shell que lo lanza
# (setsid + nohup: sobrevive a los timeouts de 10 min de las herramientas del agente).
#   scripts/dev_server_8098.sh [start]   arranca sólo si nadie escucha en el puerto (idempotente)
#   scripts/dev_server_8098.sh restart   detiene el server.mjs propio del puerto y lo vuelve a lanzar; exige
#                                        chrome.lock libre (lo toma él mismo). No envolverlo en `flock`.
#   scripts/dev_server_8098.sh stop|status
# El servidor lee cada archivo en cada petición (Cache-Control: no-cache, ETag débil tamaño+mtime),
# así que un reload de la página toma el JS de port/target/web y un boot.wasm nuevos sin reiniciar.
# Sólo hace falta reiniciar si cambia dev-server/*.mjs o el manifest de media (se calcula una vez).
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
# Mods de mapa (opcional): ~/.config/pz-web/mapa puede definir PZWEB_MODS_DIR; el dev server sirve esos archivos al navegador.
PZWEB_MAP_CONFIG=${PZWEB_MAP_CONFIG:-$HOME/.config/pz-web/mapa}
if [[ -f "$PZWEB_MAP_CONFIG" ]]; then source "$PZWEB_MAP_CONFIG"; [[ -n "${PZWEB_MODS_DIR:-}" ]] && export PZWEB_MODS_DIR; fi
cd "$PZ_WEB_ROOT"
PORT=8098
LOG=${PZ_SERVER_LOG:-/tmp/pz80-server.log}
export PORT PZ_HOME=${PZ_HOME:-/home/agente47/pz-pinned/25485521/projectzomboid} PZ_PACKS=${PZ_PACKS:-build/real-browser/packs}
FORBIDDEN=" 446769 12149 12150 26807 "
CHROME_LOCK=${PZ_CHROME_LOCK:-/tmp/pz80-locks/chrome.lock}
# Cierra en este (sub)shell todo fd heredado > 2: si el llamador tiene un flock (chrome.lock, build.lock,
# git.lock) el server lo heredaría y lo retendría para siempre (ronda 80: deadlock de chrome.lock).
close_inherited_fds() { local fd; for fd in $(ls /proc/$BASHPID/fd); do (( fd > 2 )) && eval "exec $fd>&-" 2>/dev/null; done; return 0; }

listening() { curl -fsS -m 3 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; }
server_pid() { # pid de node que escucha en 127.0.0.1:$PORT y ejecuta dev-server/server.mjs
  local pid
  pid=$(ss -ltnpH "sport = :$PORT" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2) || true
  [[ -n "$pid" ]] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q 'dev-server/server.mjs' || { echo "puerto $PORT ocupado por pid $pid ajeno" >&2; return 2; }
  echo "$pid"
}
start() {
  if listening; then echo "server ya escucha en 127.0.0.1:$PORT (pid $(server_pid || echo '?'))"; return 0; fi
  echo "[$(date +%T)] start PORT=$PORT PZ_HOME=$PZ_HOME PZ_PACKS=$PZ_PACKS" >> "$LOG"
  ( close_inherited_fds; exec setsid nohup node dev-server/server.mjs >> "$LOG" 2>&1 < /dev/null ) &
  for _ in $(seq 1 60); do listening && { echo "server arriba en 127.0.0.1:$PORT (pid $(server_pid))"; return 0; }; sleep 0.5; done
  echo "el server no respondió en 30 s; ver $LOG" >&2; tail -5 "$LOG" >&2; return 1
}
stop() {
  local pid; pid=$(server_pid) || { echo "no hay server.mjs en $PORT"; return 0; }
  [[ "$FORBIDDEN" == *" $pid "* ]] && { echo "pid $pid prohibido; no se toca" >&2; return 1; }
  kill "$pid"
  for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || { echo "server $pid detenido"; return 0; }; sleep 0.25; done
  kill -9 "$pid"; echo "server $pid forzado"
}
case "${1:-start}" in
  start) start ;;
  restart) # sólo con chrome.lock libre (ningún Chrome de prueba usa el server); lo toma durante el reinicio
    exec 9>>"$CHROME_LOCK"
    flock -n 9 || { echo "chrome.lock ocupado: hay un Chrome de prueba usando el server; no se reinicia" >&2; exit 1; }
    stop; start ;;
  stop) stop ;;
  status) if listening; then echo "arriba (pid $(server_pid || echo '?'))"; else echo "abajo"; exit 1; fi ;;
  *) echo "uso: $0 start|restart|stop|status" >&2; exit 2 ;;
esac
