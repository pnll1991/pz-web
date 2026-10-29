#!/usr/bin/env python3
"""Append the boot resources the real game asks for, derived from PZ_HOME (never guessed).

Evidence (docs/notes-from-code.md, rounds 49/49b, and build/real-browser/debuglog-round49.txt):
- zombie.core.IndieFileLoader.getStreamReader("media/shaders/default.vert") -> FileNotFoundException:
  ShaderProgram's constant pool builds "media/shaders/{name}{_static}{_instanced}.vert" and
  "media/shaders/{name}{_instanced}.frag"; ShaderUnit resolves every #include relative to the
  including file's folder ("util/instancing" -> "media/shaders/util/instancing.h"). The whole tree
  is mounted because the menu programs use several of them and includes are resolved at runtime.
- GameWindow.initShared -> LoadTexturePack throws FileNotFoundException for the UI packs.
- zombie.SandboxOptions::<clinit> -> LuaManager.RunLua("lua/shared/defines.lua") throws the 8th
  FileNotFoundException; the same class then reads "media/lua/shared/Sandbox/{name}.lua".
Only these three prefixes are added, and every path must exist in PZ_HOME (checked by pack.py).
"""
import argparse
import os
import sys
from pathlib import Path

TEXTUREPACKS = ('UI.pack', 'UI2.pack', 'IconsMoveables.pack', 'RadioIcons.pack',
                'ApComUI.pack', 'Mechanics.pack', 'WeatherFx.pack')
LUA_FILES = ('lua/shared/defines.lua',)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pz-home', required=True)
    parser.add_argument('--files', required=True, type=Path, help='startup list to append to')
    args = parser.parse_args()
    home = Path(args.pz_home).expanduser().resolve()
    media = home / 'media'
    if not media.is_dir():
        raise SystemExit(f'PZ_HOME has no media directory: {home}')
    wanted = []
    for path in sorted(media.rglob('*')):
        if path.is_file():
            wanted.append('media/' + path.relative_to(media).as_posix())
    for name in TEXTUREPACKS:
        path = media / 'texturepacks' / name
        if not path.is_file():
            raise SystemExit(f'Missing required texturepack: media/texturepacks/{name}')
    for relative in LUA_FILES:
        if not (media / relative).is_file():
            raise SystemExit(f'Missing required lua file: media/{relative}')
    sandbox = media / 'lua' / 'shared' / 'Sandbox'
    if not sandbox.is_dir():
        raise SystemExit('Missing required lua folder: media/lua/shared/Sandbox')
    keep = []
    for name in wanted:
        if name.startswith('media/shaders/'):
            keep.append(name)
        elif name.startswith('media/scripts/'):
            keep.append(name)
        elif name.startswith('media/texturepacks/') and name.split('/')[-1] in TEXTUREPACKS:
            keep.append(name)
        elif name.startswith('media/lua/shared/defines.lua') or name.startswith('media/lua/shared/Sandbox/'):
            keep.append(name)
    existing = {line.strip() for line in args.files.read_text().splitlines() if line.strip()}
    added = [name for name in keep if name not in existing]
    with args.files.open('a') as handle:
        for name in added:
            handle.write(name + '\n')
    size = sum((home / name).stat().st_size for name in added)
    print(f'Appended {len(added)} startup entries ({size} B): shaders='
          f'{sum(1 for n in added if n.startswith("media/shaders/"))} '
          f'texturepacks={sum(1 for n in added if n.startswith("media/texturepacks/"))} '
          f'lua={sum(1 for n in added if n.startswith("media/lua/"))}')


if __name__ == '__main__':
    try: main()
    except OSError as error: sys.exit(f'startup_extra: {error}')
