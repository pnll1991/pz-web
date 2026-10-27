package port.shim;

import org.teavm.jso.JSBody;

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

    public static void glEnable(int cap) { call1("enable", cap); }
    public static void glDisable(int cap) { call1("disable", cap); }
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
}
