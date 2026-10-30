#!/usr/bin/env python3
"""Mount the port's own /Zomboid/options.ini into a pack index as a read-only entry.

Why this file exists (build/real-browser/console-round51.txt): GameWindow.InitDisplay calls
Core.loadOptions(), which reads ZomboidFileSystem.getCacheDirSub("options.ini"). getCacheDir() is
System.getProperty("deployment.user.cachedir") (null here) else user.home, and port.shim.BrowserFileSystem
installs user.home="/", so the resolved path is "/Zomboid/options.ini". Without that file loadOptions()
falls into initOptionsINI() (Display.getAvailableDisplayModes + glfwGetMonitorWorkarea + copyPasteFolders)
and the option texture2x stays at the game's default true, so GameWindow.enter() mounts the eight 2x
texture packs (Tiles2x 321 MB, JumboTreesBigs2x 82 MB, Clock2x, ...) whose reads are the eight
FileNotFoundExceptions, and Texture.getSharedTexture("TileIndieStoneTentFrontLeft") ends null ->
"Rebuild Tiles.pack with \"1 Include This in .pack\"". The VFS budget (256 MB) can never hold Tiles2x,
so the honest answer is the game's own 1x path: this file sets texture2x=false.

Content rules verified from the pinned jar (zombie.config.ConfigFile.read): lines are `name=value`,
the version line is `version=N` because loadOptions sets versionString "version", `#` lines are
comments, values are `line.split("=")[1]` so no `=` may appear inside a value, and version>=8 keeps
values unmodified (Core.upgradeOptionValue). saveOptions() rewrites the same path, so the entry is
read-only in the VFS (VirtualFileSystem.mountReadOnly) and flush() never persists it.

This is the port's own text, never a copy of a game file.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from budget import VFS_MAX_BYTES

MOUNT_PATH = '/Zomboid/options.ini'
BUDGET = VFS_MAX_BYTES


VERSION = 8


def render(width, height, language, texture2x):
    lines = [f'version={VERSION}', f'width={width}', f'height={height}',
             f'language={language}', f'texture2x={str(texture2x).lower()}']
    # Round 52 correction: texture2x is written for completeness but the game ignores it.
    # javap -p -c zombie.core.Core shows getOptionTexture2x() returning iconst_1 unconditionally,
    # so Core.tileScale is always 2 and the 2x tile packs are always the ones the game asks for.
    return ('# Project Zomboid browser port: options the real game asks for.\n'
            '# texture2x is read but ignored: zombie.core.Core.getOptionTexture2x() is hardcoded\n'
            '# true in the pinned build, so the game always mounts the 2x tile packs.\n'
            + '\n'.join(lines) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--index', required=True, type=Path)
    parser.add_argument('--width', type=int, default=1280)
    parser.add_argument('--height', type=int, default=720)
    parser.add_argument('--language', default='EN')
    parser.add_argument('--texture2x', action='store_true',
                        help='write texture2x=true instead of the port default false')
    args = parser.parse_args()
    if not 1 <= args.width <= 16384 or not 1 <= args.height <= 16384:
        raise SystemExit('width/height must be inside the game option range 1..16384')
    if '=' in args.language or not args.language.isalnum():
        raise SystemExit('language must be a bare code without "=" (ConfigFile splits on "=")')
    index = json.loads(args.index.read_text())
    if index.get('schema_version') != 1 or not isinstance(index.get('files'), list) \
            or not isinstance(index.get('packs'), list):
        raise SystemExit('Unsupported pack index')
    if any(f['path'] == MOUNT_PATH for f in index['files']):
        raise SystemExit(f'{MOUNT_PATH} is already mounted; edit that blob instead of adding a second entry')

    content = render(args.width, args.height, args.language, args.texture2x).encode('ascii')
    checksum = hashlib.sha256(content).hexdigest()
    name = checksum + '.bin'
    if any(p['name'] == name for p in index['packs']):
        raise SystemExit(f'Blob {name} already exists in the index')
    blob_dir = args.index.parent
    total = sum(f['length'] for f in index['files']) + len(content)
    if total > BUDGET:
        raise SystemExit(f'Mounting {MOUNT_PATH} exceeds the VFS memory budget ({total} B > {BUDGET} B)')

    # Write the immutable blob first; the index is replaced last so it never points at a partial blob.
    blob = blob_dir / name
    with tempfile.NamedTemporaryFile('wb', dir=blob_dir, delete=False) as tmp:
        tmp.write(content)
        os.replace(tmp.name, blob)
    with blob.open('rb') as handle:
        stored = handle.read()
    if stored != content or hashlib.sha256(stored).hexdigest() != checksum:
        blob.unlink(missing_ok=True)
        raise SystemExit(f'Blob verification failed for {name}')

    index['packs'].append({'name': name, 'bytes': len(content), 'sha256': checksum})
    index['files'].append({'path': MOUNT_PATH, 'offset': 0, 'length': len(content),
                           'sha256': checksum, 'pack': name})
    index['files'].sort(key=lambda f: f['path'])
    with tempfile.NamedTemporaryFile('w', dir=blob_dir, delete=False) as tmp:
        tmp.write(json.dumps(index, indent=2) + '\n')
        os.replace(tmp.name, args.index)
    print(f'Mounted {MOUNT_PATH} read-only ({len(content)} B, blob {name}): texture2x='
          f'{str(args.texture2x).lower()} {args.width}x{args.height} language={args.language}; '
          f'mounted total now {total} B of {BUDGET} B')


if __name__ == '__main__':
    try: main()
    except OSError as error: sys.exit(f'boot_options: {error}')
