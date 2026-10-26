package port.demo;

/** Own native declarations used to test per-method GL forwarding in the compiler. */
public final class GLMappingProbe {
    public static native void glClearColor(float r, float g, float b, float a);
    public static native void glClear(int mask);
    public static native int glGetError();
}
