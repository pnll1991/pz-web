#!/usr/bin/env python3
"""Classify actual LWJGL signatures; map only verified scalar calls and queries."""
import argparse
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools/inventory'))
from classfile import ClassReader

# Verified against the pinned jar: each (class, name, descriptor) is an existing static method
# that port.shim.GL11 / port.shim.GL15 implement over the real WebGL2 context. Every mapped name
# is in gl.mjs PORT_PROVIDED, so a capability address handed out for it can actually be called.
# Everything else stays unmapped and keeps the fail-fast raise.
SCALAR = 'port.shim.GL11'
GL15 = 'port.shim.GL15'
DIRECT_CLASSES = ('org.lwjgl.opengl.GL11', 'org.lwjgl.opengl.GL11C', 'org.lwjgl.opengl.GL30C',
                  'org.lwjgl.opengl.GL15C')
SCALAR_DIRECT = {('glClearColor','(FFFF)V'), ('glClear','(I)V'), ('glViewport','(IIII)V'), ('glGetError','()I'),
    ('glEnable','(I)V'), ('glDisable','(I)V'), ('glBlendFunc','(II)V'), ('glClearStencil','(I)V'),
    ('glStencilMask','(I)V'), ('glCullFace','(I)V'), ('glFrontFace','(I)V'), ('glDepthFunc','(I)V'),
    ('glDepthMask','(Z)V'), ('glLineWidth','(F)V'), ('glPixelStorei','(II)V'), ('glScissor','(IIII)V'),
    ('glGetString','(I)Ljava/lang/String;'), ('glGetStringi','(II)Ljava/lang/String;'),
    ('glGetInteger','(I)I'), ('glGetIntegerv','(I[I)V'), ('glGetIntegerv','(ILjava/nio/IntBuffer;)V'),
    ('glGetFloat','(I)F'), ('glGetFloatv','(I[F)V'), ('glGetFloatv','(ILjava/nio/FloatBuffer;)V'),
    ('glGetBoolean','(I)Z'), ('glGetBooleanv','(ILjava/nio/ByteBuffer;)V')}
# Only the natives on GL15C: the wrappers of GL15/GL15C stay untouched and reach them.
GL15_DIRECT = {('nglGenBuffers','(IJ)V'), ('nglDeleteBuffers','(IJ)V'), ('glBindBuffer','(II)V'),
    ('glIsBuffer','(I)Z'), ('nglBufferData','(IJJI)V'), ('nglBufferSubData','(IJJJ)V'),
    ('nglGetBufferSubData','(IJJJ)V'), ('nglGetBufferParameteriv','(IIJ)V'),
    ('nglGetBufferPointerv','(IIJ)V'), ('nglMapBuffer','(II)J'), ('glUnmapBuffer','(I)Z'),
    ('nglGenQueries','(IJ)V'), ('nglDeleteQueries','(IJ)V'), ('glIsQuery','(I)Z'),
    ('glBeginQuery','(II)V'), ('glEndQuery','(I)V'), ('nglGetQueryiv','(IIJ)V'),
    ('nglGetQueryObjectiv','(IIJ)V'), ('nglGetQueryObjectuiv','(IIJ)V')}


def target_for(class_name, method_name, descriptor):
    if class_name == 'org.lwjgl.opengl.GL15C':
        return GL15 if (method_name, descriptor) in GL15_DIRECT else None
    return SCALAR if (method_name, descriptor) in SCALAR_DIRECT else None


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
                    target = target_for(cls['name'], method['name'], method['descriptor']) if method['static'] else None
                    direct = target is not None
                    signatures[key] = {'class':cls['name'],'method':method['name'],'descriptor':method['descriptor'],
                        'native':method['native'], 'status':'direct' if direct else ('throws-native' if method['native'] else 'original-wrapper-needs-review'),
                        'target':target}
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
