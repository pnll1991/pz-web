#!/usr/bin/env python3
"""Round 79: re-point pack index entries to freshly staged files without rebuilding every pack.

Every index entry under the given prefixes whose path (case-insensitively) exists in the staging
tree is moved into one new content-addressed blob; all other entries and blobs stay untouched.
The previous index is kept as index.json.prev. Output stays in the git-ignored packs directory.
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
    a = ap.parse_args()
    index_path = a.packs / 'index.json'
    index = json.loads(index_path.read_text())
    staged = {}
    for f in (a.stage / 'media').rglob('*'):
        if f.is_file():
            staged['/' + f.relative_to(a.stage).as_posix().lower()] = f
    blob, moved = bytearray(), []
    for entry in index['files']:
        if not any(entry['path'].startswith(p) for p in a.prefix):
            continue
        src = staged.get(entry['path'].lower())
        if src is None:
            continue
        data = src.read_bytes()
        entry.update(offset=len(blob), length=len(data), sha256=hashlib.sha256(data).hexdigest())
        blob += data
        moved.append(entry)
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
    shutil.copy2(index_path, a.packs / 'index.json.prev')
    index_path.write_text(json.dumps(index, separators=(',', ':')))
    print(f'overlay {name}: {len(moved)} entries, {len(blob)} bytes')


if __name__ == '__main__':
    main()
