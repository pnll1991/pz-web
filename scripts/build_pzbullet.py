#!/usr/bin/env python3
"""Build the pinned upstream Bullet sources plus our own wrapper into a wasm32 module.

Only public upstream Bullet sources (inside .toolchain, ignored by git) and the
wrapper in port/pzbullet are compiled. No game binaries are read or copied.
"""
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.toolchain'
SRC = CACHE / 'bullet-src/bullet3-2.83'
DOWNLOADS = CACHE / 'downloads'
BUILD = ROOT / 'build/round7/pzbullet'
OUTPUT = ROOT / 'port/target/web/vendor/pzbullet'
EXPORTS = ['pzbullet_version', 'pzbullet_init_logging', 'pzbullet_logging_initialised',
           'pzbullet_logging_buffer_bytes', 'pzbullet_world_initialised', 'pzbullet_create_world', 'pzbullet_body_count',
           'pzbullet_add_box', 'pzbullet_add_sphere',
           'pzbullet_step_simulation', 'pzbullet_get_transform', 'pzbullet_remove_all_bodies',
           'pzbullet_dispose_world',
           # Round 52: Bullet.defineVehicleScript(name, float[]) is the native the real game calls
           # during script load (241 times in console-round51.txt). These are the real registry
           # entry points of our own module, not stubs.
           'pzbullet_define_vehicle_script', 'pzbullet_vehicle_scratch', 'pzbullet_vehicle_script_name',
           'pzbullet_vehicle_script_count',
           'pzbullet_vehicle_script_unique_count', 'pzbullet_vehicle_script_overwrite_count',
           'pzbullet_vehicle_script_param_count', 'pzbullet_vehicle_script_param']


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    lock = json.loads((ROOT / 'scripts/bullet-lock.json').read_text())
    archive = DOWNLOADS / lock['filename']
    if not archive.is_file():
        sys.exit(f'Missing Bullet archive: {archive}')
    if sha256(archive) != lock['sha256']:
        sys.exit(f'Bullet archive hash mismatch for {archive.name}; refusing unverified sources.')
    if not SRC.is_dir():
        sys.exit(f'Missing extracted sources at {SRC}')
    emcc = CACHE / 'emsdk/upstream/emscripten/emcc'
    if not emcc.is_file():
        sys.exit('Missing emcc: run scripts/bootstrap.py')
    BUILD.mkdir(parents=True, exist_ok=True)
    sources = [str(ROOT / 'port/pzbullet/pzbullet_wrapper.cpp')]
    for directory in ('src/LinearMath', 'src/BulletCollision', 'src/BulletDynamics'):
        sources.extend(str(path) for path in sorted((SRC / directory).rglob('*.cpp')))
    compile_flags = [str(emcc), '-O2', '-std=c++17', '-stdlib=libc++', '-fno-exceptions', '-fno-rtti',
                     '-I' + str(SRC / 'src'), '-Wno-parentheses', '-Wno-unused-variable',
                     '-Wno-unused-but-set-variable', '-Wno-invalid-offsetof', '-Wno-comment',
                     '-Wno-macro-redefined', '-Wno-unknown-warning-option']
    # -fno-exceptions makes emcc skip libc++abi, which still provides operator new and
    # the static-initialisation guards Bullet's translation units need.
    link_flags = ['--no-entry', '-lc++abi', '-sMODULARIZE=1', '-sEXPORT_ES6=1', '-sEXPORT_NAME=pzBulletModule',
                  '-sALLOW_MEMORY_GROWTH=1',
                  '-sINITIAL_MEMORY=16777216', '-sENVIRONMENT=web,worker,node',
                  '-sEXPORTED_RUNTIME_METHODS=cwrap,HEAPU8,UTF8ToString',
                  '-sEXPORTED_FUNCTIONS=' + ','.join('_' + name for name in EXPORTS),
                  '-o', str(BUILD / 'pzbullet.js')]
    print(f'Compiling {len(sources)} translation units (Bullet 2.83 + own wrapper)…', flush=True)
    subprocess.run(compile_flags + sources + link_flags, cwd=SRC.parent.parent, check=True)
    wasm, js = BUILD / 'pzbullet.wasm', BUILD / 'pzbullet.js'
    if not wasm.is_file() or not js.is_file():
        sys.exit('Emscripten produced no module.')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name in ('pzbullet.js', 'pzbullet.wasm'):
        target = OUTPUT / name
        target.write_bytes((BUILD / name).read_bytes())
        os.chmod(target, 0o644)
    manifest = {'schema_version': 1,
                'libraries': ['PZBullet64', 'PZBullet'],
                'bullet': {'version': lock['version'], 'archive': lock['filename'],
                           'archive_sha256': lock['sha256'], 'bt_bullet_version_macro': 283,
                           'source_tree': str(SRC.relative_to(ROOT))},
                'wrapper': {'path': 'port/pzbullet/pzbullet_wrapper.cpp',
                            'sha256': sha256(ROOT / 'port/pzbullet/pzbullet_wrapper.cpp')},
                'module': {'js_bytes': (OUTPUT / 'pzbullet.js').stat().st_size,
                           'wasm_bytes': wasm.stat().st_size, 'wasm_sha256': sha256(wasm),
                           'js_sha256': sha256(js)},
                'exports': EXPORTS,
                'limits': ['The module implements only the operations listed in exports.',
                           'PZBullet JNI semantics (buffers, units, ownership, effects) are not recovered yet; Java natives stay fail-fast with their JNI names.',
                           'No game binaries were read, copied, or linked into this module.']}
    (OUTPUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Built pzbullet.wasm {wasm.stat().st_size} bytes, sha256 {manifest["module"]["wasm_sha256"]}')


if __name__ == '__main__':
    main()
