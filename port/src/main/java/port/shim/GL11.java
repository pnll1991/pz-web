package port.shim;

import org.teavm.jso.JSBody;

/** Initial verified subset; not yet a drop-in LWJGL class replacement. */
public final class GL11 {
    public static final int GL_COLOR_BUFFER_BIT = 0x4000;
    // Normal Java wrappers keep JSO call lowering inside the original class body.
    // Calls injected by later compiler transformers must target these wrappers.
    public static void glClearColor(float r, float g, float b, float a) { clearColor(r, g, b, a); }
    public static void glClear(int mask) { clear(mask); }
    public static void glViewport(int x, int y, int w, int h) { viewport(x, y, w, h); }
    public static int glGetError() { return getError(); }
    @JSBody(params={"r","g","b","a"}, script="globalThis.pzRuntime.gl.clearColor(r,g,b,a);")
    private static native void clearColor(float r, float g, float b, float a);
    @JSBody(params="mask", script="globalThis.pzRuntime.gl.clear(mask);")
    private static native void clear(int mask);
    @JSBody(params={"x","y","w","h"}, script="globalThis.pzRuntime.gl.viewport(x,y,w,h);")
    private static native void viewport(int x, int y, int w, int h);
    @JSBody(script="return globalThis.pzRuntime.gl.getError();")
    private static native int getError();
}
