#!/usr/bin/env bash
# Ronda 80: publica un build de staging (scripts/build.sh --dev) en el directorio servido.
#   bash scripts/swap_staged.sh port/target/web-staging/game port/target/web/game
# Cada archivo se copia a un temporal en el mismo directorio y se renombra (rename atómico): una página
# que ya cargó sigue con su módulo; una que carga ve el archivo viejo o el nuevo, nunca uno a medias ni
# ausente. Después sincroniza la parte web (scripts/sync_web.sh). Rechaza un staging sin éxito.
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
stage=${1:?staging}; dest=${2:?destino}
[ -f "$stage/boot.wasm" ] || { echo "swap: falta $stage/boot.wasm" >&2; exit 1; }
grep -q '"success":true' "$stage/build-state.json" || { echo "swap: $stage/build-state.json no indica éxito" >&2; exit 1; }
mkdir -p "$dest"
for name in boot.wasm pulse-map.json build-state.json; do
  [ -f "$stage/$name" ] || continue
  cp "$stage/$name" "$dest/.$name.swap"
  mv -f "$dest/.$name.swap" "$dest/$name"
done
echo "swap: $stage -> $dest ($(stat -c %s "$dest/boot.wasm") B, $(date +%T))"
bash scripts/sync_web.sh
