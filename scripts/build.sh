#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
for tool in java javac mvn node; do
  command -v "$tool" >/dev/null || { echo "Missing $tool: run python3 scripts/bootstrap.py" >&2; exit 1; }
done
cd "$PZ_WEB_ROOT"
python3 scripts/prepare_build.py "$@"
python3 scripts/prepare_teavm.py
java tools/compatibility/JdkContracts.java port/target/generated-sources/contracts
mvn -B -ntp -Dmaven.repo.local="$PZ_WEB_ROOT/.toolchain/m2" -f port/pom.xml package
mkdir -p port/target/web/runtime
cp port/web/* port/target/web/
cp port/runtime/*.mjs port/target/web/runtime/
python3 scripts/copy_web_vendor.py
python3 scripts/prepare_sqlite.py
# Gate (docs/notes-from-code.md): Thread.currentThread/setCurrentThread deben ser sincrónicos.
# Si contienen ops de Fiber, Fiber.suspend hace popInt sobre un int-stack que nunca pusheó.
if [ -f port/target/web/game/boot.wasm ]; then
  python3 scripts/wasmfn.py port/target/web/game/boot.wasm \
    'java.lang.Thread::currentThread' 'java.lang.Thread::setCurrentThread' | tee port/target/reports/thread-fiber-gate.txt
  if grep -q 'fiberops: True' port/target/reports/thread-fiber-gate.txt; then
    echo "GATE FAIL: Thread::currentThread/setCurrentThread son corutinas (Fiber ops). Ver docs/notes-from-code.md" >&2
    exit 1
  fi
  # Gate (ronda 50): el patch de zombie.util.StringUtils debe haber reemplazado el regex.
  # TeaVM recursa (ReluctantGroupQuantifierSet) por expansión y revienta la pila de JS en
  # CraftRecipe::LoadIO -> ScriptManager::Load. Un transformer que no matchea no deja nada.
  python3 scripts/wasmfn.py port/target/web/game/boot.wasm \
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
  python3 scripts/wasmfn.py port/target/web/game/boot.wasm \
    'zombie.scripting.ui.XuiSkin$EntityUiStyle::<clinit>' 'zombie.scripting.ui.XuiSkin::<clinit>' \
    'zombie.core.textures.Texture::<clinit>' 'zombie.core.SpriteRenderer$RingBuffer$StateRun::<clinit>' \
    | tee port/target/reports/assertions-gate.txt
  if grep -q 'desiredAssertionStatus' port/target/reports/assertions-gate.txt; then
    echo "GATE FAIL: AssertionDisabledCompatibility no se aplicó (queda desiredAssertionStatus en <clinit>)" >&2
    exit 1
  fi
fi
