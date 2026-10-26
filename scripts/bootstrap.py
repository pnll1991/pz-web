#!/usr/bin/env python3
"""Install pinned Linux x86_64 toolchains inside the ignored project directory."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TC = ROOT / '.toolchain'


def install(name, spec):
    destination = TC / name
    marker = destination / '.pz-version' if name != 'vineflower' else TC / 'vineflower.version'
    if marker.exists() and marker.read_text().strip() == spec['version']:
        print(f'{name}: already installed ({spec["version"]})', flush=True)
        return
    archive = TC / 'downloads' / spec['filename']
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        print(f'Downloading {name} {spec["version"]}', flush=True)
        partial = archive.with_suffix(archive.suffix + '.partial')
        subprocess.run(['curl', '-fL', '--retry', '3', '--connect-timeout', '20',
                        '--silent', '--show-error', '-o', str(partial), spec['url']], check=True)
        partial.replace(archive)
    algorithm = 'sha512' if 'sha512' in spec else 'sha256'
    with archive.open('rb') as f:
        actual = hashlib.file_digest(f, algorithm).hexdigest()
    expected = spec.get(algorithm)
    if not expected:
        raise RuntimeError(f'{name}: no checksum pinned in toolchain-lock.json')
    if actual != expected:
        raise RuntimeError(f'{name}: checksum mismatch; remove {archive} and retry')
    if name == 'vineflower':
        shutil.copyfile(archive, TC / 'vineflower.jar')
    else:
        staging = TC / (name + '.extract')
        if staging.exists():
            shutil.rmtree(staging)
        staging.mkdir()
        if archive.suffix == '.zip':
            with zipfile.ZipFile(archive) as z:
                for item in z.infolist():
                    path = (staging / item.filename).resolve()
                    if not path.is_relative_to(staging.resolve()):
                        raise RuntimeError(f'Unsafe archive path: {item.filename}')
                z.extractall(staging)
                for item in z.infolist():
                    mode = (item.external_attr >> 16) & 0o777
                    if mode:
                        (staging / item.filename).chmod(mode)
        else:
            with tarfile.open(archive) as t:
                t.extractall(staging, filter='data')
        items = list(staging.iterdir())
        source = items[0] if len(items) == 1 and items[0].is_dir() else staging
        if destination.exists():
            shutil.rmtree(destination)
        source.rename(destination)
        if staging.exists():
            staging.rmdir()
    marker.write_text(spec['version'] + '\n')
    print(f'{name}: installed and checksum verified', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--extras', action='store_true', help='also Ghidra, Vineflower, apitrace, CMake, Ninja and Emscripten')
    args = parser.parse_args()
    if sys.platform != 'linux' or os.uname().machine != 'x86_64':
        parser.error('This lock is for Linux x86_64 only')
    lock = json.loads((ROOT / 'scripts/toolchain-lock.json').read_text())
    names = ['jdk', 'maven', 'node']
    if args.extras:
        names += ['cmake', 'ninja', 'vineflower', 'apitrace', 'renderdoc', 'ghidra', 'emsdk']
    for name in names:
        install(name, lock[name])
    if args.extras:
        env = os.environ.copy()
        env.update(EM_CACHE=str(TC / 'emscripten-cache'), EMSDK_PYTHON=sys.executable,
                   EMSDK_NODE=str(TC / 'node/bin/node'))
        for command in ('install', 'activate'):
            subprocess.run([str(TC / 'emsdk/emsdk'), command, lock['emsdk']['version']],
                           cwd=TC / 'emsdk', env=env, check=True)
    print('Ready. Run: source scripts/env.sh')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        sys.exit(f'bootstrap: {error}')
