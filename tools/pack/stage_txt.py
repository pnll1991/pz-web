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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pz-home', required=True)
    parser.add_argument('--stage', required=True, type=Path)
    parser.add_argument('--clean', action='store_true')
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
    for src_rel in ANIMS:
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
