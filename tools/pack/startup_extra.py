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

# Round 52 correction, verified against the pinned bytecode: javap -p -c zombie.core.Core shows
#   public boolean getOptionTexture2x();  0: iconst_1  1: ireturn
# i.e. the method is hardcoded true, so Core.tileScale is ALWAYS 2 no matter what options.ini
# says (the earlier texture2x=false assumption was wrong). GameWindow.enter() therefore always
# mounts the eight 2x packs, and Texture.getSharedTexture("TileIndieStoneTentFrontLeft") only
# resolves if Tiles2x.pack is mounted (verified by scanning the pinned packs: that name appears
# in Tiles2x.pack and Tiles1x.pack; animated_clock_01_* only in Clock2x.pack).
# A per-pack FileNotFoundException is logged and tolerated by the game (debuglog-round52.txt shows
# the boot continuing past each one), while the null TileIndieStoneTentFrontLeft is fatal. So the
# mount set is Tiles2x + Clock2x, the two that the fatal checks need; the other six 2x packs
# (107 MB more, including JumboTreesBigs2x at 82 MB) stay unmounted for now to keep the tab inside
# the memory budget, and the game reports them honestly.
TILE_PACKS = ('Tiles2x.pack', 'Clock2x.pack')
# Two "failed to read image /media/ui/cursor_blank.png|cursor_white.png" in the same log: both
# files exist in the pinned copy (110 B and 463 B). Only these two, never all 28 MB of media/ui.
# Paths here are relative to media/, exactly like LUA_FILES.
UI_FILES = ('ui/cursor_blank.png', 'ui/cursor_white.png')


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
    for name in TILE_PACKS:
        path = media / 'texturepacks' / name
        if not path.is_file():
            raise SystemExit(f'Missing required texturepack: media/texturepacks/{name}')
    for relative in UI_FILES:
        if not (media / relative).is_file():
            raise SystemExit(f'Missing required ui file: media/{relative}')
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
        elif name.startswith('media/texturepacks/') and name.split('/')[-1] in TILE_PACKS:
            keep.append(name)
        elif name.startswith('media/ui/') and name[len('media/'):] in UI_FILES:
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
          f'ui={sum(1 for n in added if n.startswith("media/ui/"))} '
          f'lua={sum(1 for n in added if n.startswith("media/lua/"))}')


if __name__ == '__main__':
    try: main()
    except OSError as error: sys.exit(f'startup_extra: {error}')
