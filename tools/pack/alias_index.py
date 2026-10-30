#!/usr/bin/env python3
"""Add read-only alias entries to a pack index so boot fallback paths resolve.

ZomboidFileSystem#getString resolves a relative path through the mod/file map, which is built
from the jar/code-source location on desktop. That location does not exist in a tab, so the map
is empty and TextManager#getFontFilePath falls back to "media/fonts/<file>" and then
"media/<file>". Those are the very same bytes as media/fonts/EN/<size>/<file>. An alias entry
points at the same blob offset, so the immutable blob stays unduplicated on disk; the VFS mounts
one slice per entry, which costs the extra bytes in memory (a few MB here) and is checked
against the blob and the memory budget below.
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

FALLBACK_DIRS = ('', 'fonts/')
FALLBACK_PREFIXES = tuple('/media/' + d for d in FALLBACK_DIRS)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--index', required=True, type=Path)
    parser.add_argument('--from-dir', default='media/fonts/EN/1x/',
                        help='mounted prefix whose files get the boot fallback aliases')
    args = parser.parse_args()
    index = json.loads(args.index.read_text())
    if index.get('schema_version') != 1 or not isinstance(index.get('files'), list):
        raise SystemExit('Unsupported pack index')
    prefix = '/' + args.from_dir if args.from_dir.startswith('/') else '/' + args.from_dir
    if not prefix.endswith('/'):
        prefix += '/'
    sources = [f for f in index['files'] if f['path'].startswith(prefix)
               and (f['path'].endswith('.fnt') or f['path'].endswith('.png'))]
    if not sources:
        raise SystemExit(f'No font files under {prefix}')
    known = {f['path'] for f in index['files']}
    packs = {p['name']: p for p in index['packs']}
    added = []
    for source in sources:
        name = source['path'][len(prefix):]
        for base in FALLBACK_PREFIXES:
            target = base + name
            if target in known or target.startswith(prefix):
                continue
            entry = dict(source)
            entry['path'] = target
            entry['alias_of'] = source['path']
            added.append(entry)
            known.add(target)
    # Every alias must still match the blob bytes it points at.
    blob_dir = args.index.parent
    for entry in added:
        pack = packs[entry['pack']]
        with (blob_dir / pack['name']).open('rb') as handle:
            handle.seek(entry['offset'])
            content = handle.read(entry['length'])
        if len(content) != entry['length'] or hashlib.sha256(content).hexdigest() != entry['sha256']:
            raise SystemExit(f'Alias target does not match blob bytes: {entry["path"]}')
    index['files'].extend(added)
    index['files'].sort(key=lambda f: f['path'])
    total = sum(f['length'] for f in index['files'])
    if total > VFS_MAX_BYTES:
        raise SystemExit('Alias index exceeds the VFS memory budget')
    with tempfile.NamedTemporaryFile('w', dir=blob_dir, delete=False) as tmp:
        tmp.write(json.dumps(index, indent=2) + '\n')
        os.replace(tmp.name, args.index)
    print(f'Added {len(added)} alias entries (sources={len(sources)}, mounted bytes reported '
          f'{total} B; blob bytes stay {sum(p["bytes"] for p in index["packs"])} B)')


if __name__ == '__main__':
    main()
