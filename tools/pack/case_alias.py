#!/usr/bin/env python3
"""Add lowercase read-only aliases for mounted mixed-case prefixes.

Why (round 59, diag163.log): ModelManager::create dies with the Java NPE
"Required Animation Asset not found: bob/bob_idle". The lookup name comes from
FileTask_AbstractLoadModel.checkExtensionType (javap -p -c): the constructor prefixes are the
lowercase literals "media/models"/"media/models_x" (FileTask_LoadMesh) and
"media/anims"/"media/anims_x" (FileTask_LoadAnimation), the name is lowercased
(String.toLowerCase(Locale.ENGLISH)), and the candidate is "<prefix>/<name>{.fbx,.glb,.x}"
resolved through ZomboidFileSystem.getString and then new File(...).exists().
The pinned copy stores media/models_X and media/anims_X, so a case-sensitive browser VFS never
matches those lowercase lookups. An alias entry points at the same blob offset (no duplicate blob
bytes on disk) but does cost the mounted bytes, so only the prefixes given on the command line are
aliased and the total is checked against the VFS budget.
"""
import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from budget import VFS_MAX_BYTES


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--index', required=True, type=Path)
    parser.add_argument('--prefix', action='append', required=True,
                        help='mounted prefix (media/...) whose files get a lowercase alias')
    args = parser.parse_args()
    index = json.loads(args.index.read_text())
    if index.get('schema_version') != 1 or not isinstance(index.get('files'), list):
        raise SystemExit('Unsupported pack index')
    prefixes = []
    for raw in args.prefix:
        prefix = '/' + raw if not raw.startswith('/') else raw
        if not prefix.endswith('/'):
            prefix += '/'
        prefixes.append(prefix)
    known = {f['path'] for f in index['files']}
    packs = {p['name']: p for p in index['packs']}
    added = []
    for source in index['files']:
        if not any(source['path'].startswith(prefix) for prefix in prefixes):
            continue
        if 'alias_of' in source:
            continue
        target = source['path'].lower()
        if target == source['path'] or target in known:
            continue
        entry = dict(source)
        entry['path'] = target
        entry['alias_of'] = source['path']
        added.append(entry)
        known.add(target)
    blob_dir = args.index.parent
    for entry in added:
        pack = packs[entry['pack']]
        with (blob_dir / pack['name']).open('rb') as handle:
            handle.seek(entry['offset'])
            content = handle.read(entry['length'])
        if len(content) != entry['length'] or hashlib.sha256(content).hexdigest() != entry['sha256']:
            raise SystemExit(f'Case alias target does not match blob bytes: {entry["path"]}')
    index['files'].extend(added)
    index['files'].sort(key=lambda f: f['path'])
    total = sum(f['length'] for f in index['files'])
    if total > VFS_MAX_BYTES:
        raise SystemExit(f'Case aliases exceed the VFS memory budget ({total} B > {VFS_MAX_BYTES} B)')
    with tempfile.NamedTemporaryFile('w', dir=blob_dir, delete=False) as tmp:
        tmp.write(json.dumps(index, indent=2) + '\n')
        os.replace(tmp.name, args.index)
    print(f'Added {len(added)} lowercase alias entries; mounted bytes now {total} B '
          f'of {VFS_MAX_BYTES} B')


if __name__ == '__main__':
    try: main()
    except OSError as error: sys.exit(f'case_alias: {error}')
