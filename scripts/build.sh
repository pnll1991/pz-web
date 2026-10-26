#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
for tool in java javac mvn node; do
  command -v "$tool" >/dev/null || { echo "Missing $tool: run python3 scripts/bootstrap.py" >&2; exit 1; }
done
cd "$PZ_WEB_ROOT"
python3 scripts/prepare_build.py "$@"
python3 scripts/prepare_teavm.py
mvn -B -ntp -Dmaven.repo.local="$PZ_WEB_ROOT/.toolchain/m2" -f port/pom.xml package
mkdir -p port/target/web/runtime
cp port/web/* port/target/web/
cp port/runtime/*.mjs port/target/web/runtime/
python3 scripts/copy_web_vendor.py
