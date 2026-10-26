#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
mkdir -p build/toolchain-check .toolchain/user-home
cat > build/toolchain-check/probe.c <<'C'
int add(int a, int b) { return a + b; }
C
"$PZ_WEB_ROOT/.toolchain/emsdk/upstream/emscripten/emcc" build/toolchain-check/probe.c --no-entry \
  -sMODULARIZE=1 -sEXPORT_ES6=1 -sENVIRONMENT=node -sEXPORTED_FUNCTIONS='["_add"]' \
  -o build/toolchain-check/probe.mjs
node --input-type=module <<'JS'
import create from './build/toolchain-check/probe.mjs';
const module = await create();
if (module._add(20,22) !== 42) throw new Error('Emscripten result mismatch');
console.log('Emscripten: compiled C, loaded Wasm, add(20,22) = 42');
JS
gcc -shared -fPIC -g build/toolchain-check/probe.c -o build/toolchain-check/libprobe.so
JAVA_TOOL_OPTIONS="-Duser.home=$PZ_WEB_ROOT/.toolchain/user-home -Djava.awt.headless=true" \
  .toolchain/ghidra/support/analyzeHeadless "$PZ_WEB_ROOT/build/toolchain-check" synthetic-probe \
  -import "$PZ_WEB_ROOT/build/toolchain-check/libprobe.so" -deleteProject \
  -log "$PZ_WEB_ROOT/.toolchain/ghidra-analysis.log"
java -jar .toolchain/vineflower.jar --silent port/target/classes/port/demo/ReflectionProbe.class build/toolchain-check/decompiled-fixture
test -f build/toolchain-check/decompiled-fixture/ReflectionProbe.java
echo 'Ghidra and Vineflower: analyzed only our own synthetic fixtures'
