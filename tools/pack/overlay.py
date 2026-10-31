#!/usr/bin/env python3
"""Round 79: re-point pack index entries to freshly staged files without rebuilding every pack.

Every index entry under the given prefixes whose path (case-insensitively) exists in the staging
tree is moved into one new content-addressed blob; all other entries and blobs stay untouched.
The previous index is kept as index.json.prev. Output stays in the git-ignored packs directory.
Round 80 (front H): --add also adds staged files under the prefixes that the index does not have yet
(e.g. the player locomotion clips from stage_txt.py --bob-anims locomotion). Identical contents share
one range of the new blob (packs.mjs/vfs.mjs count such aliases once). The index is written to a
temporary file and renamed, and the result is checked against the browser VFS budget first.
  python3 tools/pack/overlay.py --packs build/real-browser/packs --stage build/real-browser/stage \
      --prefix /media/models/skinned/ --prefix /media/anims/ --prefix /media/anims_X/ --prefix /media/anims_x/
"""
import argparse, hashlib, json, shutil
from pathlib import Path


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--packs', type=Path, required=True)
    ap.add_argument('--stage', type=Path, required=True)
    ap.add_argument('--prefix', action='append', required=True)
    ap.add_argument('--add', action='store_true', help='also add staged files missing from the index')
    ap.add_argument('--max-bytes', type=int, default=512 * 1024 * 1024, help='VFS budget (vfs.mjs maxBytes)')
    a = ap.parse_args()
    index_path = a.packs / 'index.json'
    index = json.loads(index_path.read_text())
    staged = {}
    for f in (a.stage / 'media').rglob('*'):
        if f.is_file():
            staged['/' + f.relative_to(a.stage).as_posix().lower()] = f
    blob, moved, ranges = bytearray(), [], {}

    def place(data):
        sha = hashlib.sha256(data).hexdigest()
        if sha not in ranges:
            ranges[sha] = len(blob)
            blob.extend(data)
        return dict(offset=ranges[sha], length=len(data), sha256=sha)

    known = set()
    for entry in index['files']:
        known.add(entry['path'])
        if not any(entry['path'].startswith(p) for p in a.prefix):
            continue
        src = staged.get(entry['path'].lower())
        if src is None:
            continue
        entry.update(place(src.read_bytes()))
        moved.append(entry)
    added = []
    if a.add:
        # Real-case paths from the staging tree (the game lists media/anims_X/Bob case-sensitively).
        for f in sorted((a.stage / 'media').rglob('*')):
            path = '/' + f.relative_to(a.stage).as_posix()
            if f.is_file() and path not in known and any(path.startswith(p) for p in a.prefix):
                entry = dict(path=path, **place(f.read_bytes()))
                index['files'].append(entry)
                moved.append(entry); added.append(entry)
        index['files'].sort(key=lambda e: e['path'])
    if not moved:
        raise SystemExit('No index entry matched the staging tree')
    sha = hashlib.sha256(blob).hexdigest()
    name = f'{sha}.bin'
    (a.packs / name).write_bytes(blob)
    for entry in moved:
        entry['pack'] = name
    index['packs'].append({'name': name, 'sha256': sha, 'bytes': len(blob)})
    used = {e['pack'] for e in index['files']}
    index['packs'] = [p for p in index['packs'] if p['name'] in used]
    resident = sum(p['bytes'] for p in index['packs'])
    unique = sum(e['length'] for e in {(e['pack'], e['offset'], e['length']): e for e in index['files']}.values())
    if resident > a.max_bytes or unique > a.max_bytes:
        (a.packs / name).unlink()
        raise SystemExit(f'Over the VFS budget: packs {resident} B, unique files {unique} B > {a.max_bytes} B')
    shutil.copy2(index_path, a.packs / 'index.json.prev')
    tmp = index_path.with_name('index.json.tmp')
    tmp.write_text(json.dumps(index, separators=(',', ':')))
    tmp.replace(index_path)
    print(f'overlay {name}: {len(moved)} entries ({len(added)} new), {len(blob)} bytes; '
          f'packs {resident} B, unique files {unique} B of {a.max_bytes} B')


if __name__ == '__main__':
    main()
