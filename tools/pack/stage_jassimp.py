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
    ap.add_argument('--bob-anims', choices=('none', 'locomotion', 'combat', 'mobility'), default='none',
                    help='locomotion = the 240-clip closure of stage_txt.py (AnimSets/player idle/walk/run/'
                         'sprint/turning + runtime sources), dumped with the anim flags (profile x-anim); combat = the closure of the '
                         'player AnimSets for being hit/knocked down/getting up, aiming and melee/shove/stomp (stage_txt.COMBAT_ANIMSETS); mobility = fence vault/climb, window/wall/sheet-rope climbing, window open/close/smash, '
                         'falling and body throws (stage_txt.MOBILITY_ANIMSETS)')
    ap.add_argument('--zombie-anims', action='store_true',
                    help='also dump every clip of media/anims_X/Zombie (the zombie AnimSets: walk, lunge, attack, hit reactions...); '
                         'without them zombies have no pose (not drawn) and no root motion (do not move)')
    ap.add_argument('--weapon-meshes', action='store_true',
                    help='also dump every .x mesh under media/models_X/weapons (melee/firearm models): the game only lets the player attack when the '
                         'model of the weapon in hand is loaded (IsoGameCharacter.isWeaponReady -> primaryHandModel.model.isReady); the .fbx ones are skipped')
    ap.add_argument('mesh', nargs='*', help='extra media-relative .x paths (models_X/...)')
    a = ap.parse_args()
    home = Path(a.pz_home).expanduser().resolve()
    stage = a.stage.expanduser().resolve()
    if stage.is_relative_to(home):
        raise SystemExit(f'Staging root must stay outside PZ_HOME: {stage}')
    classes = ROOT / 'port/target/classes/port/natives/JassimpCacheReader.class'
    if not classes.is_file():
        raise SystemExit('port/target/classes is not built (needed for --verify): run scripts/build.sh')
    extra = []
    if a.weapon_meshes:
        wdir = home / 'media' / 'models_X' / 'weapons'
        extra = sorted(f'models_X/weapons/{f.relative_to(wdir).as_posix()}' for f in wdir.rglob('*') if f.is_file() and f.suffix == '.x')
        print(f'weapon-meshes: {len(extra)} .x meshes from {wdir}')
    meshes = list(dict.fromkeys(list(MESHES) + extra + a.mesh))
    for rel in meshes:
        if not rel.startswith('models_X/') or not rel.endswith('.x') or not (home / 'media' / rel).is_file():
            raise SystemExit(f'Not a pinned models_X .x file: media/{rel}')
    real = stage / 'real'
    if a.clean and stage.exists():
        shutil.rmtree(stage)
    real.mkdir(parents=True, exist_ok=True)
    jobs = [('x-mesh', meshes, 'models_X/')]
    if a.bob_anims != 'none':
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        import stage_txt
        clips, missing = stage_txt.bob_clips(home / 'media', a.bob_anims)
        clips = list(dict.fromkeys((list(stage_txt.ANIMS) if a.bob_anims == 'locomotion' else []) + clips))
        print(f'bob-anims={a.bob_anims}: {len(clips)} clips; not in anims_X/Bob: {missing}')
        jobs.append(('x-anim', clips, 'anims_X/'))
    if a.zombie_anims:
        zdir = home / 'media' / 'anims_X' / 'Zombie'
        zclips = sorted(f'anims_X/Zombie/{f.name}' for f in zdir.iterdir() if f.is_file() and f.suffix.lower() == '.x')
        print(f'zombie-anims: {len(zclips)} clips from {zdir}')
        jobs.append(('x-anim', zclips, 'anims_X/'))
    media = stage / 'media'
    for profile, rels, prefix in jobs:
        listing = stage / f'{profile}-{len(rels)}.list'
        listing.write_text('\n'.join(rels) + '\n')
        cmd = [a.java, '--enable-native-access=ALL-UNNAMED', f'-Djava.library.path={home / "natives"}',
               '-cp', f'{home / "projectzomboid.jar"}{os.pathsep}{ROOT / "port/target/classes"}',
               str(ROOT / 'tools/jassimp/JassimpCache.java'), '--natives', str(home / 'natives'),
               '--profile', profile, '--verify', str(real), str(home / 'media'), f'@{listing}']
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)
        listing.unlink()
        total = 0
        for rel in rels:
            data = (real / rel).read_bytes()
            total += len(data)
            # real case (what loadAnimsFromDir / the asset list enumerate) + the lowercase lookup path
            # checkExtensionType builds ("media/models_x" / "media/anims_x" + lowercase name + ".x")
            lower = prefix.lower() + rel[len(prefix):].lower()
            for out in (media / rel, media / lower):
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_bytes(data)
        print(f'{profile}: {len(rels)} caches, {total} B (each also at its lowercase alias)')
    shutil.rmtree(real)
    print(f'staged jassimp caches under {media}')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(f'stage_jassimp: dumper failed ({error.returncode})')
