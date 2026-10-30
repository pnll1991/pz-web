#!/usr/bin/env python3
"""Reference parser for the ASCII Direct3X (.x) files used by the pinned copy.

Purpose: pin down the exact grammar and byte layouts that the browser jassimp
replacement (port/compat/jassimp) must produce. Prints metadata only; it never
copies game files into the repo and never stores parsed content.

Grammar comes from the pinned files themselves plus upstream assimp
(code/AssetLib/X/XFileParser.cpp, XFileImporter.cpp,
code/PostProcessing/ConvertToLHProcess.cpp), which is what jassimp64 wraps.
See docs/decisions.md.
"""
import re
import sys

TOKEN = re.compile(
    r'\s*([{}]|,,|;;|;|,|"[^"]*"|[-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?|[A-Za-z0-9_\-]+)'
)


class Tokens:
    def __init__(self, text):
        self.items = []
        pos = 0
        while pos < len(text):
            m = TOKEN.match(text, pos)
            if not m:
                pos += 1
                continue
            self.items.append(m.group(1))
            pos = m.end()
        self.i = 0

    def peek(self):
        return self.items[self.i] if self.i < len(self.items) else None

    def next(self):
        tok = self.items[self.i]
        self.i += 1
        return tok

    def skip_separators(self):
        while self.peek() in (';', ',', ',,', ';;'):
            self.next()

    def number(self):
        return float(self.next())


def read_floats(t, count):
    """assimp ReadFloats: consume tokens until `count` numbers, then swallow separators."""
    out = []
    while len(out) < count:
        tok = t.peek()
        if tok is None:
            raise ValueError('unexpected end reading %d floats' % count)
        t.next()
        if tok in (';', ',', ',,', ';;'):
            continue
        out.append(float(tok))
    t.skip_separators()
    return out


def head_of_object(t):
    """assimp readHeadOfDataObject: optional name, optional GUID, then '{'."""
    name = None
    tok = t.peek()
    if tok not in ('{', None):
        name = t.next()
        if t.peek() == ':':
            t.next()
            t.next()  # GUID token
    t.eat_or_skip('{')
    return name


def eat_or_skip(self, want):  # monkey-patched below
    pass


Tokens.eat_or_skip = lambda self, want: (self.next() if self.peek() == want else None)


def parse_mesh(t, mesh):
    """Mesh { nVerts; x;y;z; ... nFaces; 3;a,b,c; ... } plus sub-objects."""
    mesh['num_vertices'] = int(t.number())
    mesh['positions'] = read_floats(t, 3 * mesh['num_vertices'])
    mesh['num_faces'] = int(t.number())
    faces = []
    for _ in range(mesh['num_faces']):
        n = int(t.number())
        faces.append(n)
        read_floats(t, n)
    mesh['face_index_counts'] = set(faces)
    running = True
    while running:
        tok = t.peek()
        if tok is None:
            raise ValueError('unterminated Mesh')
        if tok == '}':
            t.next()
            break
        t.next()
        if tok == 'MeshNormals':
            t.eat_or_skip('{')
            n = int(t.number())
            mesh['normals'] = read_floats(t, 3 * n)
        elif tok == 'MeshMaterialList':
            t.eat_or_skip('{')
            nmat = int(t.number())
            per_face = int(t.number())
            mesh['num_materials'] = nmat
            mesh['materials_per_face'] = per_face
            read_floats(t, per_face)
            refs = 0
            while t.peek() == '{':
                t.next()
                t.next()
                t.eat_or_skip('}')
                refs += 1
            mesh['material_defs'] = refs
        elif tok == 'MeshTextureCoords':
            if t.peek() not in ('{', None):
                t.next()  # coordinate set label such as c1
            t.eat_or_skip('{')
            n = int(t.number())
            uv = read_floats(t, 2 * n)
            mesh['uv0'] = len(uv) // max(n, 1)
        elif tok == 'SkinWeights':
            t.eat_or_skip('{')
            bone = t.next().strip('"')
            n = int(t.number())
            read_floats(t, n)   # vertex indices
            read_floats(t, n)   # weights
            read_floats(t, 16)  # offset matrix (row order a1..d4)
            mesh.setdefault('bones', []).append(bone)
        else:
            skip_object(t)
    return mesh


def skip_object(t):
    # Accept either an already-opened object (brace consumed by the caller) or a
    # bare name still followed by '{' (template declarations).
    if t.peek() == '{':
        t.next()
    depth = 1
    while depth:
        tok = t.peek()
        if tok is None:
            raise ValueError('unterminated object')
        t.next()
        if tok == '{':
            depth += 1
        elif tok == '}':
            depth -= 1


def parse_frame(t, frame, stats):
    stats['frames'] += 1
    running = True
    while running:
        tok = t.peek()
        if tok is None:
            raise ValueError('unterminated Frame')
        if tok == '}':
            t.next()
            break
        t.next()
        if tok == 'FrameTransformMatrix':
            t.eat_or_skip('{')
            m = read_floats(t, 16)
            frame.setdefault('matrix', m)
            stats['matrices'] += 1
        elif tok == 'Mesh':
            t.eat_or_skip('{')
            stats['meshes'].append(parse_mesh(t, {}))
        elif tok in ('Frame', 'AnimationSet', 'AnimationKey', 'AnimTicksPerSecond'):
            parse_container(t, tok, stats)
        else:
            skip_object(t)
    return frame


def parse_container(t, kind, stats):
    if kind == 'Frame':
        t.eat_or_skip('{')
        parse_frame(t, {}, stats)
    elif kind == 'AnimationSet':
        t.eat_or_skip('{')
        parse_animation_set(t, stats)
    elif kind == 'AnimationKey':
        t.eat_or_skip('{')
        parse_animation_key(t, stats)
    else:
        # Material / XSkinMeshHeader / AnimTicksPerSecond: skip_object consumes '{'.
        skip_object(t)


def parse_animation_key(t, stats):
    key_type = int(t.number())
    n = int(t.number())
    width = {0: 4, 1: 3, 2: 3, 3: 16, 4: 16}.get(key_type)
    if width is None:
        raise ValueError('unknown AnimationKey type %d' % key_type)
    times = []
    for _ in range(n):
        times.append(int(t.number()))
        read_floats(t, width)
        t.skip_separators()
    stats['keys'].setdefault(key_type, []).append({'count': n, 'times': times})


def parse_animation_set(t, stats):
    name = None
    if t.peek() not in ('{', '}', 'Animation', None):
        name = t.next()
    stats['anim_sets'].append(name)
    running = True
    while running:
        tok = t.peek()
        if tok is None:
            raise ValueError('unterminated AnimationSet')
        if tok == '}':
            t.next()
            break
        t.next()
        if tok == 'Animation':
            t.eat_or_skip('{')
            stats['animations'] += 1
            bone = None
            while True:
                inner = t.peek()
                if inner is None:
                    raise ValueError('unterminated Animation')
                if inner == '}':
                    t.next()
                    break
                t.next()
                if inner == '{':
                    bone = t.next()
                    t.eat_or_skip('}')
                    stats['bones'].append(bone)
                elif inner == 'AnimationKey':
                    t.eat_or_skip('{')
                    parse_animation_key(t, stats)
                else:
                    skip_object(t)
        else:
            skip_object(t)


def parse(text, label):
    t = Tokens(text)
    header = [t.next(), t.next(), t.next(), t.next()]
    stats = {'frames': 0, 'matrices': 0, 'meshes': [], 'keys': {}, 'anim_sets': [],
             'animations': 0, 'bones': []}
    while t.peek() is not None:
        tok = t.next()
        if tok == 'template':
            skip_object(t)
        elif tok in ('Frame', 'Mesh'):
            parse_container(t, tok, stats)
        elif tok in ('AnimationSet', 'AnimationKey', 'AnimTicksPerSecond',
                     'Material', 'XSkinMeshHeader'):
            parse_container(t, tok, stats)
        else:
            skip_object(t)
    return stats


def report(path):
    text = open(path, 'r', errors='replace').read()
    st = parse(text, path)
    meshes = st['meshes']
    print('FILE %s' % path)
    print('  frames=%d frame_matrices=%d meshes=%d anim_sets=%s animations=%d channels=%d'
          % (st['frames'], st['matrices'], len(meshes), st['anim_sets'], st['animations'], len(st['bones'])))
    for i, m in enumerate(meshes):
        print('  mesh[%d] verts=%d faces=%d face_index_counts=%s normals=%s uv0=%s materials=%s per_face=%s bones=%d'
              % (i, m['num_vertices'], m['num_faces'], sorted(m['face_index_counts']),
                 len(m.get('normals', [])) // 3, m.get('uv0'),
                 m.get('num_materials'), m.get('materials_per_face'), len(m.get('bones', []))))
    for kt, groups in sorted(st['keys'].items()):
        times = [x for g in groups for x in g['times']]
        print('  keyType=%d groups=%d keys=%d max_time=%s min_time=%s'
              % (kt, len(groups), len(times), max(times), min(times)))


if __name__ == '__main__':
    for p in sys.argv[1:]:
        report(p)
