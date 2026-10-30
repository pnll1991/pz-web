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
# Round 65 (debuglog-round65.txt): "UI_Loading_Lua" passes with nothing loaded because only
# defines.lua and Sandbox/ were mounted, so LuaManager.LoadDirBase finds no shared/client/server
# scripts and the Lua MainScreen (logo + buttons) never exists. The whole media/lua tree is mounted
# except Translate/ for languages other than EN (Translate is 110 MB of the 131 MB).
LUA_PREFIX = 'media/lua/'
LUA_TRANSLATE_PREFIX = 'media/lua/shared/Translate/'
LUA_TRANSLATE_KEEP = ('EN/',)
LUA_REQUIRED = ('lua/client/OptionScreens/MainScreen.lua',)

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
# Round 60: NO .x file is mounted at all (animations and meshes alike). diag166.log shows
# UnsatisfiedLinkError: System.loadLibrary: browser implementation is missing for jassimp64 right
# before every "Failed to load asset: AssetPath{ "Skinned/..." }". checkExtensionType tries
# "<xprefix>/<lowercase>.fbx/.glb/.x" first and only falls back to "<mediaFilePath>/<lowercase>.txt"
# when none of those exist, and that fallback is parsed by ModelLoader.loadTxt in pure Java. So the
# .x sources are converted by x_anim_to_txt.py into a staging tree outside PZ_HOME and the .x stay
# unmounted on purpose: mounting them would make the loader pick the .x and hit the missing native.
# The .x sources below are still checked for existence because they are what the staged .txt came from.
MODELS_X_REQUIRED = ('models_X/Skinned/MaleBody.x', 'models_X/Skinned/FemaleBody.x',
                     'models_X/Skinned/Male_Skeleton.x', 'models_X/Skinned/Female_Skeleton.x')
# Paths the staging root must provide (lowercase, exactly as checkExtensionType builds them):
# meshes are looked up as media/models/<lowercase>.txt and anims as media/anims/<lowercase>.txt.
STAGED_REQUIRED = ('models/skinned/malebody.txt', 'models/skinned/femalebody.txt',
                   'models/skinned/male_skeleton.txt', 'models/skinned/female_skeleton.txt',
                   'anims/bob/bob_idle.txt', 'anims/bob/bob_walk.txt', 'anims/bob/bob_run.txt',
                   'anims_X/Bob/Bob_Idle.txt', 'anims_X/Bob/Bob_Walk.txt', 'anims_X/Bob/Bob_Run.txt')
# AnimSets/*.xml are the animation-set definitions (14 MB, 2949 files); player.xml references the
# Bob/Kate folders and is read by the anim-set loader after the meshes load.
ANIMSETS_PREFIX = 'media/AnimSets/'
# Round 64 (DebugLog 2026-10-30_15-02, right after UI_Loading_OnGameBoot): "Failed to load file
# Guid table.." + FileNotFoundException. zombie.ZomboidFileSystem.loadFileGuidTable() (decompiled to
# /tmp/r64/out_zfs/ZomboidFileSystem.java:1060) opens getMediaFile("fileGuidTable.xml"), i.e.
# media/fileGuidTable.xml, which exists in the pinned copy (252,438 B) but was never mounted.
# GUID lookups resolve asset paths, so the table is mounted whole (only that one file).
GUID_TABLE = 'fileGuidTable.xml'
# Round 64 (probe /tmp/diag-menu66.log): MainScreenState#renderBackground entered and never
# returned -> DebugLog "UnsatisfiedLinkError: System.loadLibrary: browser implementation is
# missing for Bink2x64" from the MainThread. With options.ini doVideoEffects=false the game takes
# its own fallback branch renderOriginalBackground(), which is Texture.getSharedTexture on the
# eight media/ui/Title*.png files, and MainScreenState.render() also draws the logo bar through
# renderNinePatchTextures() (NinePatch1..4.png) and preloadBackgroundTextures() names the
# playstyleIcons, the TIS credits logo and the three controller spritesheets. All of them exist in
# the pinned copy; only these names/folders are mounted (3.5 MB), never the whole 24 MB media/ui.
MENU_UI_FILES = ('ui/Title.png', 'ui/Title2.png', 'ui/Title3.png', 'ui/Title4.png',
                 'ui/Title_lightning.png', 'ui/Title_lightning2.png',
                 'ui/Title_lightning3.png', 'ui/Title_lightning4.png',
                 'ui/NinePatch1.png', 'ui/NinePatch2.png', 'ui/NinePatch3.png',
                 'ui/NinePatch4.png',
                 'ui/Logos/TheIndieStoneLogo_credits.png')
MENU_UI_PREFIXES = ('media/ui/playstyleIcons/', 'media/ui/controllerBindingsEditor/')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pz-home', required=True)
    parser.add_argument('--files', required=True, type=Path, help='startup list to append to')
    parser.add_argument('--stage', type=Path, default=None,
                        help='Optional staging root (our own conversions, e.g. media/anims/*.txt). '
                             'Its media/ tree is appended to the list; pass the same root to '
                             'pack.py as --stage.')
    args = parser.parse_args()
    home = Path(args.pz_home).expanduser().resolve()
    media = home / 'media'
    if not media.is_dir():
        raise SystemExit(f'PZ_HOME has no media directory: {home}')
    wanted = []
    for path in sorted(media.rglob('*')):
        if path.is_file():
            wanted.append('media/' + path.relative_to(media).as_posix())
    staged = []
    if args.stage is not None:
        stage = args.stage.expanduser().resolve()
        if stage.is_relative_to(home):
            raise SystemExit(f'Staging root must stay outside PZ_HOME: {stage}')
        if not (stage / 'media').is_dir():
            raise SystemExit(f'Staging root has no media directory: {stage}')
        for path in sorted((stage / 'media').rglob('*')):
            if path.is_file():
                staged.append('media/' + path.relative_to(stage / 'media').as_posix())
        wanted.extend(staged)
    staged_set = set(staged)
    for relative in STAGED_REQUIRED:
        if 'media/' + relative not in staged_set:
            raise SystemExit(f'Staging root is missing a required converted file: media/{relative}')
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
    if not (media / GUID_TABLE).is_file():
        raise SystemExit(f'Missing required guid table: media/{GUID_TABLE}')
    for relative in MENU_UI_FILES:
        if not (media / relative).is_file():
            raise SystemExit(f'Missing required menu ui file: media/{relative}')
    for prefix in MENU_UI_PREFIXES:
        if not (home / prefix).is_dir():
            raise SystemExit(f'Missing required menu ui folder: {prefix}')
    for relative in LUA_FILES + LUA_REQUIRED:
        if not (media / relative).is_file():
            raise SystemExit(f'Missing required lua file: media/{relative}')
    if not (home / DEPTHMAP_REQUIRED).is_file():
        raise SystemExit(f'Missing required depthmap: {DEPTHMAP_REQUIRED}')
    for relative in MODELS_X_REQUIRED:
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
        elif (name in staged_set
              and (name.startswith('media/models/skinned/')
                   or name.startswith('media/anims/')
                   or name.startswith('media/anims_X/'))):
            # Only our converted .txt (staged outside PZ_HOME). The game's own .x under
            # models_X/anims_X is deliberately NOT mounted: it would make checkExtensionType pick
            # the .x path and die on the missing jassimp64 native.
            keep.append(name)
        elif name.startswith(ANIMSETS_PREFIX):
            keep.append(name)
        elif name.startswith('media/texturepacks/') and name.split('/')[-1] in TEXTUREPACKS:
            keep.append(name)
        elif name.startswith('media/texturepacks/') and name.split('/')[-1] in TILE_PACKS:
            keep.append(name)
        elif name.startswith('media/ui/') and name[len('media/'):] in UI_FILES:
            keep.append(name)
        elif name.startswith('media/ui/') and name[len('media/'):] in MENU_UI_FILES:
            keep.append(name)
        elif any(name.startswith(prefix) for prefix in MENU_UI_PREFIXES):
            keep.append(name)
        elif name.startswith('media/' + UI_PROGRESS_PREFIX):
            keep.append(name)
        elif name == 'media/' + GUID_TABLE:
            keep.append(name)
        elif name.startswith(LUA_TRANSLATE_PREFIX):
            if name[len(LUA_TRANSLATE_PREFIX):].startswith(LUA_TRANSLATE_KEEP):
                keep.append(name)
        elif name.startswith(LUA_PREFIX):
            keep.append(name)
    existing = {line.strip() for line in args.files.read_text().splitlines() if line.strip()}
    added = [name for name in keep if name not in existing]
    with args.files.open('a') as handle:
        for name in added:
            handle.write(name + '\n')
    def stat_size(name):
        real = home / name
        if real.is_file():
            return real.stat().st_size
        if args.stage is not None:
            return (args.stage.expanduser().resolve() / name).stat().st_size
        raise SystemExit(f'Startup entry has no source file: {name}')
    size = sum(stat_size(name) for name in added)
    print(f'Appended {len(added)} startup entries ({size} B): shaders='
          f'{sum(1 for n in added if n.startswith("media/shaders/"))} '
          f'texturepacks={sum(1 for n in added if n.startswith("media/texturepacks/"))} '
          f'ui={sum(1 for n in added if n.startswith("media/ui/"))} '
          f'lua={sum(1 for n in added if n.startswith("media/lua/"))} '
          f'depthmaps={sum(1 for n in added if n.startswith(DEPTHMAP_PREFIX))} '
          f'staged_meshes={sum(1 for n in added if n.startswith("media/models/skinned/"))} '
          f'staged_anims_x={sum(1 for n in added if n.startswith("media/anims_X/"))} '
          f'staged_anims_txt={sum(1 for n in added if n.startswith("media/anims/"))} '
          f'animsets={sum(1 for n in added if n.startswith(ANIMSETS_PREFIX))} '
          f'guid_table={sum(1 for n in added if n == "media/" + GUID_TABLE)}')


if __name__ == '__main__':
    try: main()
    except OSError as error: sys.exit(f'startup_extra: {error}')
