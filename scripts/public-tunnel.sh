#!/usr/bin/env bash
# Round 84: abre pz-web a internet con un túnel rápido de Cloudflare, protegido por una clave (aprobado por Paolozky para jugar con un amigo).
#   scripts/public-tunnel.sh start   clave nueva -> dev server 8098 en modo público -> cloudflared -> imprime la URL completa con ?k=
#   scripts/public-tunnel.sh stop    mata cloudflared, borra la clave y reinicia el dev server SIN modo público
#   scripts/public-tunnel.sh status
# El servidor PZ (127.0.0.1:16261) y los puentes siguen en loopback; sólo el dev server 8098 sale por el túnel.
# Quien no tenga la clave recibe 403 (dev-server/public-mode.mjs). Clave: /tmp/pzweb-public.key (600). Log: /tmp/pzweb-tunnel.log.
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
CF=${CLOUDFLARED:-$HOME/.local/bin/cloudflared}
KEYFILE=/tmp/pzweb-public.key LOG=/tmp/pzweb-tunnel.log PIDFILE=/tmp/pzweb-tunnel.pid URLFILE=/tmp/pzweb-tunnel.url

tunnel_pid() { # pid de nuestro cloudflared (verifica el comando, no sólo el archivo)
  [[ -f $PIDFILE ]] || return 1
  local pid; pid=$(cat "$PIDFILE"); [[ -n $pid ]] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q 'cloudflared.*tunnel.*--url http://127.0.0.1:8098' || return 1
  echo "$pid"
}
server_public() { # ¿el dev server de 8098 corre con la clave?
  local pid; pid=$(ss -ltnpH "sport = :8098" 2>/dev/null | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2) || true
  [[ -n $pid ]] && tr '\0' '\n' < "/proc/$pid/environ" 2>/dev/null | grep -q '^PZWEB_PUBLIC_KEY=.\{16,\}'
}

case "${1:-status}" in
  start)
    [[ -x $CF ]] || { echo "falta cloudflared en $CF" >&2; exit 1; }
    if tunnel_pid >/dev/null; then echo "el túnel ya está abierto: $(cat $URLFILE 2>/dev/null)"; exit 0; fi
    (umask 077; head -c 24 /dev/urandom | base64 -w0 | tr '+/' '-_' | tr -d '=' > "$KEYFILE"); chmod 600 "$KEYFILE"
    KEY=$(cat "$KEYFILE")
    PZWEB_PUBLIC_KEY="$KEY" bash scripts/dev_server_8098.sh restart >/dev/null
    : > "$LOG"
    setsid nohup "$CF" tunnel --no-autoupdate --url http://127.0.0.1:8098 > "$LOG" 2>&1 < /dev/null &
    echo $! > "$PIDFILE"
    for _ in $(seq 1 60); do
      URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$LOG" | head -1 || true)
      [[ -n $URL ]] && break
      sleep 1
    done
    [[ -n ${URL:-} ]] || { echo "cloudflared no dio una URL (log: $LOG)" >&2; bash "$0" stop >/dev/null 2>&1 || true; exit 1; }
    echo "$URL" > "$URLFILE"
    echo "$URL/play.html?k=$KEY" ;;
  stop)
    if pid=$(tunnel_pid); then kill "$pid" 2>/dev/null || true; fi
    rm -f "$PIDFILE" "$URLFILE" "$KEYFILE"
    env -u PZWEB_PUBLIC_KEY bash scripts/dev_server_8098.sh restart >/dev/null
    echo "túnel cerrado; dev server sin modo público" ;;
  status)
    if pid=$(tunnel_pid); then echo "túnel: abierto (pid $pid) $(cat $URLFILE 2>/dev/null)"; else echo "túnel: cerrado"; fi
    if server_public; then echo "dev server: modo público (clave activa)"; else echo "dev server: sólo local"; fi
    [[ -f $KEYFILE ]] && echo "clave: $KEYFILE ($(stat -c %a "$KEYFILE"))" || echo "clave: ninguna" ;;
  *) echo "uso: $0 start|stop|status" >&2; exit 2 ;;
esac
