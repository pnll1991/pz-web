#!/usr/bin/env bash
# Round 82: servidor dedicado REAL de PZ (misma instalación fijada), sólo 127.0.0.1, sin Steam, sin UPnP.
# Datos fuera del repo (PZ_MP_DATA, por defecto /tmp/pz82/zomboid). Puerto UDP 16261 (+16262).
#   scripts/mp-server.sh start|stop|status   (logs: /tmp/pz82/server.log)
set -euo pipefail
G=${PZ_HOME:-/home/agente47/pz-pinned/25485521/projectzomboid}; D=${PZ_MP_DATA:-/tmp/pz82/zomboid}; LOG=/tmp/pz82/server.log
pid() { ps -eo pid,args | grep -v grep | grep "GameServer -nosteam -cachedir=$D" | awk '{print $1}' | head -1; }
case "${1:-status}" in
  start)
    [[ -z "$(pid)" ]] || { echo "ya corre (pid $(pid))"; exit 0; }
    mkdir -p "$D/Server" /tmp/pz82
    # el servidor abre puertos del router por UPnP por defecto: se apaga antes de arrancar
    # primera ejecución: ini mínimo (el juego completa el resto con valores por defecto) para que UPnP nunca llegue a estar activo
    [[ -f "$D/Server/pzweb.ini" ]] || printf 'UPnP=false\nSteamVAC=false\n' > "$D/Server/pzweb.ini"
    if [[ -f "$D/Server/pzweb.ini" ]]; then sed -i 's/^UPnP=true/UPnP=false/; s/^SteamVAC=true/SteamVAC=false/' "$D/Server/pzweb.ini"; fi
    cd "$G"; export PATH="$G/jre64/bin:$PATH" LD_LIBRARY_PATH="$G/natives:$G/jre64/lib/amd64"
    setsid nohup java -cp ./:./projectzomboid.jar -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED \
      --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -Xms1g -Xmx2g -XX:+UseZGC -Dzomboid.steam=0 -Djava.library.path=./:./natives/ \
      -Djava.security.egd=file:/dev/urandom zombie/network/GameServer -nosteam -cachedir="$D" -ip 127.0.0.1 -port 16261 -udpport 16262 \
      -servername pzweb -adminpassword "${PZ_MP_ADMIN_PASSWORD:-pzweb82}" > "$LOG" 2>&1 < /dev/null &
    echo "arrancando; esperá 'SERVER STARTED' en $LOG" ;;
  stop) p=$(pid); [[ -n "$p" ]] && kill "$p" && echo "detenido $p" || echo "no corre" ;;
  status) p=$(pid); [[ -n "$p" ]] && echo "corre pid $p" || echo "detenido"; grep -c "SERVER STARTED" "$LOG" 2>/dev/null || true ;;
esac
