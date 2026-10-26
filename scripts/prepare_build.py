#!/usr/bin/env python3
"""Generate compiler metadata, referencing external jars without copying them."""
import argparse
import glob
import hashlib
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--with-game', action='store_true', help='attach inventoried classpath; Boot is still only a platform probe')
    args = parser.parse_args()
    target = ROOT / 'port/target/config'
    target.mkdir(parents=True, exist_ok=True)
    paths, natives = [], ['port.demo.NativeProbe\tmissing\t(I)I\tJava_port_demo_NativeProbe_missing__I']
    if args.with_game:
        if not os.environ.get('PZ_HOME'): parser.error('--with-game requires PZ_HOME and a matching inventory')
        home = Path(os.environ['PZ_HOME']).resolve()
        inventory = ROOT / 'inventory/inventory.json'
        if not inventory.is_file(): parser.error('Run tools/inventory/run.sh "$PZ_HOME" first')
        data = json.loads(inventory.read_text())
        if Path(data['pz_home']) != home: parser.error('PZ_HOME differs from inventory; regenerate it')
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
    (target / 'natives.tsv').write_text('\n'.join(sorted(set(natives))) + '\n')
    (target / 'gl-overrides.tsv').write_text('')
    if args.with_game:
        sys.path.insert(0, str(ROOT / 'tools/glgen'))
        from generate import generate
        generate(ROOT / 'inventory/inventory.json', target)
    # Synthetic forwarding probes run in every build, including with an external classpath.
    with (target / 'gl-overrides.tsv').open('a') as stream:
        for method, descriptor in [('glClearColor', '(FFFF)V'), ('glClear', '(I)V'), ('glGetError', '()I')]:
            stream.write(f'port.demo.GLMappingProbe\t{method}\t{descriptor}\tport.shim.GL11\n')
    print(f'Prepared platform probe: {len(paths)} external classpath entries, {len(natives)} native guards. Game initialization is TODO.')


if __name__ == '__main__': main()
