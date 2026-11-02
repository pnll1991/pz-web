#!/usr/bin/env bash
# Round 82: servidor dedicado REAL de PZ (misma instalación fijada), sólo 127.0.0.1, sin Steam, sin UPnP.
# Datos fuera del repo (PZ_MP_DATA, por defecto /tmp/pz82/zomboid). Puerto UDP 16261 (+16262).
#   scripts/mp-server.sh start|stop|status|reset-world   (logs: /tmp/pz82/server.log)
set -euo pipefail
G=${PZ_HOME:-/home/agente47/pz-pinned/25485521/projectzomboid}; D=${PZ_MP_DATA:-/tmp/pz82/zomboid}; LOG=${PZ_MP_LOG:-/tmp/pz82/server.log}
# Una segunda instancia para pruebas (R86): PZ_MP_DATA=/tmp/pz86/zomboid PZ_MP_PORT=16271 PZ_MP_UDPPORT=16272 PZ_MP_LOG=/tmp/pz86/server.log
PORT=${PZ_MP_PORT:-16261}; UDPPORT=${PZ_MP_UDPPORT:-16262}
pid() { ps -eo pid,args | grep -v grep | grep "GameServer -nosteam -cachedir=$D" | awk '{print $1}' | head -1; }
# Ajustes del servidor que el cliente web todavía no puede atender (nativas ausentes que lanzan su nombre JNI):
#  - VoiceEnable=false: RakVoice (chat de voz) no existe en el navegador.
#  - EnableVehicles=false: Bullet.addVehicle no existe todavía (en solitario también se juega sin vehículos).
# Contraseña de la cuenta admin: aleatoria, guardada en /tmp/pz82/admin.pw (600). Con el túnel público, 'pzweb82' sería adivinable.
admin_password() {
  [[ -n "${PZ_MP_ADMIN_PASSWORD:-}" ]] && { echo "$PZ_MP_ADMIN_PASSWORD"; return; }
  [[ -s /tmp/pz82/admin.pw ]] || (umask 077; mkdir -p /tmp/pz82; head -c 18 /dev/urandom | base64 -w0 | tr '+/' 'ab' | tr -d '=' > /tmp/pz82/admin.pw)
  cat /tmp/pz82/admin.pw
}
apply_config() {
  [[ -f "$D/Server/pzweb.ini" ]] && sed -i 's/^VoiceEnable=.*/VoiceEnable=false/; s/^UPnP=true/UPnP=false/; s/^SteamVAC=true/SteamVAC=false/' "$D/Server/pzweb.ini"
  [[ -f "$D/Server/pzweb_SandboxVars.lua" ]] && sed -i 's/EnableVehicles = true/EnableVehicles = false/' "$D/Server/pzweb_SandboxVars.lua"
  #  - Zombis (R86): se quieren zombis cerca del punto de aparición (el bar). Distribution=2 (uniforme, no sólo urbano),
  #    PlayerSpawnZombieRemoval=4 (pueden aparecer donde aparecen los jugadores), ZombieRespawn=2 (reaparecen normal), Zombies=3 (alta).
  #    La densidad se fija al crear el mundo: tras cambiarla, `reset-world`.
  if [[ -f "$D/Server/pzweb_SandboxVars.lua" ]]; then
    sed -i -E 's/^(\s*Zombies = )[0-9]+,/\13,/; s/^(\s*Distribution = )[0-9]+,/\12,/; s/^(\s*ZombieRespawn = )[0-9]+,/\12,/; s/^(\s*PlayerSpawnZombieRemoval = )[0-9]+,/\14,/' "$D/Server/pzweb_SandboxVars.lua"
  fi
  #  - DoLuaChecksum=false: el modo directo agrega media/lua/client/zzz_pzweb_play.lua (código propio), y el servidor expulsa a los
  #    clientes cuyos Lua no coinciden con los suyos. Sólo para este servidor local de confianza.
  [[ -f "$D/Server/pzweb.ini" ]] && sed -i 's/^DoLuaChecksum=.*/DoLuaChecksum=false/' "$D/Server/pzweb.ini"
  # una sola región de aparición (punto fijo de pzweb_spawnpoints.lua): los jugadores de prueba aparecen juntos
  [[ -d "$D/Server" ]] && printf 'function SpawnRegions()\n\treturn {\n\t\t{ name = "pzweb", serverfile = "pzweb_spawnpoints.lua" },\n\t}\nend\n' > "$D/Server/pzweb_spawnregions.lua"
  return 0
}
case "${1:-status}" in
  reset-world) # borra el mundo guardado (sólo datos de prueba bajo $D) y reaplica la configuración
    [[ -z "$(pid)" ]] || { echo "detené el servidor primero"; exit 1; }
    rm -rf "$D/Saves/Multiplayer/pzweb" "$D/db/pzweb.db"; apply_config; echo "mundo reiniciado" ;;
  start)
    [[ -z "$(pid)" ]] || { echo "ya corre (pid $(pid))"; exit 0; }
    mkdir -p "$D/Server" /tmp/pz82
    # el servidor abre puertos del router por UPnP por defecto: se apaga antes de arrancar
    # primera ejecución: ini mínimo (el juego completa el resto con valores por defecto) para que UPnP nunca llegue a estar activo
    [[ -f "$D/Server/pzweb.ini" ]] || printf 'UPnP=false\nSteamVAC=false\nVoiceEnable=false\n' > "$D/Server/pzweb.ini"
    apply_config
    if [[ -f "$D/Server/pzweb.ini" ]]; then sed -i 's/^UPnP=true/UPnP=false/; s/^SteamVAC=true/SteamVAC=false/' "$D/Server/pzweb.ini"; fi
    cd "$G"; export PATH="$G/jre64/bin:$PATH" LD_LIBRARY_PATH="$G/natives:$G/jre64/lib/amd64"
    setsid nohup java -cp ./:./projectzomboid.jar -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED \
      --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -Xms1g -Xmx2g -XX:+UseZGC -Dzomboid.steam=0 -Djava.library.path=./:./natives/ \
      -Djava.security.egd=file:/dev/urandom zombie/network/GameServer -nosteam -cachedir="$D" -ip 127.0.0.1 -port "$PORT" -udpport "$UDPPORT" \
      -servername pzweb -adminpassword "$(admin_password)" > "$LOG" 2>&1 < /dev/null &
    echo "arrancando; esperá 'SERVER STARTED' en $LOG" ;;
  stop) p=$(pid); [[ -n "$p" ]] && kill "$p" && echo "detenido $p" || echo "no corre" ;;
  status) p=$(pid); [[ -n "$p" ]] && echo "corre pid $p" || echo "detenido"; grep -c "SERVER STARTED" "$LOG" 2>/dev/null || true ;;
esac
