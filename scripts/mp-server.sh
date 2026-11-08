#!/usr/bin/env bash
# Round 82: servidor dedicado REAL de PZ (misma instalación fijada), sólo 127.0.0.1, sin Steam, sin UPnP.
# Datos fuera del repo (PZ_MP_DATA, por defecto /tmp/pz82/zomboid). Puerto UDP 16261 (+16262).
#   scripts/mp-server.sh start|stop|status|apply-config|reset-world|admin-pass-file|gen-admin-pass [--force]   (logs: /tmp/pz82/server.log)
set -euo pipefail
G=${PZ_HOME:-/home/agente47/pz-pinned/25485521/projectzomboid}; D=${PZ_MP_DATA:-/tmp/pz82/zomboid}; LOG=${PZ_MP_LOG:-/tmp/pz82/server.log}
# Una segunda instancia para pruebas (R86): PZ_MP_DATA=/tmp/pz86/zomboid PZ_MP_PORT=16271 PZ_MP_UDPPORT=16272 PZ_MP_LOG=/tmp/pz86/server.log
PORT=${PZ_MP_PORT:-16261}; UDPPORT=${PZ_MP_UDPPORT:-16262}
pid() { ps -eo pid,args | grep -v grep | grep "GameServer -nosteam -cachedir=$D" | awk '{print $1}' | head -1 || true; } # sin '|| true', pipefail+set -e cortan el script en silencio cuando no corre
# Ajustes del servidor que el cliente web todavía no puede atender (nativas ausentes que lanzan su nombre JNI):
#  - VoiceEnable=false: RakVoice (chat de voz) no existe en el navegador.
#  - EnableVehicles=false: Bullet.addVehicle no existe todavía (en solitario también se juega sin vehículos).
# Contraseña de la cuenta admin: aleatoria, nunca impresa ni en el repo. Con el túnel público, 'pzweb82' sería adivinable.
# De dónde se lee (la primera que exista; si se nombra un archivo que no existe es un error, nunca se cae a otra en silencio):
#   1. PZ_MP_ADMIN_PASSWORD (entorno)   2. PZ_MP_ADMIN_PASSWORD_FILE   3. ~/.config/pz-web/admin-pass (persiste a reinicios, a diferencia de /tmp)
#   4. /tmp/pz82/admin.pw (el de antes; se crea aleatorio si falta)
# Rotarla: `gen-admin-pass --force` escribe el archivo nuevo (3 o 2) sin mostrarlo; surte efecto en el próximo `start` (ver docs/operacion/manejar.md).
ADMIN_PASS_DEFAULT=${XDG_CONFIG_HOME:-$HOME/.config}/pz-web/admin-pass; ADMIN_PASS_LEGACY=/tmp/pz82/admin.pw
new_password() { head -c 18 /dev/urandom | base64 -w0 | tr '+/' 'ab' | tr -d '='; }
admin_pass_file() {
  if [[ -n "${PZ_MP_ADMIN_PASSWORD_FILE:-}" ]]; then echo "$PZ_MP_ADMIN_PASSWORD_FILE"; return; fi
  if [[ -s "$ADMIN_PASS_DEFAULT" ]]; then echo "$ADMIN_PASS_DEFAULT"; else echo "$ADMIN_PASS_LEGACY"; fi
}
admin_password() {
  [[ -n "${PZ_MP_ADMIN_PASSWORD:-}" ]] && { echo "$PZ_MP_ADMIN_PASSWORD"; return; }
  local f; f=$(admin_pass_file)
  if [[ "$f" == "$ADMIN_PASS_LEGACY" && ! -s "$f" ]]; then (umask 077; mkdir -p "$(dirname "$f")"; new_password > "$f"); fi
  [[ -s "$f" ]] || { echo "el archivo de contraseña admin no existe o está vacío: $f" >&2; return 1; }
  local modo; modo=$(stat -c %a "$f"); [[ "$modo" == 600 || "$modo" == 400 ]] || { echo "permisos $modo en $f: tienen que ser 600" >&2; return 1; }
  head -n1 "$f" | tr -d '\r\n'
}
# Población de zombis (pocos): se reescribe en cada start/apply-config; cada valor se cambia con su variable de entorno.
# Enums tal como los documenta el pzweb_SandboxVars.lua que genera el servidor de la versión fijada (B42):
#   Zombies: 1 Insane, 2 Very High, 3 High, 4 Normal, 5 Low, 6 None (no existe "Very Low")
#   Distribution: 1 Urban Focused, 2 Uniform · ZombieRespawn: 1 High, 2 Normal, 3 Low, 4 None
#   PlayerSpawnZombieRemoval: 1 en el edificio y alrededor (por defecto), 2 en el edificio, 3 en la habitación, 4 en cualquier lado
# El servidor dedicado NO deriva ZombieConfig del preset (el mundo en vivo tenía Zombies=3 y PopulationMultiplier=0.65):
# los multiplicadores de ZombieConfig se fijan explícitamente. Low = 0.15 según el propio archivo.
# La densidad de una celda se fija la primera vez que se visita (zpop/zpop_<x>_<y>.bin): ver docs/operacion/manejar.md.
PZWEB_ZOMBIES=${PZWEB_ZOMBIES:-5}; PZWEB_ZOMBIE_DISTRIBUTION=${PZWEB_ZOMBIE_DISTRIBUTION:-2}
PZWEB_ZOMBIE_RESPAWN=${PZWEB_ZOMBIE_RESPAWN:-4}; PZWEB_SPAWN_ZOMBIE_REMOVAL=${PZWEB_SPAWN_ZOMBIE_REMOVAL:-1}
PZWEB_POP_MULT=${PZWEB_POP_MULT:-0.15}; PZWEB_POP_START_MULT=${PZWEB_POP_START_MULT:-0.5}; PZWEB_POP_PEAK_MULT=${PZWEB_POP_PEAK_MULT:-1.0}
PZWEB_RESPAWN_HOURS=${PZWEB_RESPAWN_HOURS:-0.0}; PZWEB_RESPAWN_UNSEEN_HOURS=${PZWEB_RESPAWN_UNSEEN_HOURS:-0.0}
PZWEB_RESPAWN_MULT=${PZWEB_RESPAWN_MULT:-0.0}; PZWEB_REDISTRIBUTE_HOURS=${PZWEB_REDISTRIBUTE_HOURS:-12.0}
apply_zombie_config() { # $1 = pzweb_SandboxVars.lua; idempotente; valida todo antes de escribir (nunca deja el archivo a medias)
  local f=$1 k v var min max t spec edits=()
  # clave:variable:mín:máx:tipo (int = entero; dec = decimal, se escribe con punto como lo deja el juego)
  for spec in Zombies:PZWEB_ZOMBIES:1:6:int Distribution:PZWEB_ZOMBIE_DISTRIBUTION:1:2:int ZombieRespawn:PZWEB_ZOMBIE_RESPAWN:1:4:int \
      PlayerSpawnZombieRemoval:PZWEB_SPAWN_ZOMBIE_REMOVAL:1:4:int PopulationMultiplier:PZWEB_POP_MULT:0:4:dec \
      PopulationStartMultiplier:PZWEB_POP_START_MULT:0:4:dec PopulationPeakMultiplier:PZWEB_POP_PEAK_MULT:0:4:dec \
      RespawnHours:PZWEB_RESPAWN_HOURS:0:8760:dec RespawnUnseenHours:PZWEB_RESPAWN_UNSEEN_HOURS:0:8760:dec \
      RespawnMultiplier:PZWEB_RESPAWN_MULT:0:1:dec RedistributeHours:PZWEB_REDISTRIBUTE_HOURS:0:8760:dec; do
    IFS=: read -r k var min max t <<< "$spec"; v=${!var}
    if [[ $t == int ]]; then [[ $v =~ ^[0-9]+$ ]] || { echo "$var=$v: tiene que ser un entero" >&2; return 1; }
    else [[ $v =~ ^[0-9]+(\.[0-9]+)?$ ]] || { echo "$var=$v: tiene que ser un número decimal (con punto)" >&2; return 1; }
      [[ $v == *.* ]] || v=$v.0; fi
    awk -v v="$v" -v a="$min" -v b="$max" 'BEGIN{exit !(v+0>=a+0 && v+0<=b+0)}' || { echo "$var=$v: fuera de rango [$min, $max]" >&2; return 1; }
    if grep -qE "^[[:space:]]*$k = " "$f"; then edits+=(-e "s/^([[:space:]]*$k = )[0-9.]+,/\\1$v,/"); else echo "aviso: $k no está en $f (no se cambia)" >&2; fi
  done
  (( ${#edits[@]} == 0 )) || sed -i -E "${edits[@]}" "$f"
}
# Idioma del servidor (PZWEB_LANGUAGE, código de carpeta de media/lua/shared/Translate; por defecto ES). El dedicado lo lee de
# $D/options.ini (Translator.getLanguage -> Core.getOptionLanguageName); se reescribe en cada start/apply-config. Si el archivo no
# existe todavía (mundo nuevo) se crea uno mínimo y el juego completa el resto al guardarlo. El idioma de la interfaz de cada
# jugador lo decide su cliente (port/runtime/language.mjs), no este valor.
PZWEB_LANGUAGE=${PZWEB_LANGUAGE:-ES}
# Mapa de un mod (opcional, local y fuera de git): ~/.config/pz-web/mapa define PZWEB_MAP (carpeta del mapa), PZWEB_MODS (ids de
# mod separados por ';'), PZWEB_MODS_DIR (carpeta con los mods, se enlaza en $D/mods) y PZWEB_SPAWN (worldX,worldY,posX,posY del
# punto de aparición). Sin el archivo ni las variables, el servidor queda con el mapa del juego como siempre.
PZWEB_MAP_CONFIG=${PZWEB_MAP_CONFIG:-$HOME/.config/pz-web/mapa}
[[ -f "$PZWEB_MAP_CONFIG" ]] && source "$PZWEB_MAP_CONFIG"
apply_map() { # $1 = pzweb.ini
  local f=$1 key value
  for key in Map Mods; do
    [[ $key == Map ]] && value=${PZWEB_MAP:-} || value=${PZWEB_MODS:-}
    [[ -n "$value" ]] || continue
    [[ "$value" != *$'\n'* && "$value" != *'|'* ]] || { echo "PZWEB_MAP/PZWEB_MODS inválido" >&2; return 1; }
    if grep -q "^$key=" "$f"; then sed -i "s|^$key=.*|$key=$value|" "$f"; else printf '%s=%s\n' "$key" "$value" >> "$f"; fi
  done
  if [[ -n "${PZWEB_MODS_DIR:-}" ]]; then
    [[ -d "$PZWEB_MODS_DIR" ]] || { echo "PZWEB_MODS_DIR=$PZWEB_MODS_DIR no existe" >&2; return 1; }
    # el juego crea $D/mods vacía en el primer arranque: una carpeta vacía se reemplaza por el enlace; una con archivos no se toca
    if [[ -d "$D/mods" && ! -L "$D/mods" ]] && [[ -z "$(ls -A "$D/mods")" ]]; then rmdir "$D/mods"; fi
    if [[ -L "$D/mods" || ! -e "$D/mods" ]]; then ln -sfn "$PZWEB_MODS_DIR" "$D/mods"
    else echo "aviso: $D/mods es una carpeta con archivos; no se enlaza $PZWEB_MODS_DIR" >&2; fi
  fi
  return 0
}
apply_language() { # $1 = options.ini del servidor
  local f=$1 code; code=$(printf '%s' "$PZWEB_LANGUAGE" | tr '[:lower:]' '[:upper:]')
  [[ $code =~ ^[A-Z][A-Z0-9_]{1,15}$ ]] || { echo "PZWEB_LANGUAGE=$PZWEB_LANGUAGE: código inválido" >&2; return 1; }
  # sin la copia del juego (CI) no hay contra qué validar el código: solo el formato
  [[ ! -d "$G/media/lua/shared/Translate" || -f "$G/media/lua/shared/Translate/$code/language.json" ]] || { echo "PZWEB_LANGUAGE=$code: no existe $G/media/lua/shared/Translate/$code" >&2; return 1; }
  if [[ ! -f "$f" ]]; then printf 'version=8\nlanguage=%s\n' "$code" > "$f"
  elif grep -q '^language=' "$f"; then sed -i "s/^language=.*/language=$code/" "$f"
  else printf 'language=%s\n' "$code" >> "$f"; fi
}
# Mensaje de bienvenida del chat: es texto de configuración del servidor (pzweb.ini), no pasa por las traducciones. Con ES y el
# mensaje por defecto del juego (empieza con "Welcome to Project Zomboid Multiplayer!") se cambia por esta versión propia en español.
# PZWEB_WELCOME_MESSAGE lo fija a mano (sin saltos de línea; <LINE> separa renglones); un mensaje ya personalizado no se toca.
WELCOME_ES='¡Bienvenido al multijugador de Project Zomboid! <LINE> <LINE> Para usar el chat: Tab, T o Enter. <LINE> <LINE> Tab cambia el canal del mensaje. <LINE> <LINE> Canales globales: /all <LINE> Canales locales: /say, /yell <LINE> Canales especiales: /whisper, /safehouse, /faction. <LINE> <LINE> La flecha hacia arriba recorre los mensajes anteriores. El engranaje configura el chat. <LINE> <LINE> ¡Buena suerte sobreviviendo!'
apply_welcome() { # $1 = pzweb.ini
  local f=$1 msg="${PZWEB_WELCOME_MESSAGE:-}" code; code=$(printf '%s' "$PZWEB_LANGUAGE" | tr '[:lower:]' '[:upper:]')
  [[ "$msg" != *$'\n'* && "$msg" != *$'\r'* ]] || { echo "PZWEB_WELCOME_MESSAGE no puede tener saltos de línea" >&2; return 1; }
  if [[ -z "$msg" ]]; then
    [[ $code == ES ]] && grep -q '^ServerWelcomeMessage=Welcome to Project Zomboid Multiplayer!' "$f" || return 0
    msg=$WELCOME_ES
  fi
  local tmp; tmp=$(mktemp "$f.XXXXXX")
  if grep -q '^ServerWelcomeMessage=' "$f"; then
    M=$msg awk '/^ServerWelcomeMessage=/{print "ServerWelcomeMessage=" ENVIRON["M"]; next} {print}' "$f" > "$tmp"
  else cat "$f" > "$tmp"; printf 'ServerWelcomeMessage=%s\n' "$msg" >> "$tmp"; fi
  cat "$tmp" > "$f"; rm -f "$tmp"
}
apply_config() {
  if [[ -d "$D/Server" ]]; then apply_language "$D/options.ini" || return 1; fi
  if [[ -f "$D/Server/pzweb.ini" ]]; then apply_welcome "$D/Server/pzweb.ini" || return 1; fi
  [[ -f "$D/Server/pzweb.ini" ]] && sed -i 's/^VoiceEnable=.*/VoiceEnable=false/; s/^UPnP=true/UPnP=false/; s/^SteamVAC=true/SteamVAC=false/' "$D/Server/pzweb.ini"
  [[ -f "$D/Server/pzweb_SandboxVars.lua" ]] && sed -i 's/EnableVehicles = true/EnableVehicles = false/' "$D/Server/pzweb_SandboxVars.lua"
  if [[ -f "$D/Server/pzweb_SandboxVars.lua" ]]; then apply_zombie_config "$D/Server/pzweb_SandboxVars.lua" || return 1; fi
  #  - DoLuaChecksum=false: el modo directo agrega media/lua/client/zzz_pzweb_play.lua (código propio), y el servidor expulsa a los
  #    clientes cuyos Lua no coinciden con los suyos. Sólo para este servidor local de confianza.
  [[ -f "$D/Server/pzweb.ini" ]] && sed -i 's/^DoLuaChecksum=.*/DoLuaChecksum=false/' "$D/Server/pzweb.ini"
  # una sola región de aparición (punto fijo de pzweb_spawnpoints.lua): los jugadores de prueba aparecen juntos
  if [[ -f "$D/Server/pzweb.ini" ]]; then apply_map "$D/Server/pzweb.ini" || return 1; fi
  if [[ -n "${PZWEB_SPAWN:-}" && -d "$D/Server" ]]; then
    [[ $PZWEB_SPAWN =~ ^([0-9]+),([0-9]+),([0-9]+),([0-9]+)$ ]] || { echo "PZWEB_SPAWN=$PZWEB_SPAWN: se espera worldX,worldY,posX,posY" >&2; return 1; }
    printf 'function SpawnPoints()\n\treturn {\n\t\tunemployed = {\n\t\t\t{ worldX = %s, worldY = %s, posX = %s, posY = %s }\n\t\t}\n\t}\nend\n' \
      "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}" "${BASH_REMATCH[4]}" > "$D/Server/pzweb_spawnpoints.lua"
  fi
  [[ -d "$D/Server" ]] && printf 'function SpawnRegions()\n\treturn {\n\t\t{ name = "pzweb", serverfile = "pzweb_spawnpoints.lua" },\n\t}\nend\n' > "$D/Server/pzweb_spawnregions.lua"
  return 0
}
case "${1:-status}" in
  apply-config) # reaplica la configuración a los archivos de $D sin arrancar (un servidor en marcha no la lee hasta el próximo start)
    apply_config ;;
  reset-world) # borra el mundo guardado (sólo datos de prueba bajo $D) y reaplica la configuración
    [[ -z "$(pid)" ]] || { echo "detené el servidor primero"; exit 1; }
    rm -rf "$D/Saves/Multiplayer/pzweb" "$D/db/pzweb.db"; apply_config; echo "mundo reiniciado" ;;
  start)
    [[ -z "$(pid)" ]] || { echo "ya corre (pid $(pid))"; exit 0; }
    ADMIN_PW=$(admin_password) || exit 1 # antes de tocar nada: un archivo ausente o con permisos flojos corta el arranque
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
      -servername pzweb -adminpassword "$ADMIN_PW" > "$LOG" 2>&1 < /dev/null &
    echo "arrancando; esperá 'SERVER STARTED' en $LOG" ;;
  admin-pass-file) admin_pass_file ;; # sólo la ruta que usaría `start`, nunca la contraseña
  gen-admin-pass) # crea el archivo con una contraseña aleatoria nueva (600), sin imprimirla; --force para reemplazar una existente
    f=${PZ_MP_ADMIN_PASSWORD_FILE:-$ADMIN_PASS_DEFAULT}
    [[ ! -e "$f" || "${2:-}" == --force ]] || { echo "ya existe $f (usar --force para reemplazarla)"; exit 1; }
    (umask 077; d=$(dirname "$f"); [[ -d "$d" ]] || mkdir -p "$d"; t=$(mktemp "$f.XXXXXX"); new_password > "$t"; mv -f "$t" "$f"); chmod 600 "$f"
    echo "$f (surte efecto en el próximo start; el servidor en marcha no se toca)" ;;
  stop) p=$(pid); [[ -n "$p" ]] && kill "$p" && echo "detenido $p" || echo "no corre" ;;
  status) p=$(pid)
    if [[ -n "$p" ]]; then echo "corre pid $p (puerto $PORT/udp)"; else echo "detenido"; fi
    ultimo=$(grep -E "SERVER STARTED|Shutdown handling started|Server exited" "$LOG" 2>/dev/null | tail -1 || true)
    echo "último evento en $LOG: ${ultimo:-ninguno}"
    [[ -n "$p" ]] ;;
esac
