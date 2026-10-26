#!/usr/bin/env python3
"""Copy the pinned open-source parser to local build output, never game files."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
source = root / 'node_modules/@shaderfrog/glsl-parser'
target = root / 'port/target/web/vendor/glsl-parser'
if not (source / 'index.js').is_file():
    raise SystemExit('Missing GLSL parser: source scripts/env.sh && npm ci --ignore-scripts')
for file in sorted(source.rglob('*.js')):
    destination = target / file.relative_to(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(file, destination)
