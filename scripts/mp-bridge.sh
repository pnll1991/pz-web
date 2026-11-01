#!/usr/bin/env bash
# Round 82: compila y/o lanza el puente RakNet (stdio). Usado por dev-server/ws-bridge.mjs (una JVM por navegador).
#   scripts/mp-bridge.sh build   compila tools/mp-bridge/Bridge.java contra el jar fijado (salida en port/target/mp-bridge)
#   scripts/mp-bridge.sh run     ejecuta el puente (stdin/stdout = tramas)
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
G=${PZ_HOME:-/home/agente47/pz-pinned/25485521/projectzomboid}
OUT="$PZ_WEB_ROOT/port/target/mp-bridge"
case "${1:-run}" in
  build) mkdir -p "$OUT"; "$PZ_WEB_ROOT/.toolchain/jdk/bin/javac" -cp "$G/projectzomboid.jar" -d "$OUT" "$PZ_WEB_ROOT/tools/mp-bridge/Bridge.java" ;;
  run) exec env LD_LIBRARY_PATH="$G/natives" "$PZ_WEB_ROOT/.toolchain/jdk/bin/java" -Xmx256m -XX:+UseSerialGC -cp "$OUT:$G/projectzomboid.jar" \
         -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
         -Djava.library.path="$G/natives" Bridge ;;
  *) echo "uso: $0 build|run" >&2; exit 2 ;;
esac
