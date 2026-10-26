#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$PZ_WEB_ROOT"
bash scripts/build.sh
python3 -m unittest discover -s tests/python -v
npm test
npm run test:browser
