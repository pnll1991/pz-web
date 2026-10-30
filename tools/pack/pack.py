#!/usr/bin/env python3
"""Package an explicit startup resource list from PZ_HOME; never guess game assets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'inventory'))
from inventory import resolve_game_home


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pz-home', default=os.environ.get('PZ_HOME'))
    parser.add_argument('--files', required=True, type=Path, help='UTF-8 list of media/relative paths, one per line')
    parser.add_argument('--stage', type=Path, default=None,
                        help='Optional staging root (its media/ tree supplies names PZ_HOME does not '
                             'have, e.g. our own .txt conversions of the .x animation files).')
    parser.add_argument('--out', type=Path, default=Path(__file__).resolve().parents[2] / 'packs')
    parser.add_argument('--max-bytes', type=int, default=32 * 1024 * 1024)
    args = parser.parse_args()
    if not args.pz_home: parser.error('PZ_HOME is required')
    requested = Path(args.pz_home).expanduser().resolve()
    root = resolve_game_home(requested)
    stage = None if args.stage is None else args.stage.expanduser().resolve()
    if stage is not None:
        if not (stage / 'media').is_dir():
            parser.error(f'Staging root has no media directory: {stage}')
        if stage.is_relative_to(root):
            parser.error('Staging root must be outside PZ_HOME (PZ_HOME stays read-only)')
    if args.out.resolve().is_relative_to(requested): parser.error('Output must be outside read-only PZ_HOME')
    if args.max_bytes < 1: parser.error('--max-bytes must be positive')
    def source_of(name):
        """Real game file when it exists, otherwise our staged file (never a placeholder)."""
        for base in (root, stage) if stage is not None else (root,):
            path = base / name
            if path.resolve().is_relative_to(base / 'media') and path.is_file():
                return path
        return None
    paths = sorted({line.strip() for line in args.files.read_text().splitlines() if line.strip() and not line.startswith('#')})
    if not paths: parser.error('The startup file list is empty')
    for name in paths:
        if '\\' in name or not name.startswith('media/') or any(p in ('.','..') for p in name.split('/')):
            parser.error(f'Invalid resource path: {name}')
        if source_of(name) is None:
            parser.error(f'Invalid resource path: {name}')
    args.out.mkdir(parents=True, exist_ok=True)
    result = {'schema_version':1, 'packs':[], 'files':[]}
    with tempfile.TemporaryDirectory(prefix='.pack-', dir=args.out) as tmp:
        temporary = Path(tmp) / 'blob'
        pack_entries, offset, hasher = [], 0, hashlib.sha256()
        stream = temporary.open('wb')
        def finish():
            nonlocal stream, pack_entries, offset, hasher
            stream.close()
            if not pack_entries: return
            checksum = hasher.hexdigest(); name = checksum + '.bin'
            temporary.replace(args.out / name)
            result['packs'].append({'name':name,'bytes':offset,'sha256':checksum})
            for entry in pack_entries: entry['pack'] = name
            result['files'].extend(pack_entries)
            pack_entries, offset, hasher = [], 0, hashlib.sha256()
            stream = temporary.open('wb')
        try:
            for name in paths:
                path = source_of(name)
                size = path.stat().st_size
                if offset and offset + size > args.max_bytes: finish()
                entry = {'path':'/' + name,'offset':offset,'length':0}
                file_hash = hashlib.sha256()
                with path.open('rb') as source:
                    while chunk := source.read(1024 * 1024):
                        stream.write(chunk); hasher.update(chunk); file_hash.update(chunk)
                        entry['length'] += len(chunk); offset += len(chunk)
                entry['sha256'] = file_hash.hexdigest()
                pack_entries.append(entry)
            finish()
        finally: stream.close()
        index = Path(tmp) / 'index.json'
        index.write_text(json.dumps(result, indent=2) + '\n')
        index.replace(args.out / 'index.json')  # Publish index last; old blobs remain valid.
    print(f"Packed {len(result['files'])} files into {len(result['packs'])} immutable blobs: {args.out}")


if __name__ == '__main__':
    try: main()
    except (OSError, ValueError) as error: sys.exit(f'pack: {error}')
