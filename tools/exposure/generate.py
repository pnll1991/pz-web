#!/usr/bin/env python3
"""Deterministic reflection metadata from literal Lua registration call sites."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/inventory'))
from classfile import ClassReader


def generate(inventory, output):
    data = json.loads(Path(inventory).read_text())
    home = Path(data['pz_home'])
    game = next(x for x in data['jars'] if Path(x['path']).name == 'projectzomboid.jar')
    jar_path = home / game['path']
    with jar_path.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != game['sha256']:
            raise ValueError('Game jar changed since inventory')
    result = subprocess.run(['java', '-Xmx512m', str(ROOT / 'tools/inventory/BytecodeMetadata.java'),
                             str(jar_path), 'zombie/Lua/LuaManager'], check=True, capture_output=True, text=True)
    registrations, unresolved, helpers, versions = [], [], [], []
    for line in result.stdout.splitlines():
        kind, *values = line.split('\t')
        if kind == 'V':
            if values[0] == 'zombie.core.Core#<clinit>()V' and values[1] == '(IILjava/lang/String;)V':
                versions.append({'version': '.'.join(values[2:4]) + values[4], 'source': values[0], 'constructor': values[1]})
        elif kind in ('R', 'U', 'H'):
            record = dict(zip(('class', 'caller', 'registration'), values))
            {'R': registrations, 'U': unresolved, 'H': helpers}[kind].append(record)
        else: raise ValueError(f'Unexpected metadata record: {kind}')
    roots = {r['class'] for r in registrations}
    classes, external = {}, set()
    with zipfile.ZipFile(jar_path) as jar:
        pending = sorted(roots)
        while pending:
            name = pending.pop()
            if name in classes or name in external: continue
            entry = name.replace('.', '/') + '.class'
            if entry not in jar.namelist():
                external.add(name)
                continue
            cls = ClassReader(jar.read(entry)).parse()
            classes[name] = {'name': name, 'source': game['path'], 'registered': name in roots,
                'super': cls['super'], 'interfaces': cls['interfaces'],
                'public_methods': [{'name': m['name'], 'descriptor': m['descriptor'], 'access': m['access']}
                                   for m in cls['methods'] if m['access'] & 1],
                'public_fields': [{'name': f['name'], 'descriptor': f['descriptor'], 'access': f['access']}
                                  for f in cls['fields'] if f['access'] & 1]}
            pending.extend(cls['interfaces'])
            if cls['super']: pending.append(cls['super'])
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    manifest = {'schema_version': 1, 'jar_sha256': game['sha256'], 'game_versions': versions,
        'registrations': sorted(registrations, key=lambda x: (x['class'], x['caller'])),
        'classes': [classes[n] for n in sorted(classes)], 'external_classes': sorted(external),
        'unresolved': sorted(unresolved, key=lambda x: x['caller']), 'helpers': sorted(helpers, key=lambda x: x['class']),
        'scope': 'Literal registrations, global objects constructed inline, and inheritance. Does not prove LuaManager.init execution or dynamic recursive exposure completeness.'}
    (output / 'exposure.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=True) + '\n')
    (output / 'reflection-lua.txt').write_text('\n'.join(sorted(set(classes) | external)) + '\n')
    print(f'Lua exposure: {len(roots)} registered classes, {len(classes)} jar classes including inheritance, '
          f'{len(external)} external classes, {len(unresolved)} unresolved registrations')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path, default=ROOT / 'inventory/inventory.json')
    parser.add_argument('--out', type=Path, default=ROOT / 'port/target/config')
    args = parser.parse_args()
    try: generate(args.inventory, args.out)
    except subprocess.CalledProcessError as error:
        sys.exit(f'exposure: metadata reader failed:\n{error.stderr}')
    except (OSError, ValueError, StopIteration) as error:
        sys.exit(f'exposure: {error}')
