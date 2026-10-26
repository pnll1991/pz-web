package port.demo.lwjgl;

/** Synthetic desktop API: these bodies must be replaced by the TeaVM policy. */
public final class GL11 {
    public static final int GL_COLOR_BUFFER_BIT = 0x4000;
    public static void glClearColor(float r, float g, float b, float a) { throw new AssertionError("Desktop GL not substituted"); }
    public static void glClear(int mask) { throw new AssertionError("Desktop GL not substituted"); }
    public static void glViewport(int x, int y, int w, int h) { throw new AssertionError("Desktop GL not substituted"); }
    public static int glGetError() { throw new AssertionError("Desktop GL not substituted"); }
}
