#!/usr/bin/env bash
# Source this file; do not alter the user's global environment or shell config.
PZ_WEB_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
export PZ_WEB_ROOT
export JAVA_HOME="$PZ_WEB_ROOT/.toolchain/jdk"
export PATH="$PZ_WEB_ROOT/.toolchain/node/bin:$JAVA_HOME/bin:$PZ_WEB_ROOT/.toolchain/maven/bin:$PZ_WEB_ROOT/.toolchain/cmake/bin:$PZ_WEB_ROOT/.toolchain/ninja:$PZ_WEB_ROOT/.toolchain/apitrace/bin:$PZ_WEB_ROOT/.toolchain/renderdoc/bin:$PATH"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx20g}"
export npm_config_cache="$PZ_WEB_ROOT/.toolchain/npm-cache"
export PLAYWRIGHT_BROWSERS_PATH="$PZ_WEB_ROOT/.toolchain/playwright"
export EM_CACHE="$PZ_WEB_ROOT/.toolchain/emscripten-cache"
export EMSDK_PYTHON="$(command -v python3)"
export EMSDK_NODE="$PZ_WEB_ROOT/.toolchain/node/bin/node"
