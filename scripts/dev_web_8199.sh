#!/usr/bin/env bash
# Ronda 86: web de PRUEBAS en 127.0.0.1:8199 con raiz propia (/tmp/pz86/web), desacoplada de port/target/web (que sirve el 8098 en vivo).
#   scripts/dev_web_8199.sh sync     copia port/web/*, port/runtime/*.mjs (y lo pesado una vez) a la raiz de pruebas
#   scripts/dev_web_8199.sh start|stop|status
# Sin clave publica, sin tunel, registro de accesos y lista de calentamiento aparte (no mezclan con los reales).
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
PORT=8199; W=${PZ_DEV_WEB:-/tmp/pz86/web}; LOG=/tmp/pz86/web.log
sync_dev() {
  mkdir -p "$W/runtime"
  [[ -d "$W/release-minpulse" ]] || rsync -a --exclude=/game --exclude=/release --exclude=/release-noopt --exclude=/simple-o2 "${PZ_BUILD_WEB:-port/target/web}/" "$W/"
  for f in port/web/*; do [[ -f "$f" ]] || continue; cmp -s "$f" "$W/$(basename -- "$f")" || { cp "$f" "$W/$(basename -- "$f")"; echo "  sync $(basename -- "$f")"; }; done
  for f in port/runtime/*.mjs; do cmp -s "$f" "$W/runtime/$(basename -- "$f")" || { cp "$f" "$W/runtime/$(basename -- "$f")"; echo "  sync runtime/$(basename -- "$f")"; }; done
}
pidof_dev() { ss -ltnpH "sport = :$PORT" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2; }
case "${1:-status}" in
  sync) sync_dev ;;
  start) sync_dev; [[ -z "$(pidof_dev)" ]] || { echo "ya escucha"; exit 0; }
    ( for fd in $(ls /proc/$BASHPID/fd); do (( fd > 2 )) && eval "exec $fd>&-" 2>/dev/null; done
      export PORT PZWEB_WEB_ROOT="$W" PZ_HOME=${PZ_HOME:-/home/agente47/pz-pinned/25485521/projectzomboid} PZ_PACKS=${PZ_DEV_PACKS:-/tmp/pz86/packs} \
        PZWEB_ACCESS_LOG=/tmp/pz86/access.log PZWEB_WARM_FILE=/tmp/pz86/warm.json
      exec setsid nohup node dev-server/server.mjs >> "$LOG" 2>&1 < /dev/null ) &
    for _ in $(seq 1 60); do curl -fsS -m 2 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1 && { echo "web de pruebas arriba en 127.0.0.1:$PORT"; exit 0; }; sleep 0.5; done
    echo "no arranco; ver $LOG" >&2; tail -5 "$LOG" >&2; exit 1 ;;
  stop) p=$(pidof_dev); [[ -n "$p" ]] && tr '\0' ' ' < /proc/$p/cmdline | grep -q dev-server/server.mjs && kill "$p" && echo "detenido $p" || echo "no corre" ;;
  status) p=$(pidof_dev); [[ -n "$p" ]] && echo "corre pid $p" || echo "detenido" ;;
esac
