#!/usr/bin/env bash
# Ronda 80: parte web del build (la misma que hace scripts/build.sh después de Maven), sin Maven ni TeaVM.
# Para cambios sólo en port/web/* o port/runtime/*.mjs: copia a port/target/web y listo; un reload de la
# página los toma (el dev server lee cada archivo por petición, Cache-Control: no-cache, ETag tamaño+mtime).
# Sólo copia archivos cuyo contenido cambió (los demás conservan mtime -> ETag igual -> 304).
# No toca boot.wasm, boot.wasm-runtime.js, pulse-map.json ni build-state.json: eso sale de Java/TeaVM.
#   bash scripts/sync_web.sh          (scripts/build.sh lo llama al final)
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
started=$(date +%s%N)
out=port/target/web
mkdir -p "$out/runtime"
changed=0
sync_file() { # origen destino
  if ! cmp -s "$1" "$2"; then cp "$1" "$2"; echo "  sync $2"; changed=$((changed + 1)); fi
}
for f in port/web/*; do if [ -f "$f" ]; then sync_file "$f" "$out/$(basename -- "$f")"; fi; done
for f in port/runtime/*.mjs; do sync_file "$f" "$out/runtime/$(basename -- "$f")"; done
python3 scripts/copy_web_vendor.py
python3 scripts/prepare_sqlite.py
[ -f "$out/game/boot.wasm" ] || echo "aviso: falta $out/game/boot.wasm (hace falta scripts/build.sh --with-game una vez)" >&2
echo "sync_web: $changed archivo(s) actualizados en $(( ($(date +%s%N) - started) / 1000000 )) ms"
