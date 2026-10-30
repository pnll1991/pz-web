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
# Round 59b (debuglog-round59b.txt): while UI_Loading_ModelsAnimations is on screen the game asks
# for the loading-bar animation frames and gets ExecutionException: FileNotFoundException for
# media/ui/Progress/{MaleDoor,MaleSprint06,MaleWalk2}.png. Those three exist in the pinned copy
# (the whole folder is 36 KB), so the folder is mounted; nothing else under media/ui is added.
UI_PROGRESS_PREFIX = 'ui/Progress/'

# Round 59, measured in diag162.log: the boot now reaches zombie.GameWindow#enter and dies with
# "dereferencing a null pointer" in zombie.tileDepth.TileSeamManager::init. The pinned bytecode of
# that method (javap -p -c zombie.tileDepth.TileSeamManager) is:
#   4: ldc "media/depthmaps/SEAMS_01.png"; 7: Texture.getSharedTexture(String;I)
#   41: aload_2; invokevirtual Texture.getTextureId()
# so a null shared texture is dereferenced right away. The whole media/depthmaps/ tree (220 files,
# 6.2 MB in the pinned copy) is mounted because zombie.tileDepth/zombie.core.depth read DEPTH_*.png
# by name as tiles load. Only real files from PZ_HOME are packed, never placeholders.
DEPTHMAP_PREFIX = 'media/depthmaps/'
DEPTHMAP_REQUIRED = 'media/depthmaps/SEAMS_01.png'

# Round 59, measured in diag163.log: the boot now reaches GameWindow#enter and dies with the Java
# NPE "Required Animation Asset not found: bob/bob_idle" (ModelManager::create). Before that, the
# DebugLog shows "Failed to load asset: AssetPath{ "Skinned/MaleBody" }" for every skinned model,
# so nothing under models_X/anims_X is mounted, ModelMesh("Human") never isReady() and
# loadHumanAnimations returns early (both verified in the pinned bytecode).
# FileTask_AbstractLoadModel.checkExtensionType (javap -p -c) builds the lookup name from the
# constructor's two prefixes, lowercased: FileTask_LoadMesh passes ("media/models","media/models_x")
# and FileTask_LoadAnimation passes ("media/anims","media/anims_x"), then tries
# "<xprefix>/<name>.fbx", ".glb" and ".x" through ZomboidFileSystem.getString + new File(...).exists().
# Those prefixes are lowercase, while the pinned copy stores models_X/anims_X, so the mount needs
# both spellings (case_alias.py adds the lowercase ones); case_alias.py adds the lowercase ones.
# Only the files the failing checks actually need are mounted: the four skinned bodies/skeletons
# ModelManager.create loads, the three Bob animations getAnimationAssetRequired demands, and the
# Kate folder (2,3 MB) because AnimationsMesh "Human" lists it as animationDirectory. The rest of
# anims_X (Bob is 373 MB / 1615 files, Zombie 100+ MB) stays unmounted: the VFS budget is 512 MiB
# and loadAnimsFromDir loads every file listFiles() returns, so a directory mount would load all.
MODELS_X_REQUIRED = ('models_X/Skinned/MaleBody.x', 'models_X/Skinned/FemaleBody.x',
                     'models_X/Skinned/Male_Skeleton.x', 'models_X/Skinned/Female_Skeleton.x')
ANIMS_X_REQUIRED = ('anims_X/Bob/Bob_Idle.x', 'anims_X/Bob/Bob_Walk.x', 'anims_X/Bob/Bob_Run.x')
# AnimSets/*.xml are the animation-set definitions (14 MB, 2949 files); player.xml references the
# Bob/Kate folders and is read by the anim-set loader after the meshes load.
ANIMSETS_PREFIX = 'media/AnimSets/'


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
    if not (media / UI_PROGRESS_PREFIX).is_dir():
        raise SystemExit(f'Missing required ui folder: media/{UI_PROGRESS_PREFIX}')
    for relative in LUA_FILES:
        if not (media / relative).is_file():
            raise SystemExit(f'Missing required lua file: media/{relative}')
    if not (home / DEPTHMAP_REQUIRED).is_file():
        raise SystemExit(f'Missing required depthmap: {DEPTHMAP_REQUIRED}')
    for relative in MODELS_X_REQUIRED + ANIMS_X_REQUIRED:
        if not (media / relative).is_file():
            raise SystemExit(f'Missing required model/animation file: media/{relative}')
    if not (media / 'AnimSets').is_dir():
        raise SystemExit('Missing required folder: media/AnimSets')
    sandbox = media / 'lua' / 'shared' / 'Sandbox'
    if not sandbox.is_dir():
        raise SystemExit('Missing required lua folder: media/lua/shared/Sandbox')
    keep = []
    for name in wanted:
        if name.startswith('media/shaders/'):
            keep.append(name)
        elif name.startswith('media/scripts/'):
            keep.append(name)
        elif name.startswith(DEPTHMAP_PREFIX):
            keep.append(name)
        elif name.startswith('media/models_X/') and name[len('media/'):] in MODELS_X_REQUIRED:
            keep.append(name)
        elif name.startswith('media/anims_X/') and name[len('media/'):] in ANIMS_X_REQUIRED:
            keep.append(name)
        elif name.startswith('media/anims_X/Kate/'):
            keep.append(name)
        elif name.startswith(ANIMSETS_PREFIX):
            keep.append(name)
        elif name.startswith('media/texturepacks/') and name.split('/')[-1] in TEXTUREPACKS:
            keep.append(name)
        elif name.startswith('media/texturepacks/') and name.split('/')[-1] in TILE_PACKS:
            keep.append(name)
        elif name.startswith('media/ui/') and name[len('media/'):] in UI_FILES:
            keep.append(name)
        elif name.startswith('media/' + UI_PROGRESS_PREFIX):
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
          f'lua={sum(1 for n in added if n.startswith("media/lua/"))} '
          f'depthmaps={sum(1 for n in added if n.startswith(DEPTHMAP_PREFIX))} '
          f'models_x={sum(1 for n in added if n.startswith("media/models_X/"))} '
          f'anims_x={sum(1 for n in added if n.startswith("media/anims_X/"))} '
          f'animsets={sum(1 for n in added if n.startswith(ANIMSETS_PREFIX))}')


if __name__ == '__main__':
    try: main()
    except OSError as error: sys.exit(f'startup_extra: {error}')
