package port.shim;

import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Uint8Array;
import port.runtime.memory.NativeMemory;

/**
 * Real WebGL2 wrappers for the scalar entrypoints this backend implements. Every name here is
 * present in port/runtime/gl.mjs PORT_PROVIDED, so a capability address handed out for it can
 * actually be called. Calls injected by later compiler transformers target these wrappers.
 */
public final class GL11 {
    public static final int GL_COLOR_BUFFER_BIT = 0x4000;

    public static void glClearColor(float r, float g, float b, float a) { clearColor(r, g, b, a); }
    public static void glClear(int mask) { clear(mask); }
    public static void glViewport(int x, int y, int w, int h) { viewport(x, y, w, h); }
    public static int glGetError() { return getError(); }

    public static void glEnable(int cap) { capability(cap, 1); }
    public static void glDisable(int cap) { capability(cap, 0); }
    public static void glBlendFunc(int srcFactor, int dstFactor) { call2("blendFunc", srcFactor, dstFactor); }
    public static void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        call4("blendFuncSeparate", srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    public static void glClearStencil(int s) { call1("clearStencil", s); }
    public static void glStencilMask(int mask) { call1("stencilMask", mask); }
    public static void glCullFace(int mode) { call1("cullFace", mode); }
    public static void glFrontFace(int mode) { call1("frontFace", mode); }
    public static void glDepthFunc(int func) { call1("depthFunc", func); }
    public static void glDepthMask(boolean flag) { depthMask(flag); }
    public static void glLineWidth(float width) { lineWidth(width); }
    public static void glPixelStorei(int pname, int param) { call2("pixelStorei", pname, param); }
    public static void glScissor(int x, int y, int width, int height) { call4("scissor", x, y, width, height); }
    public static void glClearDepthf(float depth) { clearDepthf(depth); }
    public static void glClearDepth(double depth) { clearDepthNative(depth); }
    public static void glDepthRange(double near, double far) { depthRangeNative(near, far); }
    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        colorMaskNative(red ? 1 : 0, green ? 1 : 0, blue ? 1 : 0, alpha ? 1 : 0);
    }
    public static void glStencilFunc(int func, int ref, int mask) { stencilFuncNative(func, ref, mask); }
    public static void glStencilOp(int fail, int zfail, int zpass) { stencilOpNative(fail, zfail, zpass); }
    // org.lwjgl.opengl.GL11's own native: the alpha-test state lives in port/runtime/gl.mjs and is
    // reproduced inside the generated fragment shaders (see ALPHA_TEST_FUNCTION in port/runtime/glsl.mjs).
    public static void glAlphaFunc(int func, float ref) { alphaTestNative(func, ref); }
    // GL13C/GL14C natives the game reaches from RenderThread and the GLState wrapper classes.
    public static void glActiveTexture(int texture) { activeTextureNative(texture); }
    public static void glBlendEquation(int mode) { blendEquationNative(mode); }
    // Desktop attribute stacks. WebGL2 has none, so port/runtime/gl.mjs keeps its own snapshots of
    // the state this bridge owns and restores them; bits whose desktop state does not exist here
    // are counted in attribState() instead of being dropped silently (docs/decisions.md).
    public static void glPushAttrib(int mask) { pushAttribNative(mask); }
    public static void glPopAttrib() { popAttribNative(); }
    public static void glPushClientAttrib(int mask) { pushClientAttribNative(mask); }
    public static void glPopClientAttrib() { popClientAttribNative(); }

    // --- Queries the game reads during Core.init (GL tokens answered by port/runtime/gl.mjs) ---
    public static String glGetString(int pname) { return stringNative(pname); }
    public static String glGetStringi(int pname, int index) { return stringIndexedNative(pname, index); }
    public static int glGetInteger(int pname) { return intParameterNative(pname, 0); }
    public static int glGetIntegeri(int pname, int index) { return intParameterNative(pname, index); }
    public static float glGetFloat(int pname) { return floatParameterNative(pname, 0); }
    public static boolean glGetBoolean(int pname) { return boolParameterNative(pname, 0) != 0; }
    public static void glGetIntegerv(int pname, int[] data) {
        for (int i = 0; i < data.length; i++) data[i] = intParameterNative(pname, i);
    }
    public static void glGetIntegerv(int pname, java.nio.IntBuffer data) {
        long base = port.runtime.memory.NativeMemory.memAddress(data);
        for (int i = 0; i < data.remaining(); i++)
            port.runtime.memory.NativeMemory.memPutInt(base + 4L * i, intParameterNative(pname, i));
    }
    public static void glGetFloatv(int pname, float[] data) {
        for (int i = 0; i < data.length; i++) data[i] = floatParameterNative(pname, i);
    }
    public static void glGetFloatv(int pname, java.nio.FloatBuffer data) {
        long base = port.runtime.memory.NativeMemory.memAddress(data);
        for (int i = 0; i < data.remaining(); i++)
            port.runtime.memory.NativeMemory.memPutFloat(base + 4L * i, floatParameterNative(pname, i));
    }
    public static void glGetBooleanv(int pname, java.nio.ByteBuffer data) {
        long base = port.runtime.memory.NativeMemory.memAddress(data);
        for (int i = 0; i < data.remaining(); i++)
            port.runtime.memory.NativeMemory.memPutByte(base + i, (byte) boolParameterNative(pname, i));
    }

    @JSBody(params={"r","g","b","a"}, script="globalThis.pzRuntime.gl.clearColor(r,g,b,a);")
    private static native void clearColor(float r, float g, float b, float a);
    @JSBody(params="mask", script="globalThis.pzRuntime.gl.clear(mask);")
    private static native void clear(int mask);
    @JSBody(params={"x","y","w","h"}, script="globalThis.pzRuntime.gl.viewport(x,y,w,h);")
    private static native void viewport(int x, int y, int w, int h);
    @JSBody(script="return globalThis.pzRuntime.gl.getError();")
    private static native int getError();

    @JSBody(params={"name","a"}, script="globalThis.pzRuntime.gl.call(name,a);")
    private static native void call1(String name, int a);
    /**
     * glEnable/glDisable receive desktop tokens. port/runtime/gl.mjs translates them to the
     * WebGL2 capability constant when one exists, and raises with the token otherwise, so the
     * log shows which fixed-function feature this backend cannot provide.
     */
    @org.teavm.jso.JSBody(params={"cap","on"}, script="globalThis.pzRuntime.gl.capability(cap,on===1);")
    private static native void capability(int cap, int on);
    @JSBody(params={"name","a","b"}, script="globalThis.pzRuntime.gl.call(name,a,b);")
    private static native void call2(String name, int a, int b);
    @JSBody(params={"name","a","b","c","d"}, script="globalThis.pzRuntime.gl.call(name,a,b,c,d);")
    private static native void call4(String name, int a, int b, int c, int d);
    @JSBody(params="flag", script="globalThis.pzRuntime.gl.call('depthMask',flag ? 1 : 0);")
    private static native void depthMask(boolean flag);
    @JSBody(params="width", script="globalThis.pzRuntime.gl.call('lineWidth',width);")
    private static native void lineWidth(float width);
    @JSBody(params="depth", script="globalThis.pzRuntime.gl.call('clearDepthf',depth);")
    private static native void clearDepthf(float depth);
    @JSBody(params="depth", script="globalThis.pzRuntime.gl.clearDepth(depth);")
    private static native void clearDepthNative(double depth);
    @JSBody(params="mask", script="globalThis.pzRuntime.gl.pushAttrib(mask);")
    private static native void pushAttribNative(int mask);
    @JSBody(script="globalThis.pzRuntime.gl.popAttrib();")
    private static native void popAttribNative();
    @JSBody(params="mask", script="globalThis.pzRuntime.gl.pushClientAttrib(mask);")
    private static native void pushClientAttribNative(int mask);
    @JSBody(script="globalThis.pzRuntime.gl.popClientAttrib();")
    private static native void popClientAttribNative();
    @JSBody(params={"near","far"}, script="globalThis.pzRuntime.gl.depthRange(near,far);")
    private static native void depthRangeNative(double near, double far);
    @JSBody(params={"r","g","b","a"}, script="globalThis.pzRuntime.gl.colorMask(r,g,b,a);")
    private static native void colorMaskNative(int r, int g, int b, int a);
    @JSBody(params={"func","ref","mask"}, script="globalThis.pzRuntime.gl.stencilFunc(func,ref,mask);")
    private static native void stencilFuncNative(int func, int ref, int mask);
    @JSBody(params={"fail","zfail","zpass"}, script="globalThis.pzRuntime.gl.stencilOp(fail,zfail,zpass);")
    private static native void stencilOpNative(int fail, int zfail, int zpass);
    @JSBody(params={"func","ref"}, script="globalThis.pzRuntime.gl.alphaTest(func,ref);")
    private static native void alphaTestNative(int func, float ref);
    @JSBody(params="texture", script="globalThis.pzRuntime.gl.activeTexture(texture);")
    private static native void activeTextureNative(int texture);
    @JSBody(params="mode", script="globalThis.pzRuntime.gl.blendEquation(mode);")
    private static native void blendEquationNative(int mode);
    @JSBody(params="pname", script="return globalThis.pzRuntime.gl.stringParameter(pname);")
    private static native String stringNative(int pname);
    @JSBody(params={"pname","index"}, script="return globalThis.pzRuntime.gl.stringParameterIndexed(pname,index);")
    private static native String stringIndexedNative(int pname, int index);
    @JSBody(params={"pname","index"}, script="return globalThis.pzRuntime.gl.intParameter(pname,index);")
    private static native int intParameterNative(int pname, int index);
    @JSBody(params={"pname","index"}, script="return globalThis.pzRuntime.gl.floatParameter(pname,index);")
    private static native float floatParameterNative(int pname, int index);
    @JSBody(params={"pname","index"}, script="return globalThis.pzRuntime.gl.boolParameter(pname,index);")
    private static native int boolParameterNative(int pname, int index);

    // --- Texture lifecycle (org.lwjgl.opengl.GL11C natives used by zombie.core.textures.TextureID)
    // Descriptors match the natives exactly so tools/glgen/generate.py can map them. Pixel bytes
    // move through the native arena, the same way port.shim.GL15 does for buffers.
    public static void nglGenTextures(int count, long textures) {
        if (count <= 0) return;
        Int32Array handles = genTexturesNative(count);
        for (int i = 0; i < count; i++) NativeMemory.memPutInt(textures + 4L * i, handles.get(i));
    }
    public static void nglDeleteTextures(int count, long textures) {
        if (count <= 0) return;
        Int32Array handles = Int32Array.create(count);
        for (int i = 0; i < count; i++) handles.set(i, NativeMemory.memGetInt(textures + 4L * i));
        deleteTexturesNative(handles);
    }
    public static boolean glIsTexture(int texture) { return isTextureNative(texture) != 0; }
    public static void glBindTexture(int target, int texture) { bindTextureNative(target, texture); }
    public static void glTexParameteri(int target, int pname, int param) { texParameteriNative(target, pname, param); }
    public static void nglTexImage2D(int target, int level, int internalformat, int width, int height,
            int border, int format, int type, long data) {
        int bytes = textureBytes(format, type, width, height);
        texImage2DNative(target, level, internalformat, width, height, border, format, type,
            data == 0L ? null : arena(data, bytes));
    }
    public static void nglTexSubImage2D(int target, int level, int xoffset, int yoffset, int width,
            int height, int format, int type, long data) {
        texSubImage2DNative(target, level, xoffset, yoffset, width, height, format, type,
            arena(data, textureBytes(format, type, width, height)));
    }
    public static void nglGetTexImage(int target, int level, int format, int type, long data) {
        ByteBuffer view = NativeMemory.memByteBuffer(data, getTexImageBytesNative(target, level));
        getTexImageNative(target, level, format, type, Uint8Array.fromJavaBuffer(view));
    }
    /**
     * Byte count of an image block: channels x width x height, where the channel count follows from
     * the format (RGBA=4, RGB=3, RED/R=1, RG/DEPTH=1 as WebGL2 stores them). A format this backend
     * cannot upload raises rather than guessing a stride (AGENTS.md).
     */
    private static int textureBytes(int format, int type, int width, int height) {
        int channels;
        switch (format) {
            case 6408: channels = 4; break; // GL_RGBA
            case 6407: channels = 3; break; // GL_RGB
            case 6403: case 33319: case 6402: channels = 1; break; // GL_RED, GL_RG, GL_DEPTH_COMPONENT
            default: throw new UnsupportedOperationException(
                "Unknown pixel format for a texture upload: 0x" + Integer.toHexString(format));
        }
        int element = type == 5121 || type == 5120 ? 1 : type == 5122 || type == 5123 ? 2
            : type == 5124 || type == 5125 || type == 5126 ? 4 : 0;
        if (element == 0) throw new UnsupportedOperationException(
            "Unknown pixel type for a texture upload: 0x" + Integer.toHexString(type));
        long bytes = (long) channels * width * height * element;
        if (bytes < 0 || bytes > Integer.MAX_VALUE) throw new IllegalArgumentException("Texture size: " + bytes);
        return (int) bytes;
    }
    private static Uint8Array arena(long address, int bytes) {
        return Uint8Array.fromJavaBuffer(NativeMemory.memByteBuffer(address, bytes));
    }

    @JSBody(params="count", script="return globalThis.pzRuntime.gl.genTextures(count);")
    private static native Int32Array genTexturesNative(int count);
    @JSBody(params="handles", script="globalThis.pzRuntime.gl.deleteTextures(handles);")
    private static native void deleteTexturesNative(Int32Array handles);
    @JSBody(params="texture", script="return globalThis.pzRuntime.gl.isTexture(texture) ? 1 : 0;")
    private static native int isTextureNative(int texture);
    @JSBody(params={"target","texture"}, script="globalThis.pzRuntime.gl.bindTexture(target,texture);")
    private static native void bindTextureNative(int target, int texture);
    @JSBody(params={"target","pname","param"}, script="globalThis.pzRuntime.gl.texParameteri(target,pname,param);")
    private static native void texParameteriNative(int target, int pname, int param);
    @JSBody(params={"target","level","internalformat","width","height","border","format","type","data"},
        script="globalThis.pzRuntime.gl.texImage2D(target,level,internalformat,width,height,border,format,type,data);")
    private static native void texImage2DNative(int target, int level, int internalformat, int width,
        int height, int border, int format, int type, Uint8Array data);
    @JSBody(params={"target","level","xoffset","yoffset","width","height","format","type","data"},
        script="globalThis.pzRuntime.gl.texSubImage2D(target,level,xoffset,yoffset,width,height,format,type,data);")
    private static native void texSubImage2DNative(int target, int level, int xoffset, int yoffset,
        int width, int height, int format, int type, Uint8Array data);
    @JSBody(params={"target","level","format","type","data"},
        script="globalThis.pzRuntime.gl.getTexImage(target,level,format,type,data);")
    private static native void getTexImageNative(int target, int level, int format, int type, Uint8Array data);
    @JSBody(params={"target","level"}, script="return globalThis.pzRuntime.gl.texLevelBytes(level);")
    private static native int getTexImageBytesNative(int target, int level);

    // --- Draw calls (GL11C/GL12C natives reached by VBORenderer, IsoPuddles, IsoWater, WorldMapVBOs,
    // VertexBufferObject and VisibilityPolygon2). With an ELEMENT_ARRAY_BUFFER bound, desktop GL
    // reads `indices` as a byte offset into it, which is exactly WebGL2's offset argument. A client
    // memory pointer (no element buffer bound) has no WebGL2 equivalent: gl.mjs raises for it.
    public static void glDrawArrays(int mode, int first, int count) { drawArraysNative(mode, first, count); }
    public static void nglDrawElements(int mode, int count, int type, long indices) {
        drawElementsNative(mode, count, type, offset(indices, "org.lwjgl.opengl.GL11C#nglDrawElements(IIIJ)V"));
    }
    public static void nglDrawRangeElements(int mode, int start, int end, int count, int type, long indices) {
        drawRangeElementsNative(mode, start, end, count, type,
            offset(indices, "org.lwjgl.opengl.GL12C#nglDrawRangeElements(IIIIIJ)V"));
    }
    private static int offset(long indices, String identity) {
        if (indices < 0 || indices > Integer.MAX_VALUE)
            throw new UnsupportedOperationException("WebGL2 draws only from a bound ELEMENT_ARRAY_BUFFER offset, got "
                + indices + ": " + identity);
        return (int) indices;
    }
    @JSBody(params={"mode","first","count"}, script="globalThis.pzRuntime.gl.drawArrays(mode,first,count);")
    private static native void drawArraysNative(int mode, int first, int count);
    @JSBody(params={"mode","count","type","indices"},
        script="globalThis.pzRuntime.gl.drawElements(mode,count,type,indices);")
    private static native void drawElementsNative(int mode, int count, int type, int indices);
    @JSBody(params={"mode","start","end","count","type","indices"},
        script="globalThis.pzRuntime.gl.drawRangeElements(mode,start,end,count,type,indices);")
    private static native void drawRangeElementsNative(int mode, int start, int end, int count, int type, int indices);
}
