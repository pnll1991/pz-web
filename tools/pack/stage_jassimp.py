#!/usr/bin/env python3
"""Round 80 (front L): stage the preconverted jassimp caches of the .x meshes the browser serves.

tools/jassimp/JassimpCache.java runs the pinned libjassimp64.so on this PC with the flags of
FileTask_LoadMesh.loadX and writes, per .x, a cache that port.natives.Jassimp64 (the browser's
Jassimp.aiImportFile) rebuilds into the same AiScene. The cache is served AT the .x path, so the
game's checkExtensionType sees the .x, takes the jassimp path (ImportedSkinnedMesh: vertex layout
0 V, 1 N, 2 weights, 3 indices, 4 uv, the one basicEffect.vert declares) instead of the .txt
fallback (ModelLoader.loadTxt: tangent at 2, uv at 3, which basicEffect reads as weights/indices).

Each cache is staged twice: real case (media/models_X/Skinned/MaleBody.x) and the lowercase path
checkExtensionType builds ("media/models_x" + lowercase name + ".x"); overlay.py stores both over
one blob range. Output stays in a git-ignored staging root outside PZ_HOME.

  python3 tools/pack/stage_jassimp.py --pz-home $PZ_HOME --stage build/real-browser/stage-jassimp --clean
  python3 tools/pack/overlay.py --packs build/real-browser/packs --stage build/real-browser/stage-jassimp \
      --add --prefix /media/models_X/ --prefix /media/models_x/
"""
import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# The player's body and the two skeletons ModelManager.create loads first (same four .x the .txt
# fallback converted since round 60).
MESHES = ('models_X/Skinned/MaleBody.x', 'models_X/Skinned/FemaleBody.x',
          'models_X/Skinned/Male_Skeleton.x', 'models_X/Skinned/Female_Skeleton.x')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--pz-home', required=True)
    ap.add_argument('--stage', required=True, type=Path)
    ap.add_argument('--clean', action='store_true')
    ap.add_argument('--java', default=str(ROOT / '.toolchain/jdk/bin/java'))
    ap.add_argument('mesh', nargs='*', help='extra media-relative .x paths (models_X/...)')
    a = ap.parse_args()
    home = Path(a.pz_home).expanduser().resolve()
    stage = a.stage.expanduser().resolve()
    if stage.is_relative_to(home):
        raise SystemExit(f'Staging root must stay outside PZ_HOME: {stage}')
    classes = ROOT / 'port/target/classes/port/natives/JassimpCacheReader.class'
    if not classes.is_file():
        raise SystemExit('port/target/classes is not built (needed for --verify): run scripts/build.sh')
    meshes = list(dict.fromkeys(list(MESHES) + a.mesh))
    for rel in meshes:
        if not rel.startswith('models_X/') or not rel.endswith('.x') or not (home / 'media' / rel).is_file():
            raise SystemExit(f'Not a pinned models_X .x file: media/{rel}')
    real = stage / 'real'
    if a.clean and stage.exists():
        shutil.rmtree(stage)
    real.mkdir(parents=True, exist_ok=True)
    cmd = [a.java, '--enable-native-access=ALL-UNNAMED', f'-Djava.library.path={home / "natives"}',
           '-cp', f'{home / "projectzomboid.jar"}{os.pathsep}{ROOT / "port/target/classes"}',
           str(ROOT / 'tools/jassimp/JassimpCache.java'), '--natives', str(home / 'natives'), '--verify',
           str(real), str(home / 'media')] + meshes
    subprocess.run(cmd, check=True)
    media = stage / 'media'
    for rel in meshes:
        data = (real / rel).read_bytes()
        for out in (media / rel, media / rel.replace('models_X/', 'models_x/', 1).lower()):
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
    shutil.rmtree(real)
    print(f'staged {len(meshes)} jassimp caches (x2 paths) under {media}')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(f'stage_jassimp: dumper failed ({error.returncode})')
