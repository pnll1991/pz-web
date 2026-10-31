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
GL20 = 'port.shim.GL20'
GL30 = 'port.shim.GL30'
DIRECT_CLASSES = ('org.lwjgl.opengl.GL11', 'org.lwjgl.opengl.GL11C', 'org.lwjgl.opengl.GL30C',
                  'org.lwjgl.opengl.GL15C', 'org.lwjgl.opengl.GL20C', 'org.lwjgl.opengl.GL13C',
                  'org.lwjgl.opengl.GL14C')
SCALAR_DIRECT = {('glClearColor','(FFFF)V'), ('glClear','(I)V'), ('glViewport','(IIII)V'), ('glGetError','()I'),
    ('glEnable','(I)V'), ('glDisable','(I)V'), ('glBlendFunc','(II)V'), ('glClearStencil','(I)V'),
    ('glStencilMask','(I)V'), ('glCullFace','(I)V'), ('glFrontFace','(I)V'), ('glDepthFunc','(I)V'),
    ('glDepthMask','(Z)V'), ('glLineWidth','(F)V'), ('glPixelStorei','(II)V'), ('glScissor','(IIII)V'),
    # RenderThread/GLState state setters the game reaches from zombie.core.Core and the
    # GLStateRenderThread wrapper classes. port.shim.GL11 implements each over real WebGL2 calls;
    # glAlphaFunc additionally drives the shader-side alpha-test discard (port/runtime/glsl.mjs).
    ('glAlphaFunc','(IF)V'), ('glColorMask','(ZZZZ)V'), ('glStencilFunc','(III)V'),
    ('glStencilOp','(III)V'), ('glDepthRange','(DD)V'), ('glClearDepth','(D)V'),
    ('glBlendFuncSeparate','(IIII)V'), ('glActiveTexture','(I)V'), ('glBlendEquation','(I)V'),
    # Desktop attribute stacks (zombie.core.Core, RenderList, RenderTarget, EquippedTextureCreator,
    # DeadBodyAtlas, TextureCombiner). port.shim.GL11 drives port/runtime/gl.mjs, which keeps its
    # own snapshots because WebGL2 has no attribute stack (docs/decisions.md).
    ('glPushAttrib','(I)V'), ('glPopAttrib','()V'), ('glPushClientAttrib','(I)V'),
    ('glPopClientAttrib','()V'),
    # Round 79: WorldMapRenderer sets GL_FILL every frame; WebGL2 only rasterizes filled polygons.
    ('glPolygonMode','(II)V'), ('glTexEnvi','(III)V'),
    # Round 79: VBORenderer.flush disables the conventional VERTEX/COLOR/TEXTURE_COORD arrays.
    ('glEnableClientState','(I)V'), ('glDisableClientState','(I)V'), ('glVertexPointer','(IIIJ)V'),
    ('glGetString','(I)Ljava/lang/String;'), ('glGetStringi','(II)Ljava/lang/String;'),
    ('glGetInteger','(I)I'), ('glGetIntegerv','(I[I)V'), ('glGetIntegerv','(ILjava/nio/IntBuffer;)V'),
    ('glGetFloat','(I)F'), ('glGetFloatv','(I[F)V'), ('glGetFloatv','(ILjava/nio/FloatBuffer;)V'),
    ('glGetBoolean','(I)Z'), ('glGetBooleanv','(ILjava/nio/ByteBuffer;)V')}
# GL11 texture lifecycle: the natives on GL11C that zombie.core.textures.TextureID calls. port.shim.GL11
# implements each one over WebGL2 (port/runtime/gl.mjs), and the GL11/GL11C wrappers reach them
# through these mappings. Desktop-only texture natives (compressed formats, glCopyTexImage,
# glAreTexturesResident, tex-level parameters) stay unmapped and keep the fail-fast raise.
TEXTURE_DIRECT = {('nglGenTextures','(IJ)V'), ('nglDeleteTextures','(IJ)V'), ('glIsTexture','(I)Z'),
    ('glBindTexture','(II)V'), ('glTexParameteri','(III)V'), ('nglTexImage2D','(IIIIIIIIJ)V'),
    ('nglTexSubImage2D','(IIIIIIIIJ)V'), ('nglGetTexImage','(IIIIJ)V')}
# Only the natives on GL15C: the wrappers of GL15/GL15C stay untouched and reach them.
GL15_DIRECT = {('nglGenBuffers','(IJ)V'), ('nglDeleteBuffers','(IJ)V'), ('glBindBuffer','(II)V'),
    ('glIsBuffer','(I)Z'), ('nglBufferData','(IJJI)V'), ('nglBufferSubData','(IJJJ)V'),
    ('nglGetBufferSubData','(IJJJ)V'), ('nglGetBufferParameteriv','(IIJ)V'),
    ('nglGetBufferPointerv','(IIJ)V'), ('nglMapBuffer','(II)J'), ('glUnmapBuffer','(I)Z'),
    ('nglGenQueries','(IJ)V'), ('nglDeleteQueries','(IJ)V'), ('glIsQuery','(I)Z'),
    ('glBeginQuery','(II)V'), ('glEndQuery','(I)V'), ('nglGetQueryiv','(IIJ)V'),
    ('nglGetQueryObjectiv','(IIJ)V'), ('nglGetQueryObjectuiv','(IIJ)V')}


# Only the natives on GL20C: the wrappers of GL20/GL20C stay untouched and reach them. Every
# entry is implemented in port.shim.GL20 over real WebGL2 calls, or raises with its own identity
# when WebGL2 has no equivalent (docs/decisions.md).
GL20_DIRECT = {('glCreateProgram','()I'), ('glDeleteProgram','(I)V'), ('glIsProgram','(I)Z'),
    ('glCreateShader','(I)I'), ('glDeleteShader','(I)V'), ('glIsShader','(I)Z'),
    ('glAttachShader','(II)V'), ('glDetachShader','(II)V'), ('nglShaderSource','(IIJJ)V'),
    ('glCompileShader','(I)V'), ('glLinkProgram','(I)V'), ('glUseProgram','(I)V'),
    ('glValidateProgram','(I)V'), ('nglGetShaderiv','(IIJ)V'), ('nglGetProgramiv','(IIJ)V'),
    ('nglGetShaderInfoLog','(IIJJ)V'), ('nglGetProgramInfoLog','(IIJJ)V'),
    ('nglGetUniformLocation','(IJ)I'), ('nglGetAttribLocation','(IJ)I'),
    ('glUniform1f','(IF)V'), ('glUniform2f','(IFF)V'), ('glUniform3f','(IFFF)V'),
    ('glUniform4f','(IFFFF)V'), ('glUniform1i','(II)V'), ('glUniform2i','(III)V'),
    ('glUniform3i','(IIII)V'), ('glUniform4i','(IIIII)V'), ('nglUniform1fv','(IIJ)V'),
    ('nglUniform2fv','(IIJ)V'), ('nglUniform3fv','(IIJ)V'), ('nglUniform4fv','(IIJ)V'),
    ('nglUniform1iv','(IIJ)V'), ('nglUniform2iv','(IIJ)V'), ('nglUniform3iv','(IIJ)V'),
    ('nglUniform4iv','(IIJ)V'), ('nglUniformMatrix2fv','(IIZJ)V'), ('nglUniformMatrix3fv','(IIZJ)V'),
    ('nglUniformMatrix4fv','(IIZJ)V'), ('nglVertexAttribPointer','(IIIZIJ)V'),
    ('glEnableVertexAttribArray','(I)V'), ('glDisableVertexAttribArray','(I)V'),
    ('glVertexAttrib1f','(IF)V'), ('glVertexAttrib2f','(IFF)V'), ('glVertexAttrib3f','(IFFF)V'),
    ('glVertexAttrib4f','(IFFFF)V'), ('nglGetUniformfv','(IIJ)V'), ('nglGetUniformiv','(IIJ)V'),
    ('nglGetVertexAttribiv','(IIJ)V'), ('nglGetVertexAttribfv','(IIJ)V'),
    ('nglGetVertexAttribdv','(IIJ)V'), ('nglGetVertexAttribPointerv','(IIJ)V'),
    ('nglGetAttachedShaders','(IIJJ)V'), ('nglBindAttribLocation','(IIJ)V'),
    ('nglGetShaderSource','(IIJJ)V'), ('nglGetActiveUniform','(IIIJJJJ)V'),
    ('nglGetActiveAttrib','(IIIJJJJ)V')}


# Only the natives on GL30C: vertex array objects, framebuffers and renderbuffers, all implemented
# in port.shim.GL30 over WebGL2. Desktop-only ones (glBlitFramebuffer, glFramebufferTexture1D/3D,
# glRenderbufferStorageMultisample, the *Parameteriv queries) stay unmapped and keep the fail-fast raise.
GL30_DIRECT = {('nglGenVertexArrays','(IJ)V'), ('nglDeleteVertexArrays','(IJ)V'),
    ('glIsVertexArray','(I)Z'), ('glBindVertexArray','(I)V'),
    ('nglGenFramebuffers','(IJ)V'), ('nglDeleteFramebuffers','(IJ)V'), ('glIsFramebuffer','(I)Z'),
    ('glBindFramebuffer','(II)V'), ('glCheckFramebufferStatus','(I)I'),
    ('glFramebufferTexture2D','(IIIII)V'), ('glFramebufferRenderbuffer','(IIII)V'),
    ('nglGenRenderbuffers','(IJ)V'), ('nglDeleteRenderbuffers','(IJ)V'),
    ('glIsRenderbuffer','(I)Z'), ('glBindRenderbuffer','(II)V'), ('glRenderbufferStorage','(IIII)V')}

# Draw calls the game reaches (VBORenderer, IsoPuddles, IsoWater, WorldMapVBOs, VertexBufferObject,
# VisibilityPolygon2). port.shim.GL11 forwards them to gl.mjs; offsets into the bound element buffer only.
DRAW_DIRECT = {('org.lwjgl.opengl.GL11C','glDrawArrays','(III)V'),
    ('org.lwjgl.opengl.GL11C','nglDrawElements','(IIIJ)V'),
    ('org.lwjgl.opengl.GL12C','nglDrawRangeElements','(IIIIIJ)V')}


def target_for(class_name, method_name, descriptor):
    if (class_name, method_name, descriptor) in DRAW_DIRECT: return SCALAR
    if class_name == 'org.lwjgl.opengl.GL15C':
        return GL15 if (method_name, descriptor) in GL15_DIRECT else None
    if class_name == 'org.lwjgl.opengl.GL20C':
        return GL20 if (method_name, descriptor) in GL20_DIRECT else None
    if class_name == 'org.lwjgl.opengl.GL30C':
        # GL30C owns the VAO/framebuffer/renderbuffer natives, but it also re-exposes the GL11/GL20
        # entrypoints the older mapping already covered (glGetStringi), so those keep their target.
        if (method_name, descriptor) in GL30_DIRECT: return GL30
        return SCALAR if (method_name, descriptor) in SCALAR_DIRECT else None
    return SCALAR if (method_name, descriptor) in SCALAR_DIRECT or (method_name, descriptor) in TEXTURE_DIRECT else None


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
