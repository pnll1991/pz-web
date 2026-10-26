#!/usr/bin/env python3
"""Classify actual LWJGL signatures. Only four verified, scalar GL calls are mapped."""
import argparse
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools/inventory'))
from classfile import ClassReader

DIRECT = {('glClearColor','(FFFF)V'), ('glClear','(I)V'), ('glViewport','(IIII)V'), ('glGetError','()I')}


def generate(inventory, output):
    data = json.loads(inventory.read_text())
    root = Path(data['pz_home'])
    signatures = {}
    for jar in data['jars']:
        with zipfile.ZipFile(root/jar['path']) as archive:
            for entry in sorted(archive.namelist()):
                if not entry.startswith('org/lwjgl/opengl/') or not entry.endswith('.class'): continue
                cls = ClassReader(archive.read(entry)).parse()
                for method in cls['methods']:
                    if method['name'].startswith('<'): continue
                    key = (cls['name'],method['name'],method['descriptor'])
                    # No inheritance-based guesses, pointer overloads or desktop GLSL rewrites.
                    direct = cls['name'] in ('org.lwjgl.opengl.GL11','org.lwjgl.opengl.GL11C') and method['static'] and (method['name'],method['descriptor']) in DIRECT
                    signatures[key] = {'class':cls['name'],'method':method['name'],'descriptor':method['descriptor'],
                        'native':method['native'], 'status':'direct' if direct else ('throws-native' if method['native'] else 'original-wrapper-needs-review'),
                        'target':'port.shim.GL11' if direct else None}
    rows = [signatures[key] for key in sorted(signatures)]
    output.mkdir(parents=True, exist_ok=True)
    (output/'coverage.json').write_text(json.dumps({'schema_version':1,'signatures':rows,
        'limits':['Coverage describes compiler mappings; standalone shader/texture tests do not prove game rendering.', 'Fixed pipeline, pointer functions and LWJGL shader entrypoints are not mapped.']},indent=2)+'\n')
    (output/'gl-overrides.tsv').write_text(''.join('\t'.join([r['class'],r['method'],r['descriptor'],r['target']])+'\n' for r in rows if r['target']))
    print(f"GL signatures: {len(rows)}, verified scalar mappings: {sum(r['status']=='direct' for r in rows)}")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory',type=Path,default=ROOT/'inventory/inventory.json')
    parser.add_argument('--out',type=Path,default=ROOT/'port/target/config')
    args = parser.parse_args()
    try: generate(args.inventory,args.out)
    except (OSError, ValueError, zipfile.BadZipFile) as error: sys.exit(f'glgen: {error}')
