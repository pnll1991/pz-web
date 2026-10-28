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
fi
