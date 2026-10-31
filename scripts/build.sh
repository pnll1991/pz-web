#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
for tool in java javac mvn node; do
  command -v "$tool" >/dev/null || { echo "Missing $tool: run python3 scripts/bootstrap.py" >&2; exit 1; }
done
cd "$PZ_WEB_ROOT"
# Ronda 80: --dev = iteración rápida con las MISMAS opciones de TeaVM (SIMPLE, strict, mismos transformers y
# gates). Sólo salta trabajo que el navegador no usa o cuyas entradas no cambiaron (ver docs/notes-from-code.md
# "Ronda 80 — iteración"): exposición Lua cacheada por hash, fuentes generadas sin tocar mtime si no cambian
# (javac incremental), mvn -o, sin tests JUnit ni jar (fase prepare-package). Además compila en
# port/target/web-staging y sólo al final, con los gates aprobados, reemplaza boot.wasm/pulse-map.json/
# build-state.json servidos por rename atómico (scripts/swap_staged.sh): el boot.wasm servido no desaparece
# durante el build. PZ_SWAP_LOCK=<archivo> hace el swap en segundo plano bajo `flock` de ese archivo (p. ej. el
# chrome.lock de la ronda), sin retener el build;
# PZ_NO_SWAP=1 deja el resultado en staging (swap manual con scripts/swap_staged.sh). Sin --dev: igual que antes.
DEV=0; GAME=0; ARGS=()
for arg in "$@"; do
  case "$arg" in --dev) DEV=1 ;; --with-game) GAME=1; ARGS+=("$arg") ;; *) ARGS+=("$arg") ;; esac
done
step_t=$(date +%s); build_t=$step_t
step() { local now; now=$(date +%s); echo "[build.sh] $1: $((now - step_t)) s"; step_t=$now; }
if [ "$GAME" = 1 ]; then SUB=game; else SUB=.; fi
if [ "$DEV" = 1 ]; then
  STAGE=port/target/web-staging/$SUB
  python3 scripts/prepare_build.py "${ARGS[@]}" --cache-exposure --output-dir "$STAGE"; step prepare_build
  # Generadores que reescriben siempre: conservar el mtime de los archivos cuyo contenido no cambió,
  # para que maven-compiler no recompile el módulo entero por fuentes "nuevas" idénticas.
  gen=port/target/generated-sources; prev=port/target/cache/generated-prev
  rm -rf "$prev"; mkdir -p "$gen" "$(dirname "$prev")"; cp -a "$gen" "$prev"
  python3 scripts/prepare_teavm.py
  java tools/compatibility/JdkContracts.java port/target/generated-sources/contracts
  (cd "$gen" && find . -type f) | while read -r f; do
    if [ -f "$prev/$f" ] && cmp -s "$gen/$f" "$prev/$f"; then touch -r "$prev/$f" "$gen/$f"; fi
  done
  step prepare_teavm+contracts
  # pz.teavm.filterCache: memo de DependencyAnalyzer.filterType (scripts/prepare_teavm.py patch_dependency_filter).
  mvn -B -ntp -o -Dmaven.repo.local="$PZ_WEB_ROOT/.toolchain/m2" -Dmaven.test.skip=true -Dpz.teavm.filterCache=true \
    -f port/pom.xml prepare-package
  step maven
  WASM=$STAGE/boot.wasm
else
  python3 scripts/prepare_build.py "$@"; step prepare_build
  python3 scripts/prepare_teavm.py
  java tools/compatibility/JdkContracts.java port/target/generated-sources/contracts
  step prepare_teavm+contracts
  mvn -B -ntp -Dmaven.repo.local="$PZ_WEB_ROOT/.toolchain/m2" -f port/pom.xml package
  step maven
  bash scripts/sync_web.sh
  step sync_web
  WASM=port/target/web/game/boot.wasm
fi
# Gate (docs/notes-from-code.md): Thread.currentThread/setCurrentThread deben ser sincrónicos.
# Si contienen ops de Fiber, Fiber.suspend hace popInt sobre un int-stack que nunca pusheó.
if [ -f "$WASM" ] && { [ "$DEV" = 0 ] || [ "$GAME" = 1 ]; }; then
  python3 scripts/wasmfn.py "$WASM" \
    'java.lang.Thread::currentThread' 'java.lang.Thread::setCurrentThread' | tee port/target/reports/thread-fiber-gate.txt
  if grep -q 'fiberops: True' port/target/reports/thread-fiber-gate.txt; then
    echo "GATE FAIL: Thread::currentThread/setCurrentThread son corutinas (Fiber ops). Ver docs/notes-from-code.md" >&2
    exit 1
  fi
  # Gate (ronda 50): el patch de zombie.util.StringUtils debe haber reemplazado el regex.
  # TeaVM recursa (ReluctantGroupQuantifierSet) por expansión y revienta la pila de JS en
  # CraftRecipe::LoadIO -> ScriptManager::Load. Un transformer que no matchea no deja nada.
  python3 scripts/wasmfn.py "$WASM" \
    'zombie.util.StringUtils::containsWhitespace' 'zombie.util.StringUtils::removeWhitespace' \
    | tee port/target/reports/stringutils-gate.txt
  if grep -q 'java.lang.String::matches\|java.lang.String::replaceAll' port/target/reports/stringutils-gate.txt \
     || ! grep -q 'StringUtilsBridge' port/target/reports/stringutils-gate.txt; then
    echo "GATE FAIL: el patch de StringUtils no se aplicó (sigue usando regex). Ver AGENT_LEARNINGS.md ronda 50" >&2
    exit 1
  fi

  # Gate (ronda 50): los assert del juego deben estar desactivados, como en JVM sin -ea.
  # TeaVM responde desiredAssertionStatus() con true, así que javac's $assertionsDisabled queda
  # false y un assert de XuiSkin$EntityUiStyle mató el MainThread con AssertionError (diag148).
  python3 scripts/wasmfn.py "$WASM" \
    'zombie.scripting.ui.XuiSkin$EntityUiStyle::<clinit>' 'zombie.scripting.ui.XuiSkin::<clinit>' \
    'zombie.core.textures.Texture::<clinit>' 'zombie.core.SpriteRenderer$RingBuffer$StateRun::<clinit>' \
    | tee port/target/reports/assertions-gate.txt
  if grep -q 'desiredAssertionStatus' port/target/reports/assertions-gate.txt; then
    echo "GATE FAIL: AssertionDisabledCompatibility no se aplicó (queda desiredAssertionStatus en <clinit>)" >&2
    exit 1
  fi
fi
step gates
if [ "$DEV" = 1 ]; then
  if [ "${PZ_NO_SWAP:-0}" = 1 ]; then
    echo "[build.sh] PZ_NO_SWAP=1: resultado en $STAGE; publicar con: bash scripts/swap_staged.sh $STAGE port/target/web/$SUB"
  elif [ -n "${PZ_SWAP_LOCK:-}" ]; then
    # No retener build.lock mientras se espera el lock del swap: copia instantánea del staging y un proceso
    # desacoplado que publica cuando obtiene el lock. Un build posterior borra las copias viejas (su swap falla).
    rm -rf port/target/web-staging/ready-*
    snap=port/target/web-staging/ready-$(date +%s); cp -a "$STAGE" "$snap"
    # Cerrar los fd heredados (>2): el del `flock build.lock` que lanzó este build mantendría el lock tomado.
    setsid nohup bash -c 'for fd in /proc/$$/fd/*; do n=${fd##*/}; [ "$n" -gt 2 ] && eval "exec $n>&-" 2>/dev/null; done
      exec flock "$0" bash scripts/swap_staged.sh "$1" "$2"' "$PZ_SWAP_LOCK" "$snap" "port/target/web/$SUB" \
      >> port/target/web-staging/swap.log 2>&1 < /dev/null &
    echo "[build.sh] swap en espera de $PZ_SWAP_LOCK (pid $!, log port/target/web-staging/swap.log)"
  else
    bash scripts/swap_staged.sh "$STAGE" "port/target/web/$SUB"
  fi
  step swap
fi
echo "[build.sh] total: $(( $(date +%s) - build_t )) s (dev=$DEV)"
