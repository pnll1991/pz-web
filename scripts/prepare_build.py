#!/usr/bin/env python3
"""Generate compiler metadata, referencing external jars without copying them."""
import argparse
import glob
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/inventory'))
from inventory import resolve_game_home, launcher_metadata


EXPOSURE_OUTPUTS = ('exposure.json', 'reflection-lua.txt')


def generate_exposure(target, use_cache):
    """Lua exposure (~10 s: javap + bytecode scan of projectzomboid.jar). With use_cache (build.sh --dev),
    reuse a previous output whose inputs hash the same: inventory.json (it pins the jars by SHA-256 and is
    verified against them above), the generator sources and the JDK that runs javap."""
    if use_cache:
        digest = hashlib.sha256()
        for path in [ROOT / 'inventory/inventory.json', ROOT / 'tools/exposure/generate.py',
                     *sorted((ROOT / 'tools/inventory').glob('*.py')), *sorted((ROOT / 'tools/inventory').glob('*.java')),
                     Path(os.environ.get('JAVA_HOME', '')) / 'release']:
            digest.update(str(path.relative_to(ROOT) if path.is_relative_to(ROOT) else path).encode() + b'\0')
            digest.update(path.read_bytes() if path.is_file() else b'missing')
        cache = ROOT / 'port/target/cache/exposure' / digest.hexdigest()
        if all((cache / name).is_file() for name in EXPOSURE_OUTPUTS):
            for name in EXPOSURE_OUTPUTS: (target / name).write_bytes((cache / name).read_bytes())
            print(f'Lua exposure: reused cache {cache.name[:12]} (inputs unchanged)')
            return
    spec = importlib.util.spec_from_file_location('exposure_generator', ROOT / 'tools/exposure/generate.py')
    exposure = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(exposure)
    exposure.generate(ROOT / 'inventory/inventory.json', target)
    if use_cache:
        cache.mkdir(parents=True, exist_ok=True)
        for name in EXPOSURE_OUTPUTS: (cache / name).write_bytes((target / name).read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--with-game', action='store_true', help='compile the actual launcher main from the external copy')
    parser.add_argument('--reflection', choices=['lua', 'minimal'], default='lua', help='Lua manifest is required for full game analysis; minimal is diagnostic only')
    parser.add_argument('--output-dir', type=Path, help='build.sh --dev: write boot.wasm/build-state.json here (staging) instead of the served port/target/web')
    parser.add_argument('--cache-exposure', action='store_true', help='build.sh --dev: reuse the Lua exposure output when its inputs hash the same')
    args = parser.parse_args()
    output = ROOT / ('port/target/web/game' if args.with_game else 'port/target/web')
    if args.output_dir: output = (ROOT / args.output_dir).resolve()
    output.mkdir(parents=True, exist_ok=True)
    (output / 'boot.wasm').unlink(missing_ok=True)
    (output / 'build-state.json').write_text(json.dumps({'success':False, 'status':'preparing',
        'mode':'game-main' if args.with_game else 'platform-smoke'}) + '\n')
    target = ROOT / 'port/target/config'
    target.mkdir(parents=True, exist_ok=True)
    paths, natives = [], ['port.demo.NativeProbe\tmissing\t(I)I\tJava_port_demo_NativeProbe_missing__I']
    if args.with_game:
        if not os.environ.get('PZ_HOME'): parser.error('--with-game requires PZ_HOME and a matching inventory')
        home = resolve_game_home(os.environ['PZ_HOME'])
        inventory = ROOT / 'inventory/inventory.json'
        if not inventory.is_file(): parser.error('Run tools/inventory/run.sh "$PZ_HOME" first')
        data = json.loads(inventory.read_text())
        if Path(data['pz_home']) != home: parser.error('PZ_HOME differs from inventory; regenerate it')
        if launcher_metadata(home) != data['launcher']: parser.error('Launcher changed since inventory; regenerate it')
        for jar in data['jars']:
            with (home / jar['path']).open('rb') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != jar['sha256']:
                    parser.error(f"Jar changed since inventory: {jar['path']}. Regenerate inventory.")
        entries = data['launcher']['classPath']
        if not isinstance(entries, list) or not entries: parser.error('Launcher classPath must be a nonempty list')
        for entry in entries:
            found = sorted(glob.glob(str(home / entry))) if '*' in entry else [str(home / entry)]
            if not found: parser.error(f'Classpath entry has no matches: {entry}')
            for item in found:
                path = Path(item).resolve()
                if not path.is_relative_to(home) or not path.exists(): parser.error(f'Invalid external classpath: {entry}')
                if path.suffix == '.jar' or path.is_dir(): paths.append(str(path))
                else: parser.error(f'Unsupported classpath entry: {entry}')
        for native in data['native_methods']:
            natives.append('\t'.join([native['class'], native['method'], native['descriptor'], native['jni_long']]))
    (target / 'classpath.txt').write_text('\n'.join(dict.fromkeys(paths)) + '\n')
    (target / 'entrypoint.properties').write_text(
        'mode=' + ('game-main' if args.with_game else 'platform-smoke') + '\n' +
        'reflection=' + (args.reflection if args.with_game else 'minimal') + '\n' +
        'main=' + (data['launcher']['mainClass'] if args.with_game else '') + '\n' +
        ('output=' + str(output) + '\n' if args.output_dir else ''))
    (target / 'natives.tsv').write_text('\n'.join(sorted(set(natives))) + '\n')
    (target / 'gl-overrides.tsv').write_text('')
    if args.with_game:
        sys.path.insert(0, str(ROOT / 'tools/glgen'))
        from generate import generate
        generate(ROOT / 'inventory/inventory.json', target)
        generate_exposure(target, args.cache_exposure)
    reflection = (ROOT / 'port/config/reflection.txt').read_text()
    if args.with_game and args.reflection == 'lua': reflection += '\n' + (target / 'reflection-lua.txt').read_text()
    (target / 'reflection.txt').write_text(reflection)
    # Synthetic forwarding probes run in every build, including with an external classpath.
    with (target / 'gl-overrides.tsv').open('a') as stream:
        for method, descriptor in [('glClearColor', '(FFFF)V'), ('glClear', '(I)V'), ('glGetError', '()I')]:
            stream.write(f'port.demo.GLMappingProbe\t{method}\t{descriptor}\tport.shim.GL11\n')
    print(f"Prepared {'real game main' if args.with_game else 'platform probe'}: {len(paths)} external classpath entries, {len(natives)} native guards.")


if __name__ == '__main__': main()
