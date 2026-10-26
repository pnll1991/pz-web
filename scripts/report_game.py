#!/usr/bin/env python3
"""Group actual TeaVM diagnostics by missing API, without declaring compatibility."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--reports', type=Path, default=ROOT / 'port/target/reports/game-main-minimal')
args = parser.parse_args()
report = json.loads((args.reports / 'build-report.json').read_text())
if report.get('status') in ('running', 'compiler-failed'):
    raise SystemExit(f"No complete dependency analysis: {report['status']}; missing APIs cannot be counted")
lines = (args.reports / 'teavm-diagnostics.txt').read_text().splitlines()
missing, other = defaultdict(set), []
for line in lines:
    match = re.match(r'\w+: (.*?): ((?:Class|Method|Field) .+ was not found)$', line)
    if match: missing[match[2]].add(match[1])
    else: other.append(line)
result = {'build':report, 'missing_api_count':len(missing), 'diagnostic_count':len(lines),
          'missing_apis':[{'api':name, 'callers':sorted(callers)} for name, callers in sorted(missing.items())],
          'other_diagnostics':other}
(args.reports / 'missing-apis.json').write_text(json.dumps(result, indent=2) + '\n')
table = ['# APIs pendientes de la compilación real', '',
         f"Modo: `{report['mode']}`; reflexión: `{report.get('reflection', 'no completada')}`. Compilación exitosa: {report['success']}.",
         f'{len(missing)} APIs distintas, {len(lines)} diagnósticos.',
         ('Manifiesto Lua habilitado. Alcance de compilación; no es evidencia de ejecución del juego.'
          if report.get('reflection') == 'lua' else 'El análisis mínimo no cubre toda la reflexión Lua.'), '',
         '| API ausente | Lugares que la usan |', '|---|---:|']
table += [f'| `{name}` | {len(callers)} |' for name, callers in sorted(missing.items())]
table += ['', '## Otros diagnósticos', ''] + ['- ' + x for x in other]
(args.reports / 'missing-apis.md').write_text('\n'.join(table) + '\n')
print(f'{len(missing)} missing APIs, {len(lines)} diagnostics: {args.reports / "missing-apis.md"}')
