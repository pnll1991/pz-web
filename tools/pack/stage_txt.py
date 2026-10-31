#!/usr/bin/env python3
"""Build the staging tree of pure-Java .txt meshes/anims the browser build needs.

Why: jassimp64 (assimp) has no browser implementation, so no .x/.fbx/.glb may be mounted.
FileTask_AbstractLoadModel.checkExtensionType() only falls back to
"<mediaFilePath>/<lowercase name>.txt" - parsed by ModelLoader.loadTxt in Java - when the .x is
absent, so the staging root supplies media/models/skinned/*.txt, media/anims/bob/*.txt (the
lowercase fallback path) and media/anims_X/Bob/*.txt (the directory ModelManager enumerates).

Sources are always the pinned PZ_HOME .x files; output stays outside PZ_HOME and is git-ignored.
"""
import argparse
import os
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import x_anim_to_txt

MESHES = ('models_X/Skinned/MaleBody.x', 'models_X/Skinned/FemaleBody.x',
          'models_X/Skinned/Male_Skeleton.x', 'models_X/Skinned/Female_Skeleton.x')
ANIMS = ('anims_X/Bob/Bob_Idle.x', 'anims_X/Bob/Bob_Walk.x', 'anims_X/Bob/Bob_Run.x')


def emit(media_src, dst_root, rel_out):
    txt, bones, anims = x_anim_to_txt.convert(media_src.as_posix())
    out = dst_root / rel_out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(txt)
    return len(txt), bones, anims


# Round 80 (front H): ModelManager.loadAnimsFromDir loads every clip listed under media/anims_X/<dir>
# at boot, so with only Bob_Idle/Walk/Run the AnimSets nodes that name other clips have no track:
# defaultWalk's 2D blend (Bob_WalkSlow, limps), Idle's (Bob_Idle_HeavyBreathing), the non-looped
# turns (Bob_IdleTurn90/180L/R, Bob_IdleToWalk180L/R) and CopyFrame's Bob_IdleDrinkPopCan
# (scripts/generated/animations.txt). LOCOMOTION_ANIMSETS is the closure of m_AnimName in the
# player's idle/movement/run/sprint/turning sets; names are resolved case-insensitively against
# the pinned anims_X/Bob tree (sub-folders kept, as the game lists them recursively).
LOCOMOTION_ANIMSETS = ('AnimSets/player/idle.xml', 'AnimSets/player/idle/*.xml',
                       'AnimSets/player/walk.xml', 'AnimSets/player/movement/*.xml',
                       'AnimSets/player/run.xml', 'AnimSets/player/run/*.xml',
                       'AnimSets/player/sprint/*.xml', 'AnimSets/player/slowidleblend/*.xml',
                       'AnimSets/player/turning/*.xml', 'AnimSets/player/turningAim180/*.xml',
                       'AnimSets/player/turningIdle180/*.xml', 'AnimSets/player/turningMovement180/*.xml')
RUNTIME_ANIMATION_SCRIPTS = ('scripts/generated/animations.txt',)


def bob_clips(media, mode):
    """Relative .x paths under anims_X/Bob for the requested set ('locomotion' or 'all')."""
    import re
    tree = {}
    for src in sorted((media / 'anims_X/Bob').rglob('*.x')):
        tree.setdefault(src.stem.lower(), src.relative_to(media).as_posix())
    if mode == 'all':
        return sorted(tree.values()), []
    names = set()
    for pattern in LOCOMOTION_ANIMSETS:
        for xml in sorted(media.glob(pattern)):
            names |= set(re.findall(r'<m_AnimName>\s*([^<\s]+)\s*</m_AnimName>', xml.read_text(errors='replace')))
    for script in RUNTIME_ANIMATION_SCRIPTS:
        if (media / script).is_file():
            names |= set(re.findall(r'\bsource\s*=\s*([A-Za-z0-9_]+)', (media / script).read_text(errors='replace')))
    found = sorted({tree[n.lower()] for n in names if n.lower() in tree})
    missing = sorted(n for n in names if n.lower() not in tree)
    return found, missing


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pz-home', required=True)
    parser.add_argument('--stage', required=True, type=Path)
    parser.add_argument('--clean', action='store_true')
    parser.add_argument('--bob-anims', choices=('boot', 'locomotion', 'all'), default='boot',
                        help='boot = Bob_Idle/Walk/Run only (round 60); locomotion = closure of the player '
                             'idle/walk/run/turn AnimSets + runtime animation sources; all = every anims_X/Bob clip')
    args = parser.parse_args()
    home = Path(args.pz_home).expanduser().resolve()
    stage = args.stage.expanduser().resolve()
    if stage.is_relative_to(home):
        raise SystemExit(f'Staging root must stay outside PZ_HOME: {stage}')
    media = stage / 'media'
    if args.clean and media.is_dir():
        shutil.rmtree(media)
    total = 0
    for src_rel in MESHES:
        src = home / 'media' / src_rel
        if not src.is_file():
            raise SystemExit(f'Missing .x source: media/{src_rel}')
        name = Path(src_rel).stem.lower()
        n, bones, anims = emit(src, media, f'models/skinned/{name}.txt')
        total += n
        print(f'  models/skinned/{name}.txt {n} B bones={bones} anims={anims}')
    anims = list(ANIMS)
    if args.bob_anims != 'boot':
        extra, missing = bob_clips(home / 'media', args.bob_anims)
        anims += [rel for rel in extra if rel not in anims]
        print(f'bob-anims={args.bob_anims}: {len(anims)} clips; not in anims_X/Bob: {missing}')
    for src_rel in anims:
        src = home / 'media' / src_rel
        if not src.is_file():
            raise SystemExit(f'Missing .x source: media/{src_rel}')
        name = Path(src_rel).stem
        n, bones, anims = emit(src, media, f'anims_X/{src_rel[len("anims_X/"):-2]}.txt')
        total += n
        print(f'  anims_X/{src_rel[len("anims_X/"):-2]}.txt {n} B bones={bones} anims={anims}')
        n2, _, _ = emit(src, media, f'anims/{src_rel[len("anims_X/"):-2].lower()}.txt')
        total += n2
        print(f'  anims/{src_rel[len("anims_X/"):-2].lower()}.txt {n2} B (lowercase fallback)')
    print(f'staged bytes={total} root={stage}')


if __name__ == '__main__':
    main()
