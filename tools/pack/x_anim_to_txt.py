#!/usr/bin/env python3
"""Convert DirectX text (.x, 'xof 0303txt') animation files to the game's own
"Project Zomboid Animated Mesh" .txt format, which ModelLoader.loadTxt reads in
pure Java (no jassimp/assimp native).

FileTask_AbstractLoadModel.checkExtensionType() falls back to
media/anims/<name>.txt when no .fbx/.glb/.x exists for <name> under
media/anims_x, so the converted tree must be served as media/anims/... and the
.x files must NOT be in the VFS.

Conventions (checked against media/Bob_Sprint.txt, which ships with the game):
 - .x rotation keys are (w,x,y,z); .txt stores (x,y,z,w) conjugated: (-x,-y,-z,w).
 - translations are copied as-is; time = ticks / AnimTicksPerSecond.
 - frames are emitted for the union of R/T key times per bone (missing channel
   is interpolated: lerp for T, nlerp for R).
Animation-only output: 0 vertices / 0 faces; skeleton from the Frame tree,
bind pose from FrameTransformMatrix (transposed to row-major).

Usage: x_anim_to_txt.py SRC_DIR DST_DIR [--only GLOB ...] [--fps-cap N]
"""
import fnmatch, math, os, re, sys

TOK = re.compile(r'"[^"]*"|[{}]|[^\s{};,]+|;|,')

def tokens(text):
    return [t for t in TOK.findall(text) if t not in (',',)]

class Node:
    __slots__ = ('kind', 'name', 'data', 'children')
    def __init__(s, kind, name):
        s.kind, s.name, s.data, s.children = kind, name, [], []

def parse(text):
    if not text.startswith('xof ') or text[8:11] != 'txt':
        raise ValueError('not a text .x file')
    toks = tokens(text[16:])
    root = Node('root', None); stack = [root]; i = 0; n = len(toks)
    while i < n:
        t = toks[i]
        if t == 'template':
            depth = 0
            while True:
                i += 1
                if toks[i] == '{': depth += 1
                elif toks[i] == '}':
                    depth -= 1
                    if depth == 0: break
            i += 1; continue
        if t == '}':
            stack.pop(); i += 1; continue
        if t == '{':
            # reference { Name } or anonymous object
            if i + 2 < n and toks[i + 2] == '}' and toks[i + 1] not in ('{', ';'):
                stack[-1].children.append(Node('ref', toks[i + 1].strip('"'))); i += 3; continue
            nd = Node('anon', None); stack[-1].children.append(nd); stack.append(nd); i += 1; continue
        if t[0].isalpha() and i + 1 < n and (toks[i + 1] == '{' or (i + 2 < n and toks[i + 2] == '{' and toks[i + 1] not in (';',))):
            name = None
            if toks[i + 1] != '{': name = toks[i + 1]; i += 1
            nd = Node(t, name); stack[-1].children.append(nd); stack.append(nd); i += 2; continue
        if t != ';': stack[-1].data.append(t)
        i += 1
    return root

def find(node, kind):
    for c in node.children:
        if c.kind == kind: yield c
        yield from find(c, kind)

def skeleton(root):
    bones = []  # (name, parent, matrix16)
    def walk(fr, parent):
        idx = len(bones)
        m = next((c for c in fr.children if c.kind == 'FrameTransformMatrix'), None)
        mat = [float(v) for v in m.data[:16]] if m else [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
        bones.append((fr.name, parent, mat))
        for c in fr.children:
            if c.kind == 'Frame': walk(c, idx)
    for c in root.children:
        if c.kind == 'Frame': walk(c, -1)
    return bones

def keys(akey):
    d = akey.data; ktype = int(d[0]); cnt = int(d[1]); out = []; p = 2
    for _ in range(cnt):
        t = float(d[p]); nv = int(d[p + 1]); vals = [float(v) for v in d[p + 2:p + 2 + nv]]
        out.append((t, vals)); p += 2 + nv
    return ktype, out

def sample(ks, t, quat):
    if not ks: return None
    if t <= ks[0][0]: return ks[0][1]
    for (t0, a), (t1, b) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            if quat and sum(x * y for x, y in zip(a, b)) < 0: b = [-x for x in b]
            v = [x + (y - x) * f for x, y in zip(a, b)]
            if quat:
                l = math.sqrt(sum(x * x for x in v)) or 1.0; v = [x / l for x in v]
            return v
    return ks[-1][1]

def inverse(m):
    a = [[m[c * 4 + r] for c in range(4)] + [1.0 if r == k else 0.0 for k in range(4)] for r in range(4)]
    for c in range(4):
        p = max(range(c, 4), key=lambda r: abs(a[r][c]))
        if abs(a[p][c]) < 1e-12: return [1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
        a[c], a[p] = a[p], a[c]; d = a[c][c]; a[c] = [x / d for x in a[c]]
        for r in range(4):
            if r != c:
                f = a[r][c]; a[r] = [x - f * y for x, y in zip(a[r], a[c])]
    return [a[r][4 + c] for c in range(4) for r in range(4)]

def mul(a, b):
    """Row-major product, matching GLMatrix4f.mul(a, b) with row vectors v*a*b."""
    return [sum(a[r * 4 + k] * b[k * 4 + c] for k in range(4)) for r in range(4) for c in range(4)]

def fmt(v): return ', '.join('%.8f' % x for x in v)

def mesh_of(root):
    for m in find(root, 'Mesh'):
        if len(m.data) > 1: return m
    return None

def _vec_list(node, n, per):
    """Read a MeshNormals/MeshTextureCoords block: count, then count*per floats."""
    if node is None or not node.data: return None
    if int(node.data[0]) != n: return None
    vals = [float(x) for x in node.data[1:1 + n * per]]
    if len(vals) < n * per: return None
    return [vals[i * per:(i + 1) * per] for i in range(n)]

def parse_mesh(node, index):
    """Return (positions, normals, uvs, faceIndices, perVertexSkin) or None.

    Mesh.data holds the template's scalars/arrays in order: nVertices, 3*nVertices floats,
    nFaces, then nFaces MeshFace entries ("3 i j k"; the leading number is that face's index
    count). Skin weights come from the SkinWeights children ("Bone"; k; k vertex indices;
    k weights; offset matrix).
    """
    d = node.data; p = 0
    n = int(d[p]); p += 1
    if n <= 0 or len(d) < 1 + 3 * n: return None
    pos = [[float(d[p + 3 * i + k]) for k in range(3)] for i in range(n)]; p += 3 * n
    if p >= len(d): return None
    nfaces = int(float(d[p])); p += 1
    faces = []
    for _ in range(nfaces):
        if p >= len(d): return None
        k = int(float(d[p])); p += 1
        idx = [int(float(x)) for x in d[p:p + k]]; p += k
        if len(idx) < k or k < 3: return None
        if k == 3:
            faces.extend(idx)
        else:                      # fan triangulation of a polygon face
            for i in range(1, k - 1):
                a, b, c = idx[0], idx[i], idx[i + 1]
                if a != b and b != c and a != c: faces.extend((a, b, c))
    normals = _vec_list(next((c for c in node.children if c.kind == 'MeshNormals'), None), n, 3)
    uvs = _vec_list(next((c for c in node.children if c.kind == 'MeshTextureCoords'), None), n, 2)
    skin = [[] for _ in range(n)]
    for sw in node.children:
        if sw.kind != 'SkinWeights' or not sw.data: continue
        name = sw.data[0].strip('"'); q = 1
        try: k = int(sw.data[q])
        except (ValueError, IndexError): continue
        q += 1
        idx = [int(float(x)) for x in sw.data[q:q + k]]; q += k
        wts = [float(x) for x in sw.data[q:q + k]]
        if name not in index or len(idx) < k or len(wts) < k: continue
        bi = index[name]
        for vi, wv in zip(idx, wts):
            if 0 <= vi < n and wv > 0.0: skin[vi].append((wv, bi))
    return pos, normals, uvs, faces, skin

def face_normals(pos, faces, n):
    """Normals averaged over the fan-triangulated faces.

    Bob_Idle.x / Bob_Run.x (and Kate_Idle/Kate_Run) carry no MeshNormals at all, and
    ModelLoader.loadTxt refuses a file whose "# Vertex Count:" is 0: with numVertices == 0 the
    `case 6` loop never runs, so its trailing `reader.readLine()` no longer eats the
    "# Number of Faces:" comment and `case 8` then parses the skeleton's first "0" as a face and
    crashes on spl[1] ("array element access out of bounds").  Every .txt the game ships has real
    vertices, so we always emit geometry: positions come from the Mesh, normals are derived, and
    texture coords are 0 when the .x has no MeshTextureCoords.
    """
    acc = [[0.0, 0.0, 0.0] for _ in range(n)]
    for i in range(0, len(faces) - 2, 3):
        a, b, c = pos[faces[i]], pos[faces[i + 1]], pos[faces[i + 2]]
        ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        for k in (faces[i], faces[i + 1], faces[i + 2]):
            acc[k][0] += nx; acc[k][1] += ny; acc[k][2] += nz
    out = []
    for v in acc:
        l = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        out.append([v[0] / l, v[1] / l, v[2] / l] if l > 1e-9 else [0.0, 0.0, 1.0])
    return out


def convert(path):
    with open(path, 'r', encoding='latin-1') as fh: text = fh.read()
    root = parse(text)
    bones = skeleton(root)
    index = {b[0]: i for i, b in enumerate(bones)}
    tps = next((float(n.data[0]) for n in find(root, 'AnimTicksPerSecond')), 4800.0)
    mesh = parse_mesh(mesh_of(root), index) if mesh_of(root) is not None else None
    has_geom = mesh is not None and len(mesh[0]) > 0 and len(mesh[3]) >= 3
    if has_geom and (mesh[1] is None or mesh[2] is None):
        pos, _, uvs, faces, skin = mesh
        normals = face_normals(pos, faces, len(pos))
        uvs = uvs if uvs is not None else [[0.0, 0.0] for _ in pos]
        mesh = (pos, normals, uvs, faces, skin)
    out = []
    W = out.append
    W('# Project Zomboid Animated Mesh'); W('# File Version:'); W('1.00000000')
    W('# Model Name:'); W(os.path.splitext(os.path.basename(path))[0])
    W('# Vertex Stride Element Count:'); W('6'); W('# Vertex Stride Size (in bytes):'); W('76')
    W('# Vertex Stride Data:'); W('# (Int)    Offset'); W('# (String) Type')
    for o, n in ((0,'VertexArray'),(12,'NormalArray'),(24,'TangentArray'),(36,'TextureCoordArray'),(44,'BlendWeightArray'),(60,'BlendIndexArray')):
        W(str(o)); W(n)
    if has_geom:
        pos, normals, uvs, faces, skin = mesh
        n = len(pos)
        W('# Vertex Count:'); W(str(n)); W('# Vertex Buffer:')
        for i in range(n):
            W(fmt(pos[i])); W(fmt(normals[i])); W(fmt([0.0, 0.0, 0.0])); W(fmt(uvs[i]))
            e = sorted(skin[i], key=lambda x: -x[0])[:4]
            w = [e[k][0] if k < len(e) else -1.0 for k in range(4)]
            b = [e[k][1] if k < len(e) else 0 for k in range(4)]
            W(fmt(w)); W(', '.join(str(x) for x in b))
        nf = len(faces) - (len(faces) % 3)
        W('# Number of Faces:'); W(str(nf // 3)); W('# Face Data:')
        for i in range(0, nf, 3): W(', '.join(str(x) for x in faces[i:i + 3]))
    else:
        W('# Vertex Count:'); W('0'); W('# Vertex Buffer:'); W('# Number of Faces:'); W('0'); W('# Face Data:')
    W('# Number of Bones:'); W(str(len(bones)))
    W('# Skeleton Hierarchy:'); W('# (Int)    Index'); W('# (Int)    Parent Index'); W('# (String) Bone Name')
    for i, (n, p, _) in enumerate(bones): W(str(i)); W(str(p)); W(n)
    def mats(title, sub, ms):
        W('# ' + title + ':'); W('# (Int)    Bone Index'); W('# (Matrix) ' + sub)
        for i, m in enumerate(ms):
            W(str(i))
            for r in range(4): W(fmt([m[c * 4 + r] for c in range(4)]))
    # Verified against media/Bob_Sprint.txt (ships with the game): Bind Pose is the bone's local
    # FrameTransformMatrix as stored; Inverse Bind Pose is the inverse of that local matrix; Skin
    # Offset Matrices are the accumulated bind-world matrices W[k] = W[parent] * bind[k].
    # ModelLoader.getMatrix() already reads rows in file order, so no transpose here.
    bind = [m for _, _, m in bones]
    inv = [inverse(m) for m in bind]
    world = []
    for i, (_, p, _) in enumerate(bones):
        world.append(bind[i] if p is None or p < 0 or p >= i else mul(world[p], bind[i]))
    mats('Bind Pose', 'Bind Matrix', bind)
    mats('Inverse Bind Pose', 'Inverse Bind Matrix', inv)
    mats('Skin Offset Matrices', 'Offset Matrix', world)
    sets = list(find(root, 'AnimationSet'))
    W('# Number of Animations:'); W(str(len(sets)))
    W('# Animations:'); W('# (String) Anim Name'); W('# (Float)  Duration in Seconds'); W('# (Int)    Number of Frames')
    W('# -- Frame (Int)        Bone Index'); W('# -- Frame (String)     Bone Name'); W('# -- Frame (Float)      Time in Seconds')
    W('# -- Frame (Vector3)    Translation'); W('# -- Frame (Quaternion) Rotation')
    for s in sets:
        frames = []; tmax = 0.0
        for a in s.children:
            if a.kind != 'Animation': continue
            ref = next((c.name for c in a.children if c.kind == 'ref'), None)
            if ref not in index: continue
            ch = {}
            for k in a.children:
                if k.kind == 'AnimationKey':
                    kt, ks = keys(k); ch[kt] = ks
            R, T = ch.get(0, []), ch.get(2, [])
            times = sorted({t for t, _ in R} | {t for t, _ in T})
            for t in times:
                r = sample(R, t, True) or [1.0, 0.0, 0.0, 0.0]
                tr = sample(T, t, False)
                if tr is None:
                    m = bones[index[ref]][2]; tr = m[12:15]
                tmax = max(tmax, t)
                frames.append((t / tps, index[ref], ref, tr, (-r[1], -r[2], -r[3], r[0])))
        frames.sort(key=lambda f: (f[0], f[1]))
        W(s.name or os.path.splitext(os.path.basename(path))[0]); W('%.8f' % (tmax / tps)); W(str(len(frames)))
        for t, bi, bn, tr, q in frames:
            W(str(bi)); W(bn); W('%.8f' % t); W(fmt(tr)); W(fmt(q))
    return '\n'.join(out) + '\n', len(bones), len(sets)

def main(argv):
    if len(argv) < 3: print(__doc__); return 2
    src, dst = argv[1], argv[2]; only = []
    if '--only' in argv: only = argv[argv.index('--only') + 1:]
    ok = bad = 0; insz = outsz = 0
    for dp, _, fs in os.walk(src):
        for f in sorted(fs):
            if not f.lower().endswith('.x'): continue
            rel = os.path.relpath(os.path.join(dp, f), src)
            if only and not any(fnmatch.fnmatch(rel.lower(), g.lower()) for g in only): continue
            try:
                txt, nb, na = convert(os.path.join(dp, f))
            except Exception as e:
                print('FAIL', rel, e, file=sys.stderr); bad += 1; continue
            o = os.path.join(dst, rel[:-2] + '.txt'); os.makedirs(os.path.dirname(o), exist_ok=True)
            with open(o, 'w') as fh: fh.write(txt)
            ok += 1; insz += os.path.getsize(os.path.join(dp, f)); outsz += len(txt)
    print('converted=%d failed=%d in=%.1fMB out=%.1fMB' % (ok, bad, insz / 1e6, outsz / 1e6))
    return 1 if bad else 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
